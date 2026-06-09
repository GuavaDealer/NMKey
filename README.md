# NMKey

NMKey is the officially endorsed lightweight Kotlin/JVM companion library for validating NMCrate plugin license keys from Paper server plugins.

NMCrate is a Minecraft plugin store by NexoMaker. NMKey handles server fingerprinting, secure communication with the NMCrate license API, and Ed25519 response-signature verification.

If your project requires this library, it likely requires obfuscation. We strongly recommend obfuscating both your plugin and this bundled dependency.

## Features

- **Server Fingerprinting**: Identifies the server using hostname, MAC address, and server port.
- **Cryptographic Verification**: Verifies signed API responses using Ed25519 to prevent tampering.
- **Automated Lifecycle Management**: Validates upon start and can optionally release the key slot upon plugin shutdown.
- **Developer-Friendly**: Provides both Kotlin convenience extensions and a straightforward Java API.
- **Bundled Resource Integration**: Automatically reads `nmkey.txt` directly from the plugin's jar.

## Requirements

- **Java**: 17+ (Java 25 required to run the bundled Paper test server task)
- **Paper API**: 1.17+
- **Gradle**: 9.5.1 (Wrapper included)

## Installation & Coordinates

Release coordinates:

```kotlin
group = "com.nmcrate.key"
version = "1.0.0"
```

Gradle Kotlin DSL:

```kotlin
repositories {
    maven("https://nmcrate.com/reposilite/releases")
}

dependencies {
    implementation("com.nmcrate.key:NMKey:1.0.0")
}
```

NMKey is published as a minimized bundled artifact. Consumers should include it in their plugin jar instead of expecting Paper to provide NMKey, Ktor, or Kotlin runtime classes at server runtime.

Example with Shadow:

```kotlin
plugins {
    id("com.gradleup.shadow") version "9.4.2"
}

dependencies {
    implementation("com.nmcrate.key:NMKey:1.0.0")
}

tasks.shadowJar {
    relocate("com.nmcrate.key", "your.plugin.libs.nmkey")
    relocate("io.ktor", "your.plugin.libs.ktor")
    relocate("kotlin", "your.plugin.libs.kotlin")
    relocate("kotlinx", "your.plugin.libs.kotlinx")
}
```

## Usage

### License Key Resource

NMCrate automatically injects the license key into the plugin jar when downloaded.
NMKey expects to find an `nmkey.txt` file at the root of the jar's resources, which it will read, trim, and cache in memory via `JavaPlugin#getResource("nmkey.txt")`.

### Kotlin

NMKey provides small extension functions for Kotlin users.

```kotlin
import com.nmcrate.key.nmKey
import com.nmcrate.key.releaseNmKey
import org.bukkit.plugin.java.JavaPlugin

class MyPlugin : JavaPlugin() {
    private lateinit var keySession: com.nmcrate.key.NMKeySession

    override fun onEnable() {
        server.scheduler.runTaskAsynchronously(
            this,
            Runnable {
                keySession = nmKey("your-plugin-id")
                keySession.disablePluginTaskIfInvalid()
            }
        )
    }

    override fun onDisable() {
        releaseNmKey("your-plugin-id")
    }
}
```

### Java

For Java developers, NMKey offers a simple static API.

```java
import com.nmcrate.key.NMKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class MyPlugin extends JavaPlugin {
    private static final String PLUGIN_ID = "your-plugin-id";

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            if (!NMKey.check(this, PLUGIN_ID)) {
                getLogger().severe("Invalid license key. Disabling plugin...");
                getServer().getScheduler().runTask(this, () ->
                        getServer().getPluginManager().disablePlugin(this)
                );
            }
        });
    }

    @Override
    public void onDisable() {
        NMKey.release(this, PLUGIN_ID);
    }
}
```

## How It Works (API Behavior)

### Validation (`NMKey.check`)

1. **Public Key Fetch**: Fetches and caches the plugin's public key from `/public-key?pluginId=...`.
2. **Resource Load**: Reads and caches the `nmkey.txt` bundled within the jar.
3. **Fingerprinting**: Generates a server fingerprint using the machine's hostname, MAC address, and server port.
4. **Validation Request**: Posts the license key, fingerprint, plugin ID, and a nonce to `/validate`.
5. **Verification**: Accepts the response *only* if the status is `valid` and the Ed25519 signature perfectly matches the payload.
6. **Result**: Returns `false` on missing keys, invalid responses, network failures, request timeouts, or signature validation failures.

`NMKey.check` and `NMKey.release` are blocking calls. Run validation asynchronously from the plugin startup unless you intentionally want startup to wait for license validation. NMKey applies explicit connect/request/socket timeouts so network failure does not hang indefinitely.

### Release (`NMKey.release`)

Posts the key and fingerprint to `/release` to immediately free the license slot when the server shuts down. Release is recommended for plugins that want shutdown to free a license seat promptly.

## Development & Testing

### Project Layout

- `src/main/kotlin/com/nmcrate/key/NMKey.kt` - Static Java validation API
- `src/main/kotlin/com/nmcrate/key/NMKeyExtensions.kt` - Kotlin convenience extensions
- `src/test/kotlin/com/nmcrate/key/tests/NMKeyTestPlugin.kt` - Runnable Paper test plugin
- `src/test/resources/nmkey.txt` - Sample license key used by the test plugin
- `src/test/resources/paper-plugin.yml` - Test plugin descriptor

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
</table>
