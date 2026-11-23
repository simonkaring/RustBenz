# AGENTS.md

> Agent instructions for developing and maintaining RustBenz.
> Applies to all AI coding agents working in this repository.

## 1. Project Overview

RustBenz is a premium, cross-platform electric vehicle companion application.

The application provides vehicle telemetry, charging information, remote climate control, and eventually OBD2 diagnostics through a modern native mobile experience.

### Technology Stack

| Component | Technology |
|---|---|
| Shared core | Rust |
| iOS | SwiftUI |
| Android | Kotlin + Jetpack Compose |
| Interoperability | UniFFI |
| Networking | Reqwest + Rustls |
| Async runtime | Tokio |
| Serialization | Serde |
| Persistence | SQLite |
| Diagnostics | Tracing |
| Knowledge management | Open Knowledge Format (OKF) |

### Architectural Principles

- Native-first user experience.
- Shared Rust business logic.
- Local-first architecture.
- Secure-by-default implementation.
- Manufacturer-independent domain models.
- Explicit boundaries around external APIs.
- Minimal dependencies and abstractions.
- Maintainability over cleverness.
- Correctness over implementation speed.

Do not introduce additional frameworks, services, or infrastructure without a demonstrated requirement.

---

## 2. Agent Operating Principles

Act as a senior software engineer responsible for the long-term quality of the project.

### Autonomy

- Execute well-defined tasks without unnecessary confirmation.
- Inspect existing implementations before introducing new ones.
- Make reasonable implementation decisions independently.
- Prefer completing a small, working vertical slice over scaffolding multiple unfinished features.
- Ask questions only when missing information materially affects correctness, security, or architecture.
- Never claim functionality works without verification.
- Do not modify unrelated code.
- Do not overwrite or revert user changes without permission.
- Never push, rebase, or force-push unless explicitly requested.

### Engineering Judgment

Prefer:

- Simple solutions over elaborate abstractions.
- Existing conventions over personal preferences.
- Explicit types over ambiguous representations.
- Composition over inheritance.
- Small, cohesive modules.
- Reusable domain logic.
- Incremental implementation.
- Tests that verify behavior rather than implementation details.

Avoid:

- Premature optimization.
- Speculative generalization.
- Unnecessary design patterns.
- Large refactors unrelated to the task.
- Duplicate implementations.
- Placeholder functionality presented as complete.
- Excessive comments or documentation.
- Silently swallowing errors.
- Introducing dependencies for trivial functionality.

**Rule: Build what is needed now while preserving reasonable extension points for the future.**

---

## 3. Open Knowledge Format

This repository uses Open Knowledge Format (OKF) as its persistent knowledge system.

Specification:
https://github.com/open-knowledge-format/open-knowledge-format (v0.3; the GoogleCloudPlatform repository is deprecated)

The `knowledge/` directory contains curated architectural, technical, and domain knowledge.

### Before Every Task

1. Read this `AGENTS.md`.
2. Read `knowledge/index.md` if it exists.
3. Identify and read relevant OKF concepts.
4. Inspect the affected source code and tests.
5. Identify applicable architectural decisions.
6. Formulate a concise implementation plan.

Do not load the entire knowledge repository unless necessary.

### During Implementation

- Follow documented architectural decisions.
- Update knowledge when significant new facts are established.
- Record important architectural decisions.
- Preserve provenance and verification metadata.
- Link related concepts.
- Distinguish verified facts from hypotheses.
- Keep documentation synchronized with implementation.
- Extend established components instead of creating parallel implementations.
- Never silently overwrite a conflicting architectural decision. Deprecate it
  with `superseded_by`, or raise the conflict with the user.

### After Implementation

Update relevant OKF concepts when changes affect:

- Architecture or module responsibilities.
- Public interfaces and contracts.
- Authentication or API behavior.
- External dependencies or protocols.
- Domain models.
- Important implementation constraints.
- Significant technical decisions.

Update the knowledge log and project state according to the repository's OKF conventions.

Do not create knowledge documents for trivial changes.

### OKF Conventions in This Repository

- The bundle root is `knowledge/`, and `knowledge/index.md` declares `okf_version: "0.3"`.
- Every concept needs frontmatter with at least `type`, `title`, `description`
  and `generated: { by, at }`. Types in use: `Architecture`,
  `Architecture Decision`, `Domain Model`, `Protocol Reference`, `Research`,
  `Project State`.
