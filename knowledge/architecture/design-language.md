---
type: Design System
title: Design language ("dark luxury") and data-honesty rules
description: Visual language, component inventory, information architecture and the rules that keep sample, cached and live data distinguishable in the apps.
tags: [design, ui, android, ios]
status: draft
generated: { by: opencode/claude-opus-5-5, at: "2026-11-24T13:45:00Z" }
x-rustbenz-implementation: apps/android/app/src/main/java/app/rustbenz/ui
---

Chosen by the user on 2026-11-24: dark luxury, bottom navigation, a
stylised car illustration, built as working Compose screens. iOS should
mirror this language natively in SwiftUI rather than copy Android widgets.

# Visual language

- **Palette (`Lux`):**
  - Layered near-black surfaces: background `#0B0B0D`, surface `#141417`,
    raised `#1C1C21` / `#26262C`, hairline `#2C2C33`.
  - Warm-white text `#F4F1EA`, muted `#A39F96`, faint `#6E6B65`.
  - Champagne accent `#C9A96E` (bright `#E9D4A4`, deep `#8C7446`).
  - Muted status hues: positive, caution, critical, cool, warm.
- **Type (`LuxType`):**
  - Light or extra-light numerals with tabular figures (hero 88 sp).
  - Uppercase labels with 0.16 em tracking.
  - 15 sp body text. Uses the system font, with no font assets.
- **Shape and depth:** 28 dp panels with a hairline border and a faint top
  sheen. There are no shadows; depth comes from surface layering.
- **Iconography:** a custom 24-grid line set with a 1.6 stroke
  (`LuxIcons`). The Material icon libraries are not used.
- **Motion:** an animated battery sweep, a shimmer while charging, tiles
  that scale when pressed, cross-fades between tabs, and a charge-port
  pulse.
- **Car illustration:** vector line art of a compact SUV with no brand marks,
  drawn on Canvas. A generated raster image was attempted, but the image
  service failed with "Invalid credential".

# Information architecture

| Tab | Contents |
|---|---|
| Home | Battery ring (SoC, range, limit tick), car illustration, status pills, 2×3 quick controls, weekly insights, recent activity |
| Climate | Temperature dial (16–28 °C, 0.5 steps, drag or ±), start/stop, per-seat heating, departure timers |
| Charging | Now panel, charge limit slider, program (Standard/Home/Work), schedule, live session (power curve), history with AC/DC split and DKK cost |
| Trips | Week summary, daily distance bars, consumption trend, trip log with efficiency colouring |
| Vehicle | Live signals, openings, tyres, telemetry groups (HV battery, 12 V, efficiency, service), settings, sign out |

# Data-honesty rules (mandatory)

1. Every displayed value carries a `Source`. `Reported(at)` is rendered as
   the vehicle-side age ("14 h ago"). `Sample` is rendered as a SAMPLE chip.
2. In live mode, car fields not provided by the Rust core are `null` and
   shown as "Not reported". Untagged sample values never mix with real ones.
3. Panels made of sample data carry a single SAMPLE chip at panel level.
4. Controls show real availability. Unavailable controls explain why when
   tapped; the availability list mirrors live capabilities (see
   [capabilities](../mercedes/capabilities.md)).
5. Remote commands use the confirm → sent → accepted → confirmed-by-car flow
   (see [remote commands](../mercedes/remote-commands.md)). Until commands
   are implemented, the flow is labelled "Simulation · not sent to your car".
6. "Explore with sample data" on the sign-in screen opens a full preview
   with no account and no network calls.

# Known gaps

- Settings rows (units, notifications) are placeholders that explain
  themselves.
- Trips and charging history are sample data until RustBenz records local
  snapshots over time.
- The car illustration is a generic crossover, not an exact EQA silhouette.
