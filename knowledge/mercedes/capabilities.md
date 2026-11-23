---
type: Protocol Reference
title: Mercedes capability discovery
description: Per-vehicle feature and command availability endpoints, and how command names map to domain capabilities.
tags: [mercedes, rest, capabilities]
status: stable
x-rustbenz-evidence: live-verified
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T20:05:00Z" }
verified:
  - { by: opencode/claude-opus-5-5, at: "2026-11-23T19:08:00Z" }
  - { by: "process:eqa-live-capabilities", at: "2026-11-23T20:05:00Z" }
sources:
  - id: init
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/__init__.py
    title: mbapi2020 __init__.py L80-148
  - id: const
    resource: https://github.com/ReneNulschDE/mbapi2020/blob/6976c44e63d203708e60e987d2f8d76376e74f8a/custom_components/mbapi2020/const.py
    title: mbapi2020 const.py (command names L690, L706, L1913)
---

# Endpoints

- `GET {bff}/v1/vehicle/{vin}/capabilities`: returns `features` (map) and
  `vehicle` (info). It is **not used yet**. Some vehicles answer it with HTTP
  401 (mbapi2020 issue #83).[^init]
- `GET {bff}/v1/vehicle/{vin}/capabilities/commands` returns
  `commands[]`.[^init] Each command has:
  - `commandName`
  - `isAvailable`
  - `capabilityInformation[]`
  - `parameters[]` (`parameterName`, `allowedEnums`)

# Domain mapping (implemented)

| Domain `Capability` | Required available commands |
|---|---|
| `ClimatePreconditioning` | `ZEV_PRECONDITIONING_START` **and** `ZEV_PRECONDITIONING_STOP` |
| `ChargeProgramConfiguration` | `CHARGE_PROGRAM_CONFIGURE` |
| `RemoteLock` | `DOORS_LOCK` |

Command names come from mbapi2020.[^const] A missing `isAvailable` is treated
as unavailable. `eqa vehicles capabilities` also prints the raw command list,
which is how new mappings should be found.

Availability depends on the vehicle, the market and Digital Extras
subscriptions. RustBenz only reports what Mercedes says and never works
around entitlements.

# Live observation: EQA 250 (2021, EU account, 2026-11-23)

The endpoint returned 61 commands, 11 of them available. The response shape
matched the parser; nothing was dropped.

- **Available:**
  - `ZEV_PRECONDITIONING_START`, `ZEV_PRECONDITIONING_STOP`
  - `ZEV_PRECONDITION_CONFIGURE`, `ZEV_PRECONDITION_CONFIGURE_SEATS`
  - `TEMPERATURE_CONFIGURE`
  - `CHARGE_PROGRAM_CONFIGURE`, `BATTERY_CHARGE_PROGRAM_CONFIGURE`,
    `BATTERY_MAX_SOC_CONFIGURE`
  - `WEEK_PROFILE_CONFIGURE`, `WEEK_PROFILE_CONFIGURE_V2`
  - `REMOTE_UPDATE_START`
- **Listed but unavailable:**
  - `DOORS_LOCK`/`DOORS_UNLOCK`, `WINDOWS_*`, `SUNROOF_*`, `SIGPOS_START`
  - `THEFTALARM_*`, `HVBATTERY_*_CONDITIONING`, `CHARGING_CONFIGURE`,
    `CHARGE_COUPLER_STOP`, `SPEEDALERT_*`
  - plus others such as `AUXHEAT_*` and `ENGINE_*`

The mapped domain capabilities were `ClimatePreconditioning` and
`ChargeProgramConfiguration`. This matches the user's report that climate
control works in the official app. It is a **hypothesis** that the
unavailable lock and window commands require Digital Extras subscriptions; it
could also be the vehicle hardware or market.

[^init]: mbapi2020 __init__.py
[^const]: mbapi2020 const.py
