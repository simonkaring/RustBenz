//! UniFFI surface for iOS and Android.
//!
//! Exported records mirror `vehicle-core` types in FFI-friendly shapes; domain
//! crates stay free of binding attributes. Tokens cross the boundary as an
//! opaque JSON string that the platform persists in Keychain / Keystore
//! (knowledge/decisions/credential-storage.md).

use std::sync::{Arc, Mutex};
use std::time::{Duration, SystemTime};

use mercedes_client::{AuthError, Endpoints, MercedesClient, PkceLogin, TokenSet};
use vehicle_core::{self as core, ProviderError, VehicleProvider};

uniffi::setup_scaffolding!();

// ---- Errors ----

/// Errors the app can act on. Messages never contain tokens or VINs.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("not logged in")]
    NotAuthenticated,
    #[error("session expired; log in again")]
    SessionExpired,
    #[error("access denied")]
    Forbidden,
    #[error("rate limited by Mercedes; try again later")]
    RateLimited,
    #[error("vehicle not found")]
    VehicleNotFound,
    #[error("login was not completed: {reason}")]
    LoginFailed { reason: String },
    #[error("network error: {reason}")]
    Network { reason: String },
    #[error("unexpected response: {reason}")]
    Protocol { reason: String },
}

impl From<ProviderError> for CoreError {
    fn from(e: ProviderError) -> Self {
        match e {
            ProviderError::NotAuthenticated => Self::NotAuthenticated,
            ProviderError::SessionExpired => Self::SessionExpired,
            ProviderError::Forbidden => Self::Forbidden,
            ProviderError::RateLimited => Self::RateLimited,
            ProviderError::VehicleNotFound => Self::VehicleNotFound,
            ProviderError::Network(e) => Self::Network {
                reason: error_chain(e.as_ref()),
            },
            ProviderError::Protocol(reason) => Self::Protocol { reason },
            ProviderError::NotSupported(reason) => Self::Protocol {
                reason: reason.into(),
            },
        }
    }
}

impl From<AuthError> for CoreError {
    fn from(e: AuthError) -> Self {
        match e {
            AuthError::Rejected(_) | AuthError::NoRefreshToken => Self::SessionExpired,
            AuthError::RateLimited => Self::RateLimited,
            AuthError::Network(e) => Self::Network {
                reason: error_chain(&e.without_url()),
            },
            other => Self::LoginFailed {
                reason: other.to_string(),
            },
        }
    }
}

