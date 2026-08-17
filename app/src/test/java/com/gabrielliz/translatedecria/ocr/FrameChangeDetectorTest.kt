package com.gabrielliz.translatedecria.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameChangeDetectorTest {
    @Test
    fun averageDifferenceDetectsMeaningfulChanges() {
        val detector = FrameChangeDetector(threshold = 6)
        assertEquals(0, detector.averageDifference(intArrayOf(10, 20, 30), intArrayOf(10, 20, 30)))
        assertTrue(detector.averageDifference(intArrayOf(0, 0, 0), intArrayOf(30, 30, 30)) >= 30)
    }
}
