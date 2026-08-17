package com.gabrielliz.translatedecria.translation

import com.gabrielliz.translatedecria.model.ScreenTextBlock
import com.gabrielliz.translatedecria.model.SourceLanguage
import com.gabrielliz.translatedecria.model.TargetLanguage
import com.gabrielliz.translatedecria.model.TranslatedBlock
import com.gabrielliz.translatedecria.util.awaitResult
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable

class TranslationEngine : Closeable {
    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.34f).build()
    )
    private val translators = mutableMapOf<String, Translator>()
    private val translationCache = MemoryTranslationCache(300)

    suspend fun translateBlocks(
        blocks: List<ScreenTextBlock>,
        sourceLanguage: SourceLanguage,
        targetLanguage: TargetLanguage
    ): List<TranslatedBlock> {
        if (blocks.isEmpty()) return emptyList()
        val result = ArrayList<TranslatedBlock>(blocks.size)

        for (block in blocks) {
            val text = block.originalText.trim()
            if (text.isEmpty()) continue

            val sourceTag = sourceLanguage.languageTag
                ?: block.languageHint
                ?: identifyLanguage(text)
                ?: continue
            val normalizedSource = TranslateLanguage.fromLanguageTag(sourceTag) ?: continue
            val normalizedTarget = TranslateLanguage.fromLanguageTag(targetLanguage.languageTag) ?: continue

            if (normalizedSource == normalizedTarget) {
                result += TranslatedBlock(text, text, block.bounds, sourceTag)
                continue
            }

            val cacheKey = "$normalizedSource>$normalizedTarget:${normalizeForCache(text)}"
            val translated = translationCache.get(cacheKey) ?: run {
                val translator = translatorFor(normalizedSource, normalizedTarget)
                translator.downloadModelIfNeeded().awaitResult()
                translator.translate(text).awaitResult().also { translationCache.put(cacheKey, it) }
            }

            result += TranslatedBlock(text, translated, block.bounds, sourceTag)
        }

        return result
    }

    private suspend fun identifyLanguage(text: String): String? {
        val language = languageIdentifier.identifyLanguage(text).awaitResult()
        return language.takeUnless { it == "und" }
    }

    private fun translatorFor(source: String, target: String): Translator {
        val key = "$source>$target"
        return synchronized(translators) {
            translators.getOrPut(key) {
                Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(target)
                        .build()
                )
            }
        }
    }

    private fun normalizeForCache(text: String): String =
        text.lowercase().replace(Regex("\\s+"), " ").trim()

    override fun close() {
        languageIdentifier.close()
        synchronized(translators) {
            translators.values.forEach(Translator::close)
            translators.clear()
        }
        translationCache.clear()
    }
}
