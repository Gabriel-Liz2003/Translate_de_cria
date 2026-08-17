package com.gabrielliz.translatedecria.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.gabrielliz.translatedecria.model.ScreenTextBlock

object AccessibilityTextCollector {
    fun collect(root: AccessibilityNodeInfo?, ownPackage: String): List<ScreenTextBlock> {
        if (root == null) return emptyList()
        val output = mutableListOf<ScreenTextBlock>()
        traverse(root, ownPackage, output)
        return deduplicate(output)
    }

    private fun traverse(
        node: AccessibilityNodeInfo,
        ownPackage: String,
        output: MutableList<ScreenTextBlock>
    ) {
        if (node.packageName?.toString() == ownPackage) return

        val rawText = node.text?.toString()?.trim().orEmpty()
        if (rawText.length >= 2 && node.isVisibleToUser) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (!bounds.isEmpty) output += ScreenTextBlock(rawText, bounds)
        }

        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> traverse(child, ownPackage, output) }
        }
    }

    private fun deduplicate(blocks: List<ScreenTextBlock>): List<ScreenTextBlock> {
        val seen = hashSetOf<String>()
        return blocks.filter { block ->
            val key = "${block.originalText}|${block.bounds.left}|${block.bounds.top}|${block.bounds.right}|${block.bounds.bottom}"
            seen.add(key)
        }
    }
}
