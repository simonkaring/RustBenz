---
type: Domain Model
title: Vehicle, state and capabilities
description: Manufacturer-independent vehicle model — readings with timestamps, battery/charging/climate state, capabilities, and error taxonomy.
tags: [domain, battery, charging, climate]
status: stable
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T19:08:00Z" }
x-rustbenz-implementation: crates/vehicle-core/src
---

# Core rules

- **Unavailable is not zero.** Every signal is `Option<Reading<T>>`. `None`
  means the provider did not report it.
- **Freshness is explicit.** `Reading.observed_at` is the timestamp from the
  vehicle, if one was provided. `VehicleState.retrieved_at` is when the client
  received the snapshot. The UI must show how old the data is and never
  present cached data as live.
- **Unknown codes are kept visible.** A provider value with no mapping
  becomes `Unrecognized`, not a guessed variant.
- **Validated units.** `Percent` (0–100) and `Kilometers` (finite, ≥ 0)
  reject invalid values, including during deserialization.

# Model

| Type | Fields |
|---|---|
| `Vehicle` | `id: VehicleId` (VIN; redacted in Debug/Display), `model: Option<String>` |
| `BatteryState` | `state_of_charge: Percent`, `electric_range: Kilometers` |
| `ChargingState` | `status: ChargingStatus`, `plugged_in: bool`, `power_kw: f64` |
| `ClimateState` | `preconditioning_active: bool` |
| `Capability` | `ClimatePreconditioning`, `ChargeProgramConfiguration`, `RemoteLock` |
| `ProviderError` | `NotAuthenticated`, `SessionExpired`, `Forbidden`, `RateLimited`, `VehicleNotFound`, `Network`, `Protocol`, `NotSupported` |

`ChargingStatus` has the variants `Charging`, `NotCharging`, `Complete`,
`Fault` and `Unrecognized`. It is the target model; the Mercedes code mapping
is not yet known (see [Mercedes vehicle state](../mercedes/vehicle-state.md)).

Command types (accepted versus confirmed) are deliberately not defined yet.
See [remote commands](../mercedes/remote-commands.md) for the intended
outcome model.

Persistence: `VehicleState` is stored as JSON in SQLite
([local-first decision](../decisions/local-first.md)).
