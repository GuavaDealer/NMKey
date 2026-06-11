package com.nmcrate.key

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Encrypts and writes the valid API response to the local file system.
 * The hardware fingerprint is used as the 256-bit AES encryption key.
 *
 * @param pl The JavaPlugin instance.
 * @param response The successful KeyResponse to cache.
 * @param fingerprint The hardware fingerprint used as the encryption key.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.1.0
 */
internal fun saveOfflineCache(pl: JavaPlugin, response: KeyResponse, fingerprint: String) {
    if (!Config.useOfflineCache) return

    runCatching {
        val dataDir = File(pl.dataFolder, Config.cacheDirectoryPath).apply { mkdirs() }
        val fileName = Config.cacheFileNameFormat.replace("%s", pl.name)
        val cacheFile = File(dataDir, fileName)

        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        val payload = nmJson.encodeToString(KeyResponse.serializer(), response).toByteArray(StandardCharsets.UTF_8)
        cacheFile.writeBytes(iv + getAesGcmCipher(Cipher.ENCRYPT_MODE, fingerprint, iv).doFinal(payload))
    }.onFailure { pl.logger.warning("Failed to save offline cache: ${it.message}") }
}

/**
 * Attempts to decrypt the local cache using the current hardware fingerprint.
 * Validates the timestamp against the allowed grace period.
 *
 * @param pl The JavaPlugin instance.
 * @param fingerprint The current hardware fingerprint to attempt decryption.
 * @return True if the cache is valid and within the grace period; false otherwise.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.1.0
 */
internal fun checkOfflineCache(pl: JavaPlugin, fingerprint: String): Boolean {
    if (!Config.useOfflineCache) return false

    return runCatching {
        val dataDir = File(pl.dataFolder, Config.cacheDirectoryPath)
        val fileName = Config.cacheFileNameFormat.replace("%s", pl.name)
        val fileBytes = File(dataDir, fileName).takeIf { it.exists() }?.readBytes() ?: return false
        if (fileBytes.size < 12) return false

        val iv = fileBytes.copyOfRange(0, 12)
        val decrypted =
            getAesGcmCipher(Cipher.DECRYPT_MODE, fingerprint, iv).doFinal(fileBytes.copyOfRange(12, fileBytes.size))
        val response = nmJson.decodeFromString(KeyResponse.serializer(), String(decrypted, StandardCharsets.UTF_8))

        val isValid = Instant.parse(response.issuedAt).plus(Config.gracePeriodHours.hours) >= Clock.System.now()
        if (isValid) pl.logger.info("NMKey: Offline grace period active. License verified from secure cache.")
        else pl.logger.warning("NMKey: Offline grace period expired. Server must connect to the NMCrate API.")

        isValid
    }.onFailure { pl.logger.warning("NMKey: Offline cache decryption failed or cache is invalid. Hardware fingerprint may have changed.") }
        .getOrDefault(false)
}
