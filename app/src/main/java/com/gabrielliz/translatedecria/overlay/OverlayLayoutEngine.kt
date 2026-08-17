package com.gabrielliz.translatedecria.overlay

object OverlayLayoutEngine {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
    }

    data class Placement(val source: Box, val placed: Box)

    fun resolve(bounds: List<Box>, screenWidth: Int, screenHeight: Int, gapPx: Int = 6): List<Placement> {
        val occupied = mutableListOf<Box>()
        return bounds.map { source ->
            val width = source.width.coerceAtLeast(80).coerceAtMost(screenWidth.coerceAtLeast(1))
            val height = source.height.coerceAtLeast(36).coerceAtMost(screenHeight.coerceAtLeast(1))
            val left = source.left.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
            var top = source.top.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
            var candidate = Box(left, top, left + width, top + height)

            for (previous in occupied) {
                if (intersects(candidate, previous)) {
                    top = (previous.bottom + gapPx).coerceAtMost((screenHeight - height).coerceAtLeast(0))
                    candidate = Box(left, top, left + width, top + height)
                }
            }

            if (occupied.any { intersects(candidate, it) }) {
                top = (source.top - height - gapPx).coerceAtLeast(0)
                candidate = Box(left, top, left + width, top + height)
            }

            occupied += candidate
            Placement(source, candidate)
        }
    }

    internal fun intersects(a: Box, b: Box): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
}
