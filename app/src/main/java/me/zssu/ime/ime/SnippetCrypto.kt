package me.zssu.ime.ime

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected container for exported snippets.
 *
 * Layout: `KKTPL1` magic, PBKDF2 iteration count (4 bytes, big endian), salt (16), GCM nonce (12),
 * then the AES-256-GCM ciphertext with its tag. The whole header is authenticated as additional
 * data, so changing any byte of the file, or a wrong password, makes [decrypt] return null.
 *
 * PBKDF2-HMAC-SHA256 is written out here rather than taken from `SecretKeyFactory`, because that
 * algorithm only exists from API 26 and the app supports API 24.
 */
internal object SnippetCrypto {
    const val FILE_NAME = "kanakanaTemplate.enc"
    const val ITERATIONS = 600_000
    const val MIN_PASSWORD_LENGTH = 6

    private val MAGIC = "KKTPL1".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BYTES = 32
    private const val HEADER_BYTES = 6 + 4 + SALT_BYTES + NONCE_BYTES
    /** A file asking for more work than this is hostile or corrupt, not something we wrote. */
    private const val MAX_ITERATIONS = 5_000_000

    fun isEncrypted(data: ByteArray): Boolean =
        data.size >= MAGIC.size && MessageDigest.isEqual(data.copyOfRange(0, MAGIC.size), MAGIC)

    fun encrypt(
        plain: ByteArray,
        password: CharArray,
        iterations: Int = ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        require(iterations in 1..MAX_ITERATIONS)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC).putInt(iterations).put(salt).put(nonce).array()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    /** The plaintext, or null for a wrong password, a damaged file, or something that is not ours. */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray? {
        if (!isEncrypted(data) || data.size < HEADER_BYTES + TAG_BITS / 8) return null
        val buffer = ByteBuffer.wrap(data)
        buffer.position(MAGIC.size)
        val iterations = buffer.int
        if (iterations !in 1..MAX_ITERATIONS) return null
        val salt = ByteArray(SALT_BYTES).also(buffer::get)
        val nonce = ByteArray(NONCE_BYTES).also(buffer::get)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(data, 0, HEADER_BYTES)
            cipher.doFinal(data, HEADER_BYTES, data.size - HEADER_BYTES)
        } catch (e: GeneralSecurityException) {
            null
        }
    }

    private fun key(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec =
        SecretKeySpec(pbkdf2(passwordBytes(password), salt, iterations, KEY_BYTES), "AES")

    /**
     * NFKC first, so the same password typed with full-width or half-width characters, or with a
     * composed or decomposed kana, opens the same file: a Japanese IME makes those easy to mix up.
     */
    internal fun passwordBytes(password: CharArray): ByteArray =
        Normalizer.normalize(String(password), Normalizer.Form.NFKC).toByteArray(Charsets.UTF_8)

    internal fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val hashLength = mac.macLength
        val blocks = (length + hashLength - 1) / hashLength
        val out = ByteArray(blocks * hashLength)
        for (block in 1..blocks) {
            mac.update(salt)
            mac.update(ByteBuffer.allocate(4).putInt(block).array())
            var u = mac.doFinal()
            val t = u.copyOf()
            for (round in 1 until iterations) {
                u = mac.doFinal(u)
                for (i in t.indices) t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
            }
            t.copyInto(out, (block - 1) * hashLength)
        }
        return out.copyOf(length)
    }
}
