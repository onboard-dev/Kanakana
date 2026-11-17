package me.zssu.ime.ime

import android.view.KeyEvent

/**
 * JIS kana layout for a physical (e.g. Bluetooth) keyboard.
 *
 * Keys are identified by their Linux scan code rather than by Android's key code. Android picks the
 * key character map from the keyboard's vendor/product, so a JIS board that happens to be given a
 * US map reports different key codes for the same physical key; the scan code does not change.
 *
 * Pure Kotlin on purpose: the table and the routing rules are the part worth unit-testing, and the
 * service only has to carry the decision out.
 */
internal object HardwareKana {

    /** What one physical key press means to the IME. */
    sealed interface Command {
        /** Type a kana. [ascii] is the unshifted US key legend, sent to Mozc as the raw key code. */
        data class Kana(val text: String, val ascii: Char) : Command

        data object Space : Command
        data object Convert : Command
        data object Enter : Command
        data object Backspace : Command
        data object Delete : Command
        data object Left : Command
        data object Right : Command
        data object Up : Command
        data object Down : Command
        data object Escape : Command

        /**
         * F6-F8: Mozc's one-key conversion to hiragana, full-width katakana and half-width katakana.
         * F9/F10 (latin) are deliberately left to the editor: with kana input Mozc would convert the
         * US key legends (か -> "t"), which is never what the user wants.
         */
        data class Function(val number: Int) : Command

        /** 半角/全角: flip between kana and direct input. */
        data object ToggleKana : Command
        data object KanaOn : Command
        data object KanaOff : Command

        /** Not ours: let the editor receive the key untouched. */
        data object Pass : Command
    }

    private const val SCAN_HANKAKU_ZENKAKU = 41 // KEY_GRAVE
    private const val SCAN_ZENKAKU_HANKAKU = 85 // KEY_ZENKAKUHANKAKU

    private class Entry(val plain: String, val shifted: String, val ascii: Char)

    // Linux evdev scan codes of a JIS keyboard. ゛ and ゜ are U+309B / U+309C, the spacing forms
    // Mozc's kana table is keyed on.
    private val table: Map<Int, Entry> = buildMap {
        fun key(scan: Int, ascii: Char, plain: String, shifted: String = plain) = put(scan, Entry(plain, shifted, ascii))

        // Number row
        key(2, '1', "ぬ"); key(3, '2', "ふ"); key(4, '3', "あ", "ぁ"); key(5, '4', "う", "ぅ")
        key(6, '5', "え", "ぇ"); key(7, '6', "お", "ぉ"); key(8, '7', "や", "ゃ"); key(9, '8', "ゆ", "ゅ")
        key(10, '9', "よ", "ょ"); key(11, '0', "わ", "を"); key(12, '-', "ほ"); key(13, '^', "へ")
        key(124, '\\', "ー") // the ¥ key

        // Top row
        key(16, 'q', "た"); key(17, 'w', "て"); key(18, 'e', "い", "ぃ"); key(19, 'r', "す")
        key(20, 't', "か"); key(21, 'y', "ん"); key(22, 'u', "な"); key(23, 'i', "に")
        key(24, 'o', "ら"); key(25, 'p', "せ"); key(26, '@', "゛"); key(27, '[', "゜", "「")

        // Home row
        key(30, 'a', "ち"); key(31, 's', "と"); key(32, 'd', "し"); key(33, 'f', "は")
        key(34, 'g', "き"); key(35, 'h', "く"); key(36, 'j', "ま"); key(37, 'k', "の")
        key(38, 'l', "り"); key(39, ';', "れ"); key(40, ':', "け"); key(43, ']', "む", "」")

        // Bottom row
        key(44, 'z', "つ", "っ"); key(45, 'x', "さ"); key(46, 'c', "そ"); key(47, 'v', "ひ")
        key(48, 'b', "こ"); key(49, 'n', "み"); key(50, 'm', "も"); key(51, ',', "ね", "、")
        key(52, '.', "る", "。"); key(53, '/', "め", "・"); key(89, '\\', "ろ") // the ろ key
    }

    /** The kana for a scan code, or null when that key has no kana printed on it. */
    fun lookup(scanCode: Int, shift: Boolean): Command.Kana? =
        table[scanCode]?.let { Command.Kana(if (shift) it.shifted else it.plain, it.ascii) }

    /**
     * Decides what a key press does.
     *
     * With kana input off, only the mode keys are ours and every other key reaches the editor as
     * an ordinary US-style key. With it on, kana keys and Space are always ours, but the editing
     * keys (Enter, Backspace, arrows, Escape) are taken only while something is being composed so
     * that they keep their normal meaning in the editor otherwise.
     */
    fun classify(
        keyCode: Int,
        scanCode: Int,
        shift: Boolean,
        kanaOn: Boolean,
        composing: Boolean,
    ): Command {
        // A JIS keyboard's 半角/全角 key reports KEY_GRAVE (scan 41) on many Bluetooth boards, not
        // KEY_ZENKAKUHANKAKU (85), so Android hands the IME KEYCODE_GRAVE. JIS has no backquote key,
        // so the scan code alone identifies it.
        if (scanCode == SCAN_HANKAKU_ZENKAKU || scanCode == SCAN_ZENKAKU_HANKAKU) return Command.ToggleKana
        when (keyCode) {
            KeyEvent.KEYCODE_ZENKAKU_HANKAKU -> return Command.ToggleKana
            KeyEvent.KEYCODE_KATAKANA_HIRAGANA -> return Command.KanaOn
            KeyEvent.KEYCODE_EISU, KeyEvent.KEYCODE_MUHENKAN -> return Command.KanaOff
        }
        if (!kanaOn) return Command.Pass
        when (keyCode) {
            KeyEvent.KEYCODE_HENKAN -> return if (composing) Command.Convert else Command.Pass
            KeyEvent.KEYCODE_SPACE -> return Command.Space
        }
        lookup(scanCode, shift)?.let { return it }
        if (!composing) return Command.Pass
        return when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> Command.Enter
            KeyEvent.KEYCODE_DEL -> Command.Backspace
            KeyEvent.KEYCODE_FORWARD_DEL -> Command.Delete
            KeyEvent.KEYCODE_DPAD_LEFT -> Command.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> Command.Right
            KeyEvent.KEYCODE_DPAD_UP -> Command.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> Command.Down
            KeyEvent.KEYCODE_ESCAPE -> Command.Escape
            in KeyEvent.KEYCODE_F6..KeyEvent.KEYCODE_F8 -> Command.Function(keyCode - KeyEvent.KEYCODE_F1 + 1)
            else -> Command.Pass
        }
    }
}
