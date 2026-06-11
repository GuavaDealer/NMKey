package com.nmcrate.key

import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val keyFactory = KeyFactory.getInstance("Ed25519")
private val base64Decoder: Base64.Decoder = Base64.getDecoder()
private val base64UrlDecoder: Base64.Decoder = Base64.getUrlDecoder()

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
internal fun verifySignature(publicKeyB64: String, msg: String, sigB64Url: String): Boolean = runCatching {
    Signature.getInstance("Ed25519").apply {
        initVerify(keyFactory.generatePublic(X509EncodedKeySpec(base64Decoder.decode(publicKeyB64))))
        update(msg.toByteArray(StandardCharsets.UTF_8))
    }.verify(base64UrlDecoder.decode(sigB64Url))
}.getOrDefault(false)

/**
 * Removes PEM headers, footers, and whitespace from a public key string.
 *
 * @return A sanitized Base64 string representing the public key.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.0.0
 */
internal fun String.removePemPublicKeyHeaders(): String =
    replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .lineSequence().joinToString("") { it.trim() }.trim()

/**
 * Initializes a Cipher for AES/GCM encryption or decryption.
 *
 * @param mode The operation mode ([javax.crypto.Cipher.ENCRYPT_MODE] or [javax.crypto.Cipher.DECRYPT_MODE]).
 * @param fingerprint The hardware fingerprint used as the key source.
 * @param iv The initialization vector for GCM mode.
 *
 * @author Idan Nehama (GuavaDealer)
 * @since 1.1.0
 */
internal fun getAesGcmCipher(mode: Int, fingerprint: String, iv: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(
            mode,
            SecretKeySpec(fingerprint.toByteArray(StandardCharsets.UTF_8).take(32).toByteArray(), "AES"),
            GCMParameterSpec(128, iv),
        )
    }
