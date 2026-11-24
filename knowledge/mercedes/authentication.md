---
type: Protocol Reference
title: Mercedes authentication (OAuth 2.0 + PKCE)
description: Authorization-code + PKCE login against id.mercedes-benz.com, token exchange/refresh, and RustBenz's browser-based login variant.
tags: [mercedes, authentication, oauth, security]
status: stable
x-rustbenz-evidence: live-verified
generated: { by: opencode/claude-opus-5-5, at: "2026-11-24T09:20:00Z" }
verified:
  - { by: opencode/claude-opus-5-5, at: "2026-11-23T19:08:00Z" }
  - { by: "process:eqa-live-login", at: "2026-11-23T19:40:00Z" }
stale_after: "2027-02-23T00:00:00Z"
sources:
  - id: oauth-py
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/oauth.py
    title: mbapi2020 oauth.py
    author: "human:ReneNulschDE"
    last_modified: "2026-11-16"
  - id: const-py
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/const.py
    title: mbapi2020 const.py
    author: "human:ReneNulschDE"
  - id: explore-auth
    resource: https://github.com/andersdissing/mercedes-explore/blob/main/src/js/auth.js
    title: mercedes-explore auth.js
    author: "human:andersdissing"
x-rustbenz-implementation: crates/mercedes-client/src/auth.rs
---

The `verified` event above means the agent re-read the source files to
confirm these facts. The authorization-code login has been run live once;
see "Live results" below. Refresh has not been run live.

# Protocol facts (source-verified)

| Item | Value |
|---|---|
| Client ID (EU/NA/APAC) | `62778dc4-1de3-44f4-af95-115f06a3a008`[^const-py] |
| Redirect URI | `rismycar://login-callback` (the official app's custom scheme)[^oauth-py] |
| Scope | `email profile ciam-uid phone openid offline_access`[^oauth-py] |
| Authorization endpoint | `GET https://id.mercedes-benz.com/as/authorization.oauth2` with `client_id, code_challenge, code_challenge_method=S256, redirect_uri, response_type=code, scope`[^oauth-py] |
| Token endpoint | `POST https://id.mercedes-benz.com/as/token.oauth2`, form-encoded[^oauth-py] |
| Code exchange body | `client_id, code, code_verifier, grant_type=authorization_code, redirect_uri`[^oauth-py] |
| Refresh body | `grant_type=refresh_token&refresh_token=…` (no `client_id`), plus headers `X-Device-Id`, `X-Request-Id`[^oauth-py] |
| Token response | `access_token`, `refresh_token` (may be omitted on refresh; keep the old one), `expires_in` seconds[^oauth-py] |
| Auth failure on refresh | HTTP 400/401/403 means re-login is needed; other errors are temporary[^oauth-py] |

OAuth requests carry the app identification headers `Ris-Os-Name`,
`Ris-Os-Version`, `Ris-Sdk-Version`, `Ris-Application-Version`,
`X-Applicationname`, `X-Locale`, `X-Sessionid`, `X-Trackingid` and
`User-Agent`. Values are in `crates/mercedes-client/src/endpoints.rs`, each
cited to its source.[^const-py]

# How the reference projects sign in

mbapi2020, mercedes-explore and the Homey app all **script the CIAM login
pages**. They call `/ciam/auth/ua`, `/ciam/auth/login/user` and
`/ciam/auth/login/pass` with the user's email and password, and decline the
passkey-setup prompt.[^oauth-py][^explore-auth] All three **reject
`GOTO_LOGIN_OTP`**, so accounts with MFA cannot use them.

# RustBenz login (implemented, live-UNVERIFIED)

RustBenz does **not** script the login and never sees the password:

1. Build the authorization URL with a fresh PKCE verifier (32 random bytes,
   base64url, S256 challenge).
2. The user opens it in a browser and signs in on Mercedes' own page,
   including MFA or passkeys.
3. The browser is redirected to `rismycar://login-callback?code=…`. Desktop
   browsers cannot open that scheme, so the user copies the URL from DevTools
   (Network tab, "Preserve log") and pastes it into `eqa auth login`.
4. The code is exchanged at the token endpoint. Tokens go to the OS keyring
   (see [credential storage](../decisions/credential-storage.md)).

**Live results (2026-11-23, one EU account, Windows desktop browser):**

- **Confirmed:** the Mercedes web login completes for this client ID in a
  desktop browser and redirects to `rismycar://login-callback?code=…`. The
  user could copy the URL, and the token endpoint accepted the code exchange.
- **Confirmed:** the serialized token set is larger than the Windows
  Credential Manager limit of 2560 bytes. The CLI therefore splits it across
  several entries (see [credential storage](../decisions/credential-storage.md)).
- **Confirmed:** the response includes a refresh token. The access token
  lifetime was about 4 h: right after login, `eqa auth status` showed 239 min
  remaining. The exact `expires_in` value was not logged.
- **Confirmed:** when already signed in at Mercedes, the authorization URL
  redirects to the callback immediately, with no login form.
- **Still UNVERIFIED:** MFA-enabled accounts; refresh against the live service.

If this fails, the fallback options for mobile are an in-app web view
(ASWebAuthenticationSession / Custom Tabs) that intercepts the `rismycar`
scheme, or the scripted flow. The scripted flow would need an explicit
decision because it handles the password.

**Mobile variant (Android, implemented, live-UNVERIFIED):**

1. The app opens the same authorization URL in a Custom Tab.
2. The app registers an intent filter for `rismycar://login-callback`, so the
   redirect returns to it directly, with no copying or pasting.
3. The PKCE verifier stays in Rust memory (`PendingLogin`).

Open hypotheses:
- Chrome follows a server redirect to a custom scheme when no user gesture
  is involved, for example the instant redirect when already signed in.
- Mercedes does not reject the redirect for a third-party app.

On phones with the official app installed, both apps claim the scheme. See
[mobile integration](../architecture/mobile-integration.md).

Simplifications versus mbapi2020 (each marked `shortcut:` in code):

- The app version is hardcoded; mbapi2020 refreshes it from `/v1/config`.
- The device ID is random for each refresh.
- There is no `/v1/config` preflight before refresh.

[^oauth-py]: mbapi2020 oauth.py L50–105, L246–270, L405–430, L484–530
[^const-py]: mbapi2020 const.py L100–143
[^explore-auth]: mercedes-explore auth.js
