# Blueth Core

Shared library for the Blueth plugin ecosystem.

## Modules
- **config** — YAML config wrapper with versioned migration
- **gui** — Inventory GUI builder with click handlers
- **hook** — Vault, PlaceholderAPI integrations
- **scheduler** — Paper/Folia-aware task scheduling
- **persistence** — Flatfile + SQL data storage
- **webhook** — Discord webhook emitter

## Usage
Add as a dependency in your plugin's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":blueth-core"))
}
```

## License
Proprietary — LaunchDay Studio Inc.
