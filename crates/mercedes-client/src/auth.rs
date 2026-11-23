//! OAuth 2.0 authorization-code flow with PKCE (S256) against Mercedes CIAM.
//!
//! Unlike the community integrations, this crate never handles the user's
//! password: the user signs in on Mercedes' own login page (including MFA or
//! passkeys) and hands back the redirect URL. See
//! knowledge/mercedes/authentication.md.

use std::fmt;
use std::time::{Duration, SystemTime};

use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use url::Url;

use crate::endpoints::{self, CLIENT_ID, Endpoints, REDIRECT_URI, SCOPE};

#[derive(Debug, thiserror::Error)]
pub enum AuthError {
    #[error("could not find an authorization code in the pasted URL")]
    MissingCode,
    #[error("login was not completed: {0}")]
    Denied(String),
    #[error("Mercedes rejected the token request (HTTP {0}); log in again")]
    Rejected(u16),
    #[error("no refresh token stored; log in again")]
    NoRefreshToken,
    #[error("Mercedes rate-limited the token request; try again later")]
    RateLimited,
    #[error("network error")]
    Network(#[from] reqwest::Error),
    #[error("unexpected token response")]
    Protocol,
    #[error("secure random number generator unavailable")]
    Random,
}

/// Access/refresh tokens. `Debug` is redacted; serialize only into secure
/// storage (OS keyring / Keychain / Keystore).
#[derive(Clone, PartialEq, Serialize, Deserialize)]
pub struct TokenSet {
    access_token: String,
    refresh_token: Option<String>,
    pub expires_at: SystemTime,
}

impl fmt::Debug for TokenSet {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("TokenSet")
            .field("access_token", &"<redacted>")
            .field(
                "refresh_token",
                &self.refresh_token.as_ref().map(|_| "<redacted>"),
            )
            .field("expires_at", &self.expires_at)
            .finish()
    }
}

impl TokenSet {
    pub(crate) fn access_token(&self) -> &str {
        &self.access_token
    }

    pub fn can_refresh(&self) -> bool {
        self.refresh_token.is_some()
    }

    /// True if the access token expires within `margin` of `now`.
    pub fn expires_within(&self, now: SystemTime, margin: Duration) -> bool {
        now + margin >= self.expires_at
    }
}

#[derive(Deserialize)]
struct TokenResponse {
    access_token: String,
    refresh_token: Option<String>,
    expires_in: u64,
}

impl TokenResponse {
    fn into_tokens(self, now: SystemTime, previous_refresh: Option<String>) -> TokenSet {
        TokenSet {
            access_token: self.access_token,
            // oauth.py keeps the old refresh token when the response omits one.
            refresh_token: self.refresh_token.or(previous_refresh),
            expires_at: now + Duration::from_secs(self.expires_in),
        }
    }
}

/// An in-progress interactive login.
pub struct PkceLogin {
    verifier: String,
    authorization_url: Url,
}

impl PkceLogin {
    pub fn new(endpoints: &Endpoints) -> Result<Self, AuthError> {
        let mut bytes = [0u8; 32];
        getrandom::fill(&mut bytes).map_err(|_| AuthError::Random)?;
        let verifier = URL_SAFE_NO_PAD.encode(bytes);
        let challenge = URL_SAFE_NO_PAD.encode(Sha256::digest(verifier.as_bytes()));

        let mut url = endpoints::join(&endpoints.login, &["as", "authorization.oauth2"]);
        url.query_pairs_mut()
            .append_pair("client_id", CLIENT_ID)
            .append_pair("code_challenge", &challenge)
            .append_pair("code_challenge_method", "S256")
            .append_pair("redirect_uri", REDIRECT_URI)
            .append_pair("response_type", "code")
            .append_pair("scope", SCOPE);
        Ok(Self {
            verifier,
            authorization_url: url,
        })
    }

    /// URL the user must open in a browser to sign in.
    pub fn authorization_url(&self) -> &Url {
        &self.authorization_url
    }

    /// Exchanges the pasted redirect URL (or bare code) for tokens.
    pub async fn complete(
        self,
        http: &reqwest::Client,
        endpoints: &Endpoints,
        callback: &str,
    ) -> Result<TokenSet, AuthError> {
        let code = parse_callback(callback)?;
        let form = [
            ("client_id", CLIENT_ID),
            ("code", code.as_str()),
            ("code_verifier", self.verifier.as_str()),
            ("grant_type", "authorization_code"),
            ("redirect_uri", REDIRECT_URI),
        ];
        let response = token_request(http, endpoints, &form, &[]).await?;
        Ok(response.into_tokens(SystemTime::now(), None))
    }
}

