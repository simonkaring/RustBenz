---
type: Protocol Reference
title: Mercedes remote commands (preconditioning)
description: Preconditioning start/stop is a protobuf CommandRequest over the websocket; outcome arrives as asynchronous command status updates. Not implemented.
tags: [mercedes, commands, climate, websocket]
status: draft
x-rustbenz-evidence: source-code
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T19:08:00Z" }
sources:
  - id: client
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/client.py
    title: mbapi2020 client.py L2974-3110 (preheat), L1826-1868 (command status)
  - id: homey-device
    resource: https://github.com/andersdissing/Mercedes-Benz-homey-app/blob/main/drivers/mercedes-vehicle/device.js
    title: Homey device.js
---

# Protocol (from references; not implemented, not live-tested)

- **Start:** send a `ClientMessage.commandRequest` with the VIN, a UUID
  `request_id`, and `zev_preconditioning_start` containing:
  - `type`: `now`, `immediate` or `departure`
  - `departure_time`: minutes since midnight[^client]
- **Stop:** send `zev_preconditioning_stop.type`.[^client] Neither command
  needs a PIN. A PIN is needed only for some other operations, such as
  unlocking.
- **Result:** `PushMessage.apptwin_command_status_updates_by_vin` carries
  state updates: `INITIATION`, `ENQUEUED`, `PROCESSING`, `WAITING`,
  `FINISHED`, `FAILED`, among others. Each update must be acknowledged.[^client]
- The Homey app also sends these commands over the websocket.[^homey-device]

# Rules for RustBenz

- Implement only after [authentication](authentication.md) and
  [vehicle state](vehicle-state.md) are verified live.
- Model the outcome in this order:
  1. *sent*
  2. *accepted* (`ENQUEUED`/`PROCESSING`)
  3. *finished*: the backend says it executed (`FINISHED`)
  4. *confirmed*: the vehicle state shows `precondActive`

  `FAILED` and a timeout are separate terminal outcomes.
- Never retry a state-changing command automatically. Deduplicate by
  `request_id`.
- Live execution needs explicit user confirmation each time. Automated tests
  use mocks only.
- Known issue: mbapi2020 v0.44 notes that some vehicles show
  preconditioning as off while it is running. Issue #493 reports preheat
  rejected as unavailable on an EQE SUV.

[^client]: mbapi2020 client.py
[^homey-device]: Homey device.js
