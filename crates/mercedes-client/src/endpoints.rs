use reqwest::header::{HeaderMap, HeaderName, HeaderValue};
use url::Url;

// Source for every constant below: ReneNulschDE/mbapi2020 @ 6976c44
// (const.py L100-143, app_version.py L63-106 / L269-283, oauth.py _get_header).
// See knowledge/mercedes/authentication.md and knowledge/mercedes/protocol-research.md.
pub(crate) const CLIENT_ID: &str = "62778dc4-1de3-44f4-af95-115f06a3a008";
pub(crate) const REDIRECT_URI: &str = "rismycar://login-callback";
pub(crate) const SCOPE: &str = "email profile ciam-uid phone openid offline_access";

const APPLICATION_NAME: &str = "mycar-store-ece";
// shortcut: hardcoded app version; mbapi2020 refreshes it from GET /v1/config. Upgrade if Mercedes starts rejecting it.
const APPLICATION_VERSION: &str = "1.73.0";
const SDK_VERSION: &str = "4.20.1";
const OS_NAME: &str = "ios";
const OS_VERSION: &str = "26.3";
const USER_AGENT: &str = "Mercedes-Benz/3044 CFNetwork/3860.400.22 Darwin/25.3.0";
const LOCALE: &str = "de-DE";

/// Base URLs for one Mercedes region.
///
/// shortcut: only the Europe (ECE) region is provided; North America / APAC use
/// different hosts and app identifiers (see knowledge). Add when needed.
#[derive(Debug, Clone)]
pub struct Endpoints {
    /// OAuth / CIAM host.
    pub login: Url,
    /// Mobile SDK backend-for-frontend (JSON REST).
    pub rest: Url,
    /// Protobuf push channel.
    pub websocket: Url,
}

impl Endpoints {
    pub fn europe() -> Self {
        Self {
            login: Url::parse("https://id.mercedes-benz.com").expect("static URL"),
            rest: Url::parse("https://bff.emea-prod.mobilesdk.mercedes-benz.com")
                .expect("static URL"),
            websocket: Url::parse("wss://websocket.emea-prod.mobilesdk.mercedes-benz.com/v2/ws")
                .expect("static URL"),
        }
    }
}

/// Appends path segments, percent-encoding each one.
pub(crate) fn join(base: &Url, segments: &[&str]) -> Url {
    let mut url = base.clone();
    if let Ok(mut path) = url.path_segments_mut() {
        path.pop_if_empty().extend(segments);
    }
    url
}

pub(crate) fn new_uuid() -> String {
    uuid::Uuid::new_v4().to_string().to_uppercase()
}

fn insert(map: &mut HeaderMap, name: &'static str, value: &str) {
    // All values are static constants or UUIDs/tokens, which are valid header values.
    if let Ok(v) = HeaderValue::from_str(value) {
        map.insert(HeaderName::from_static(name), v);
    }
}

/// Headers for the OAuth token endpoint (oauth.py `_get_header` + `apply_oauth_headers`).
pub(crate) fn oauth_headers(session_id: &str) -> HeaderMap {
    let mut h = HeaderMap::new();
    insert(&mut h, "ris-os-name", OS_NAME);
    insert(&mut h, "ris-os-version", OS_VERSION);
    insert(&mut h, "ris-sdk-version", SDK_VERSION);
    insert(&mut h, "ris-application-version", APPLICATION_VERSION);
    insert(&mut h, "x-applicationname", APPLICATION_NAME);
    insert(&mut h, "x-locale", LOCALE);
    insert(&mut h, "x-trackingid", &new_uuid());
    insert(&mut h, "x-sessionid", session_id);
    insert(&mut h, "user-agent", USER_AGENT);
    insert(&mut h, "accept-language", "en-GB");
    h
}

/// Headers for BFF / widget REST calls (webapi.py `_request` + `apply_webapi_headers`).
pub(crate) fn rest_headers(access_token: &str, session_id: &str) -> HeaderMap {
    let mut h = HeaderMap::new();
    insert(&mut h, "authorization", &format!("Bearer {access_token}"));
    insert(&mut h, "x-sessionid", session_id);
    insert(&mut h, "x-trackingid", &new_uuid());
    insert(&mut h, "ris-os-name", OS_NAME);
    insert(&mut h, "ris-os-version", OS_VERSION);
    insert(&mut h, "ris-sdk-version", SDK_VERSION);
    insert(&mut h, "ris-application-version", APPLICATION_VERSION);
    insert(&mut h, "x-applicationname", APPLICATION_NAME);
    insert(&mut h, "x-locale", LOCALE);
    insert(&mut h, "user-agent", USER_AGENT);
    insert(&mut h, "content-type", "application/json; charset=UTF-8");
    h
}

/// Websocket handshake headers (websocket.py `_websocket_connection_headers`
/// L558-580 + `apply_websocket_headers`). Note: no `Bearer` prefix.
pub(crate) fn websocket_headers(access_token: &str, session_id: &str) -> HeaderMap {
    let mut h = HeaderMap::new();
    insert(&mut h, "authorization", access_token);
    insert(&mut h, "app-session-id", session_id);
    insert(&mut h, "output-format", "PROTO");
    insert(&mut h, "x-sessionid", session_id);
    insert(&mut h, "x-trackingid", &new_uuid());
    insert(&mut h, "ris-os-name", OS_NAME);
    insert(&mut h, "ris-os-version", OS_VERSION);
    insert(&mut h, "ris-sdk-version", SDK_VERSION);
    insert(&mut h, "ris-application-version", APPLICATION_VERSION);
    insert(&mut h, "x-applicationname", APPLICATION_NAME);
    insert(&mut h, "x-locale", LOCALE);
    insert(&mut h, "user-agent", USER_AGENT);
    h
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn join_encodes_segments() {
        let base = Url::parse("https://example.test").expect("url");
        let url = join(&base, &["v1", "vehicle", "a/b", "capabilities"]);
        assert_eq!(
            url.as_str(),
            "https://example.test/v1/vehicle/a%2Fb/capabilities"
        );
    }

    #[test]
    fn websocket_headers_use_raw_token_and_proto_output() {
        let h = websocket_headers("tok", "SESSION");
        assert_eq!(
            h["authorization"], "tok",
            "no Bearer prefix on the websocket"
        );
        assert_eq!(h["output-format"], "PROTO");
        assert_eq!(h["app-session-id"], "SESSION");
        assert_eq!(h["x-sessionid"], "SESSION");
        assert_eq!(h["x-applicationname"], "mycar-store-ece");
    }
}
