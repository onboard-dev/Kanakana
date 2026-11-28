package me.zssu.ime.keyboard

import me.zssu.ime.settings.ImeSettings.OneHandMode
import org.junit.Assert.assertEquals
import org.junit.Test

class OneHandStepsTest {
    @Test
    fun `pressing walks left to right and back`() {
        var index = 0
        val seen = mutableListOf<String>()
        repeat(13) {
            val step = OneHandSteps.steps[index]
            seen += "${step.mode.name.first()}${step.percent}"
            index = OneHandSteps.nextIndex(step.mode, step.percent / 100f, index)
        }
        assertEquals(
            listOf("O100", "L80", "L70", "L60", "R60", "R70", "R80", "R70", "R60", "L60", "L70", "L80", "O100"),
            seen,
        )
    }

    @Test
    fun `the remembered index tells the way out from the way back`() {
        // R70 is at index 5 (going right) and 7 (coming back).
        assertEquals(6, OneHandSteps.nextIndex(OneHandMode.RIGHT, 0.7f, 5))
        assertEquals(8, OneHandSteps.nextIndex(OneHandMode.RIGHT, 0.7f, 7))
    }

    @Test
    fun `an unknown width starts from the nearest step on that side`() {
        // 82% on the left is closest to L80 (index 1), so the next step is L70.
        assertEquals(2, OneHandSteps.nextIndex(OneHandMode.LEFT, 0.82f, -1))
        assertEquals(1, OneHandSteps.nextIndex(OneHandMode.OFF, 0.82f, -1))
    }
}
