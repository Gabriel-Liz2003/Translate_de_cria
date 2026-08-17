package com.gabrielliz.translatedecria.overlay

object OverlayLayoutEngine {
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = (right - left).coerceAtLeast(0)
        val height: Int get() = (bottom - top).coerceAtLeast(0)
    }

    data class Placement(val source: Box, val placed: Box)

    fun resolve(
        bounds: List<Box>,
        screenWidth: Int,
        screenHeight: Int,
        gapPx: Int = 6,
        preferAdjacent: Boolean = false
    ): List<Placement> {
        val occupied = mutableListOf<Box>()
        val reservedSourceBoxes = if (preferAdjacent) bounds else emptyList()

        return bounds.map { source ->
            val width = source.width.coerceAtLeast(80).coerceAtMost(screenWidth.coerceAtLeast(1))
            val height = source.height.coerceAtLeast(36).coerceAtMost(screenHeight.coerceAtLeast(1))
            val left = source.left.coerceIn(0, (screenWidth - width).coerceAtLeast(0))

            val candidateTops = if (preferAdjacent) {
                listOf(source.bottom + gapPx, source.top - height - gapPx, source.top)
            } else {
                listOf(source.top, source.bottom + gapPx, source.top - height - gapPx)
            }

            val candidates = candidateTops.map { requestedTop ->
                val top = requestedTop.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
                Box(left, top, left + width, top + height)
            }

            val placed = candidates.firstOrNull { candidate ->
                occupied.none { intersects(candidate, it) } &&
                    reservedSourceBoxes.none { reserved -> intersects(candidate, reserved) }
            } ?: candidates.firstOrNull { candidate ->
                occupied.none { intersects(candidate, it) }
            } ?: candidates.first()

            occupied += placed
            Placement(source, placed)
        }
    }

    internal fun intersects(a: Box, b: Box): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top
}
