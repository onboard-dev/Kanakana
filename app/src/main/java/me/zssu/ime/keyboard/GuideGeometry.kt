package me.zssu.ime.keyboard

/** Positions a neighbouring flick cell using the same pitch as the key layout. */
internal object GuideGeometry {
    data class Offset(val x: Float, val y: Float)
    /** A plain rectangle, so the geometry can be tested without Android's RectF. */
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width get() = right - left
        val height get() = bottom - top
    }

    /** How much smaller the practice hint is than the guide cells. */
    const val HINT_SCALE = 0.7f

    /**
     * Where the practice hint goes: [HINT_SCALE] of the key's size, centred in the cell diagonally
     * below the tapped key ([leftSide] picks lower left or lower right). [gap] is the gap between keys.
     */
    fun hintBox(key: Box, gap: Float, leftSide: Boolean): Box {
        val side = offset(if (leftSide) FlickDirection.LEFT else FlickDirection.RIGHT, key.width, key.height, gap)
        val down = offset(FlickDirection.DOWN, key.width, key.height, gap)
        val w = key.width * HINT_SCALE
        val h = key.height * HINT_SCALE
        val centerX = (key.left + key.right) / 2f + side.x
        val centerY = (key.top + key.bottom) / 2f + down.y
        return Box(centerX - w / 2f, centerY - h / 2f, centerX + w / 2f, centerY + h / 2f)
    }

    fun offset(direction: FlickDirection, keyWidth: Float, keyHeight: Float, gap: Float): Offset {
        val pitchX = keyWidth + 2f * gap.coerceAtLeast(0f)
        val pitchY = keyHeight + 2f * gap.coerceAtLeast(0f)
        return when (direction) {
            FlickDirection.LEFT -> Offset(-pitchX, 0f)
            FlickDirection.RIGHT -> Offset(pitchX, 0f)
            FlickDirection.UP -> Offset(0f, -pitchY)
            FlickDirection.DOWN -> Offset(0f, pitchY)
            FlickDirection.CENTER -> Offset(0f, 0f)
        }
    }
}
