package com.freeturn.app.data.backup

import android.util.Base64
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрует бэкап паролем: PBKDF2-SHA256 (BouncyCastle - одинаков на всех API, в отличие
 * от javax PBKDF2WithHmacSHA256 с API 26) + AES-256-GCM. Конверт - JSON с base64-полями.
 */
object BackupCrypto {
    private const val MAGIC = "freeturn-backup"
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val KEY_BITS = 256
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    /** Неверный пароль (не сошёлся GCM-тег). */
    class BadPasswordException : Exception()
    /** Файл не является бэкапом FreeTurn / повреждён. */
    class FormatException(message: String) : Exception(message)

    fun encrypt(plaintext: ByteArray, password: String): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(password, salt, ITERATIONS), "AES"),
                GCMParameterSpec(TAG_BITS, iv))
        }
        val ct = cipher.doFinal(plaintext)
        return JSONObject().apply {
            put("magic", MAGIC)
            put("v", VERSION)
            put("kdf", "pbkdf2-sha256")
            put("iter", ITERATIONS)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("ct", b64(ct))
        }.toString().toByteArray(Charsets.UTF_8)
    }

    fun decrypt(envelope: ByteArray, password: String): ByteArray {
        val o = try {
            JSONObject(String(envelope, Charsets.UTF_8))
        } catch (_: Exception) {
            throw FormatException("not a backup file")
        }
        if (o.optString("magic") != MAGIC) throw FormatException("not a FreeTurn backup")
        val iter = o.optInt("iter", ITERATIONS)
        if (iter !in MIN_ITERATIONS..MAX_ITERATIONS) throw FormatException("bad iteration count")
        val salt = unb64(o, "salt")
        val iv = unb64(o, "iv")
        val ct = unb64(o, "ct")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(password, salt, iter), "AES"),
                GCMParameterSpec(TAG_BITS, iv))
        }
        return try {
            cipher.doFinal(ct)
        } catch (_: Exception) {
            throw BadPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iter: Int): ByteArray {
        val gen = PKCS5S2ParametersGenerator(SHA256Digest())
        gen.init(password.toByteArray(Charsets.UTF_8), salt, iter)
        return (gen.generateDerivedParameters(KEY_BITS) as KeyParameter).key
    }

    private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(o: JSONObject, key: String): ByteArray = try {
        Base64.decode(o.getString(key), Base64.NO_WRAP)
    } catch (_: Exception) {
        throw FormatException("corrupted backup")
    }
}
