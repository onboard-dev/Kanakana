package me.zssu.ime.settings

import me.zssu.ime.settings.KeyboardLayoutInfo.AndroidMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutInfoTest {
    @Test fun keysOnlyAJisKeyboardHasProveTheLayout() {
        // ろ, 変換, カタカナひらがな, 無変換, ¥
        listOf(89, 92, 93, 94, 124).forEach { assertTrue("scan $it", KeyboardLayoutInfo.isJisOnlyKey(it)) }
    }

    @Test fun keysEveryKeyboardHasProveNothing() {
        // letters, number row, Enter, Space, backslash/Non-US hash, grave
        listOf(2, 16, 28, 30, 40, 41, 43, 57).forEach { assertFalse("scan $it", KeyboardLayoutInfo.isJisOnlyKey(it)) }
    }

    @Test fun shiftTwoTellsAndroidsMapApart() {
        assertEquals(AndroidMap.US, KeyboardLayoutInfo.androidMap('@'))
        assertEquals(AndroidMap.JIS, KeyboardLayoutInfo.androidMap('"'))
        assertEquals(AndroidMap.OTHER, KeyboardLayoutInfo.androidMap('2'))
    }
}
