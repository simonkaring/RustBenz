---
type: Protocol Reference
title: Mercedes vehicle discovery
description: GET /v2/vehicles on the BFF host returns the account's vehicles as JSON (assignedVehicles and fleets[].bookedVehicles).
tags: [mercedes, rest, vehicles]
status: stable
x-rustbenz-evidence: live-verified
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T20:05:00Z" }
verified:
  - { by: opencode/claude-opus-5-5, at: "2026-11-23T19:08:00Z" }
  - { by: "process:eqa-live-vehicles", at: "2026-11-23T20:05:00Z" }
sources:
  - id: webapi
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/webapi.py
    title: mbapi2020 webapi.py (_request headers L44-87, get_user_info L137)
  - id: init
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/__init__.py
    title: mbapi2020 __init__.py (_setup_car L54-167, masterdata L199-227)
  - id: explore-api
    resource: https://github.com/andersdissing/mercedes-explore/blob/main/src/js/api.js
    title: mercedes-explore api.js
---

# Request

`GET https://bff.emea-prod.mobilesdk.mercedes-benz.com/v2/vehicles`[^webapi][^explore-api]

REST headers:

- `Authorization: Bearer <access>`
- `X-SessionId` (an uppercase UUID per session)
- `X-TrackingId` (an uppercase UUID per request)
- `ris-os-name: ios`
- `ris-os-version`, `ris-sdk-version`, `ris-application-version`
- `X-ApplicationName: mycar-store-ece`
- `X-Locale: de-DE`
- `User-Agent`

Source: mbapi2020 webapi.py.[^webapi]

# Response fields used

mbapi2020 collects `fleets[].bookedVehicles[]` and top-level
`assignedVehicles[]`.[^init] From each entry it reads:

- `vin` (falls back to `fin`)
- `licensePlate`
- `isOwner`
- `salesRelatedInformation.baumuster.baumusterDescription` (model name)

RustBenz maps this to `Vehicle { id, model }`. It removes duplicate IDs and
skips entries with no ID. The license plate is not read, because it is
personal data and not needed yet.

Contract test: `crates/mercedes-client/tests/fixtures/vehicles.json`. This is
a **synthetic** fixture whose shape follows the parsing code; it is not a
captured response.

Live status: **verified on 2026-11-23** for the target EQA (EU account). The
request with these headers returned HTTP 200 and one vehicle.
`baumusterDescription` was `EQA 250 SUV`.

[^webapi]: mbapi2020 webapi.py
[^init]: mbapi2020 __init__.py
[^explore-api]: mercedes-explore api.js
