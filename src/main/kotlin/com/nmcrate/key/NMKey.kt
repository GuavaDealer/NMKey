@file:JvmName("NMKey")

package com.nmcrate.key

import kotlinx.serialization.encodeToString
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Singleton utility for managing license key validation and hardware fingerprinting for NMKey-protected plugins.
 *
 * This object handles communication with NMCrate's NMKey API to verify license keys, manage hardware-bound
 * sessions, and ensure the integrity of responses using Ed25519 digital signatures. It maintains an
 * internal cache for public keys and license strings to minimize network overhead.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.0.0
 */
object NMKey {
    /** The official base URL for the NMKey license validation API. */
    const val DEFAULT_API_URL = "https://www.nmcrate.com/api/nmkey/v1"

    private val publicKeys = ConcurrentHashMap<String, String>()
    private val cachedKeys = ConcurrentHashMap<String, String>()
    private val autoCleanupRegistered = ConcurrentHashMap.newKeySet<String>()
    private val heartbeatTasks = ConcurrentHashMap<String, BukkitTask>()
    private val activeSessions = ConcurrentHashMap.newKeySet<String>()

    private var executor: ExecutorService? = null
    private var _client: HttpClient? = null

    private val client: HttpClient
        @Synchronized get() = _client ?: HttpClient.newBuilder()
            .connectTimeout(3000.milliseconds.toJavaDuration())
            .executor(
                Executors.newCachedThreadPool { r ->
                    Thread(r, "NMKey-HTTP-Worker").apply { isDaemon = true; contextClassLoader = null }
                }.also { executor = it },
            )
            .build().also { _client = it }

    /**
     * Shuts down the internal Java HTTP Client executor and clears all memory caches.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.1.0
     */
    @JvmStatic
    @Synchronized
    fun shutdown() {
        executor?.shutdownNow()
        heartbeatTasks.values.forEach { runCatching { it.cancel() } }
        heartbeatTasks.clear()
        autoCleanupRegistered.clear()
        activeSessions.clear()
        executor = null
        _client = null
        clearCache()
    }

    /**
     * Validates a plugin's license key against the remote API synchronously.
     *
     * @param pl The JavaPlugin instance requesting validation.
     * @param pluginId The unique ID of the plugin.
     * @return True if the key is valid and the server signature is verified; false otherwise.
     *
     * @author Idan Nehama (GuavaDealer)
     * @author QrackyDev (Qracky)
     * @since 1.0.0
     */
    @JvmStatic
    fun check(pl: JavaPlugin, pluginId: String): Boolean {
        registerAutoCleanup(pl, pluginId)
        startHeartbeat(pl, pluginId)
        activeSessions.add(pl.name)

        pl.logger.info("NMKey: verifying license for pluginId '$pluginId'.")
        val fp = fingerprint(pl)

        val networkValid = runCatching {
            val publicKey = ensurePublicKey(pl, pluginId)
            val key = readKey(pl) ?: return false
            val nonce = UUID.randomUUID().toString()

            val body = nmJson.encodeToString(KeyRequest(pluginId, key, fp, nonce))
            val request = HttpRequest.newBuilder()
                .uri(URI("$DEFAULT_API_URL/validate"))
                .timeout(7500.milliseconds.toJavaDuration())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val responseBody = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
            val res = nmJson.decodeFromString(KeyResponse.serializer(), responseBody)

            val canonical = "v1|$pluginId|$key|$fp|${res.status}|$nonce|${res.issuedAt}"
            val valid = res.status.equals("valid", ignoreCase = true) &&
                    res.signature.isNotBlank() &&
                    verifySignature(publicKey, canonical, res.signature)

            if (valid) saveOfflineCache(pl, res, fp)
            valid
        }.onFailure { pl.logger.warning("NMKey: Network validation failed (${it::class.java.simpleName}).") }
            .getOrNull()

        return networkValid ?: checkOfflineCache(pl, fp)
    }

    /**
     * Notifies the API synchronously that the license key is being released by this hardware instance.
     *
     * @param pl The JavaPlugin instance releasing the key.
     * @param pluginId The unique ID of the plugin.
     *
     * @author Idan Nehama (GuavaDealer)
     * @author QrackyDev (Qracky)
     * @since 1.0.0
     */
    @JvmStatic
    fun release(pl: JavaPlugin, pluginId: String) {
        if (!activeSessions.remove(pl.name)) return shutdown()
        heartbeatTasks.remove(pl.name)?.cancel()

        runCatching {
            val key = readKey(pl) ?: return@runCatching
            val body = nmJson.encodeToString(KeyRequest.serializer(), KeyRequest(pluginId, key, fingerprint(pl)))
            val request = HttpRequest.newBuilder()
                .uri(URI("$DEFAULT_API_URL/release"))
                .timeout(7500.milliseconds.toJavaDuration())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            client.send(request, HttpResponse.BodyHandlers.discarding())
        }.onFailure { pl.logger.warning("NMKey: Failed to release license seat: ${it.message}") }

        shutdown()
    }

