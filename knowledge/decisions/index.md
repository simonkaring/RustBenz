# Accepted decisions

* [Shared Rust core with native mobile UIs](shared-rust-core.md) - Business logic, networking and persistence live in Rust; iOS (SwiftUI) and Android (Compose) are thin native shells.
* [UniFFI for Swift and Kotlin bindings](uniffi-interop.md) - The Rust core is exposed to iOS and Android through UniFFI from a single dedicated crate, vehicle-ffi.
* [Vehicle provider abstraction](provider-abstraction.md) - Manufacturers are integrated behind the VehicleProvider trait in vehicle-core.
* [Local-first, no backend](local-first.md) - Direct device-to-manufacturer calls; history in local SQLite; no RustBenz cloud service.
* [Isolate the unofficial Mercedes API](mercedes-isolation.md) - All Mercedes-specific protocol knowledge lives in crates/mercedes-client and is treated as unstable.
* [Secure authentication and token storage](credential-storage.md) - Tokens only in OS credential stores; passwords never persisted.

# Recording a new decision

Create `<slug>.md` with `type: Architecture Decision` and the sections Context, Decision, Alternatives considered and Consequences. To replace a decision, set `status: deprecated` and `superseded_by` on the old one. Never silently edit an accepted decision's outcome.
