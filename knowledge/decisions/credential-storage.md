---
type: Architecture Decision
title: Secure authentication and token storage
description: Tokens are stored only in OS credential stores, never in files, logs or SQLite; the user's password is never persisted.
tags: [decision, security, authentication]
status: stable
x-rustbenz-decision-status: accepted
generated: { by: opencode/claude-opus-5-5, at: "2026-11-24T09:20:00Z" }
---

# Decision

- Access and refresh tokens are stored in platform secure storage:
  - CLI: OS keyring via the `keyring` crate (Windows Credential Manager,
    macOS Keychain, Linux Secret Service).
  - iOS: Keychain (planned).
  - Android: the token JSON is encrypted with AES-256-GCM using a
    non-exportable Android Keystore key and stored in private
    SharedPreferences, with `allowBackup="false"` (`apps/android/.../TokenStore.kt`).
    `EncryptedSharedPreferences` is not used, because the library is
    deprecated.
- Windows limits one credential to 2560 bytes, and the Mercedes token set is
  larger (confirmed live). The CLI therefore stores the JSON in 2048-byte
  chunks (`mercedes-europe.0`, `.1`, …). The `mercedes-europe` entry holds
  the chunk count and is written last. See `apps/cli/src/token_store.rs`.
- The Rust core does not own storage of secrets; it receives and returns a
  token set, and the platform layer persists it.
- Passwords are never persisted. Login is interactive and respects MFA and any
  other challenge Mercedes presents; nothing is bypassed or automated around it.
- Token types implement redacted `Debug`; tokens, VINs and locations never
  appear in logs or error messages.
- Expired/revoked sessions surface as `ProviderError::SessionExpired`; the
  client refreshes once, and asks the user to log in again if refresh fails.

# Alternatives considered

- **Encrypted file / SQLite column** – requires managing a key, which itself
  needs secure storage; no benefit over the OS store.
- **Store password for silent re-login** – rejected: increases blast radius
  and may circumvent MFA intent.

Related: [Mercedes authentication](../mercedes/authentication.md).
