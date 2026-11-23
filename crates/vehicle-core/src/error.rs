/// Errors returned by a [`crate::VehicleProvider`].
///
/// Variants separate authentication, authorization, transport, protocol and
/// vehicle problems so callers can react differently (re-login vs. retry vs.
/// report). Messages must never contain credentials or raw response bodies.
#[derive(Debug, thiserror::Error)]
pub enum ProviderError {
    #[error("not logged in")]
    NotAuthenticated,
    #[error("session expired or revoked; log in again")]
    SessionExpired,
    #[error("access denied by provider")]
    Forbidden,
    #[error("rate limited by provider")]
    RateLimited,
    #[error("vehicle not found")]
    VehicleNotFound,
    #[error("network error")]
    Network(#[source] Box<dyn std::error::Error + Send + Sync>),
    #[error("unexpected provider response: {0}")]
    Protocol(String),
    /// The provider integration does not (yet) support this operation.
    #[error("not supported: {0}")]
    NotSupported(&'static str),
}
