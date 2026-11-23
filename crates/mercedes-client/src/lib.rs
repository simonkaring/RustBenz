//! Client for the **unofficial** Mercedes-Benz mobile app API.
//!
//! Everything here is reverse-engineered by the community and may change
//! without notice. Every endpoint, header and payload is traceable to the
//! sources in `knowledge/mercedes/`. Public functions return `vehicle-core`
//! types; wire types stay private.

mod auth;
mod client;
mod endpoints;
mod wire;

pub use auth::{AuthError, PkceLogin, TokenSet, parse_callback, refresh};
pub use client::{CommandCapability, MercedesClient};
pub use endpoints::Endpoints;
pub use wire::capabilities as capabilities_from_commands;

/// HTTP client with sane timeouts. TLS uses rustls with certificate
/// verification (never disabled).
pub fn http_client() -> reqwest::Result<reqwest::Client> {
    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(30))
        .connect_timeout(std::time::Duration::from_secs(10))
        .build()
}
