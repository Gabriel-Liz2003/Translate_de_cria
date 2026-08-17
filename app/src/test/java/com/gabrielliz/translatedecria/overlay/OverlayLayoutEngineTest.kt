package com.gabrielliz.translatedecria.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayLayoutEngineTest {
    @Test
    fun keepsPlacementsInsideScreenAndAvoidsSimpleOverlap() {
        val source = listOf(
            OverlayLayoutEngine.Box(10, 10, 210, 70),
            OverlayLayoutEngine.Box(20, 30, 220, 90),
            OverlayLayoutEngine.Box(40, 50, 240, 110)
        )

        val placements = OverlayLayoutEngine.resolve(source, screenWidth = 300, screenHeight = 400)

        placements.forEach { placement ->
            assertTrue(placement.placed.left >= 0)
            assertTrue(placement.placed.top >= 0)
            assertTrue(placement.placed.right <= 300)
            assertTrue(placement.placed.bottom <= 400)
        }
        assertFalse(OverlayLayoutEngine.intersects(placements[0].placed, placements[1].placed))
    }
}
