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
            tess.getUTF8Text()
            extractTextLines(sourceLanguage)
        } finally {
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

                if (
                    bounds != null &&
                    confidence >= MIN_CONFIDENCE &&
                    isUsable(bounds) &&
                    isMeaningfulText(text) &&
                    matchesRequestedLanguage(text, sourceLanguage)
                ) {
                    val hint = sourceLanguage.languageTag ?: LanguageHeuristics.detect(text)
                    if (hint != null) blocks += ScreenTextBlock(text, Rect(bounds), hint)
                }

                if (!iterator.next(level)) break
            }
        } finally {
            iterator.delete()
        }

        return deduplicate(blocks)
    }

    private fun matchesRequestedLanguage(text: String, sourceLanguage: SourceLanguage): Boolean {
        if (sourceLanguage == SourceLanguage.AUTO) return LanguageHeuristics.detect(text) != null

        var latin = 0
        var kana = 0
        var han = 0
        var hangul = 0
        text.codePoints().forEach { codePoint ->
            when {
                codePoint in 0x0041..0x005A || codePoint in 0x0061..0x007A -> latin++
                codePoint in 0x3040..0x30FF || codePoint in 0x31F0..0x31FF -> kana++
                codePoint in 0x3400..0x4DBF || codePoint in 0x4E00..0x9FFF -> han++
                codePoint in 0xAC00..0xD7AF || codePoint in 0x1100..0x11FF -> hangul++
            }
        }

        return when (sourceLanguage) {
            SourceLanguage.AUTO -> true
            SourceLanguage.ENGLISH -> latin >= 2
            SourceLanguage.JAPANESE -> kana >= 1 || han >= 2
            SourceLanguage.CHINESE -> han >= 1 && kana == 0
            SourceLanguage.KOREAN -> hangul >= 1
        }
    }

    private fun isMeaningfulText(text: String): Boolean {
        if (text.length < 2) return false
        val meaningful = text.codePoints().filter { cp ->
            Character.isLetterOrDigit(cp) ||
                cp in 0x3040..0x30FF ||
                cp in 0x31F0..0x31FF ||
                cp in 0x3400..0x4DBF ||
                cp in 0x4E00..0x9FFF ||
                cp in 0xAC00..0xD7AF
        }.count()
        return meaningful >= 2
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

    private fun isUsable(bounds: Rect): Boolean =
        bounds.width() >= MIN_BOX_SIZE_PX && bounds.height() >= MIN_BOX_SIZE_PX

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
        const val MIN_CONFIDENCE = 45f
        const val MIN_BOX_SIZE_PX = 10
    }
}
