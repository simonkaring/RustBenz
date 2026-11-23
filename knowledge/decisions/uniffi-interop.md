---
type: Architecture Decision
title: UniFFI for Swift and Kotlin bindings
description: The Rust core is exposed to iOS and Android through UniFFI from a single dedicated crate, vehicle-ffi.
tags: [decision, ffi, mobile]
status: stable
x-rustbenz-decision-status: accepted
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T18:46:00Z" }
---

# Decision

- `crates/vehicle-ffi` is the only crate that depends on UniFFI. It defines
  FFI-friendly records and converts from `vehicle-core` types.
- Domain crates stay free of FFI attributes so they remain idiomatic Rust and
  usable from the CLI and tests without binding concerns.
- UniFFI proc-macro mode (`uniffi::setup_scaffolding!`) is used; no UDL files.

# Alternatives considered

- **Hand-written C ABI + cbindgen** – full control, but manual memory
  management and duplicated Swift/Kotlin wrappers.
- **swift-bridge / flutter_rust_bridge** – single-platform or Flutter-specific.
- **Annotating domain types directly with UniFFI** – fewer conversions, but
  couples the domain model to the binding generator's type limitations.

# Consequences

- Each exported type exists twice (domain + FFI record). Conversions are
  unit-tested in `vehicle-ffi`.
- Swift/Kotlin binding generation and on-device testing are not yet set up
  (see [current state](../roadmap/current-state.md)).

Related: [shared Rust core](shared-rust-core.md).
