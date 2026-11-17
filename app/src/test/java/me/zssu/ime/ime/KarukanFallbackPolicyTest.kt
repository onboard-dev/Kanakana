package me.zssu.ime.ime

import me.zssu.ime.settings.ImeSettings.ConversionEngine
import org.junit.Assert.*
import org.junit.Test

class KarukanFallbackPolicyTest {
    private fun use(
        mode: ConversionEngine = ConversionEngine.AUTO, enabled: Boolean = true,
        ready: Boolean = true, permitted: Boolean = true, ai: Boolean = false,
    ) = KarukanFallbackPolicy.shouldUse(mode, enabled, ready, permitted, ai)

    @Test fun karukanCoversWhatZenzaiPlusDidNotSupply() {
        assertTrue(use(ai = false))
    }

    @Test fun zenzaiPlusHasPriorityOverKarukan() {
        assertFalse(use(ai = true))
    }

    @Test fun karukanNeedsItsOwnSwitch() {
        assertFalse(use(enabled = false))
    }

    @Test fun mozcOnlyUsesNoAi() {
        assertFalse(use(mode = ConversionEngine.MOZC))
    }

    @Test fun privacyAndModelAvailabilityAlwaysWin() {
        assertFalse(use(permitted = false))
        assertFalse(use(ready = false))
    }
}
