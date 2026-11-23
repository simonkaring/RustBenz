# RustBenz

Native, local-first Mercedes-Benz EV companion app. It has a shared Rust core with SwiftUI and Jetpack Compose apps (the apps are not started yet).

> Uses the **unofficial** Mercedes mobile-app API. It may break or be rate-limited at any time. Use your own account at your own risk.

```sh
cargo test --workspace
cargo run -p eqa -- auth login       # browser sign-in; tokens go to the OS keyring
cargo run -p eqa -- vehicles list
cargo run -p eqa -- vehicles capabilities
```

Start with [`AGENTS.md`](AGENTS.md) and [`knowledge/index.md`](knowledge/index.md).