- Actors: agents use `<tool>/<model>` (for example `opencode/claude-opus-5-5`),
  and people use `human:<id>`. Only add `verified` after actually checking the
  content against its sources or against live behavior. An agent check gives
  the machine-confirmed tier at most. Update `generated.at` on every
  meaningful content change; doing so invalidates older verifications.
- External claims go in `sources` entries with an `id`, and are cited in the
  body as `[^id]` footnotes. Undocumented Mercedes behavior without a source
  must be marked **UNVERIFIED** in the body.
- `x-rustbenz-evidence` (`live-verified` | `official-docs` | `source-code` |
  `hypothesis`) is the strongest evidence behind a protocol concept.
  `x-rustbenz-implementation` is the path of the implementing code.
- Use `status: draft` for incomplete or unverified concepts. Use
  `status: deprecated` with `superseded_by` when retiring a concept; never
  delete it.
- Keep the directory `index.md` files current: one bullet per concept, using
  its `description`. Add an entry to `knowledge/log.md` (newest date first)
  and update `knowledge/roadmap/current-state.md` after every task that
  changes knowledge.
- Never put tokens, VINs, locations or unredacted responses in knowledge files.

### Knowledge Reliability

Use the following precedence when sources conflict:

1. Verified runtime behavior and tests.
2. Current implementation.
3. Current authoritative external documentation.
4. Verified OKF knowledge.
5. Historical notes and unverified assumptions.

Investigate contradictions rather than silently choosing a convenient source.

Source code describes what is implemented; documentation may describe intended behavior. Distinguish these explicitly.

Never invent technical knowledge to complete documentation.

---

## 4. Repository Architecture

Target structure:

```text
apps/
  ios/
  android/
  cli/

crates/
  vehicle-core/
  mercedes-client/
  vehicle-storage/
  vehicle-ffi/
  obd-core/

knowledge/
docs/
tests/
```

The structure may evolve when justified by actual implementation needs.

### Crate Responsibilities

**vehicle-core**
- Manufacturer-independent domain models.
- Vehicle capabilities.
- Vehicle state representations.
- Provider interfaces.
- Command and error types.

**mercedes-client**
- Mercedes authentication.
- Mercedes API transport.
- Vehicle discovery.
- Vehicle state normalization.
- Capability discovery.
- Remote command execution.

**vehicle-storage**
- Local persistence.
- Cached vehicle state.
- Historical telemetry.
- Database migrations.

**vehicle-ffi**
- UniFFI bindings.
- Stable mobile-facing interfaces.
- Conversion between internal and exported types.

**obd-core**
- OBD2 protocol handling.
- Diagnostic data parsing.
- Vehicle telemetry normalization.

**apps/cli**
- Development and debugging tools.
- Mercedes integration proof of concept.
- Explicitly authorized live integration testing.

### Dependency Rules

- `vehicle-core` must not depend on Mercedes-specific implementations.
- UI applications must not implement vehicle protocol logic.
- `mercedes-client` must not depend on mobile UI frameworks.
- Platform-specific functionality belongs in platform-specific layers.
- External API response types must not leak directly into public domain interfaces.
- Avoid circular dependencies.
- Prefer narrow, stable interfaces between modules.

---

## 5. Rust Engineering Standards

Write idiomatic, maintainable Rust.

### General

- Follow established Rust API guidelines.
- Use strong typing for domain concepts.
- Prefer explicit error handling.
- Use `Result` and `Option` appropriately.
- Avoid `unwrap()` and `expect()` in production paths unless an invariant is demonstrably guaranteed.
- Avoid unnecessary allocations, cloning, and blocking operations.
- Keep public APIs small and intentional.
- Use async Rust for asynchronous I/O.
- Prefer immutable data where practical.
- Avoid unsafe Rust unless necessary and documented.

### Error Handling

- Use typed errors with meaningful context.
- Preserve underlying error causes.
- Distinguish authentication, authorization, network, protocol, and vehicle errors.
- Do not silently ignore failures.
- Never expose credentials or sensitive response contents through errors.

### Documentation

- Document public APIs when behavior is not obvious.
- Explain invariants and non-obvious design decisions.
- Avoid comments that merely repeat the code.
- Keep documentation concise.
- Remove outdated comments when modifying implementations.

### Dependencies

Before adding a dependency:

1. Check whether existing dependencies solve the problem.
2. Evaluate maintenance and compatibility.
3. Consider binary size and mobile support.
4. Avoid unnecessary feature flags.
5. Prefer established, actively maintained libraries.

---

## 6. Native Mobile Development

### iOS — SwiftUI

