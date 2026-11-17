package me.zssu.ime.settings

import me.zssu.ime.mozc.ProfileBackup
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupExtrasTest {
    /** A file the backup writes but restore refuses would make the whole restore fail. */
    @Test fun everyEncryptedFileWeExportIsAllowedOnRestore() {
        val missing = BACKUP_EXTRAS.encryptedFiles - ProfileBackup.SEALED_FILES
        assertTrue("restore would reject: $missing", missing.isEmpty())
    }

    @Test fun proofreadingAndToolbarSettingsAreBackedUp() {
        listOf("keyboard_toolbar", "proofread_remote", "proofread").forEach {
            assertTrue(it, it in BACKUP_EXTRAS.preferences)
        }
        listOf("snippet_recent.enc", "openrouter_key.enc").forEach {
            assertTrue(it, it in BACKUP_EXTRAS.encryptedFiles)
        }
    }
}
