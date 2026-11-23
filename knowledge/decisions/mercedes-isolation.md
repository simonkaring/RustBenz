---
type: Architecture Decision
title: Isolate the unofficial Mercedes API
description: All Mercedes-specific protocol knowledge lives in crates/mercedes-client and is treated as unstable.
tags: [decision, mercedes, architecture]
status: stable
x-rustbenz-decision-status: accepted
generated: { by: opencode/claude-opus-5-5, at: "2026-11-23T18:46:00Z" }
---

# Context

The consumer Mercedes API used by the official app is undocumented. It is
reverse-engineered by community projects and has changed several times (see
[protocol research](../mercedes/protocol-research.md)).

# Decision

- Only `mercedes-client` knows Mercedes URLs, headers, payloads and codes.
- Wire types are private to the crate; the public surface returns
  `vehicle-core` types.
- Every endpoint implemented must be traceable to a source documented in
  [`knowledge/mercedes/`](../mercedes/index.md). Unverified behavior is marked
  as such in code comments and knowledge.
- Parsing is tolerant: unknown fields are ignored, missing fields become
  `None`, unknown enum codes become `Unrecognized`.
- Contract tests use synthetic, redacted fixtures; CI never calls Mercedes.

# Alternatives considered

- **Official Mercedes-Benz developer APIs** – preferred if usable; see
  [protocol research](../mercedes/protocol-research.md) for why they do not
  currently cover this use case.

# Consequences

- When Mercedes changes the protocol, fixes are confined to one crate.