- Follow Apple platform conventions.
- Use SwiftUI for native interfaces.
- Keep UI state management separate from domain logic.
- Use structured concurrency.
- Support accessibility and Dynamic Type.
- Use native animations and interaction patterns.
- Store sensitive credentials in Keychain.
- Respect iOS background execution restrictions.

### Android — Jetpack Compose

- Follow modern Android architecture practices.
- Use Jetpack Compose and Kotlin.
- Prefer unidirectional data flow.
- Use coroutines and Flow where appropriate.
- Support Material accessibility and adaptive layouts.
- Use Android Keystore-backed credential protection.
- Respect Android background execution restrictions.

### Shared Rust Integration

- Use UniFFI for shared functionality.
- Keep exported interfaces simple and stable.
- Do not expose internal implementation types unnecessarily.
- Avoid blocking the main thread.
- Handle cancellation and lifecycle changes correctly.
- Test bindings on both platforms.

### Design Quality

The application should feel like a premium native product.

Prioritize:

- Clear information hierarchy.
- Excellent typography and spacing.
- Fast, responsive interactions.
- Subtle, purposeful animation.
- High-quality loading, empty, and error states.
- Consistent visual language.
- Platform-appropriate navigation.
- Accessible contrast and controls.

Avoid generic dashboard templates, excessive gradients, unnecessary decorative cards, and visually noisy interfaces.

Do not sacrifice usability for visual novelty.

---

## 7. Mercedes-Benz Integration

The Mercedes-Benz integration may depend on undocumented or unofficial APIs.

Treat this integration as unstable and isolate it accordingly.

### Protocol Research

Relevant reference projects:

- https://github.com/ReneNulschDE/mbapi2020
- https://github.com/andersdissing/mercedes-explore
- https://github.com/andersdissing/Mercedes-Benz-homey-app

Before implementing undocumented behavior:

1. Inspect current reference implementations.
2. Identify the relevant protocol or endpoint.
3. Verify available documentation or source evidence.
4. Record uncertainties in OKF.
5. Implement isolated, testable functionality.
6. Validate against authorized interactions when possible.

Never fabricate endpoints, payloads, or response schemas.

### Authentication

- Use only authorized account access.
- Never bypass MFA or other security controls.
- Never bypass paid Digital Extras entitlements.
- Never disable TLS certificate verification.
- Use secure platform credential storage.
- Do not hardcode credentials or tokens.
- Do not log authentication secrets.
- Handle expired and revoked sessions explicitly.

### Vehicle Data

- Distinguish unavailable data from zero values.
- Preserve timestamps and data freshness.
- Avoid presenting stale information as live.
- Normalize provider-specific responses into domain models.
- Handle missing, unknown, and newly introduced fields.
- Avoid unnecessary polling.

### Remote Commands

Remote vehicle commands require additional care.

- Verify capabilities before offering commands.
- Require explicit authorization for live command execution.
- Never issue live commands during ordinary automated tests.
- Distinguish command acceptance from confirmed execution.
- Handle timeouts and uncertain outcomes.
- Do not blindly retry state-changing requests.
- Prevent accidental duplicate commands.
- Respect vehicle and account rate limits.
- Never bypass security-sensitive command restrictions.

Remote climate control is the initial command target.

Locking, unlocking, charging controls, and other operations must be independently validated before implementation.

---

## 8. Testing and Quality Gates

Every meaningful change must be verified.

### Rust

Run applicable checks:

```bash
cargo fmt --all -- --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
```

### Testing Strategy

- Unit tests for domain logic.
- Integration tests for component interactions.
- Mocked HTTP tests for external APIs.
- Contract tests for protocol serialization.
- Regression tests for fixed bugs.
- Platform tests for native integration.
- Explicit opt-in tests for live vehicle communication.

### Test Quality

- Test observable behavior.
- Include error and edge cases.
- Use deterministic fixtures.
- Do not depend on live Mercedes services in CI.
- Never commit unredacted real vehicle responses.
- Do not weaken tests simply to make them pass.

If a required toolchain is unavailable, report which checks could not run.

Never claim checks passed without executing them.

---

## 9. Git and Conventional Commits

This repository follows the Conventional Commits specification:

https://www.conventionalcommits.org/

### Commit Format

```text
<type>(<scope>): <description>
```

The scope is optional.

Use lowercase types and imperative descriptions.

Examples:

```text
feat(mercedes): add vehicle discovery
feat(climate): implement remote preconditioning
fix(auth): handle expired access tokens
refactor(core): simplify vehicle state models
perf(storage): optimize telemetry queries
test(mercedes): add charging response fixtures
docs(knowledge): document authentication flow
chore(deps): update rust dependencies
ci: add cargo quality checks
build(android): configure uniffi bindings
```

