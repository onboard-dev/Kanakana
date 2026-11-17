package me.zssu.ime.ime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class SnippetCryptoTest {
    // Few iterations: the algorithm is the same at any count, and the real count is slow on purpose.
    private val fast = 1000
    private val plain = SnippetTransfer.encode(listOf("お世話になっております。", "eeee@ee.com")).toByteArray()

    @Test fun roundTrip() {
        val file = SnippetCrypto.encrypt(plain, "correct horse".toCharArray(), fast)
        assertArrayEquals(plain, SnippetCrypto.decrypt(file, "correct horse".toCharArray()))
    }

    @Test fun wrongPasswordIsRejected() {
        val file = SnippetCrypto.encrypt(plain, "correct horse".toCharArray(), fast)
        assertNull(SnippetCrypto.decrypt(file, "wrong horse".toCharArray()))
    }

    @Test fun anyChangedByteIsDetected() {
        val file = SnippetCrypto.encrypt(plain, "correct horse".toCharArray(), fast)
        for (index in listOf(7, 12, 20, 33, 40, file.size - 1)) {
            val damaged = file.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            assertNull("byte $index", SnippetCrypto.decrypt(damaged, "correct horse".toCharArray()))
        }
        assertNull(SnippetCrypto.decrypt(file.copyOf(file.size - 3), "correct horse".toCharArray()))
    }

    @Test fun outputIsRandomisedAndDoesNotContainThePlaintext() {
        val a = SnippetCrypto.encrypt(plain, "correct horse".toCharArray(), fast)
        val b = SnippetCrypto.encrypt(plain, "correct horse".toCharArray(), fast)
        assertNotEquals(a.toList(), b.toList())
        assertFalse(String(a, Charsets.ISO_8859_1).contains("eeee@ee.com"))
    }

    @Test fun recognisesItsOwnFilesOnly() {
        assertTrue(SnippetCrypto.isEncrypted(SnippetCrypto.encrypt(plain, "pass12".toCharArray(), fast)))
        assertFalse(SnippetCrypto.isEncrypted(plain))
        assertFalse(SnippetCrypto.isEncrypted(ByteArray(0)))
        assertNull(SnippetCrypto.decrypt(plain, "pass12".toCharArray()))
    }

    @Test fun absurdIterationCountsAreRefused() {
        val file = SnippetCrypto.encrypt(plain, "pass12".toCharArray(), fast)
        // Bytes 6..9 hold the iteration count; make it huge so a hostile file cannot stall the app.
        file[6] = 0x7f
        assertNull(SnippetCrypto.decrypt(file, "pass12".toCharArray()))
    }

    @Test fun fullWidthAndHalfWidthPasswordsAreTheSame() {
        val file = SnippetCrypto.encrypt(plain, "abc123".toCharArray(), fast)
        assertArrayEquals(plain, SnippetCrypto.decrypt(file, "ａｂｃ１２３".toCharArray()))
    }

    @Test fun pbkdf2MatchesTheJdkImplementation() {
        val salt = ByteArray(16) { it.toByte() }
        val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("password".toCharArray(), salt, 2000, 256)).encoded
        assertArrayEquals(expected, SnippetCrypto.pbkdf2("password".toByteArray(), salt, 2000, 32))
    }

    @Test fun pbkdf2SpansMoreThanOneBlock() {
        val salt = "salt".toByteArray()
        val expected = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("pw".toCharArray(), salt, 50, 384)).encoded
        assertArrayEquals(expected, SnippetCrypto.pbkdf2("pw".toByteArray(), salt, 50, 48))
    }

    @Test fun fileNameAdvertisesEncryption() {
        assertEquals("kanakanaTemplate.enc", SnippetCrypto.FILE_NAME)
    }
}
