---
type: Architecture Decision
title: Vehicle provider abstraction
description: Manufacturers are integrated behind the VehicleProvider trait in vehicle-core; provider crates normalize into manufacturer-independent types.
tags: [decision, architecture, domain]
status: stable
x-rustbenz-decision-status: accepted
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T18:46:00Z" }
---

# Decision

- `vehicle-core` defines the domain ([vehicle state](../domain/vehicle-state.md))
  and a small read-only `VehicleProvider` trait: `vehicles`, `state`,
  `capabilities`.
- Each manufacturer gets its own crate (`mercedes-client` today) that
  implements the trait. Wire types never appear in `vehicle-core`.
- The trait uses `impl Future + Send` return types (static dispatch). There is
  one provider today; dynamic dispatch can be added when a second provider
  exists.
- Remote commands are **not** part of the trait yet. They will be added once
  status retrieval is verified live (see
  [Mercedes remote commands](../mercedes/remote-commands.md)).

# Alternatives considered

- **Generic "signal bag" (string keys → values)** – maximally flexible, but
  pushes interpretation into every UI and loses type safety.
- **Expose Mercedes types directly** – fastest to build, but locks the apps
  to one unstable protocol.
- **`async_trait` / boxed trait objects now** – unnecessary until there are
  multiple providers selected at runtime.

# Consequences

- Adding a signal means: domain field → provider mapping → FFI record.
- Unknown provider values map to `None` or an explicit `Unrecognized` variant,
  never to a misleading default.
