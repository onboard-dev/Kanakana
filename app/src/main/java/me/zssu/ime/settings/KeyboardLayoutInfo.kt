package me.zssu.ime.settings

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent

/**
 * What KanaKana can tell about an attached physical keyboard's layout.
 *
 * A keyboard's key list does not say whether it is JIS or US (both report the Japanese keys such as
 * KEY_YEN), so "JIS" is only ever known by seeing a key that exists on JIS keyboards alone. Android's
 * own view of the layout is separate: it can treat a JIS keyboard as US.
 */
internal object KeyboardLayoutInfo {
    /** What Android's key map does with Shift+2, which tells the JIS and US maps apart. */
    enum class AndroidMap { US, JIS, OTHER }

    /** Scan codes that only a JIS keyboard has: ろ, 変換, カタカナひらがな, 無変換, ¥, 全角/半角, カタカナ, ひらがな. */
    private val jisOnlyScanCodes = setOf(85, 89, 90, 91, 92, 93, 94, 124)

    fun isJisOnlyKey(scanCode: Int): Boolean = scanCode in jisOnlyScanCodes

    fun androidMap(shiftTwo: Char): AndroidMap = when (shiftTwo) {
        '@' -> AndroidMap.US
        '"' -> AndroidMap.JIS
        else -> AndroidMap.OTHER
    }

    private const val PREFS = "keyboard_layout"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Remembers that this keyboard sent a JIS-only key. Cheap enough to call on every key press. */
    fun noteKey(context: Context, event: KeyEvent) {
        if (!isJisOnlyKey(event.scanCode)) return
        val descriptor = event.device?.descriptor ?: return
        val key = "jis_$descriptor"
        val prefs = prefs(context)
        if (!prefs.getBoolean(key, false)) prefs.edit().putBoolean(key, true).apply()
    }

    fun seenJisKey(context: Context, device: InputDevice): Boolean =
        prefs(context).getBoolean("jis_${device.descriptor}", false)

    fun androidMap(device: InputDevice): AndroidMap =
        androidMap(device.keyCharacterMap.get(KeyEvent.KEYCODE_2, KeyEvent.META_SHIFT_ON).toChar())

    /** The attached keyboards that are not the on-screen one. */
    fun attachedKeyboards(): List<InputDevice> = InputDevice.getDeviceIds().toList().mapNotNull { id ->
        InputDevice.getDevice(id)?.takeIf {
            !it.isVirtual &&
                it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                it.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        }
    }
}