/// "outer: inner: root cause" — reqwest's top-level message alone is not
/// diagnosable. Callers strip URLs (which contain VINs) before this point.
fn error_chain(e: &(dyn std::error::Error + 'static)) -> String {
    let mut text = e.to_string();
    let mut source = e.source();
    while let Some(s) = source {
        text.push_str(": ");
        text.push_str(&s.to_string());
        source = s.source();
    }
    text
}

// ---- Login ----

/// One interactive login: open [`PendingLogin::authorization_url`] in a
/// browser, then pass the `rismycar://login-callback?code=…` redirect to
/// [`PendingLogin::complete`].
#[derive(uniffi::Object)]
pub struct PendingLogin {
    login: Mutex<Option<PkceLogin>>,
    url: String,
}

#[uniffi::export(async_runtime = "tokio")]
impl PendingLogin {
    #[uniffi::constructor]
    pub fn new() -> Result<Arc<Self>, CoreError> {
        let login = PkceLogin::new(&Endpoints::europe())?;
        Ok(Arc::new(Self {
            url: login.authorization_url().to_string(),
            login: Mutex::new(Some(login)),
        }))
    }

    pub fn authorization_url(&self) -> String {
        self.url.clone()
    }

    /// Returns the token JSON to store in secure storage. Single use.
    pub async fn complete(&self, callback_url: String) -> Result<String, CoreError> {
        let login = self
            .login
            .lock()
            .map_err(|_| CoreError::LoginFailed {
                reason: "internal state".into(),
            })?
            .take()
            .ok_or(CoreError::LoginFailed {
                reason: "login already completed; start a new one".into(),
            })?;
        let http = mercedes_client::http_client()?;
        let tokens = login
            .complete(&http, &Endpoints::europe(), &callback_url)
            .await?;
        to_json(&tokens)
    }
}

// ---- Session ----

/// An authenticated session built from stored token JSON.
#[derive(uniffi::Object)]
pub struct Session {
    http: reqwest::Client,
    endpoints: Endpoints,
    tokens: Mutex<TokenSet>,
}

#[uniffi::export(async_runtime = "tokio")]
impl Session {
    #[uniffi::constructor]
    pub fn new(token_json: String) -> Result<Arc<Self>, CoreError> {
        let tokens: TokenSet =
            serde_json::from_str(&token_json).map_err(|_| CoreError::NotAuthenticated)?;
        Ok(Arc::new(Self {
            http: mercedes_client::http_client()?,
            endpoints: Endpoints::europe(),
            tokens: Mutex::new(tokens),
        }))
    }

    /// Refreshes the access token if it expires within a minute. Returns new
    /// token JSON that the caller must persist, or `None` if nothing changed.
    pub async fn refresh_if_needed(&self) -> Result<Option<String>, CoreError> {
        let current = self.tokens()?;
        if !current.expires_within(SystemTime::now(), Duration::from_secs(60)) {
            return Ok(None);
        }
        let fresh = mercedes_client::refresh(&self.http, &self.endpoints, &current).await?;
        let json = to_json(&fresh)?;
        *self
            .tokens
            .lock()
            .map_err(|_| CoreError::NotAuthenticated)? = fresh;
        Ok(Some(json))
    }

    pub async fn vehicles(&self) -> Result<Vec<Vehicle>, CoreError> {
        let vehicles = self.client()?.vehicles().await?;
        Ok(vehicles.into_iter().map(Vehicle::from).collect())
    }

    /// Opens one push-channel connection; takes up to ~30 s.
    pub async fn vehicle_state(&self, vehicle_id: String) -> Result<VehicleState, CoreError> {
        let state = self
            .client()?
            .state(&core::VehicleId::new(vehicle_id))
            .await?;
        Ok(state.into())
    }
}

impl Session {
    fn tokens(&self) -> Result<TokenSet, CoreError> {
        Ok(self
            .tokens
            .lock()
            .map_err(|_| CoreError::NotAuthenticated)?
            .clone())
    }

    fn client(&self) -> Result<MercedesClient, CoreError> {
        Ok(MercedesClient::new(
            self.http.clone(),
            self.endpoints.clone(),
            &self.tokens()?,
        ))
    }
}

fn to_json(tokens: &TokenSet) -> Result<String, CoreError> {
    serde_json::to_string(tokens).map_err(|_| CoreError::Protocol {
        reason: "could not serialize tokens".into(),
    })
}

// ---- Records ----

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Vehicle {
    /// Full provider identifier (VIN). Never log it.
    pub id: String,
    /// Last four characters, safe to display.
    pub display_id: String,
    pub model: Option<String>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NumberReading {
    pub value: f64,
    pub observed_at: Option<SystemTime>,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct BoolReading {
    pub value: bool,
    pub observed_at: Option<SystemTime>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum ChargingStatus {
    Charging,
    NotCharging,
    Complete,
    Fault,
    Unrecognized,
}

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ChargingStatusReading {
    pub value: ChargingStatus,
    pub observed_at: Option<SystemTime>,
}

/// `None` fields mean the vehicle did not report the signal.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct VehicleState {
    pub vehicle_id: String,
    pub retrieved_at: SystemTime,
    pub state_of_charge_percent: Option<NumberReading>,
    pub electric_range_km: Option<NumberReading>,
    pub charging_status: Option<ChargingStatusReading>,
    pub plugged_in: Option<BoolReading>,
    pub charging_power_kw: Option<NumberReading>,
    pub preconditioning_active: Option<BoolReading>,
}

impl From<core::Vehicle> for Vehicle {
    fn from(v: core::Vehicle) -> Self {
        Self {
            display_id: v.id.redacted(),
            id: v.id.as_str().to_owned(),
            model: v.model,
        }
    }
}

impl From<core::ChargingStatus> for ChargingStatus {
    fn from(s: core::ChargingStatus) -> Self {
        match s {
            core::ChargingStatus::Charging => Self::Charging,
            core::ChargingStatus::NotCharging => Self::NotCharging,
            core::ChargingStatus::Complete => Self::Complete,
            core::ChargingStatus::Fault => Self::Fault,
            core::ChargingStatus::Unrecognized => Self::Unrecognized,
        }
    }
}

fn number<T: Into<f64>>(r: core::Reading<T>) -> NumberReading {
    NumberReading {
        value: r.value.into(),
        observed_at: r.observed_at,
    }
}

fn boolean(r: core::Reading<bool>) -> BoolReading {
    BoolReading {
        value: r.value,
        observed_at: r.observed_at,
    }
}

impl From<core::VehicleState> for VehicleState {
    fn from(s: core::VehicleState) -> Self {
        Self {
            vehicle_id: s.vehicle_id.as_str().to_owned(),
            retrieved_at: s.retrieved_at,
            state_of_charge_percent: s.battery.state_of_charge.map(number),
            electric_range_km: s.battery.electric_range.map(number),
            charging_status: s.charging.status.map(|r| ChargingStatusReading {
                value: r.value.into(),
                observed_at: r.observed_at,
            }),
            plugged_in: s.charging.plugged_in.map(boolean),
            charging_power_kw: s.charging.power_kw.map(number),
            preconditioning_active: s.climate.preconditioning_active.map(boolean),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn converts_state_preserving_absence_and_timestamps() {
        let t = SystemTime::UNIX_EPOCH;
        let mut s = core::VehicleState::empty(core::VehicleId::new("VIN1"), t);
        s.battery.state_of_charge = core::Percent::new(0.0).map(|p| core::Reading::new(p, Some(t)));
        s.charging.status = Some(core::Reading::new(core::ChargingStatus::Complete, None));

        let ffi = VehicleState::from(s);
        assert_eq!(ffi.vehicle_id, "VIN1");
        assert_eq!(
            ffi.state_of_charge_percent,
            Some(NumberReading {
                value: 0.0,
                observed_at: Some(t)
            })
        );
        assert_eq!(
            ffi.charging_status.map(|r| r.value),
            Some(ChargingStatus::Complete)
        );
        assert_eq!(ffi.electric_range_km, None);
        assert_eq!(ffi.preconditioning_active, None);
    }

    #[test]
    fn vehicle_exposes_redacted_display_id() {
        let v = Vehicle::from(core::Vehicle {
            id: core::VehicleId::new("W1N0000000000ABCD"),
            model: None,
        });
        assert_eq!(v.display_id, "…ABCD");
    }

    #[test]
    fn invalid_token_json_is_not_authenticated() {
        assert!(matches!(
            Session::new("not json".into()),
            Err(CoreError::NotAuthenticated)
        ));
    }

    #[test]
    fn pending_login_is_single_use_and_has_pkce_url() {
        let login = PendingLogin::new().expect("login");
        assert!(
            login
                .authorization_url()
                .contains("code_challenge_method=S256")
        );
        login.login.lock().expect("lock").take();
        let rt = tokio::runtime::Builder::new_current_thread()
            .build()
            .expect("rt");
        let second = rt.block_on(login.complete("code".into()));
        assert!(matches!(second, Err(CoreError::LoginFailed { .. })));
    }

    #[test]
    fn provider_errors_map_to_actionable_variants() {
        assert!(matches!(
            CoreError::from(ProviderError::SessionExpired),
            CoreError::SessionExpired
        ));
        assert!(matches!(
            CoreError::from(ProviderError::RateLimited),
            CoreError::RateLimited
        ));
        assert!(matches!(
            CoreError::from(AuthError::NoRefreshToken),
            CoreError::SessionExpired
        ));
    }
}
