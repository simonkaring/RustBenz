//! Client for the **unofficial** Mercedes-Benz mobile app API.
//!
//! Everything here is reverse-engineered by the community and may change
//! without notice. Every endpoint, header and payload is traceable to the
//! sources in `knowledge/mercedes/`. Public functions return `vehicle-core`
//! types; wire types stay private.

mod auth;
mod client;
mod endpoints;
mod proto;
mod push;
mod status;
mod tls;
mod wire;

pub use auth::{AuthError, PkceLogin, TokenSet, parse_callback, refresh};
pub use client::{CommandCapability, MercedesClient};
pub use endpoints::Endpoints;
pub use wire::capabilities as capabilities_from_commands;

/// HTTP client with sane timeouts. TLS uses rustls with certificate
/// verification (never disabled), configured the same as the websocket.
pub fn http_client() -> Result<reqwest::Client, vehicle_core::ProviderError> {
    let network =
        |e: Box<dyn std::error::Error + Send + Sync>| vehicle_core::ProviderError::Network(e);
    let tls = tls::client_config().map_err(|e| network(Box::new(e)))?;
    reqwest::Client::builder()
        .tls_backend_preconfigured(tls)
        .timeout(std::time::Duration::from_secs(30))
        .connect_timeout(std::time::Duration::from_secs(10))
        .build()
        .map_err(|e| network(Box::new(e)))
}
