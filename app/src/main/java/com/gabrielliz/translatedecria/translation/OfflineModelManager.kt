package com.gabrielliz.translatedecria.translation

import com.gabrielliz.translatedecria.util.awaitResult
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

object OfflineModelManager {
    private val essentialSources = listOf(
        TranslateLanguage.ENGLISH,
        TranslateLanguage.JAPANESE,
        TranslateLanguage.CHINESE,
        TranslateLanguage.KOREAN
    )

    suspend fun downloadEssentials(onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }) {
        essentialSources.forEachIndexed { index, source ->
            val translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(TranslateLanguage.PORTUGUESE)
                    .build()
            )
            try {
                translator.downloadModelIfNeeded().awaitResult()
            } finally {
                translator.close()
            }
            onProgress(index + 1, essentialSources.size)
        }
    }
}
