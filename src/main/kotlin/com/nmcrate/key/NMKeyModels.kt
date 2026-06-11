package com.nmcrate.key

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shared internal JSON parser to avoid instantiating multiple instances.
 * @author Idan Nehama (GuavaDealer)
 * @since 1.1.0
 */
internal val nmJson = Json { ignoreUnknownKeys = true }

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
 * @property status The validation result (e.g., "valid", "invalid").
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
