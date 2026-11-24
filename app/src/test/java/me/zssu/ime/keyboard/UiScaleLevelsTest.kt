package me.zssu.ime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleLevelsTest {
    @Test
    fun `levels cycle normal 60 50 40 30 then normal`() {
        val percents = mutableListOf<Int>()
        var level = 0
        repeat(6) {
            percents += UiScaleLevels.percent(level)
            level = UiScaleLevels.next(level)
        }
        assertEquals(listOf(100, 60, 50, 40, 30, 100), percents)
        assertEquals(1, level)
    }

    @Test
    fun `an out of range saved level is treated as the nearest valid one`() {
        assertEquals(0.3f, UiScaleLevels.scale(99), 0f)
        assertEquals(1f, UiScaleLevels.scale(-3), 0f)
        assertEquals(0, UiScaleLevels.next(UiScaleLevels.count - 1))
    }
}
