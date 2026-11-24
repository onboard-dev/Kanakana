package me.zssu.ime.keyboard

import kotlin.math.abs
import kotlin.math.max

/**
 * Pure geometry for "which way did the finger go".
 *
 * Split out of [FlickKeyboardView] because this is the one piece of the touch path with real edge
 * cases — diagonal drags, unassigned directions, sub-threshold jitter — and testing it through a
 * View means instrumenting a device to assert on arithmetic.
 */
object FlickResolver {

    /**
     * Flick practice mode: which lower corner shows the down-flick character, drawn as a ▼.
     * The same corner also counts as a down flick, so a thumb that slides toward the ▼ still
     * types the down character.
     */
    enum class PracticeCorner { OFF, LEFT_DOWN, RIGHT_DOWN;
        companion object {
            fun of(name: String?): PracticeCorner = entries.firstOrNull { it.name == name } ?: OFF
        }
    }

    /**
     * Ratio of the shorter to the longer travel above which a drag counts as diagonal (30° to 60°
     * from either axis). Only the practice corner's diagonal is reinterpreted; the others stay as before.
     */
    private const val DIAGONAL_RATIO = 0.577f

    /** Latest finger position relative to the key; returning to the center selects center again. */
    class PathTracker(private val originX: Float, private val originY: Float) {
        var dx: Float = 0f
            private set
        var dy: Float = 0f
            private set
        fun record(x: Float, y: Float) {
            dx = x - originX
            dy = y - originY
        }
    }

    /**
     * @param key the key under the finger, used to reject directions it has nothing assigned to
     * @param dx horizontal travel from the touch-down point, in pixels
     * @param dy vertical travel, in pixels (screen coordinates: positive is down)
     * @param thresholdPx distance the finger must travel before a flick counts
     */
    fun resolve(
        key: KeySpec,
        dx: Float,
        dy: Float,
        thresholdPx: Float,
        corner: PracticeCorner = PracticeCorner.OFF,
    ): FlickDirection {
        if (max(abs(dx), abs(dy)) < thresholdPx) return FlickDirection.CENTER

        val longer = max(abs(dx), abs(dy))
        val diagonal = kotlin.math.min(abs(dx), abs(dy)) >= longer * DIAGONAL_RATIO
        val towardCorner = diagonal && dy > 0f && when (corner) {
            PracticeCorner.LEFT_DOWN -> dx < 0f
            PracticeCorner.RIGHT_DOWN -> dx > 0f
            PracticeCorner.OFF -> false
        }
        if (towardCorner && key.output(FlickDirection.DOWN) != null) return FlickDirection.DOWN

        // Ties go to the horizontal axis. Thumbs drift vertically more than horizontally, so
        // treating an exact diagonal as horizontal matches how people actually aim at い/え.
        val candidate = if (abs(dx) >= abs(dy)) {
            if (dx < 0) FlickDirection.LEFT else FlickDirection.RIGHT
        } else {
            if (dy < 0) FlickDirection.UP else FlickDirection.DOWN
        }

        // Falling back to CENTER keeps a sloppy flick usable instead of silently eating the
        // keystroke, which is what a strict reading of the gesture would do.
        return if (key.output(candidate) != null) candidate else FlickDirection.CENTER
    }
}
