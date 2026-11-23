---
type: Protocol Reference
title: Mercedes vehicle state (protobuf)
description: Vehicle status is read once from the websocket push channel (verified live); message subset, attribute mapping, and why the widget REST endpoint is not used.
tags: [mercedes, protobuf, websocket, status]
status: stable
x-rustbenz-evidence: live-verified
x-rustbenz-implementation: crates/mercedes-client/src/push.rs, crates/mercedes-client/src/status.rs, crates/mercedes-client/src/proto.rs
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T21:15:00Z" }
verified: { by: "process:eqa-live-status", at: "2026-11-23T21:15:00Z" }
stale_after: "2027-02-23T00:00:00Z"
sources:
  - id: pb2
    resource: https://github.com/ReneNulschDE/mbapi2020/tree/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/proto
    title: mbapi2020 generated protobuf descriptors (client_pb2.py, vehicle_events_pb2.py; MIT)
    author: "human:ReneNulschDE"
  - id: client
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/client.py
    title: mbapi2020 client.py on_data (L240-360), acknowledgement handling
  - id: ws
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/websocket.py
    title: mbapi2020 websocket.py (_websocket_connection_headers L558-580)
  - id: vsu
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/vsu_helper.py
    title: mbapi2020 vsu_helper.py (status mapping L61-80)
  - id: vsu-enums
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/vsu_enums.py
    title: mbapi2020 vsu_enums.py (Chargingstatus legacy ints L93-109)
  - id: pr461
    resource: https://github.com/ReneNulschDE/mbapi2020/pull/461
    title: "mbapi2020 PR #461: widget vehicleattributes is VehicleStatusUpdate"
    author: "human:ReneNulschDE"
    last_modified: "2026-10-20"
---

# Websocket push channel (implemented, verified live)

- URL: `wss://websocket.emea-prod.mobilesdk.mercedes-benz.com/v2/ws`.
- Handshake headers:[^ws]
  - `Authorization: <access token>` (**no** `Bearer` prefix)
  - `APP-SESSION-ID` and `X-SessionId` (the same UUID)
  - `OUTPUT-FORMAT: PROTO`
  - `X-TrackingId`, `ris-os-name`, `ris-os-version`, `ris-sdk-version`,
    `ris-application-version`, `X-ApplicationName`, `X-Locale`, `User-Agent`
- Server frames are binary `PushMessage`; client frames are binary
  `ClientMessage`.
- Each status message is acknowledged by sequence number:[^client]
  - `vehicle_status_updates` (tag 24) is acknowledged with
    `acknowledge_vehicle_status_updates` (tag 28).
  - `vepUpdates` (tag 2) is acknowledged with
    `acknowledge_vep_updates_by_vin` (tag 22).
- **RustBenz usage:** a single connection per `state()` call. It waits up to
  30 s for an update containing the VIN, preferring `full_update`, acks every
  status message, then closes. There is no reconnect and no retry.
  Repeated handshakes are what lead to 429 blocks
  (see [protocol research](protocol-research.md)).
- TLS uses rustls with aws-lc-rs and the OS certificate verifier, the same
  stack as reqwest. Plain `ws://` is refused except to loopback (tests).

**Live result (2026-11-23, EQA 250, parked):** the connection succeeded and a
status update arrived within the timeout.

| Signal | Result |
|---|---|
| SoC | Present |
| Electric range | Present |
| Charging status | Present (`NotCharging`) |
| Preconditioning active | Present |
| Plugged in | Unavailable |
| Charging power | Unavailable |

Vehicle-side timestamps were hours to days old, because the server serves
the car's last report. Which message kind arrived (VSU or VEP) was not
logged; run with `RUST_LOG=mercedes_client=debug` to see it. Why
plugged-in and charging power are missing is **UNKNOWN**. The attributes may
be absent, not VALID, or (for VEP) not mapped.

# Message subset

`crates/mercedes-client/src/proto.rs` was transcribed by hand from the
generated descriptors.[^pb2] Field numbers:

- `VehicleStatusUpdates`: `sequence_number`=1, `vehicle_status_updates`=2
  (map VIN → `VehicleStatusUpdate`).
- `VehicleStatusUpdate`:

  | Field | Number | Type |
  |---|---|---|
  | `fin_or_vin` | 1 | |
  | `full_update` | 2 | |
  | `charge_inlets` | 26 | |
  | `charging_power` | 37 | `DoubleAttribute` |
  | `chargingstatus` | 50 | |
  | `precond_active` | 167 | |
  | `rangeelectric` | 183 | `Int64DistanceAttribute` |
  | `soc` | 196 | `Int64RatioAttribute` |

- Attribute wrappers: `value`=1 and `metadata`=2 (`VSUMetadata`:
  `timestamp`=1, `status`=2), plus `unit`=3 for distance and ratio values.
- `VEPUpdatesByVIN`: `updates`=1 (map), `sequence_number`=2.
  `VEPUpdate.attributes`=11 is a map from name to `VehicleAttributeStatus`,
  with `status`=3, `timestamp_in_ms`=10, and the value oneof `int`=4,
  `bool`=5, `double`=7, `nil`=8.

# Value semantics (from schema enums)

- `AttributeStatus`: VALID=0, NOT_RECEIVED=1, INVALID=3, NOT_AVAILABLE=4.
  Anything other than VALID maps to `None`. Legacy VEP `status` uses the same
  numbers.[^vsu]
- `Chargingstatus`:

  | Code | Meaning |
  |---|---|
  | 0 | CHARGING |
  | 1 | END_OF_CHARGE |
  | 2 | CHARGE_BREAK |
  | 3 | CABLE_UNPLUGGED |
  | 4 | CHARGING_ERROR |
  | 5 | SLOW |
  | 6 | FAST |
  | 7 | DISCHARGING |
  | 8 | NO_CHARGING |
  | 9–11 | variants of 0, 5 and 6 "after reaching trip target" |
  | 12 | EVSE communication, no energy flow |
  | 13 | AC active |
  | 14 | DC active |
  | 15 | SoH calibration |
  | 16 | UNKNOWN |

  Legacy ints are identical.[^vsu-enums] Because of proto3, CHARGING (0) is
  omitted on the wire, so a present, VALID attribute with no value means
  *charging*.
- `ChargeInletsCouplerState`: UNKNOWN=0, PLUGGED=1, VEHICLE_PLUGGED=2,
  VEHICLE_NOT_PLUGGED=3, DEFECT=5.
- `DistanceUnit`: UNSPECIFIED=0, KILOMETERS=1, MILES=2. Whether `value` is
  in the display unit is **UNVERIFIED**, so values in miles are dropped.
- Charging power is in kW (mbapi2020 sensor unit).

Domain mapping: see [vehicle state](../domain/vehicle-state.md) and
`status.rs`.

# Widget REST endpoint (not used)

`GET https://widget.emea-prod.mobilesdk.mercedes-benz.com/v1/vehicle/{vin}/vehicleattributes`
returns a `VehicleStatusUpdate` with about 15 fields: SoC, ranges, tank
levels and position. It has **no charging or preconditioning state**.[^pr461]
Live on 2026-11-23 it returned **HTTP 400** twice, with the headers matching
the source; the cause is unknown. The client code for it was removed.

[^pb2]: mbapi2020 generated descriptors
[^client]: mbapi2020 client.py
[^ws]: mbapi2020 websocket.py
[^vsu]: mbapi2020 vsu_helper.py
[^vsu-enums]: mbapi2020 vsu_enums.py
[^pr461]: mbapi2020 PR #461
