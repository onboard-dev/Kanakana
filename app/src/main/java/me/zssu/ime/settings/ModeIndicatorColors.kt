package me.zssu.ime.settings

/**
 * The colours offered for the kana / direct-input indicator beside the menu button.
 *
 * Eight to choose from, picked by index so a saved choice stays valid if a name is reworded.
 */
internal object ModeIndicatorColors {
    class Swatch(val name: String, val argb: Int)

    val palette: List<Swatch> = listOf(
        Swatch("ミルキーグリーン", 0xFFA8E6B4.toInt()),
        Swatch("水色", 0xFF29B6F6.toInt()),
        Swatch("青", 0xFF1E88E5.toInt()),
        Swatch("紫", 0xFF8E24AA.toInt()),
        Swatch("ピンク", 0xFFEC407A.toInt()),
        Swatch("赤", 0xFFE53935.toInt()),
        Swatch("オレンジ", 0xFFFB8C00.toInt()),
        Swatch("グレー", 0xFF757575.toInt()),
    )

    /** Soft milky green while typing kana, as on a PC's IME. */
    const val DEFAULT_KANA_INDEX = 0

    /** Light blue for direct (half-width) input. */
    const val DEFAULT_DIRECT_INDEX = 1

    fun argb(index: Int): Int = palette[index.coerceIn(0, palette.lastIndex)].argb

    /** Black or white, whichever reads better on [background]. */
    fun textColorFor(background: Int): Int {
        fun channel(shift: Int): Double {
            val c = (background shr shift and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        // Contrast with white is 1.05 / (L + 0.05); with black it is (L + 0.05) / 0.05.
        return if (1.05 / (luminance + 0.05) >= (luminance + 0.05) / 0.05) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    }
}
