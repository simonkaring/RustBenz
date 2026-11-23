---
type: Protocol Reference
title: Mercedes vehicle state (protobuf)
description: Vehicle status arrives as protobuf via websocket push (primary) or the widget REST endpoint (VehicleStatusUpdate, limited fields, HTTP 400 live).
tags: [mercedes, protobuf, websocket, status]
status: draft
x-rustbenz-evidence: source-code
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T20:30:00Z" }
stale_after: "2027-02-23T00:00:00Z"
sources:
  - id: webapi
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/webapi.py
    title: mbapi2020 webapi.py get_car_p2b_data_via_rest (L230-250)
  - id: vsu
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/vsu_helper.py
    title: mbapi2020 vsu_helper.py
  - id: ws
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/websocket.py
    title: mbapi2020 websocket.py
  - id: pr461
    resource: https://github.com/ReneNulschDE/mbapi2020/pull/461
    title: "mbapi2020 PR #461: widget vehicleattributes is VehicleStatusUpdate"
    author: "human:ReneNulschDE"
    last_modified: "2026-10-20"
  - id: explore-proto
    resource: https://github.com/andersdissing/mercedes-explore/blob/main/src/proto/vehicle-events.proto
    title: mercedes-explore vehicle-events.proto (reverse-engineered)
  - id: homey-api
    resource: https://github.com/andersdissing/Mercedes-Benz-homey-app/blob/main/lib/api.js
    title: Homey app api.js L256-265
---

# Channels

1. **Websocket push** (primary in all references). The client connects to
   `wss://websocket.emea-prod.mobilesdk.mercedes-benz.com/v2/ws` and sends
   these headers:
   - `Authorization: <access token>` (**no** `Bearer` prefix)
   - `OUTPUT-FORMAT: PROTO`
   - `APP-SESSION-ID`
   - the usual `ris-*` and `X-*` headers

   Binary frames are `PushMessage`, and outgoing frames are `ClientMessage`.
   Updates must be acknowledged by sequence number. mbapi2020 uses a ping
   watchdog of about 30 s and reconnect backoff of up to 120 s. A handshake
   429 is treated as a blocked account.[^ws]
2. **Widget REST**:
   `GET https://widget.emea-prod.mobilesdk.mercedes-benz.com/v1/vehicle/{vin}/vehicleattributes`
   with REST headers. It returns **protobuf bytes, not JSON**.[^webapi]

# Widget endpoint: message type resolved, but not usable for RustBenz

- **Resolved:** the widget response is a `VehicleStatusUpdate`. mbapi2020
  PR #461 (Oct 2026) replayed a captured response: parsed as `VEPUpdate` it
  has 0 known fields; parsed as `VehicleStatusUpdate` it has 15.[^pr461] The
  Homey app and mercedes-explore still parse it as `VEPUpdate`, which is
  wrong.[^homey-api][^explore-proto]
- **Limited content:** the 15 fields are `fin_or_vin`, `full_update`,
  `soc`, `rangeelectric`, `range_electric_wltp`, `rangeliquid`,
  `overall_range`, `tanklevelpercent`, `tank_level_ad_blue`, `position_lat`,
  `position_long`, `position_heading`, `vehicle_position_error_code`,
  `proximity_calculation_for_vehicle_position_required` and `vtime`.[^pr461]
  It has **no charging status and no preconditioning state**. mbapi2020 calls
  it only when the websocket is blocked (`client.py` L3440).[^pr461]
- **Live (2026-11-23, target EQA):** two requests returned **HTTP 400**. The
  error body had no machine-readable code. The second request matched
  mbapi2020's REST headers exactly, including
  `Content-Type: application/json; charset=UTF-8`; the URL matches
  `helper.Widget_url`. The cause is **UNKNOWN**. Hypotheses: the endpoint is
  only served to some sessions or app versions, or needs a header not visible
  in the source. No more blind retries; see
  [current state](../roadmap/current-state.md).

**Consequence:** the websocket push channel is the required source for
status. The widget endpoint is at most a fallback for battery and range.

# Attribute encoding (from references)

**Legacy `VehicleAttributeStatus`** fields:

- status
- timestamp(s)
- `display_value`
- a oneof of `int_value`, `bool_value`, `string_value`, `double_value`,
  `nil_value` and nested values

**VSU attributes** use typed values with metadata (timestamp and status) and
optional units. mbapi2020 maps VSU snake_case keys to legacy names. It maps
`VALUE_INVALID` to 3 and `VALUE_NOT_AVAILABLE` to 4.[^vsu]

EV-relevant attribute names (legacy naming):

- `soc`
- `rangeelectric`
- `chargingstatus`, `chargingactive`, `chargingpower`
- `endofchargetime`
- `maxSoc`, `selectedChargeProgram`
- `precondActive`, `precondNow`, `precondState`, `precondError`,
  `precondDuration`

Integer code meanings (for example `chargingstatus`) are **UNVERIFIED**. Do
not map them until a source or a capture confirms them.

# Implementation status

`MercedesClient::vehicle_attributes_raw` fetches the undecoded bytes, and
`VehicleProvider::state` returns `NotSupported`. Domain target:
[vehicle state](../domain/vehicle-state.md).

[^webapi]: mbapi2020 webapi.py L230–250
[^pr461]: mbapi2020 PR #461
[^vsu]: mbapi2020 vsu_helper.py L67–115, L236–297
[^ws]: mbapi2020 websocket.py L329–584
[^explore-proto]: mercedes-explore vehicle-events.proto
[^homey-api]: Homey api.js
