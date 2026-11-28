package me.zssu.ime.keyboard

import me.zssu.ime.settings.ImeSettings.OneHandMode

/**
 * The one-hand button walks the keyboard from the left edge to the right edge and back:
 * full width, then left 80/70/60%, right 60/70/80%, and back right 70/60%, left 60/70/80%, full width.
 */
object OneHandSteps {
    data class Step(val mode: OneHandMode, val percent: Int)

    val steps: List<Step> = listOf(
        Step(OneHandMode.OFF, 100),
        Step(OneHandMode.LEFT, 80),
        Step(OneHandMode.LEFT, 70),
        Step(OneHandMode.LEFT, 60),
        Step(OneHandMode.RIGHT, 60),
        Step(OneHandMode.RIGHT, 70),
        Step(OneHandMode.RIGHT, 80),
        Step(OneHandMode.RIGHT, 70),
        Step(OneHandMode.RIGHT, 60),
        Step(OneHandMode.LEFT, 60),
        Step(OneHandMode.LEFT, 70),
        Step(OneHandMode.LEFT, 80),
    )

    /**
     * Where the keyboard is now. The same state appears twice (going out and coming back), so the
     * remembered [savedIndex] decides when it still matches; otherwise the nearest step of the same side.
     */
    fun currentIndex(mode: OneHandMode, widthFraction: Float, savedIndex: Int): Int {
        val percent = Math.round(widthFraction * 100)
        steps.getOrNull(savedIndex)?.let {
            if (it.mode == mode && (mode == OneHandMode.OFF || it.percent == percent)) return savedIndex
        }
        if (mode == OneHandMode.OFF) return 0
        return steps.indices
            .filter { steps[it].mode == mode }
            .minBy { Math.abs(steps[it].percent - percent) }
    }

    fun nextIndex(mode: OneHandMode, widthFraction: Float, savedIndex: Int): Int =
        (currentIndex(mode, widthFraction, savedIndex) + 1) % steps.size
}
