package com.gabrielliz.translatedecria.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.gabrielliz.translatedecria.model.ScreenTextBlock
import com.gabrielliz.translatedecria.model.SourceLanguage
import com.gabrielliz.translatedecria.translation.LanguageHeuristics
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.Closeable

class OcrEngine(context: Context) : Closeable {
    private val dataRoot = TessDataInstaller.ensureInstalled(context.applicationContext)
    private val tess = TessBaseAPI()
    private var initializedLanguageSpec: String? = null

    suspend fun recognize(bitmap: Bitmap, sourceLanguage: SourceLanguage): List<ScreenTextBlock> {
        val languageSpec = languageSpec(sourceLanguage)
        ensureInitialized(languageSpec)

        return try {
            tess.setImage(bitmap)
            // Recognition is triggered here. No recognized text is persisted or logged.
            tess.getUTF8Text()
            extractTextLines(sourceLanguage)
        } finally {
            // Drops Tesseract recognition results and its native copy of the current image.
            tess.clear()
        }
    }

    private fun ensureInitialized(languageSpec: String) {
        if (initializedLanguageSpec == languageSpec) return
        check(tess.init(dataRoot.absolutePath, languageSpec)) {
            "Could not initialize local OCR for $languageSpec"
        }
        tess.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT)
        tess.setDebug(false)
        initializedLanguageSpec = languageSpec
    }

    private fun extractTextLines(sourceLanguage: SourceLanguage): List<ScreenTextBlock> {
        val iterator = tess.resultIterator ?: return emptyList()
        val level = TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE
        val blocks = mutableListOf<ScreenTextBlock>()

        try {
            iterator.begin()
            while (true) {
                val text = iterator.getUTF8Text(level)?.trim().orEmpty()
                val confidence = iterator.confidence(level)
                val bounds = runCatching { iterator.getBoundingRect(level) }.getOrNull()

                if (text.length >= 2 && confidence >= MIN_CONFIDENCE && bounds != null && isUsable(bounds)) {
                    val hint = sourceLanguage.languageTag ?: LanguageHeuristics.detect(text)
                    blocks += ScreenTextBlock(text, Rect(bounds), hint)
                }

                if (!iterator.next(level)) break
            }
        } finally {
            iterator.delete()
        }

        return deduplicate(blocks)
    }

    private fun deduplicate(blocks: List<ScreenTextBlock>): List<ScreenTextBlock> {
        val output = mutableListOf<ScreenTextBlock>()
        for (candidate in blocks.sortedByDescending { it.bounds.width() * it.bounds.height() }) {
            val duplicate = output.any { existing ->
                normalize(existing.originalText) == normalize(candidate.originalText) &&
                    intersectionOverUnion(existing.bounds, candidate.bounds) > 0.45f
            }
            if (!duplicate) output += candidate
        }
        return output.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    }

    private fun languageSpec(sourceLanguage: SourceLanguage): String = when (sourceLanguage) {
        SourceLanguage.AUTO -> "eng+jpn+chi_sim+kor"
        SourceLanguage.ENGLISH -> "eng"
        SourceLanguage.JAPANESE -> "jpn"
        SourceLanguage.CHINESE -> "chi_sim"
        SourceLanguage.KOREAN -> "kor"
    }

    private fun isUsable(bounds: Rect): Boolean = bounds.width() >= 4 && bounds.height() >= 4

    private fun normalize(text: String): String = text.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun intersectionOverUnion(a: Rect, b: Rect): Float {
        val intersection = Rect()
        if (!intersection.setIntersect(a, b)) return 0f
        val intersectionArea = intersection.width().toLong() * intersection.height().toLong()
        val union = a.width().toLong() * a.height() + b.width().toLong() * b.height() - intersectionArea
        return if (union <= 0) 0f else intersectionArea.toFloat() / union.toFloat()
    }

    override fun close() {
        tess.recycle()
        initializedLanguageSpec = null
    }

    private companion object {
        const val MIN_CONFIDENCE = 35f
    }
}
