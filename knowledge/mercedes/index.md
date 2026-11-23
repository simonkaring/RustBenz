# Research

* [Protocol research](protocol-research.md) - API landscape (official vs unofficial), reference projects, regions, reliability and ToS risks.

# Protocol

* [Authentication](authentication.md) - OAuth 2.0 + PKCE against id.mercedes-benz.com; RustBenz browser-paste login variant.
* [Vehicle discovery](vehicle-discovery.md) - GET /v2/vehicles JSON shape and normalization.
* [Capabilities](capabilities.md) - Command availability endpoint and mapping to domain capabilities.
* [Vehicle state](vehicle-state.md) - Protobuf status; websocket is the required channel, widget REST is limited and returned HTTP 400 live.
* [Remote commands](remote-commands.md) - Preconditioning start/stop protocol and command-outcome rules (not implemented).
