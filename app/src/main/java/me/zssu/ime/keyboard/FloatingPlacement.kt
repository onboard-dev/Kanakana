package me.zssu.ime.keyboard

/** Keeps a floating keyboard inside the window it is dragged around in. */
object FloatingPlacement {
    data class Point(val x: Int, val y: Int)

    /**
     * The nearest position that keeps a [panelWidth] x [panelHeight] panel fully inside a
     * [windowWidth] x [windowHeight] window. A panel larger than the window sits at the top-left.
     */
    fun clamp(x: Int, y: Int, panelWidth: Int, panelHeight: Int, windowWidth: Int, windowHeight: Int): Point =
        Point(
            x.coerceIn(0, (windowWidth - panelWidth).coerceAtLeast(0)),
            y.coerceIn(0, (windowHeight - panelHeight).coerceAtLeast(0)),
        )
}
