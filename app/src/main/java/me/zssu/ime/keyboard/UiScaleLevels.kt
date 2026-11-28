package me.zssu.ime.keyboard

import kotlin.math.roundToInt

/** The sizes the shrink tool cycles through: normal, then 80%, 60%, 50%, 40%, 30%, then normal again. */
object UiScaleLevels {
    private val scales = floatArrayOf(1f, 0.8f, 0.6f, 0.5f)

    val count: Int get() = scales.size

    fun scale(level: Int): Float = scales[level.coerceIn(0, scales.lastIndex)]

    fun percent(level: Int): Int = (scale(level) * 100f).roundToInt()

    /** The level after [level]; the last one wraps back to normal. */
    fun next(level: Int): Int = (level.coerceIn(0, scales.lastIndex) + 1) % scales.size
}
