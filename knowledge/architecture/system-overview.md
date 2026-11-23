---
type: Architecture
title: System overview
description: Crate layout, responsibilities, dependency rules and data flow of the RustBenz monorepo.
tags: [architecture, rust, workspace]
status: stable
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T21:15:00Z" }
x-rustbenz-implementation: Cargo.toml
---

# Layout

```text
apps/cli            eqa binary: developer CLI and live-integration harness
apps/ios, android   native shells (not started)
crates/vehicle-core      domain types, VehicleProvider trait, ProviderError
crates/mercedes-client   unofficial Mercedes API: auth, REST, normalization
crates/vehicle-storage   SQLite snapshot store (rusqlite, bundled)
crates/vehicle-ffi       UniFFI records for Swift/Kotlin
knowledge/               this OKF bundle
```

# Dependency rules

```text
eqa ─► mercedes-client ─► vehicle-core ◄─ vehicle-storage
                                       ◄─ vehicle-ffi
```

- `vehicle-core` depends on nothing project-specific. It holds no I/O and no
  manufacturer code.
- `mercedes-client` keeps its wire types private and returns domain types
  ([isolation decision](../decisions/mercedes-isolation.md)).
- Only `vehicle-ffi` depends on UniFFI ([decision](../decisions/uniffi-interop.md)).
- Secret storage belongs to the app layer (CLI keyring, Keychain, Keystore),
  not the core ([decision](../decisions/credential-storage.md)).

# Data flow (current)

1. `eqa auth login` runs the PKCE login ([authentication](../mercedes/authentication.md))
   and stores a `TokenSet` in the OS keyring.
2. Before each command, the CLI loads the tokens and refreshes them if they
   expire within 60 s. It stores the new tokens, then builds a
   `MercedesClient`.
3. The client implements `VehicleProvider`:
   - `vehicles` and `capabilities` use JSON REST.
   - `state` opens one websocket connection, decodes the first protobuf
     status update and closes
     ([Mercedes vehicle state](../mercedes/vehicle-state.md)).
4. `eqa vehicles status` saves each `VehicleState` snapshot through
   `vehicle-storage` to `<local data dir>/rustbenz/snapshots.sqlite`.

# Conventions

- Edition 2024, workspace lints: `unsafe_code = forbid`, `clippy::unwrap_used = warn`.
- Errors use typed `thiserror` enums. Messages never include tokens, VINs or
  response bodies.
- `VehicleId` and `TokenSet` have redacted `Debug` output.
- Tests: unit tests next to the code. HTTP tests use `wiremock` in
  `crates/mercedes-client/tests/`, with **synthetic** fixtures.
