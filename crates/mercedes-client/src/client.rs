use reqwest::StatusCode;
use url::Url;
use vehicle_core::{
    Capabilities, ProviderError, Vehicle, VehicleId, VehicleProvider, VehicleState,
};

use crate::auth::TokenSet;
use crate::endpoints::{self, Endpoints};
use crate::wire;

/// A raw Mercedes command capability (`commandName` / `isAvailable`).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CommandCapability {
    pub name: String,
    pub available: bool,
}

/// Authenticated client for one session. Construct with a non-expired
/// [`TokenSet`]; refreshing and persisting tokens is the caller's job, so the
/// platform layer stays in control of secure storage.
pub struct MercedesClient {
    http: reqwest::Client,
    endpoints: Endpoints,
    access_token: String,
    session_id: String,
}

impl MercedesClient {
    pub fn new(http: reqwest::Client, endpoints: Endpoints, tokens: &TokenSet) -> Self {
        Self {
            http,
            endpoints,
            access_token: tokens.access_token().to_owned(),
            session_id: endpoints::new_uuid(),
        }
    }

    /// `GET {rest}/v1/vehicle/{vin}/capabilities/commands`
    pub async fn command_capabilities(
        &self,
        id: &VehicleId,
    ) -> Result<Vec<CommandCapability>, ProviderError> {
        let url = endpoints::join(
            &self.endpoints.rest,
            &["v1", "vehicle", id.as_str(), "capabilities", "commands"],
        );
        let body: wire::CommandsResponse = self.get_json(url).await?;
        Ok(body.into_commands())
    }

    /// `GET {widget}/v1/vehicle/{vin}/vehicleattributes` â€” undecoded protobuf.
    ///
    /// The message type is disputed between reference implementations
    /// (`VehicleStatusUpdate` vs `VEPUpdate`); decoding waits for an
    /// authorized capture. See knowledge/mercedes/vehicle-state.md.
    pub async fn vehicle_attributes_raw(&self, id: &VehicleId) -> Result<Vec<u8>, ProviderError> {
        let url = endpoints::join(
            &self.endpoints.widget,
            &["v1", "vehicle", id.as_str(), "vehicleattributes"],
        );
        let response = self.get(url).await?;
        let bytes = response.bytes().await.map_err(network)?;
        Ok(bytes.to_vec())
    }

    async fn get_json<T: serde::de::DeserializeOwned>(&self, url: Url) -> Result<T, ProviderError> {
        let response = self.get(url).await?;
        let bytes = response.bytes().await.map_err(network)?;
        // Never include the body in the error: it can contain VINs or personal data.
        serde_json::from_slice(&bytes)
            .map_err(|e| ProviderError::Protocol(format!("invalid JSON at line {}", e.line())))
    }

    async fn get(&self, url: Url) -> Result<reqwest::Response, ProviderError> {
        let response = self
            .http
            .get(url)
            .headers(endpoints::rest_headers(
                &self.access_token,
                &self.session_id,
            ))
            .send()
            .await
            .map_err(network)?;
        match response.status() {
            s if s.is_success() => Ok(response),
            StatusCode::UNAUTHORIZED => Err(ProviderError::SessionExpired),
            StatusCode::FORBIDDEN => Err(ProviderError::Forbidden),
            StatusCode::NOT_FOUND => Err(ProviderError::VehicleNotFound),
            StatusCode::TOO_MANY_REQUESTS => Err(ProviderError::RateLimited),
            s => {
                let code = error_code(&response.bytes().await.unwrap_or_default());
                Err(ProviderError::Protocol(match code {
                    Some(code) => format!("HTTP {} ({code})", s.as_u16()),
                    None => format!("HTTP {}", s.as_u16()),
                }))
            }
        }
    }
}

fn network(e: reqwest::Error) -> ProviderError {
    // Strip the URL: it contains the VIN.
    ProviderError::Network(Box::new(e.without_url()))
}

/// Extracts a short machine error code (`code` / `errorCode` / `error`) from
/// an error body. Anything that does not look like an identifier is dropped,
/// so free text that could echo personal data never reaches the caller.
fn error_code(body: &[u8]) -> Option<String> {
    let json: serde_json::Value = serde_json::from_slice(body).ok()?;
    ["code", "errorCode", "error"].iter().find_map(|key| {
        let value = json.get(key)?;
        let text = value
            .as_str()
            .map(str::to_owned)
            .or_else(|| value.as_i64().map(|n| n.to_string()))?;
        let is_identifier = !text.is_empty()
            && text.len() <= 64
            && text
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || "_.-".contains(c));
        is_identifier.then_some(text)
    })
}

impl VehicleProvider for MercedesClient {
    /// `GET {rest}/v2/vehicles`
    async fn vehicles(&self) -> Result<Vec<Vehicle>, ProviderError> {
        let url = endpoints::join(&self.endpoints.rest, &["v2", "vehicles"]);
        let body: wire::VehiclesResponse = self.get_json(url).await?;
        Ok(body.into_vehicles())
    }

    async fn state(&self, _id: &VehicleId) -> Result<VehicleState, ProviderError> {
        Err(ProviderError::NotSupported(
            "vehicle status decoding is pending protocol verification",
        ))
    }

    async fn capabilities(&self, id: &VehicleId) -> Result<Capabilities, ProviderError> {
        Ok(wire::capabilities(&self.command_capabilities(id).await?))
    }
}

#[cfg(test)]
mod tests {
    use super::error_code;

    #[test]
    fn error_code_keeps_only_identifier_like_codes() {
        assert_eq!(
            error_code(br#"{"code":"VEHICLE_NOT_SUPPORTED"}"#).as_deref(),
            Some("VEHICLE_NOT_SUPPORTED")
        );
        assert_eq!(
            error_code(br#"{"errorCode":4001}"#).as_deref(),
            Some("4001")
        );
        assert_eq!(error_code(br#"{"code":"vin W1N123 is invalid"}"#), None);
        assert_eq!(error_code(b"not json"), None);
        assert_eq!(error_code(br#"{"message":"x"}"#), None);
    }
}
