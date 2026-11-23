package me.zssu.ime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ModeIndicatorColorsTest {
    @Test fun offersEightDistinctColours() {
        assertEquals(8, ModeIndicatorColors.palette.size)
        assertEquals(8, ModeIndicatorColors.palette.map { it.argb }.toSet().size)
    }

    @Test fun kanaIsMilkyGreenAndDirectIsLightBlueByDefault() {
        assertEquals("ミルキーグリーン", ModeIndicatorColors.palette[ModeIndicatorColors.DEFAULT_KANA_INDEX].name)
        assertEquals("水色", ModeIndicatorColors.palette[ModeIndicatorColors.DEFAULT_DIRECT_INDEX].name)
        assertNotEquals(ModeIndicatorColors.DEFAULT_KANA_INDEX, ModeIndicatorColors.DEFAULT_DIRECT_INDEX)
    }

    @Test fun anOutOfRangeSavedIndexStillGivesAColour() {
        assertEquals(ModeIndicatorColors.palette.first().argb, ModeIndicatorColors.argb(-3))
        assertEquals(ModeIndicatorColors.palette.last().argb, ModeIndicatorColors.argb(99))
    }

    @Test fun textStaysReadableOnEveryColour() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        // Light blue is too light for white text; dark colours are too dark for black text.
        assertEquals(black, ModeIndicatorColors.textColorFor(ModeIndicatorColors.argb(ModeIndicatorColors.DEFAULT_DIRECT_INDEX)))
        assertEquals(white, ModeIndicatorColors.textColorFor(0xFF8E24AA.toInt()))
        assertEquals(white, ModeIndicatorColors.textColorFor(0xFF000000.toInt()))
        assertEquals(black, ModeIndicatorColors.textColorFor(0xFFFFFFFF.toInt()))
    }
}
