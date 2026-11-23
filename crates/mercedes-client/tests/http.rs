//! Mocked HTTP tests. No request in this file reaches Mercedes.

use std::time::{Duration, SystemTime};

use mercedes_client::{AuthError, Endpoints, MercedesClient, PkceLogin, TokenSet, refresh};
use url::Url;
use vehicle_core::{Capability, ProviderError, VehicleId, VehicleProvider};
use wiremock::matchers::{body_string_contains, header, header_exists, method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

const VEHICLES: &str = include_str!("fixtures/vehicles.json");
const COMMANDS: &str = include_str!("fixtures/capabilities_commands.json");

fn endpoints(server: &MockServer) -> Endpoints {
    let base = Url::parse(&server.uri()).expect("mock url");
    Endpoints {
        login: base.clone(),
        rest: base.clone(),
        widget: base,
    }
}

fn token_body(access: &str, refresh: Option<&str>) -> serde_json::Value {
    let mut body =
        serde_json::json!({ "access_token": access, "expires_in": 3600, "token_type": "Bearer" });
    if let Some(r) = refresh {
        body["refresh_token"] = r.into();
    }
    body
}

async fn login(server: &MockServer) -> TokenSet {
    Mock::given(method("POST"))
        .and(path("/as/token.oauth2"))
        .and(body_string_contains("grant_type=authorization_code"))
        .and(body_string_contains("code=the-code"))
        .and(body_string_contains("code_verifier="))
        .and(body_string_contains(
            "redirect_uri=rismycar%3A%2F%2Flogin-callback",
        ))
        .and(header("x-applicationname", "mycar-store-ece"))
        .respond_with(
            ResponseTemplate::new(200).set_body_json(token_body("access-1", Some("refresh-1"))),
        )
        .expect(1)
        .mount(server)
        .await;
    let endpoints = endpoints(server);
    PkceLogin::new(&endpoints)
        .expect("pkce")
        .complete(
            &reqwest::Client::new(),
            &endpoints,
            "rismycar://login-callback?code=the-code",
        )
        .await
        .expect("login")
}

#[tokio::test]
async fn login_exchanges_code_for_tokens() {
    let server = MockServer::start().await;
    let before = SystemTime::now();
    let tokens = login(&server).await;
    assert!(tokens.can_refresh());
    assert!(!tokens.expires_within(before, Duration::from_secs(3000)));
}

#[tokio::test]
async fn rejected_code_exchange_is_reported() {
    let server = MockServer::start().await;
    Mock::given(path("/as/token.oauth2"))
        .respond_with(ResponseTemplate::new(400).set_body_string(r#"{"error":"invalid_grant"}"#))
        .mount(&server)
        .await;
    let endpoints = endpoints(&server);
    let result = PkceLogin::new(&endpoints)
        .expect("pkce")
        .complete(&reqwest::Client::new(), &endpoints, "code123")
        .await;
    assert!(matches!(result, Err(AuthError::Rejected(400))));
}

#[tokio::test]
async fn refresh_keeps_previous_refresh_token_when_omitted() {
    let server = MockServer::start().await;
    let tokens = login(&server).await;
    Mock::given(method("POST"))
        .and(path("/as/token.oauth2"))
        .and(body_string_contains("grant_type=refresh_token"))
        .and(body_string_contains("refresh_token=refresh-1"))
        .and(header_exists("x-device-id"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("access-2", None)))
        .expect(1)
        .mount(&server)
        .await;

    let refreshed = refresh(&reqwest::Client::new(), &endpoints(&server), &tokens)
        .await
        .expect("refresh");
    assert!(refreshed.can_refresh());
    assert_ne!(refreshed, tokens);
}

#[tokio::test]
async fn refresh_rate_limit_is_distinct() {
    let server = MockServer::start().await;
    let tokens = login(&server).await;
    Mock::given(body_string_contains("grant_type=refresh_token"))
        .respond_with(ResponseTemplate::new(429))
        .mount(&server)
        .await;
    let result = refresh(&reqwest::Client::new(), &endpoints(&server), &tokens).await;
    assert!(matches!(result, Err(AuthError::RateLimited)));
}

#[tokio::test]
async fn lists_vehicles_with_app_headers() {
    let server = MockServer::start().await;
    let tokens = login(&server).await;
    Mock::given(method("GET"))
        .and(path("/v2/vehicles"))
        .and(header("authorization", "Bearer access-1"))
        .and(header("x-applicationname", "mycar-store-ece"))
        .and(header("ris-os-name", "ios"))
        .and(header("content-type", "application/json; charset=UTF-8"))
        .and(header_exists("x-sessionid"))
        .and(header_exists("x-trackingid"))
        .respond_with(ResponseTemplate::new(200).set_body_string(VEHICLES))
        .expect(1)
        .mount(&server)
        .await;

    let client = MercedesClient::new(reqwest::Client::new(), endpoints(&server), &tokens);
    let vehicles = client.vehicles().await.expect("vehicles");
    assert_eq!(vehicles.len(), 3);
}

#[tokio::test]
async fn capabilities_are_fetched_per_vehicle() {
    let server = MockServer::start().await;
    let tokens = login(&server).await;
    Mock::given(path("/v1/vehicle/TESTVIN00000000001/capabilities/commands"))
        .respond_with(ResponseTemplate::new(200).set_body_string(COMMANDS))
        .mount(&server)
        .await;

    let client = MercedesClient::new(reqwest::Client::new(), endpoints(&server), &tokens);
    let caps = client
        .capabilities(&VehicleId::new("TESTVIN00000000001"))
        .await
        .expect("capabilities");
    assert!(caps.contains(Capability::ClimatePreconditioning));
}

#[tokio::test]
async fn http_errors_map_to_typed_provider_errors() {
    let server = MockServer::start().await;
    let tokens = login(&server).await;
    let client = MercedesClient::new(reqwest::Client::new(), endpoints(&server), &tokens);

    for (status, check) in [
        (
            401,
            (|e| matches!(e, ProviderError::SessionExpired)) as fn(&ProviderError) -> bool,
        ),
        (403, |e| matches!(e, ProviderError::Forbidden)),
        (404, |e| matches!(e, ProviderError::VehicleNotFound)),
        (429, |e| matches!(e, ProviderError::RateLimited)),
        (500, |e| matches!(e, ProviderError::Protocol(_))),
    ] {
        server.reset().await;
        Mock::given(path("/v2/vehicles"))
            .respond_with(ResponseTemplate::new(status))
            .mount(&server)
            .await;
        let err = client.vehicles().await.expect_err("should fail");
        assert!(check(&err), "status {status} mapped to {err:?}");
    }
}

#[tokio::test]
async fn malformed_json_is_protocol_error_without_body() {
    let server = MockServer::start().await;
    let tokens = login(&server).await;
    Mock::given(path("/v2/vehicles"))
        .respond_with(ResponseTemplate::new(200).set_body_string("SECRET-VIN-123 not json"))
        .mount(&server)
        .await;
    let client = MercedesClient::new(reqwest::Client::new(), endpoints(&server), &tokens);
    let err = client.vehicles().await.expect_err("should fail");
    assert!(matches!(err, ProviderError::Protocol(_)));
    assert!(!err.to_string().contains("SECRET"));
}

#[tokio::test]
async fn vehicle_attributes_are_returned_raw() {
    let server = MockServer::start().await;
    let tokens = login(&server).await;
    Mock::given(path("/v1/vehicle/TESTVIN00000000001/vehicleattributes"))
        .respond_with(ResponseTemplate::new(200).set_body_bytes(vec![0x0a, 0x01, 0x41]))
        .mount(&server)
        .await;
    let client = MercedesClient::new(reqwest::Client::new(), endpoints(&server), &tokens);
    let id = VehicleId::new("TESTVIN00000000001");
    assert_eq!(
        client.vehicle_attributes_raw(&id).await.expect("raw"),
        vec![0x0a, 0x01, 0x41]
    );
    assert!(matches!(
        client.state(&id).await,
        Err(ProviderError::NotSupported(_))
    ));
}
