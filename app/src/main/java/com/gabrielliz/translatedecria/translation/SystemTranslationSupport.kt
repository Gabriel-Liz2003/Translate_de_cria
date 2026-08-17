package com.gabrielliz.translatedecria.translation

import android.app.PendingIntent
import android.content.Context
import android.view.translation.TranslationCapability
import android.view.translation.TranslationManager
import android.view.translation.TranslationSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TranslationSupportSummary(
    val supportedPairs: Int,
    val onDevicePairs: Int,
    val downloadingPairs: Int,
    val downloadablePairs: Int,
    val totalRequestedPairs: Int = 4
) {
    val userMessage: String
        get() = when {
            supportedPairs == 0 -> "O aparelho não informou suporte de tradução on-device para os idiomas principais."
            onDevicePairs == totalRequestedPairs -> "EN, JA, ZH e KO → PT estão prontos no dispositivo."
            else -> buildString {
                append("Modelos on-device: $onDevicePairs/$totalRequestedPairs prontos")
                if (downloadingPairs > 0) append("; $downloadingPairs baixando")
                if (downloadablePairs > 0) append("; $downloadablePairs disponíveis para baixar pelo sistema")
                append('.')
            }
    }
}

class SystemTranslationSupport(context: Context) {
    private val manager = context.applicationContext.getSystemService(TranslationManager::class.java)
    private val requiredSources = setOf("en", "ja", "zh", "ko")

    suspend fun query(): TranslationSupportSummary = withContext(Dispatchers.Default) {
        val capabilities = runCatching {
            manager.getOnDeviceTranslationCapabilities(
                TranslationSpec.DATA_FORMAT_TEXT,
                TranslationSpec.DATA_FORMAT_TEXT
            )
        }.getOrDefault(emptySet())

        val matching = capabilities.filter { capability ->
            val source = capability.sourceSpec.locale.language
            val target = capability.targetSpec.locale.language
            source in requiredSources && target == "pt"
        }

        TranslationSupportSummary(
            supportedPairs = matching.map { it.sourceSpec.locale.language }.distinct().size,
            onDevicePairs = matching.count { it.state == TranslationCapability.STATE_ON_DEVICE },
            downloadingPairs = matching.count { it.state == TranslationCapability.STATE_DOWNLOADING },
            downloadablePairs = matching.count { it.state == TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD }
        )
    }

    fun settingsIntent(): PendingIntent? = manager.onDeviceTranslationSettingsActivityIntent
}
