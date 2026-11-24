package me.zssu.ime.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideGeometryTest {
    @Test fun practiceHintIsSeventyPercentAndSitsInTheLowerCorner() {
        val key = GuideGeometry.Box(100f, 300f, 196f, 376f) // 96 x 76, centre (148, 338)
        val left = GuideGeometry.hintBox(key, 2f, leftSide = true)
        assertEquals(96f * 0.7f, left.width, 0.001f)
        assertEquals(76f * 0.7f, left.height, 0.001f)
        // One pitch (100 wide, 80 tall) to the lower left.
        assertEquals(48f, (left.left + left.right) / 2f, 0.001f)
        assertEquals(418f, (left.top + left.bottom) / 2f, 0.001f)
        val right = GuideGeometry.hintBox(key, 2f, leftSide = false)
        assertEquals(248f, (right.left + right.right) / 2f, 0.001f)
        assertEquals(418f, (right.top + right.bottom) / 2f, 0.001f)
    }

    @Test fun neighboringCellsFollowTheActualKeyPitch() {
        assertEquals(GuideGeometry.Offset(0f, 0f), GuideGeometry.offset(FlickDirection.CENTER, 96f, 76f, 2f))
        assertEquals(GuideGeometry.Offset(-100f, 0f), GuideGeometry.offset(FlickDirection.LEFT, 96f, 76f, 2f))
        assertEquals(GuideGeometry.Offset(100f, 0f), GuideGeometry.offset(FlickDirection.RIGHT, 96f, 76f, 2f))
        assertEquals(GuideGeometry.Offset(0f, -80f), GuideGeometry.offset(FlickDirection.UP, 96f, 76f, 2f))
        assertEquals(GuideGeometry.Offset(0f, 80f), GuideGeometry.offset(FlickDirection.DOWN, 96f, 76f, 2f))
    }
}
