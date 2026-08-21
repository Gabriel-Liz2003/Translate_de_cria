package com.gabrielliz.translatedecria.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.Closeable
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Safe edition fallback. Network is only needed while ML Kit downloads a language model;
 * recognition and translation requests themselves are executed on-device.
 */
class FlavorTranslationFallback : Closeable {
    private val translators = mutableMapOf<String, Translator>()

    suspend fun translate(sourceTag: String, targetTag: String, text: String): String? {
        val source = TranslateLanguage.fromLanguageTag(sourceTag.substringBefore('-')) ?: return null
        val target = TranslateLanguage.fromLanguageTag(targetTag.substringBefore('-')) ?: return null
        val key = "$source>$target"
        val translator = synchronized(translators) {
            translators[key] ?: Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            ).also { translators[key] = it }
        }

        val ready = suspendCancellableCoroutine<Boolean> { continuation ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    if (continuation.isActive) continuation.resume(true)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(false)
                }
        }
        if (!ready) return null

        return suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { translated ->
                    if (continuation.isActive) continuation.resume(translated)
                }
                .addOnFailureListener {
                    if (continuation.isActive) continuation.resume(null)
                }
        }
    }

    override fun close() {
        synchronized(translators) {
            translators.values.forEach { it.close() }
            translators.clear()
        }
    }
}
