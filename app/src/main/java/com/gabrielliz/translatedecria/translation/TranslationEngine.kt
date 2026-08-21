package com.gabrielliz.translatedecria.translation

import android.content.Context
import android.icu.util.ULocale
import android.os.CancellationSignal
import android.view.translation.TranslationContext
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationResponseValue
import android.view.translation.TranslationManager
import android.view.translation.TranslationSpec
import android.view.translation.Translator
import com.gabrielliz.translatedecria.BuildConfig
import com.gabrielliz.translatedecria.model.ScreenTextBlock
import com.gabrielliz.translatedecria.model.SourceLanguage
import com.gabrielliz.translatedecria.model.TargetLanguage
import com.gabrielliz.translatedecria.model.TranslatedBlock
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class TranslationEngine(context: Context) : Closeable {
    private val manager: TranslationManager? = context.applicationContext.getSystemService(TranslationManager::class.java)
    private val directExecutor = Executor { runnable -> runnable.run() }
    private val translators = mutableMapOf<String, Translator>()
    private val mlKitTranslators = mutableMapOf<String, com.google.mlkit.nl.translate.Translator>()
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

            val sourceTag = sourceLanguage.languageTag ?: block.languageHint ?: LanguageHeuristics.detect(text) ?: continue
            val targetTag = targetLanguage.languageTag
            if (sourceTag.equals(targetTag, ignoreCase = true)) {
                result += TranslatedBlock(text, text, block.bounds, sourceTag)
                continue
            }

            val cacheKey = "$sourceTag>$targetTag:${normalizeForCache(text)}"
            val translated = translationCache.get(cacheKey)
                ?: translateWithSystem(sourceTag, targetTag, text)
                ?: if (!BuildConfig.HAS_ACCESSIBILITY) translateWithMlKit(sourceTag, targetTag, text) else null

            if (!translated.isNullOrBlank()) {
                translationCache.put(cacheKey, translated)
                result += TranslatedBlock(text, translated, block.bounds, sourceTag)
            }
        }
        return result
    }

    private suspend fun translateWithSystem(sourceTag: String, targetTag: String, text: String): String? =
        withTimeoutOrNull(4_000L) {
            val translator = translatorFor(sourceTag, targetTag) ?: return@withTimeoutOrNull null
            translateText(translator, text)
        }

    private suspend fun translateWithMlKit(sourceTag: String, targetTag: String, text: String): String? {
        val source = TranslateLanguage.fromLanguageTag(sourceTag.substringBefore('-')) ?: return null
        val target = TranslateLanguage.fromLanguageTag(targetTag.substringBefore('-')) ?: return null
        val key = "$source>$target"
        val translator = synchronized(mlKitTranslators) {
            mlKitTranslators[key] ?: Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
            ).also { mlKitTranslators[key] = it }
        }

        val modelReady = suspendCancellableCoroutine<Boolean> { continuation ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(true) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(false) }
        }
        if (!modelReady) return null

        return suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
                .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        }
    }

    private suspend fun translatorFor(sourceTag: String, targetTag: String): Translator? {
        val translationManager = manager ?: return null
        val key = "$sourceTag>$targetTag"
        synchronized(translators) { translators[key] }?.let { return it }

        val sourceSpec = TranslationSpec(ULocale.forLanguageTag(sourceTag), TranslationSpec.DATA_FORMAT_TEXT)
        val targetSpec = TranslationSpec(ULocale.forLanguageTag(targetTag), TranslationSpec.DATA_FORMAT_TEXT)
        val translationContext = TranslationContext.Builder(sourceSpec, targetSpec)
            .setTranslationFlags(TranslationContext.FLAG_LOW_LATENCY)
            .build()

        val created = suspendCancellableCoroutine<Translator?> { continuation ->
            translationManager.createOnDeviceTranslator(translationContext, directExecutor) { translator ->
                if (continuation.isActive) continuation.resume(translator)
            }
        } ?: return null

        return synchronized(translators) { translators[key] ?: created.also { translators[key] = it } }
    }

    private suspend fun translateText(translator: Translator, text: String): String? = suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        continuation.invokeOnCancellation { cancellationSignal.cancel() }
        val request = TranslationRequest.Builder()
            .setTranslationRequestValues(listOf(TranslationRequestValue.forText(text)))
            .build()

        translator.translate(request, cancellationSignal, directExecutor) { response ->
            if (!continuation.isActive || !response.isFinalResponse) return@translate
            val value = response.translationResponseValues.get(0)
            val translated = if (
                response.translationStatus == TranslationResponse.TRANSLATION_STATUS_SUCCESS &&
                value?.statusCode == TranslationResponseValue.STATUS_SUCCESS
            ) value.text?.toString() else null
            continuation.resume(translated)
        }
    }

    private fun normalizeForCache(text: String): String = text.lowercase().replace(Regex("\\s+"), " ").trim()

    override fun close() {
        synchronized(translators) {
            translators.values.forEach { if (!it.isDestroyed) it.destroy() }
            translators.clear()
        }
        synchronized(mlKitTranslators) {
            mlKitTranslators.values.forEach { it.close() }
            mlKitTranslators.clear()
        }
        translationCache.clear()
    }
}
