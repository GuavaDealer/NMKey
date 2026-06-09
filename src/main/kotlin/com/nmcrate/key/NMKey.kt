package com.nmcrate.key

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.plugin.java.JavaPlugin
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton utility for managing license key validation and hardware fingerprinting for NMCrate plugins.
 *
 * This object handles communication with the NMCrate API to verify license keys, manage hardware-bound
 * sessions, and ensure the integrity of responses using Ed25519 digital signatures. It maintains an
 * internal cache for public keys and license strings to minimize network overhead and I/O operations.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.0.0
 */
object NMKey {
    /**
     * The official base URL for the NMKey license validation API.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    const val DEFAULT_API_URL = "https://www.nmcrate.com/api/nmkey/v1"

    private val publicKeys = ConcurrentHashMap<String, String>()
    private val cachedKeys = ConcurrentHashMap<String, String>()
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 3_000
                requestTimeoutMillis = 7_500
                socketTimeoutMillis = 7_500
            }
        }
    }
    private val keyFactory = KeyFactory.getInstance("Ed25519")
    private val base64Decoder: Base64.Decoder = Base64.getDecoder()
    private val base64UrlDecoder: Base64.Decoder = Base64.getUrlDecoder()

    /**
     * Data transfer object representing a request to validate or release a license key.
     *
     * @property pluginId The unique identifier of the plugin being validated.
     * @property key The license key string provided by the user.
     * @property fingerprint A unique hardware identifier for the current server environment.
     * @property nonce A cryptographically random string used to prevent replay attacks.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @Serializable
    data class KeyRequest(
        val pluginId: String,
        val key: String,
        val fingerprint: String,
        val nonce: String? = null,
    )

    /**
     * Data transfer object representing the signed response from the validation server.
     *
     * @property status The validation result (e.g., "valid", "invalid", "expired").
     * @property issuedAt An ISO-8601 timestamp indicating when the response was generated.
     * @property signature A Base64Url encoded Ed25519 signature of the response payload.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @Serializable
    data class KeyResponse(
        val status: String,
        val issuedAt: String = "",
        val signature: String = "",
    )

    /**
     * Validates a plugin's license key against the remote API.
     *
     * This method performs a blocking network call to verify the license. It generates a unique hardware
     * fingerprint and a random nonce, sends them to the API, and then cryptographically verifies the
     * server's signature to ensure the response has not been tampered with.
     *
     * @param pl The JavaPlugin instance requesting validation.
     * @param pluginId The unique ID of the plugin.
     * @return True if the key is valid and the server signature is verified; false otherwise.
     * @throws Exception Although caught internally, underlying network or cryptographic failures may occur.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @JvmStatic
    fun check(pl: JavaPlugin, pluginId: String): Boolean {
        pl.logger.info("NMKey: verifying license for pluginId '$pluginId'.")

        return runCatching {
            runBlocking {
                val publicKey = ensurePublicKey(pl, pluginId)

                val key = readKey(pl)
                if (key == null) {
                    pl.logger.warning("NMKey: license verification failed because nmkey.txt is missing or empty.")
                    return@runBlocking false
                }
                val fp = fingerprint(pl)
                val nonce = UUID.randomUUID().toString()

                pl.logger.info("NMKey: sending validation request to $DEFAULT_API_URL/validate.")
                val res = client.post("$DEFAULT_API_URL/validate") {
                    contentType(ContentType.Application.Json)
                    setBody(KeyRequest(pluginId, key, fp, nonce))
                }.body<KeyResponse>()

                val canonical = "v1|$pluginId|$key|$fp|${res.status}|$nonce|${res.issuedAt}"
                val valid = res.status.equals("valid", ignoreCase = true) &&
                        res.signature.isNotBlank() &&
                        verify(publicKey, canonical, res.signature)

                if (valid) {
                    pl.logger.info("NMKey: license verified successfully.")
                } else {
                    pl.logger.warning(
                        "NMKey: license verification failed. " +
                                "status='${res.status}', signaturePresent=${res.signature.isNotBlank()}.",
                    )
                }

                valid
            }
        }.onFailure { error ->
            pl.logger.warning("NMKey: license verification failed: ${error.message ?: error::class.java.simpleName}.")
        }.getOrDefault(false)
    }

    /**
     * Notifies the API that the license key is being released by this hardware instance.
     *
     * This is typically called during plugin disablement to allow the key to be used on other
     * hardware if the license seat limit allows. It performs a fire-and-forget style blocking request.
     *
     * @param pl The JavaPlugin instance releasing the key.
     * @param pluginId The unique ID of the plugin.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @JvmStatic
    fun release(pl: JavaPlugin, pluginId: String) {
        runCatching {
            runBlocking {
                val key = readKey(pl) ?: return@runBlocking
                client.post("$DEFAULT_API_URL/release") {
                    contentType(ContentType.Application.Json)
                    setBody(KeyRequest(pluginId, key, fingerprint(pl)))
                }
            }
        }
    }

    /**
     * Reads the license key from the plugin's resources or internal cache.
     *
     * The method looks for a file named `nmkey.txt` in the plugin's JAR resources.
     * Once read, the key is cached in memory to avoid repeated disk I/O.
     *
     * @param pl The JavaPlugin instance to read the key for.
     * @return The trimmed license key string, or null if not found or empty.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @JvmStatic
    fun readKey(pl: JavaPlugin): String? =
        cachedKeys[pl.name] ?: pl.getResource("nmkey.txt")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.also { cachedKeys[pl.name] = it }

    /**
     * Clears the internal memory cache for license keys and public keys.
     *
     * This forces the library to re-read keys from the disk and re-fetch public keys from the API
     * on the next validation attempt.
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
     * If the key is not cached, it fetches it from the API's `/public-key` endpoint and
     * strips any PEM formatting headers.
     *
     * @param pluginId The unique ID of the plugin.
     * @return The raw Base64 encoded public key string.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    private suspend fun ensurePublicKey(pl: JavaPlugin, pluginId: String): String {
        publicKeys[pluginId]?.let {
            pl.logger.info("NMKey: using cached public key for pluginId '$pluginId'.")
            return it
        }

        pl.logger.info("NMKey: fetching public key from $DEFAULT_API_URL/public-key.")
        val fetched = client.get("$DEFAULT_API_URL/public-key") { parameter("pluginId", pluginId) }
            .body<String>()
            .removePemPublicKeyHeaders()

        publicKeys[pluginId] = fetched
        pl.logger.info("NMKey: public key fetched and cached.")
        return fetched
    }

    /**
     * Generates a unique hardware fingerprint for the current server environment.
     *
     * The fingerprint is derived from the local hostname, the MAC address of the primary
     * network interface, and the server's configured port. These values are hashed using
     * SHA-256 to create a 32-character hex string.
     *
     * @param pl The JavaPlugin instance used to access server port information.
     * @return A 32-character hexadecimal string representing the hardware fingerprint.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    @JvmStatic
    fun fingerprint(pl: JavaPlugin): String {
        try {
            val addr = InetAddress.getLocalHost()
            val mac = NetworkInterface.getByInetAddress(addr)
                ?.hardwareAddress
                ?.joinToString("") { byte -> "%02x".format(byte) }
                ?: "0"
            val raw = "${addr.hostName}|$mac|${pl.server.port}"
            return MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
                .take(32)
        } catch (e: Exception) {
            return "0"
        }
    }

    /**
     * Verifies an Ed25519 digital signature against a message.
     *
     * @param publicKeyB64 The Base64 encoded Ed25519 public key.
     * @param msg The canonical message string to verify.
     * @param sigB64Url The Base64Url encoded signature.
     * @return True if the signature is valid for the given message and key; false otherwise.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    private fun verify(publicKeyB64: String, msg: String, sigB64Url: String): Boolean = runCatching {
        val pk = keyFactory.generatePublic(
            X509EncodedKeySpec(base64Decoder.decode(publicKeyB64)),
        )
        Signature.getInstance("Ed25519").run {
            initVerify(pk)
            update(msg.toByteArray(StandardCharsets.UTF_8))
            verify(base64UrlDecoder.decode(sigB64Url))
        }
    }.getOrDefault(false)

    /**
     * Removes PEM headers, footers, and whitespace from a public key string.
     *
     * @return A sanitized Base64 string representing the public key.
     *
     * @author Idan Nehama (GuavaDealer)
     * @since 1.0.0
     */
    private fun String.removePemPublicKeyHeaders(): String =
        replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .lineSequence()
            .joinToString("") { it.trim() }
            .trim()
}
