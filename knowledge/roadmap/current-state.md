---
type: Project State
title: Current project state
description: What is implemented and verified, what is not, open technical questions, and the next task.
tags: [roadmap, status]
status: stable
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T20:30:00Z" }
---

# Implemented

| Area | State |
|---|---|
| Workspace, domain model, provider trait | Done |
| SQLite snapshot store with migrations | Done; not used by the CLI yet |
| UniFFI records (`vehicle-ffi`) | Records and conversions only; Swift/Kotlin generation not set up |
| PKCE login, code exchange | Done; **verified live** (2026-11-23, EU account, desktop browser) |
| Token refresh | Done; live-unverified |
| `eqa auth login / status / logout` | Done; tokens split across several OS keyring entries (Windows 2560-byte limit) |
| `eqa vehicles list` | Done; **verified live** (EQA 250, 2026-11-23) |
| `eqa vehicles capabilities` | Done; **verified live**; preconditioning start/stop available |
| `eqa vehicles status` | Returns `NotSupported`. The widget endpoint (`--raw-out`) returned **HTTP 400** live |
| Climate start/stop | Not started (blocked by status retrieval) |
| iOS / Android apps | Not started |

Login, the vehicle list and capabilities have been run against the live
Mercedes API. All automated tests use mocked HTTP and synthetic fixtures.

# Open questions

1. Browser-paste login is confirmed on desktop. Still open: does it work with
   MFA enabled? ([authentication](../mercedes/authentication.md))
2. ~~Widget message type~~: resolved, it is `VehicleStatusUpdate`
   (mbapi2020 PR #461). Still open: why the widget endpoint returns HTTP 400
   for this account. ([vehicle state](../mercedes/vehicle-state.md))
3. What do the `chargingstatus` integer codes mean, and which units does the
   EQA report range in?
4. Do the hardcoded application version and headers stay accepted, or is a
   `/v1/config` version refresh required?
5. Do rate limits (websocket 429) apply per account, session or device? How
   often can the websocket be connected safely?
6. Which `.proto` source should be used? mercedes-explore has a hand-written
   `.proto` (license not checked) that predates `VehicleStatusUpdates`.
   mbapi2020 only ships generated Python descriptors (MIT), from which
   `.proto` files can be reconstructed with `protoc`-compatible tooling.
7. Mobile login: use an in-app browser session (ASWebAuthenticationSession /
   Custom Tabs) that intercepts the `rismycar://` scheme. Does Mercedes allow
   that redirect for a third-party bundle ID?

# Next task (recommended)

**Read vehicle status over the websocket push channel.** It is the only
channel that carries charging and preconditioning state.

1. Reconstruct the needed `.proto` messages (`PushMessage`, `ClientMessage`,
   `VehicleStatusUpdates`, the acknowledge messages) from mbapi2020's
   generated descriptors (MIT). Record their provenance.
2. Add a websocket client to `mercedes-client` using `tokio-tungstenite`
   (rustls) and `prost`: connect, receive the first status update, send the
   acknowledgement, then disconnect. Use the headers documented in
   [vehicle state](../mercedes/vehicle-state.md).
3. Add `eqa vehicles status` support: connect once, wait for the first
   update, print the normalized `VehicleState`, and save a snapshot. Use no
   background polling, to keep 429 risk low.
4. Test with synthetic protobuf fixtures built from the message definitions
   (never captured payloads), then verify live once.

Only after that: preconditioning start/stop over the same websocket,
following [remote commands](../mercedes/remote-commands.md).
