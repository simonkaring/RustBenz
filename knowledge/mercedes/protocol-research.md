---
type: Research
title: Mercedes-Benz API landscape and reference implementations
description: Which Mercedes APIs exist, which one RustBenz uses, the reference projects, and known reliability risks.
tags: [mercedes, research, risk]
status: stable
x-rustbenz-evidence: source-code
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T19:08:00Z" }
stale_after: "2027-02-23T00:00:00Z"
sources:
  - id: mbapi2020
    resource: https://github.com/ReneNulschDE/mbapi2020/tree/6976c44e63d203708e60e987d2f8d76376e74f8a
    title: mbapi2020 Home Assistant integration (master @ 6976c44, MIT)
    author: "human:ReneNulschDE"
    last_modified: "2026-11-16"
  - id: explore
    resource: https://github.com/andersdissing/mercedes-explore
    title: mercedes-explore (JavaScript API/data explorer)
    author: "human:andersdissing"
  - id: homey
    resource: https://github.com/andersdissing/Mercedes-Benz-homey-app
    title: Mercedes-Benz Homey app (Node.js)
    author: "human:andersdissing"
  - id: ev-status-3
    resource: https://developer.mercedes-benz.com/products/electric_vehicle_status_3/docs
    title: Official Electric Vehicle Status 3.0 development guide
    author: "team:mercedes-benz-developers"
  - id: fleet-partner
    resource: https://connectivity.mercedes-benz.com/products/mercedes-benz-fleet-partner-api
    title: Mercedes-Benz Fleet Partner API
    author: "team:mercedes-benz-connectivity-services"
  - id: byocar-eol
    resource: https://community.openhab.org/t/mercedes-me-binding-and-sitemap-end-of-life/144087/2
    title: openHAB thread quoting the BYOCAR shutdown notice
---

# Which API

| API | Audience | Covers EQA use case? |
|---|---|---|
| Official EV Status 3.0 | B2B: developer-portal project, onboarding, vehicle-owner consent[^ev-status-3] | SoC/range/charging read, no climate commands; not self-serve for consumers |
| Official Fleet Partner API | B2B framework agreement; includes remote preconditioning commands[^fleet-partner] | Not available to individuals |
| Official BYOCAR "connect" APIs | Switched off 31 Aug 2023 (secondary source)[^byocar-eol] | Gone |
| **Unofficial mobile-app API** (the one the Mercedes app uses) | Any account, reverse-engineered[^mbapi2020][^explore][^homey] | Yes; **used by RustBenz** |

Decision: [isolate the unofficial API](../decisions/mercedes-isolation.md).
Revisit if Mercedes opens an official consumer API.

# Reference implementations

- **mbapi2020** (Python, Home Assistant) is the most complete and actively
  maintained. Primary reference.[^mbapi2020]
- **mercedes-explore** (browser JS) uses the same OAuth constants and REST
  endpoints. It ships a hand-written `vehicle-events.proto`, with field
  numbers recovered from live payloads.[^explore]
- **Homey app** (Node.js) uses the same login. It uses the websocket push
  channel for status and sends preconditioning commands over it.[^homey]

The three projects agree on the client ID, redirect URI, scopes, PKCE, and the
`/v2/vehicles` and capabilities endpoints. That cross-confirmation is why
these parts are implemented.

# Transports

- OAuth/CIAM on `id.mercedes-benz.com`: see [authentication](authentication.md).
- JSON REST on the BFF host: see [vehicle discovery](vehicle-discovery.md)
  and [capabilities](capabilities.md).
- Protobuf over REST (widget host) and websocket (`/v2/ws`) for status and
  commands: see [vehicle state](vehicle-state.md) and
  [remote commands](remote-commands.md).

# Regions

| Region | BFF REST | Websocket | Login |
|---|---|---|---|
| Europe (incl. Denmark) | `bff.emea-prod.mobilesdk.mercedes-benz.com` | `wss://websocket.emea-prod.mobilesdk.mercedes-benz.com/v2/ws` | `id.mercedes-benz.com` |
| North America, APAC | `bff.amap-prod.mobilesdk.mercedes-benz.com` | `…amap-prod…/v2/ws` | `id.mercedes-benz.com` |
| China | `bff.cn-prod…` | `…cn-prod…/v2/ws` | `ciam-1.mercedes-benz.com.cn`, separate PIN flow; CAPTCHA currently blocks it |

Each region also has its own application name and version headers. Source:
mbapi2020 `const.py` L118–143 and `app_version.py` L63–106.[^mbapi2020] Only
Europe is implemented.

# Known reliability risks

- **Rate limiting / blocking.** Websocket handshakes can return HTTP 429 for
  hours while login and REST still work (Homey #89, Oct 2026; mbapi2020
  #344/#353). The README recommends a dedicated account. It warns against
  using the same account in several integrations at once.[^mbapi2020] It is
  not established whether limits apply per account, session or device.
- **Login changes.** Passkey prompts were added to the login flow in July 2026
  (mbapi2020 #418). There was an all-region TLS chain change in July 2026
  (v0.38.2/v0.39.0). The application version may be validated through
  `/v1/config`.
- **Vehicle-generation differences.** mbapi2020 v0.44 changed preconditioning
  handling by vehicle generation. Issue #493 reports preheat rejected as
  unavailable on an EQE SUV.
- **Terms of service.** The unofficial API presents itself as the official
  app (its client ID and User-Agent). Mercedes may consider this
  unauthorized. This is accepted as a project risk; RustBenz never bypasses
  authentication, MFA, subscriptions or vehicle authorization.

# Open questions

See [current state](../roadmap/current-state.md#open-questions).

[^mbapi2020]: mbapi2020 @ 6976c44
[^explore]: mercedes-explore
[^homey]: Mercedes-Benz Homey app
[^ev-status-3]: EV Status 3.0 guide
[^fleet-partner]: Fleet Partner API
[^byocar-eol]: openHAB BYOCAR end-of-life thread
