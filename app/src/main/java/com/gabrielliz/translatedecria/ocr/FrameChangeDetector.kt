package com.gabrielliz.translatedecria.ocr

import android.graphics.Bitmap
import kotlin.math.abs

class FrameChangeDetector(private val threshold: Int = 6) {
    private var lastSamples: IntArray? = null
    private var lastWidth = -1
    private var lastHeight = -1

    fun hasChanged(bitmap: Bitmap): Boolean {
        val samples = sampleLuminance(bitmap)
        val previous = lastSamples
        val changed = previous == null || bitmap.width != lastWidth || bitmap.height != lastHeight ||
            averageDifference(previous, samples) >= threshold

        lastSamples = samples
        lastWidth = bitmap.width
        lastHeight = bitmap.height
        return changed
    }

    fun reset() {
        lastSamples = null
        lastWidth = -1
        lastHeight = -1
    }

    internal fun averageDifference(a: IntArray, b: IntArray): Int {
        if (a.size != b.size || a.isEmpty()) return Int.MAX_VALUE
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return (sum / a.size).toInt()
    }

    private fun sampleLuminance(bitmap: Bitmap): IntArray {
        val columns = 12
        val rows = 8
        val output = IntArray(columns * rows)
        var index = 0
        for (row in 0 until rows) {
            val y = ((row + 0.5f) * bitmap.height / rows).toInt().coerceIn(0, bitmap.height - 1)
            for (column in 0 until columns) {
                val x = ((column + 0.5f) * bitmap.width / columns).toInt().coerceIn(0, bitmap.width - 1)
                val pixel = bitmap.getPixel(x, y)
                val r = pixel shr 16 and 0xff
                val g = pixel shr 8 and 0xff
                val b = pixel and 0xff
                output[index++] = (r * 30 + g * 59 + b * 11) / 100
            }
        }
        return output
    }
}
