---
type: Project State
title: Current project state
description: What is implemented and verified, what is not, open technical questions, and the next task.
tags: [roadmap, status]
status: stable
generated: { by: opencode/claude-opus-5-5, at: "2026-11-24T09:20:00Z" }
---

# Implemented

| Area | State |
|---|---|
| Workspace, domain model, provider trait | Done |
| SQLite snapshot store with migrations | Done; used by `eqa vehicles status` |
| UniFFI API (`vehicle-ffi`) | `PendingLogin`, `Session` and records; Kotlin generated at build time |
| Android app | Read-only dashboard; **verified live on the emulator** (login, vehicle, status) on 2026-11-24 |
| PKCE login, code exchange | Done; **verified live** (2026-11-23, EU account, desktop browser) |
| Token refresh | Done; live-unverified |
| `eqa auth login / status / logout` | Done; tokens split across several OS keyring entries (Windows 2560-byte limit) |
| `eqa vehicles list` | Done; **verified live** (EQA 250) |
| `eqa vehicles capabilities` | Done; **verified live**; preconditioning start/stop available |
| `eqa vehicles status` | Done via websocket push; **verified live**. SoC, range, charging status and preconditioning present; plugged-in and charging power unavailable |
| Climate start/stop | Not started; protocol documented in [remote commands](../mercedes/remote-commands.md) |
| iOS app | Not started (needs macOS/Xcode) |

All automated tests use mocks: wiremock for HTTP and a local websocket server
for push, with synthetic fixtures only.

# Open questions

1. Does browser-paste login work with MFA enabled?
   ([authentication](../mercedes/authentication.md))
2. Why does the widget endpoint return HTTP 400? This has low priority,
   because the endpoint is no longer used.
3. Which push message kind does this account receive (VSU or legacy VEP)?
   Why are plugged-in and charging power unavailable? Check with
   `RUST_LOG=mercedes_client=debug`, ideally while the car is plugged in.
4. Is `rangeelectric.value` in the display unit or always in km? This only
   matters for non-EU regions.
5. Do the hardcoded application version and headers stay accepted?
6. Rate limits: is one websocket connection per `eqa vehicles status` call
   safe at, for example, a few calls per hour?
7. Does token refresh work live? It will be exercised automatically once the
   ~4 h access token expires.
8. Mobile login works on the emulator. What happens on real phones that also
   have the official app (both claim `rismycar://`)?
9. Android TLS uses bundled webpki roots. Switch to rustls-platform-verifier
   (JNI init and Gradle component) when user or enterprise CAs matter.

# Next task (recommended)

**Remote climate preconditioning (start/stop)**, first in the CLI, then as a
dashboard control in the Android app. This needs explicit user
confirmation for every live command.

1. Extend `proto.rs` with `CommandRequest` and the ZEV preconditioning
   start/stop messages, plus `AppTwinCommandStatusUpdatesByVIN`, all
   transcribed from the mbapi2020 descriptors.
2. Add domain command types to `vehicle-core`: a command plus an outcome of
   `Accepted`, `Finished`, `Failed` or `TimedOut`. Gate the command on
   `Capability::ClimatePreconditioning`.
3. Websocket flow: send one `CommandRequest` with a fresh `request_id`, then
   follow its status updates until a terminal state. Never retry
   automatically.
4. Add `eqa climate start|stop` with an interactive confirmation prompt.
   After `FINISHED`, run a status read to confirm `precondActive`.
5. Test against the mock websocket server; verify live once, with the user
   present.