/// Extracts the authorization code from `rismycar://login-callback?code=…`.
/// A bare code (no `?`/`=`) is accepted as-is.
pub fn parse_callback(input: &str) -> Result<String, AuthError> {
    let input = input.trim();
    if !input.is_empty() && !input.contains(['?', '=', '&', ' ', '/']) {
        return Ok(input.to_owned());
    }
    let query = input.split_once('?').map_or(input, |(_, q)| q);
    let query = query.split('#').next().unwrap_or_default();
    let mut code = None;
    for (key, value) in url::form_urlencoded::parse(query.as_bytes()) {
        match key.as_ref() {
            "code" if !value.is_empty() => code = Some(value.into_owned()),
            // OAuth error codes are short, standardized tokens; safe to show.
            "error" => return Err(AuthError::Denied(value.chars().take(64).collect())),
            _ => {}
        }
    }
    code.ok_or(AuthError::MissingCode)
}

/// Uses the refresh token to obtain a new access token.
///
/// Mirrors oauth.py `async_refresh_access_token` (form body without
/// `client_id`, plus `X-Device-Id`/`X-Request-Id`). mbapi2020 additionally
/// preflights `GET /v1/config`; that is skipped here (unverified necessity).
pub async fn refresh(
    http: &reqwest::Client,
    endpoints: &Endpoints,
    tokens: &TokenSet,
) -> Result<TokenSet, AuthError> {
    let refresh_token = tokens
        .refresh_token
        .as_deref()
        .ok_or(AuthError::NoRefreshToken)?;
    let form = [
        ("grant_type", "refresh_token"),
        ("refresh_token", refresh_token),
    ];
    // shortcut: random device id per refresh; mbapi2020 keeps a persistent one. Persist if Mercedes starts rejecting refreshes.
    let device_id = endpoints::new_uuid();
    let request_id = endpoints::new_uuid();
    let extra = [
        ("x-device-id", device_id.as_str()),
        ("x-request-id", request_id.as_str()),
    ];
    let response = token_request(http, endpoints, &form, &extra).await?;
    Ok(response.into_tokens(SystemTime::now(), tokens.refresh_token.clone()))
}

async fn token_request(
    http: &reqwest::Client,
    endpoints: &Endpoints,
    form: &[(&str, &str)],
    extra_headers: &[(&'static str, &str)],
) -> Result<TokenResponse, AuthError> {
    let url = endpoints::join(&endpoints.login, &["as", "token.oauth2"]);
    let mut request = http
        .post(url)
        .headers(endpoints::oauth_headers(&endpoints::new_uuid()))
        .form(form);
    for (name, value) in extra_headers {
        request = request.header(*name, *value);
    }
    let response = request.send().await?;
    let status = response.status();
    if status.as_u16() == 429 {
        return Err(AuthError::RateLimited);
    }
    if !status.is_success() {
        // Body is intentionally not surfaced: it may echo request data.
        tracing::debug!(status = status.as_u16(), "token request rejected");
        return Err(AuthError::Rejected(status.as_u16()));
    }
    response.json().await.map_err(|_| AuthError::Protocol)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_full_callback_url() {
        let code = parse_callback("rismycar://login-callback?code=abc123&state=x").expect("code");
        assert_eq!(code, "abc123");
    }

    #[test]
    fn parses_bare_code_and_trims() {
        assert_eq!(parse_callback("  abc123 \n").expect("code"), "abc123");
    }

    #[test]
    fn reports_oauth_error() {
        assert!(matches!(
            parse_callback("rismycar://login-callback?error=access_denied"),
            Err(AuthError::Denied(e)) if e == "access_denied"
        ));
    }

    #[test]
    fn missing_code_is_error() {
        assert!(matches!(
            parse_callback("rismycar://login-callback?foo=bar"),
            Err(AuthError::MissingCode)
        ));
        assert!(matches!(parse_callback(""), Err(AuthError::MissingCode)));
    }

    #[test]
    fn authorization_url_has_pkce_parameters() {
        let login = PkceLogin::new(&Endpoints::europe()).expect("login");
        let url = login.authorization_url();
        assert_eq!(url.path(), "/as/authorization.oauth2");
        let pairs: std::collections::HashMap<_, _> = url.query_pairs().collect();
        assert_eq!(pairs["client_id"], CLIENT_ID);
        assert_eq!(pairs["code_challenge_method"], "S256");
        assert_eq!(pairs["redirect_uri"], REDIRECT_URI);
        assert_eq!(pairs["response_type"], "code");
        let expected = URL_SAFE_NO_PAD.encode(Sha256::digest(login.verifier.as_bytes()));
        assert_eq!(pairs["code_challenge"], expected);
        assert_eq!(login.verifier.len(), 43);
    }

    #[test]
    fn debug_redacts_tokens() {
        let tokens = TokenSet {
            access_token: "secret-access".into(),
            refresh_token: Some("secret-refresh".into()),
            expires_at: SystemTime::UNIX_EPOCH,
        };
        let debug = format!("{tokens:?}");
        assert!(!debug.contains("secret"));
    }

    #[test]
    fn expiry_margin() {
        let now = SystemTime::UNIX_EPOCH + Duration::from_secs(1000);
        let tokens = TokenSet {
            access_token: String::new(),
            refresh_token: None,
            expires_at: now + Duration::from_secs(30),
        };
        assert!(tokens.expires_within(now, Duration::from_secs(60)));
        assert!(!tokens.expires_within(now, Duration::from_secs(10)));
    }
}
