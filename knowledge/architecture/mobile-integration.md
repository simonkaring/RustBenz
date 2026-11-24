---
type: Architecture
title: Mobile integration (Android, iOS)
description: How the native apps consume the Rust core — UniFFI API surface, Android build pipeline, login redirect, secure storage, TLS, and toolchain versions.
tags: [architecture, mobile, android, ios, uniffi]
status: draft
generated: { by: opencode/claude-opus-5-5, at: "2026-11-24T09:20:00Z" }
x-rustbenz-implementation: crates/vehicle-ffi, apps/android
---

# Rust API exposed to apps (`crates/vehicle-ffi`)

UniFFI proc-macros with `async_runtime = "tokio"`. Async methods become Kotlin
`suspend` functions and Swift `async` functions. The generated Kotlin package
is `app.rustbenz.core`; the Swift module is `RustBenzCore`.

| Object / function | Purpose |
|---|---|
| `PendingLogin()` → `authorization_url()`, `complete(callback_url)` | One PKCE login. Single use. Returns opaque token JSON |
| `Session(token_json)` → `refresh_if_needed()`, `vehicles()`, `vehicle_state(id)` | Authenticated calls. `refresh_if_needed` returns new token JSON to persist |
| `CoreError` (Kotlin `CoreException`) | Typed errors (see below) |

`CoreError` variants: `NotAuthenticated`, `SessionExpired`, `Forbidden`,
`RateLimited`, `VehicleNotFound`, `LoginFailed`, `Network`, `Protocol`.

Tokens cross the boundary only as an opaque string. Platforms persist it in
secure storage ([credential storage](../decisions/credential-storage.md)).
`vehicle-ffi` depends on `mercedes-client`; apps never see Mercedes types.

# Android (`apps/android`), implemented

- **Build:** Gradle runs `cargo ndk -t x86_64 -t arm64-v8a build --release -p vehicle-ffi`,
  then `uniffi-bindgen generate --library … --language kotlin` into
  `app/build/generated/uniffi`, before `preBuild`. Nothing generated is
  committed. The Kotlin bindings use JNA (`jna@aar`) and coroutines.
- **Login:** a Custom Tab opens Mercedes' page. The `rismycar://login-callback`
  intent filter on `MainActivity` (`singleTask`) receives the redirect. See
  [authentication](../mercedes/authentication.md).
- **Tokens:** AES-256-GCM with a non-exportable Android Keystore key. The
  ciphertext is stored in private SharedPreferences, and
  `allowBackup="false"`.
- **UI:** a Compose Material 3 dashboard. Each signal shows the vehicle-side
  age, "not reported" is kept distinct from zero, and pull-to-refresh is
  supported. A status read takes up to 30 s (one websocket read).
- **TLS:** rustls with bundled `webpki-roots`. The rustls platform verifier
  needs a JNI init and a Gradle component on Android; this is marked as a
  `shortcut:` in `crates/mercedes-client/src/tls.rs`.

Verified on 2026-11-24: `assembleDebug` and the unit tests pass. The app
installs and starts on an emulator (API 36.1 Google Play x86_64, WHPX).
**Not yet verified:** live login and status on Android.

Known limits:
- The first vehicle is used; there is no vehicle picker.
- If the process dies while the browser is open, the PKCE verifier is lost
  and the user must start the login again.
- On a phone that also has the official Mercedes app, both apps claim
  `rismycar://`; Android may show a chooser.

# Toolchain (as installed 2026-11-24)

| Component | Version |
|---|---|
| AGP | 9.5.2 (built-in Kotlin) |
| Gradle | 9.9.2 |
| Kotlin compose plugin | 2.4.21 |
| Compose BOM | 2026.11.01 |
| compileSdk | 37.1 (required by Compose 1.13) |
| targetSdk | 36 |
| minSdk | 28 |
| NDK | 28.2.13676358 |
| JDK | Microsoft OpenJDK 21 |
| cargo-ndk | 4.1.2 |

`sdkmanager` is deprecated in favour of the `android` CLI
(`android --no-metrics sdk install platforms/android-37.1`). Use
`--no-metrics` to opt out of usage telemetry.

# iOS

Not started. Building requires macOS/Xcode. The plan is
`uniffi-bindgen generate --language swift`, an XCFramework of the static
library, `ASWebAuthenticationSession` with the `rismycar` callback scheme,
and Keychain storage.
