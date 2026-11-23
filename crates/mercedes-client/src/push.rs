//! One-shot read of vehicle status from the websocket push channel.
//!
//! Connect, wait for the first status update containing the requested
//! vehicle, acknowledge it (as mbapi2020 `client.py` `on_data` does), then
//! close. No reconnects or retries: repeated handshakes are what trigger HTTP
//! 429 account blocks (knowledge/mercedes/protocol-research.md).

use std::net::IpAddr;
use std::sync::Arc;
use std::time::{Duration, SystemTime};

use futures_util::{SinkExt, StreamExt};
use prost::Message as _;
use reqwest::header::HeaderMap;
use rustls_platform_verifier::BuilderVerifierExt;
use tokio::io::{AsyncRead, AsyncWrite};
use tokio::net::TcpStream;
use tokio_tungstenite::tungstenite::{self, Message, client::IntoClientRequest};
use tokio_tungstenite::{WebSocketStream, client_async};
use url::Url;
use vehicle_core::{ProviderError, VehicleId, VehicleState};

use crate::proto::{self, client_message, push_message};
use crate::status;

pub(crate) async fn fetch_state(
    url: &Url,
    headers: HeaderMap,
    id: &VehicleId,
    timeout: Duration,
) -> Result<VehicleState, ProviderError> {
    let deadline = tokio::time::Instant::now() + timeout;
    let mut request = url
        .as_str()
        .into_client_request()
        .map_err(|_| ProviderError::Protocol("invalid websocket URL".into()))?;
    request.headers_mut().extend(headers);

    let host = url
        .host_str()
        .ok_or_else(|| ProviderError::Protocol("websocket URL has no host".into()))?
        .to_owned();
    let port = url
        .port_or_known_default()
        .ok_or_else(|| ProviderError::Protocol("websocket URL has no port".into()))?;

    let use_tls = match url.scheme() {
        "wss" => true,
        // Plain websockets only for local test servers.
        "ws" if host.parse::<IpAddr>().is_ok_and(|ip| ip.is_loopback()) => false,
        _ => {
            return Err(ProviderError::Protocol(
                "refusing non-TLS websocket URL".into(),
            ));
        }
    };

    let connect = async {
        let tcp = TcpStream::connect((host.as_str(), port))
            .await
            .map_err(|e| ProviderError::Network(Box::new(e)))?;
        if use_tls {
            let name = rustls::pki_types::ServerName::try_from(host.clone())
                .map_err(|_| ProviderError::Protocol("invalid TLS server name".into()))?;
            let tls = tls_connector()?
                .connect(name, tcp)
                .await
                .map_err(|e| ProviderError::Network(Box::new(e)))?;
            let (ws, _) = client_async(request, tls).await.map_err(handshake_error)?;
            Ok(Either::Tls(ws))
        } else {
            let (ws, _) = client_async(request, tcp).await.map_err(handshake_error)?;
            Ok(Either::Plain(ws))
        }
    };
    let ws = tokio::time::timeout_at(deadline, connect)
        .await
        .map_err(|_| ProviderError::Protocol("websocket connect timed out".into()))??;

    match ws {
        Either::Tls(ws) => read_first_status(ws, id, deadline).await,
        Either::Plain(ws) => read_first_status(ws, id, deadline).await,
    }
}

enum Either<A, B> {
    Tls(A),
    Plain(B),
}

