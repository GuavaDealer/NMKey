# Changelog

## 1.1.0

- **Automated Lifecycle**: Implemented `PluginDisableEvent` listeners to automate session cleanup; manual `release()` calls in `onDisable` are no longer required.
- **Configurable Behavior**: Introduced `NMKey.Config` to allow global adjustment of cache TTL, grace period durations, and automatic release toggles.
- **Footprint Optimization**: Refactored core logic into `internal` classes to encapsulate implementation details and reduce the public API surface.
- **Thread Safety**: Resolved coroutine dispatcher conflicts and migrated HTTP operations to a dedicated daemon thread pool to prevent main thread blocking.

## 1.0.0

- Initial NMKey release for NMCrate plugin license validation.
- Added Java API with `NMKey.check(plugin, pluginId)` and `NMKey.release(plugin, pluginId)`.
- Added Kotlin convenience extensions with `nmKey(pluginId)`, `releaseNmKey(pluginId)`, and `NMKeySession`.
- Added signed NMCrate API response verification using Ed25519 digital signatures.
- Added hardware fingerprinting based on hostname, MAC address, and server port (SHA-256).
- Implemented internal caching for license keys and public keys to minimize network and I/O overhead.
- Automated `nmkey.txt` resource loading directly from the plugin's JAR.
- Added Paper API resolution checks for versions from 1.17 through 1.21.x and Experimental 1.22.
- Integrated Ktor/CIO for asynchronous license validation and slot releasing.
