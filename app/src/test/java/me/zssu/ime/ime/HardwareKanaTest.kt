package me.zssu.ime.ime

import android.view.KeyEvent
import me.zssu.ime.ime.HardwareKana.Command
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareKanaTest {
    private fun kana(scan: Int, shift: Boolean = false) = HardwareKana.lookup(scan, shift)?.text

    @Test fun numberRow() {
        val plain = listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 124).map { kana(it) }
        assertEquals(listOf("ぬ", "ふ", "あ", "う", "え", "お", "や", "ゆ", "よ", "わ", "ほ", "へ", "ー"), plain)
    }

    @Test fun letterRows() {
        val top = listOf(16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27).map { kana(it) }
        assertEquals(listOf("た", "て", "い", "す", "か", "ん", "な", "に", "ら", "せ", "゛", "゜"), top)
        val home = listOf(30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 43).map { kana(it) }
        assertEquals(listOf("ち", "と", "し", "は", "き", "く", "ま", "の", "り", "れ", "け", "む"), home)
        val bottom = listOf(44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 89).map { kana(it) }
        assertEquals(listOf("つ", "さ", "そ", "ひ", "こ", "み", "も", "ね", "る", "め", "ろ"), bottom)
    }

    @Test fun shiftedForms() {
        assertEquals("ぁ", kana(4, true)); assertEquals("ぅ", kana(5, true)); assertEquals("ぇ", kana(6, true))
        assertEquals("ぉ", kana(7, true)); assertEquals("ぃ", kana(18, true))
        assertEquals("ゃ", kana(8, true)); assertEquals("ゅ", kana(9, true)); assertEquals("ょ", kana(10, true))
        assertEquals("を", kana(11, true)); assertEquals("っ", kana(44, true))
        assertEquals("「", kana(27, true)); assertEquals("」", kana(43, true))
        assertEquals("、", kana(51, true)); assertEquals("。", kana(52, true)); assertEquals("・", kana(53, true))
    }

    @Test fun everyJisKanaKeyIsDistinctAndNothingElseIsMapped() {
        // 48 physical keys carry kana (13 + 12 + 12 + 11); the numpad, Enter, Space etc. must stay unmapped.
        val mapped = (0..255).filter { HardwareKana.lookup(it, false) != null }
        assertEquals(48, mapped.size)
        assertNull(HardwareKana.lookup(57, false)) // space
        assertNull(HardwareKana.lookup(28, false)) // enter
        assertNull(HardwareKana.lookup(79, false)) // numpad 1
    }

    @Test fun asciiLegendIsTheUsKey() {
        assertEquals('t', HardwareKana.lookup(20, false)?.ascii)
        assertEquals('@', HardwareKana.lookup(26, false)?.ascii)
        assertEquals('1', HardwareKana.lookup(2, true)?.ascii)
    }

    private fun classify(code: Int, scan: Int = 0, on: Boolean = true, composing: Boolean = false) =
        HardwareKana.classify(code, scan, shift = false, kanaOn = on, composing = composing)

    @Test fun modeKeysWorkInBothModes() {
        assertEquals(Command.ToggleKana, classify(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, on = false))
        assertEquals(Command.ToggleKana, classify(KeyEvent.KEYCODE_ZENKAKU_HANKAKU, on = true))
        assertEquals(Command.KanaOn, classify(KeyEvent.KEYCODE_KATAKANA_HIRAGANA, on = false))
        assertEquals(Command.KanaOff, classify(KeyEvent.KEYCODE_MUHENKAN))
        assertEquals(Command.KanaOff, classify(KeyEvent.KEYCODE_EISU))
    }

    @Test fun hankakuZenkakuArrivesAsGraveKeyOnBluetoothBoards() {
        assertEquals(Command.ToggleKana, classify(KeyEvent.KEYCODE_GRAVE, scan = 41, on = true))
        assertEquals(Command.ToggleKana, classify(KeyEvent.KEYCODE_GRAVE, scan = 41, on = false))
        assertEquals(Command.ToggleKana, classify(KeyEvent.KEYCODE_UNKNOWN, scan = 85))
    }

    @Test fun directModePassesEverythingElse() {
        assertEquals(Command.Pass, classify(KeyEvent.KEYCODE_T, scan = 20, on = false))
        assertEquals(Command.Pass, classify(KeyEvent.KEYCODE_SPACE, on = false, composing = true))
        assertEquals(Command.Pass, classify(KeyEvent.KEYCODE_DEL, on = false, composing = true))
    }

    @Test fun editingKeysAreOursOnlyWhileComposing() {
        for (code in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_FORWARD_DEL)) {
            assertEquals(Command.Pass, classify(code, composing = false))
            assertEquals(false, classify(code, composing = true) == Command.Pass)
        }
        assertEquals(Command.Enter, classify(KeyEvent.KEYCODE_ENTER, composing = true))
        assertEquals(Command.Backspace, classify(KeyEvent.KEYCODE_DEL, composing = true))
    }

    @Test fun functionKeysConvertOnlyWhileComposing() {
        assertEquals(Command.Function(7), classify(KeyEvent.KEYCODE_F7, composing = true))
        assertEquals(Command.Function(6), classify(KeyEvent.KEYCODE_F6, composing = true))
        assertEquals(Command.Function(8), classify(KeyEvent.KEYCODE_F8, composing = true))
        // F9/F10 would convert the key legends (か -> "t"), so the editor gets them untouched.
        assertEquals(Command.Pass, classify(KeyEvent.KEYCODE_F9, composing = true))
        assertEquals(Command.Pass, classify(KeyEvent.KEYCODE_F10, composing = true))
        assertEquals(Command.Pass, classify(KeyEvent.KEYCODE_F7, composing = false))
        assertEquals(Command.Pass, classify(KeyEvent.KEYCODE_F5, composing = true))
    }

    @Test fun spaceAndKanaAreAlwaysOursInKanaMode() {
        assertEquals(Command.Space, classify(KeyEvent.KEYCODE_SPACE))
        assertEquals("か", (classify(KeyEvent.KEYCODE_T, scan = 20) as Command.Kana).text)
        assertEquals(Command.Pass, classify(KeyEvent.KEYCODE_HENKAN, composing = false))
        assertEquals(Command.Convert, classify(KeyEvent.KEYCODE_HENKAN, composing = true))
    }
}