async fn read_first_status<S>(
    mut ws: WebSocketStream<S>,
    id: &VehicleId,
    deadline: tokio::time::Instant,
) -> Result<VehicleState, ProviderError>
where
    S: AsyncRead + AsyncWrite + Unpin,
{
    let mut partial = None;
    let result = loop {
        let frame = match tokio::time::timeout_at(deadline, ws.next()).await {
            Err(_) | Ok(None) => break None,
            Ok(Some(Err(e))) => {
                let _ = ws.close(None).await;
                return Err(ProviderError::Network(Box::new(e)));
            }
            Ok(Some(Ok(frame))) => frame,
        };
        let Message::Binary(bytes) = frame else {
            continue; // pings are answered by tungstenite
        };
        let Ok(push) = proto::PushMessage::decode(bytes) else {
            tracing::debug!("skipping undecodable push message");
            continue;
        };
        let now = SystemTime::now();
        let (ack, found) = match push.msg {
            Some(push_message::Msg::VehicleStatusUpdates(u)) => (
                client_message::Msg::AcknowledgeVehicleStatusUpdates(
                    proto::AcknowledgeVehicleStatusUpdates {
                        sequence_number: u.sequence_number,
                    },
                ),
                u.vehicle_status_updates
                    .get(id.as_str())
                    .map(|v| (v.full_update, status::from_vsu(id.clone(), now, v))),
            ),
            Some(push_message::Msg::VepUpdates(u)) => (
                client_message::Msg::AcknowledgeVepUpdatesByVin(
                    proto::AcknowledgeVepUpdatesByVin {
                        sequence_number: u.sequence_number,
                    },
                ),
                u.updates
                    .get(id.as_str())
                    .map(|v| (v.full_update, status::from_vep(id.clone(), now, v))),
            ),
            None => continue, // other message types are not needed for a one-shot read
        };
        if let Some((full, _)) = &found {
            let kind = match ack {
                client_message::Msg::AcknowledgeVehicleStatusUpdates(_) => "vehicle_status_updates",
                client_message::Msg::AcknowledgeVepUpdatesByVin(_) => "vep_updates",
            };
            tracing::debug!(
                kind,
                full_update = full,
                "status update for vehicle received"
            );
        }
        let ack = proto::ClientMessage { msg: Some(ack) }.encode_to_vec();
        if let Err(e) = ws.send(Message::Binary(ack.into())).await {
            return Err(ProviderError::Network(Box::new(e)));
        }
        match found {
            Some((true, state)) => break Some(state),
            Some((false, state)) => partial = Some(state),
            None => {}
        }
    };
    let _ = ws.close(None).await;
    result.or(partial).ok_or_else(|| {
        ProviderError::Protocol("no status update for this vehicle before timeout".into())
    })
}

fn tls_connector() -> Result<tokio_rustls::TlsConnector, ProviderError> {
    let provider = Arc::new(rustls::crypto::aws_lc_rs::default_provider());
    let config = rustls::ClientConfig::builder_with_provider(provider)
        .with_safe_default_protocol_versions()
        .and_then(|b| b.with_platform_verifier())
        .map_err(|e| ProviderError::Network(Box::new(e)))?
        .with_no_client_auth();
    Ok(tokio_rustls::TlsConnector::from(Arc::new(config)))
}