    /**
     * Reads the license key from the plugin's resources or internal cache.
     *
     * @param pl The JavaPlugin instance to read the key for.
     * @return The trimmed license key string, or null if not found or empty.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @JvmStatic
    fun readKey(pl: JavaPlugin): String? = cachedKeys.computeIfAbsent(pl.name) {
        pl.getResource("nmkey.txt")?.bufferedReader()?.use { it.readText() }?.trim()?.takeIf(String::isNotEmpty) ?: ""
    }.takeIf(String::isNotEmpty)

    /**
     * Clears the internal memory cache for license keys and public keys.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @JvmStatic
    fun clearCache() {
        cachedKeys.clear()
        publicKeys.clear()
    }

    /**
     * Ensures the Ed25519 public key for the specific plugin is available in the cache.
     *
     * @param pluginId The unique ID of the plugin.
     * @param pl The JavaPlugin instance for logging.
     * @return The raw Base64 encoded public key string.
     *
     * @author Idan Nehama (GuavaDealer)
     * @author QrackyDev (Qracky)
     * @since 1.1.0
     */
    private fun ensurePublicKey(pl: JavaPlugin, pluginId: String): String = publicKeys.computeIfAbsent(pluginId) {
        pl.logger.info("NMKey: fetching public key from $DEFAULT_API_URL/public-key.")
        val encodedId = URLEncoder.encode(pluginId, StandardCharsets.UTF_8)
        val request = try {
            HttpRequest.newBuilder().uri(URI("$DEFAULT_API_URL/public-key?pluginId=$encodedId"))
                .timeout(7500.milliseconds.toJavaDuration()).GET().build()
        } catch (e: Exception) {
            throw IllegalStateException("Invalid API URI for pluginId '$pluginId'", e)
        }

        runCatching { client.send(request, HttpResponse.BodyHandlers.ofString()).body().removePemPublicKeyHeaders() }
            .onSuccess { pl.logger.info("NMKey: public key fetched and cached.") }
            .getOrElse { throw IllegalStateException("Failed to fetch public key for '$pluginId'", it) }
    }

    /**
     * Generates a dynamic hardware fingerprint based on system heuristics.
     *
     * @param pl The JavaPlugin instance used to access server port information.
     * @return A 32-character hexadecimal string representing the hardware fingerprint.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @JvmStatic
    fun fingerprint(pl: JavaPlugin): String {
        val port = runCatching { pl.server.port.toString() }.getOrDefault("25565")
        val procs = Runtime.getRuntime().availableProcessors()
        val osArch = System.getProperty("os.arch", "unknown")
        val osVer = System.getProperty("os.version", "unknown")

        val mac = runCatching {
            NetworkInterface.getByInetAddress(InetAddress.getLocalHost())
                ?.hardwareAddress?.joinToString("") { "%02x".format(it) }
        }.onFailure { pl.logger.fine("NMKey: Could not determine MAC address: ${it.message}") }.getOrNull() ?: "0"

        return runCatching {
            MessageDigest.getInstance("SHA-256")
                .digest("$port|$procs|$osArch|$osVer|$mac".toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(32)
        }.onFailure { pl.logger.severe("NMKey: Fingerprint failed: ${it.message}") }.getOrDefault("0")
    }

    /**
     * Starts a background heartbeat task that re-validates the license every 30 minutes.
     *
     * @param pl The JavaPlugin instance.
     * @param pluginId The unique ID of the plugin.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.1.0
     */
    private fun startHeartbeat(pl: JavaPlugin, pluginId: String) {
        if (heartbeatTasks.containsKey(pl.name)) return

        val interval = 30.minutes.toJavaDuration().toMillis() / 50
        val task = pl.server.scheduler.runTaskTimerAsynchronously(
            pl,
            Runnable {
                release(pl, pluginId)
                if (!check(pl, pluginId) && Config.autoDisablePlugin) {
                    pl.server.scheduler.runTask(
                        pl,
                        Runnable {
                            pl.logger.severe("NMKey: Heartbeat failed and grace period expired. Disabling plugin.")
                            pl.server.pluginManager.disablePlugin(pl)
                        },
                    )
                }
            },
            interval, interval,
        )
        heartbeatTasks[pl.name] = task
    }

    /**
     * Silently registers a Bukkit event listener to automatically release the license seat.
     *
     * @param pl The JavaPlugin instance.
     * @param pluginId The unique ID of the plugin.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.1.0
     */
    private fun registerAutoCleanup(pl: JavaPlugin, pluginId: String) {
        if (!Config.autoReleaseOnDisable) return

        if (autoCleanupRegistered.add(pl.name)) {
            pl.server.pluginManager.registerEvents(
                object : Listener {
                    @EventHandler
                    fun onPluginDisable(e: PluginDisableEvent) {
                        if (e.plugin === pl && activeSessions.contains(pl.name)) {
                            pl.logger.info("NMKey: Auto-releasing license seat and cleaning up background resources...")
                            release(pl, pluginId)
                        }
                    }
                },
                pl,
            )
        }
    }
}
