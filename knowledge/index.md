---
okf_version: "0.3"
title: RustBenz knowledge
description: Architecture, decisions, domain model and Mercedes-Benz protocol research for the RustBenz EV companion app. Start here before any task.
---

# Start here

* [Current state](roadmap/current-state.md) - What works, what is unverified, open questions, next task.

# Sections

* [Architecture](architecture/) - Crate layout, dependency rules, data flow.
* [Decisions](decisions/) - Accepted architecture decisions with rationale and alternatives.
* [Domain](domain/) - Manufacturer-independent vehicle model.
* [Mercedes](mercedes/) - Unofficial Mercedes API: authentication, discovery, capabilities, state, commands, research.
* [Roadmap](roadmap/) - Project state and next steps.

# Conventions

* Concepts follow [OKF v0.3](https://github.com/open-knowledge-format/open-knowledge-format/blob/main/SPEC.md). See `AGENTS.md` §3 for this repository's frontmatter rules.
* `x-rustbenz-evidence` gives the strongest evidence behind a protocol concept: `live-verified`, `official-docs`, `source-code` or `hypothesis`. Individual claims in the body marked **UNVERIFIED** override it.
* `x-rustbenz-implementation` gives the repository path that implements the concept. Code is authoritative for current behavior.