fn handshake_error(e: tungstenite::Error) -> ProviderError {
    match e {
        tungstenite::Error::Http(response) => match response.status().as_u16() {
            401 => ProviderError::SessionExpired,
            403 => ProviderError::Forbidden,
            429 => ProviderError::RateLimited,
            s => ProviderError::Protocol(format!("websocket handshake HTTP {s}")),
        },
        other => ProviderError::Network(Box::new(other)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::proto::{Int64RatioAttribute, VehicleStatusUpdate, VehicleStatusUpdates};
    use tokio::net::TcpListener;
    use tokio_tungstenite::accept_async;

    fn vsu(vin: &str, full: bool, soc: i64) -> Vec<u8> {
        let mut updates = VehicleStatusUpdates {
            sequence_number: 7,
            ..Default::default()
        };
        updates.vehicle_status_updates.insert(
            vin.into(),
            VehicleStatusUpdate {
                fin_or_vin: vin.into(),
                full_update: full,
                soc: Some(Int64RatioAttribute {
                    value: soc,
                    metadata: None,
                    unit: 1,
                }),
                ..Default::default()
            },
        );
        proto::PushMessage {
            msg: Some(push_message::Msg::VehicleStatusUpdates(updates)),
        }
        .encode_to_vec()
    }

    async fn server(frames: Vec<Vec<u8>>) -> (Url, tokio::task::JoinHandle<Vec<Vec<u8>>>) {
        let listener = TcpListener::bind("127.0.0.1:0").await.expect("bind");
        let url = Url::parse(&format!(
            "ws://{}/v2/ws",
            listener.local_addr().expect("addr")
        ))
        .expect("url");
        let task = tokio::spawn(async move {
            let (tcp, _) = listener.accept().await.expect("accept");
            let mut ws = accept_async(tcp).await.expect("handshake");
            let mut received = Vec::new();
            for f in frames {
                ws.send(Message::Binary(f.into())).await.expect("send");
            }
            while let Some(Ok(msg)) = ws.next().await {
                if let Message::Binary(b) = msg {
                    received.push(b.to_vec());
                }
            }
            received
        });
        (url, task)
    }

    #[tokio::test]
    async fn waits_for_full_update_of_requested_vehicle_and_acks() {
        let (url, task) = server(vec![
            vsu("OTHERVIN", true, 10),
            vsu("TESTVIN", false, 20),
            vsu("TESTVIN", true, 30),
        ])
        .await;
        let state = fetch_state(
            &url,
            HeaderMap::new(),
            &VehicleId::new("TESTVIN"),
            Duration::from_secs(5),
        )
        .await
        .expect("state");
        assert_eq!(
            state.battery.state_of_charge.map(|r| r.value.get()),
            Some(30.0)
        );

        let acks = task.await.expect("server");
        assert_eq!(acks.len(), 3, "every status message is acknowledged");
        let ack = proto::ClientMessage::decode(acks[0].as_slice()).expect("ack");
        assert_eq!(
            ack.msg,
            Some(client_message::Msg::AcknowledgeVehicleStatusUpdates(
                proto::AcknowledgeVehicleStatusUpdates { sequence_number: 7 }
            ))
        );
    }

    #[tokio::test]
    async fn falls_back_to_partial_update_at_timeout() {
        let (url, _task) = server(vec![vsu("TESTVIN", false, 42)]).await;
        let state = fetch_state(
            &url,
            HeaderMap::new(),
            &VehicleId::new("TESTVIN"),
            Duration::from_millis(500),
        )
        .await
        .expect("state");
        assert_eq!(
            state.battery.state_of_charge.map(|r| r.value.get()),
            Some(42.0)
        );
    }

    #[tokio::test]
    async fn times_out_without_update() {
        let (url, _task) = server(vec![vsu("OTHERVIN", true, 10)]).await;
        let err = fetch_state(
            &url,
            HeaderMap::new(),
            &VehicleId::new("TESTVIN"),
            Duration::from_millis(300),
        )
        .await
        .expect_err("timeout");
        assert!(matches!(err, ProviderError::Protocol(_)));
    }

    #[tokio::test]
    async fn handshake_429_is_rate_limited() {
        let listener = TcpListener::bind("127.0.0.1:0").await.expect("bind");
        let url = Url::parse(&format!(
            "ws://{}/v2/ws",
            listener.local_addr().expect("addr")
        ))
        .expect("url");
        tokio::spawn(async move {
            use tokio::io::{AsyncReadExt, AsyncWriteExt};
            let (mut tcp, _) = listener.accept().await.expect("accept");
            let mut buf = [0u8; 2048];
            let _ = tcp.read(&mut buf).await;
            let _ = tcp
                .write_all(b"HTTP/1.1 429 Too Many Requests\r\ncontent-length: 0\r\n\r\n")
                .await;
        });
        let err = fetch_state(
            &url,
            HeaderMap::new(),
            &VehicleId::new("X"),
            Duration::from_secs(5),
        )
        .await
        .expect_err("429");
        assert!(matches!(err, ProviderError::RateLimited));
    }

    #[test]
    fn tls_config_builds_with_platform_verifier() {
        assert!(tls_connector().is_ok());
    }

    #[tokio::test]
    async fn refuses_plain_websocket_to_remote_host() {
        let url = Url::parse("ws://example.com/v2/ws").expect("url");
        let err = fetch_state(
            &url,
            HeaderMap::new(),
            &VehicleId::new("X"),
            Duration::from_millis(1),
        )
        .await
        .expect_err("refused");
        assert!(matches!(err, ProviderError::Protocol(_)));
    }
}
