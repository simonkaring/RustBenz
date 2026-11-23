---
type: Architecture Decision
title: Local-first, no backend
description: The app talks directly to the manufacturer API from the device and persists history in local SQLite; there is no RustBenz cloud service.
tags: [decision, architecture, storage, privacy]
status: stable
x-rustbenz-decision-status: accepted
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T18:46:00Z" }
---

# Decision

- No RustBenz backend. Devices call the Mercedes API directly with the user's
  own session.
- `vehicle-storage` keeps state snapshots in SQLite on the device. Snapshots
  are stored as JSON documents of the domain `VehicleState`, with
  `PRAGMA user_version` migrations.
- Cached data is always shown with its timestamp; stale data is never shown as
  live.

# Alternatives considered

- **Backend relay/poller** – enables server push and long-term analytics, but
  requires holding user tokens server-side (security and privacy liability)
  and operating infrastructure.
- **Normalized relational schema per signal** – better for analytics queries,
  but needs a migration per new signal. Revisit when charging analytics are
  implemented.

# Consequences

- Push notifications when the app is closed are limited by OS background
  execution rules. A backend may be reconsidered only with a concrete
  requirement and an explicit decision superseding this one.
