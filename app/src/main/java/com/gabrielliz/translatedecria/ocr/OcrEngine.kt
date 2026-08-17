package com.gabrielliz.translatedecria.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.gabrielliz.translatedecria.model.ScreenTextBlock
import com.gabrielliz.translatedecria.model.SourceLanguage
import com.gabrielliz.translatedecria.util.awaitResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.Closeable

class OcrEngine : Closeable {
    private val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val japanese = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val chinese = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    private val korean = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    suspend fun recognize(bitmap: Bitmap, sourceLanguage: SourceLanguage): List<ScreenTextBlock> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val candidates = when (sourceLanguage) {
            SourceLanguage.ENGLISH -> listOf(latin to "en")
            SourceLanguage.JAPANESE -> listOf(japanese to "ja")
            SourceLanguage.CHINESE -> listOf(chinese to "zh")
            SourceLanguage.KOREAN -> listOf(korean to "ko")
            SourceLanguage.AUTO -> listOf(
                latin to null,
                japanese to null,
                chinese to null,
                korean to null
            )
        }

        val allBlocks = mutableListOf<ScreenTextBlock>()
        for ((recognizer, hint) in candidates) {
            val result = recognizer.process(image).awaitResult()
            allBlocks += result.textBlocks.mapNotNull { it.toDomainBlock(hint) }
        }
        return deduplicate(allBlocks)
    }

    private fun Text.TextBlock.toDomainBlock(languageHint: String?): ScreenTextBlock? {
        val box = boundingBox ?: return null
        val clean = text.trim()
        if (clean.length < 2 || box.width() < 4 || box.height() < 4) return null
        return ScreenTextBlock(clean, Rect(box), languageHint)
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

    private fun normalize(text: String): String = text.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun intersectionOverUnion(a: Rect, b: Rect): Float {
        val intersection = Rect()
        if (!intersection.setIntersect(a, b)) return 0f
        val intersectionArea = intersection.width().toLong() * intersection.height().toLong()
        val union = a.width().toLong() * a.height() + b.width().toLong() * b.height() - intersectionArea
        return if (union <= 0) 0f else intersectionArea.toFloat() / union.toFloat()
    }

    override fun close() {
        latin.close()
        japanese.close()
        chinese.close()
        korean.close()
    }
}