### Allowed Types

| Type | Purpose |
|---|---|
| `feat` | New functionality |
| `fix` | Bug fix |
| `refactor` | Code restructuring without behavior changes |
| `perf` | Performance improvement |
| `test` | Tests |
| `docs` | Documentation or OKF updates |
| `chore` | Maintenance |
| `build` | Build system or dependencies |
| `ci` | CI/CD configuration |
| `style` | Formatting without behavioral changes |
| `revert` | Reverting a previous commit |

### Commit Rules

- Keep commits focused and logically atomic.
- Use a clear, concise subject.
- Prefer subjects of approximately 72 characters or fewer.
- Do not end the subject with a period.
- Explain motivation and trade-offs in the body when useful.
- Reference issues when applicable.
- Use `!` and a `BREAKING CHANGE:` footer for breaking changes.
- Separate unrelated changes into separate commits.
- Do not combine unrelated refactoring with feature implementation.
- Include related tests and knowledge updates in the same logical change.

### Breaking Change Example

```text
feat(core)!: redesign vehicle provider interface

Replace the previous provider contract with capability-aware
vehicle operations.

BREAKING CHANGE: VehicleProvider implementations must adopt
the new capability and command interfaces.
```

### Git Safety

**Never create commits automatically.**

Unless explicitly instructed:

- Do not run `git commit`.
- Do not push branches.
- Do not merge or rebase.
- Do not amend existing commits.
- Do not rewrite history.
- Do not discard uncommitted changes.

When a task is complete, suggest an appropriate Conventional Commit message.

If the user explicitly requests a commit, inspect the diff and stage only files belonging to the requested change.

Never include secrets, generated credentials, or sensitive vehicle data.

---

## 10. Task Execution Workflow

Follow this process for implementation tasks.

### Step 1 — Understand

- Read the user request.
- Inspect relevant OKF concepts.
- Examine existing code.
- Identify dependencies and constraints.
- Determine acceptance criteria.

### Step 2 — Plan

For non-trivial tasks, create a concise plan covering:

- Intended changes.
- Affected components.
- Technical approach.
- Risks or unknowns.
- Verification strategy.

Do not produce lengthy plans for trivial changes.

### Step 3 — Implement

- Make focused, incremental changes.
- Follow existing patterns.
- Keep modules cohesive.
- Avoid unrelated modifications.
- Add or update tests.
- Update relevant knowledge.

### Step 4 — Verify

- Run applicable formatting, linting, and tests.
- Inspect the resulting diff.
- Check for accidental changes.
- Verify documentation consistency.
- Identify unresolved issues.

### Step 5 — Report

Provide a concise completion summary containing:

**Implemented**
- What changed.

**Verified**
- Tests and checks actually executed.

**Knowledge**
- Relevant OKF concepts updated, if any.

**Remaining**
- Unresolved issues or limitations, if any.

**Suggested commit**
- One appropriate Conventional Commit message.

Avoid verbose progress reports or repeating the original task.

---

## 11. Security and Privacy

Vehicle integrations handle sensitive personal and operational data.

Treat the following as sensitive:

- Account credentials and tokens.
- Vehicle identification numbers.
- Vehicle location and movement history.
- Home and work locations.
- Authentication and authorization responses.
- Vehicle command credentials.
- Personal account identifiers.

Requirements:

- Never commit secrets.
- Never expose tokens in logs.
- Redact sensitive fields from fixtures.
- Use secure storage.
- Apply least-privilege access.
- Validate external inputs.
- Use HTTPS with certificate verification.
- Avoid unnecessary data collection.
- Do not transmit vehicle telemetry to unrelated services.
- Never execute destructive or security-sensitive operations without authorization.

Do not introduce analytics or telemetry services without explicit approval.

---

## 12. Definition of Done

A task is complete when:

- The requested behavior is implemented.
- The implementation follows repository architecture.
- Relevant tests have been added or updated.
- Applicable quality checks pass.
- No known security regressions are introduced.
- Relevant OKF knowledge is updated.
- No unrelated changes are included.
- The final report accurately reflects verification.
- A Conventional Commit message is suggested.

If any criterion cannot be satisfied, clearly identify the limitation.

---

## 13. Guiding Principle

**Build reliable software, preserve useful knowledge, and leave the repository easier to understand than you found it.**

Every implementation should improve the product without unnecessarily increasing its complexity.

Every significant discovery should make future development easier.

Every change should be understandable, testable, and maintainable.