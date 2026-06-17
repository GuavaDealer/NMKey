# NMKey

NMKey is the officially endorsed, ultra-lightweight Kotlin/JVM companion library for validating NMCrate plugin license keys from Paper server plugins.

NMCrate is a Minecraft plugin store by NexoMaker.
NMKey handles server fingerprinting, secure communication with NMCrate's NMKey API, AES-GCM encrypted offline grace periods, and Ed25519 response-signature verification.

If your project requires this library, it likely requires obfuscation. We strongly recommend obfuscating both your plugin and this bundled dependency.

## Features

- **Ultra-Lightweight**: No networking bloat, no Ktor, and no background coroutine dispatchers. Relies purely on the native Java 17 `HttpClient` and the Bukkit API.
- **Idiomatic Kotlin**: Uses `kotlinx.serialization` for lightning-fast, compile-time JSON parsing without the heavy footprint of runtime reflection.
- **Dynamic Fingerprinting**: Identifies the server using purely in-memory JVM heuristics (processors, OS arch, server port) to prevent seat spoofing in containerized environments.
- **Offline Grace Period**: Generates a 256-bit AES encrypted cache mapped to the server's fingerprint. Survives API outages for up to 48 hours.
- **Cryptographic Verification**: Verifies signed API responses using Ed25519 to prevent tampering.
- **Self-Cleaning Architecture**: Hooks into Bukkit's `PluginDisableEvent` to automatically release license seats and destroy HTTP threads during server reloads.

## Requirements

- **Java**: 17+ (Java 25 required to run the bundled Paper test server task)
- **Paper API**: 1.17+
- **Kotlin**: 2.4+ (Standard Library and Serialization)

## Security & Obfuscation (Crucial)

Because NMKey is a client-side shaded library, the cryptographic verification only protects against network-level spoofing. If a malicious user opens your compiled JAR in a bytecode editor (like Recaf), they can simply delete the `NMKey.check()` call and bypass the licensing entirely.

To secure your plugin, you **must** run your final compiled JAR through an obfuscator (such as ProGuard, Zelix KlassMaster, or Stringer) and ensure that the shaded `com.nmcrate.key` package is heavily obfuscated.

- Use **Control Flow Obfuscation** to scramble the signature checking logic.
- Use **String Encryption** to hide the `https://www.nmcrate.com` API endpoints and JSON keys.

## Installation

```kotlin
dependencies {
    implementation("com.nmcrate.key:NMKey:1.1.0")
}
```

## Usage

NMKey is designed as a "fire-and-forget" library. Because of the **Self-Cleaning Architecture**, you do not need to perform manual cleanup in `onDisable()`.

### Kotlin (One-Liner)

```kotlin
import com.nmcrate.key.nmKeyAsync
import org.bukkit.plugin.java.JavaPlugin

class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        // Validates async, handles disabling on failure, and auto-cleans on shutdown.
        nmKeyAsync("your-plugin-id")
    }
}
```

### Java Usage

For Java developers, NMKey offers a simple static API. You should wrap the `check` method in Bukkit's async scheduler. Because NMKey is **Self-Cleaning**, you do not need to perform manual cleanup in `onDisable()`.

```java
import com.nmcrate.key.NMKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    private static final String PLUGIN_ID = "your-plugin-id";

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            // NMKey will automatically release the license seat on shutdown
            // if Config.autoReleaseOnDisable is true (default).
            boolean valid = NMKey.check(this, PLUGIN_ID);

            getServer().getScheduler().runTask(this, () -> {
                if (!valid && NMKey.Config.getAutoDisablePlugin()) {
                    getLogger().severe("Invalid license key. Disabling plugin...");
                    getServer().getPluginManager().disablePlugin(this);
                }
            });
        });
    }
}
```

### Configuration

Customize library behavior globally before validation:

```kotlin
NMKey.Config.useOfflineCache = true
NMKey.Config.gracePeriodHours = 24L
NMKey.Config.autoDisablePlugin = true
NMKey.Config.autoReleaseOnDisable = true
```

## How It Works

