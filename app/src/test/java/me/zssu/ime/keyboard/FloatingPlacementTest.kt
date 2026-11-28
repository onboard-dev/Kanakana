package me.zssu.ime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingPlacementTest {
    @Test
    fun `a position inside the window is kept`() {
        assertEquals(FloatingPlacement.Point(100, 300), FloatingPlacement.clamp(100, 300, 400, 200, 1000, 800))
    }

    @Test
    fun `a position past an edge is pulled back so the panel stays visible`() {
        assertEquals(FloatingPlacement.Point(600, 600), FloatingPlacement.clamp(900, 700, 400, 200, 1000, 800))
        assertEquals(FloatingPlacement.Point(0, 0), FloatingPlacement.clamp(-50, -20, 400, 200, 1000, 800))
    }

    @Test
    fun `a panel larger than the window sits at the top left`() {
        assertEquals(FloatingPlacement.Point(0, 0), FloatingPlacement.clamp(80, 80, 1200, 900, 1000, 800))
    }
}
