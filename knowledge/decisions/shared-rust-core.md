---
type: Architecture Decision
title: Shared Rust core with native mobile UIs
description: Business logic, networking and persistence live in Rust; iOS (SwiftUI) and Android (Compose) are thin native shells.
tags: [decision, architecture, mobile]
status: stable
x-rustbenz-decision-status: accepted
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T18:46:00Z" }
---

# Context

The product targets iOS and Android with a premium, platform-native feel. The
Mercedes protocol is unofficial and changes without notice, so protocol code
must be fixed once, not per platform.

# Decision

- All vehicle protocol, authentication, normalization and persistence logic is
  written in Rust (Cargo workspace, see [system overview](../architecture/system-overview.md)).
- UIs are native: SwiftUI on iOS, Kotlin + Jetpack Compose on Android.
- Platform layers own only UI, OS integration (notifications, widgets,
  background scheduling) and secure credential storage.

# Alternatives considered

- **Flutter / React Native / KMP UI** – one UI codebase, but weaker native feel
  for widgets, Live Activities and system integrations.
- **Duplicate native implementations (Swift + Kotlin)** – best per-platform
  ergonomics, but every Mercedes API change must be fixed twice.
- **Kotlin Multiplatform core** – viable, but Rust gives a single core usable
  from the CLI, mobile and potentially desktop without a JVM/Kotlin-native split.

# Consequences

- Requires an FFI boundary: see [UniFFI decision](uniffi-interop.md).
- The CLI (`apps/cli`) exercises the same core as mobile and is the primary
  integration test harness until mobile apps exist.