### Validation (`NMKey.check`)

1. **Public Key Fetch**: Fetches the plugin's public key from the API.
2. **Resource Load**: Reads the `nmkey.txt` bundled within the jar.
3. **Dynamic Fingerprinting**: Generates an in-memory heuristic hash of the host container.
4. **Validation Request**: Posts the payload and a nonce to the validation endpoint.
5. **Verification & Caching**: If valid, verifies the Ed25519 signature and encrypts the response to the local file system using the hardware fingerprint as an AES-GCM key.
6. **Grace Period**: If the network request fails or times out, attempts to decrypt the local cache. If the fingerprint matches and the cache is less than the configured `gracePeriodHours` old, validation succeeds offline.

`NMKey.check` and `NMKey.release` are blocking calls. Run validation asynchronously from the plugin startup using the provided Kotlin extensions or manually wrap them in Bukkit runnables.

### Release (`NMKey.release`)

Posts the key and fingerprint to `/release` to immediately free the license slot when the server shuts down. This method runs synchronously and explicitly destroys the internal HTTP client thread pool to prevent memory leaks during `/reload`. **This is now handled automatically by the Self-Cleaning listener.**

## Development & Testing

### Project Layout

- `src/main/kotlin/com/nmcrate/key/NMKey.kt` - Main validation API
- `src/main/kotlin/com/nmcrate/key/NMKeyExtensions.kt` - Kotlin convenience extensions
- `src/main/kotlin/com/nmcrate/key/NMKeyConfig.kt` - Global configuration settings
- `src/main/kotlin/com/nmcrate/key/NMKeyModels.kt` - Serialization data objects
- `src/test/kotlin/com/nmcrate/key/tests/NMKeyTestPlugin.kt` - Runnable Paper test plugin

### Compatibility Checks

This build defines Paper API compatibility resolution tasks to ensure the declared Paper API targets remain available for the JVM versions NMKey supports:

- `1.17-R0.1-SNAPSHOT` (Java 17)
- `1.18.2-R0.1-SNAPSHOT` (Java 17)
- `1.19.4-R0.1-SNAPSHOT` (Java 17)
- `1.20.6-R0.1-SNAPSHOT` (Java 21)
- `1.21.8-R0.1-SNAPSHOT` (Java 21)
- `26.1.2.build.+` (Java 25)

These checks resolve the target Paper API artifacts; they are not per-version compiled or runtime matrix. You can run them directly via Gradle:

```bash
./gradlew verifyPaperCompatibility
```

### Tests

Normal CI-safe tests exclude live NMCrate API calls:

```bash
./gradlew test
```

Live integration tests validate the bundled test key against the NMCrate API. They require network access and a valid `src/test/resources/nmkey.txt`, and are intentionally separate from the normal `build` lifecycle:

```bash
./gradlew liveIntegrationTest
```

### Publishing

The project uses `GDPublish` explicitly for publishing. `GDPublish` configures itself from `.env` and `gradle.properties`, including the release/snapshot repository URLs, and exposes `publishRelease` / `publishSnapshot` for remote publication.

Release preflight:

```bash
./gradlew clean test shadowJar generatePomFileForMavenPublication
./gradlew liveIntegrationTest
```

Publishing is manual and should be done through GDPublish only:

```bash
./gradlew publishRelease
```

## License

NMKey is released under the Apache License 2.0. See `LICENSE`.

## Contributors

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/GuavaDealer">
        <img src="https://github.com/GuavaDealer.png" width="128" height="128" style="border-radius: 50%;"  alt="GuavaDealer Logo"/>
        <br />
        <b>GuavaDealer</b>
        <br />
        Lead Developer
      </a>
    </td>
  </tr>
  <tr>
    <td align="center">
      <a href="https://github.com/QrackyDev">
        <img src="https://github.com/QrackyDev.png" width="128" height="128" style="border-radius: 50%;"  alt="QrackyDev Logo"/>
        <br />
        <b>QrackyDev</b>
        <br />
        Contributor
      </a>
    </td>
  </tr>
</table>
