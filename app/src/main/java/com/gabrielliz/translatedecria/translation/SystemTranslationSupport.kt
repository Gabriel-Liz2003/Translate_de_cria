package com.gabrielliz.translatedecria.translation

import android.app.PendingIntent
import android.content.Context
import android.view.translation.TranslationCapability
import android.view.translation.TranslationManager
import android.view.translation.TranslationSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TranslationSupportSummary(
    val serviceAvailable: Boolean,
    val supportedPairs: Int,
    val onDevicePairs: Int,
    val downloadingPairs: Int,
    val downloadablePairs: Int,
    val totalRequestedPairs: Int = 4
) {
    val userMessage: String
        get() = when {
            !serviceAvailable -> "Este Android/ROM não fornece um serviço de tradução on-device compatível. A tradução pelo sistema não está disponível neste aparelho."
            supportedPairs == 0 -> "O serviço de tradução existe, mas não informou suporte para EN/JA/ZH/KO → PT."
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
    private val manager: TranslationManager? =
        context.applicationContext.getSystemService(TranslationManager::class.java)
    private val requiredSources = setOf("en", "ja", "zh", "ko")

    suspend fun query(): TranslationSupportSummary = withContext(Dispatchers.Default) {
        val translationManager = manager ?: return@withContext unavailableSummary()
        val capabilities = runCatching {
            translationManager.getOnDeviceTranslationCapabilities(
                TranslationSpec.DATA_FORMAT_TEXT,
                TranslationSpec.DATA_FORMAT_TEXT
            )
        }.getOrElse { return@withContext unavailableSummary() }

        val matching = capabilities.filter { capability ->
            val source = capability.sourceSpec.locale.language
            val target = capability.targetSpec.locale.language
            source in requiredSources && target == "pt"
        }

        TranslationSupportSummary(
            serviceAvailable = true,
            supportedPairs = matching.map { it.sourceSpec.locale.language }.distinct().size,
            onDevicePairs = matching.count { it.state == TranslationCapability.STATE_ON_DEVICE },
            downloadingPairs = matching.count { it.state == TranslationCapability.STATE_DOWNLOADING },
            downloadablePairs = matching.count { it.state == TranslationCapability.STATE_AVAILABLE_TO_DOWNLOAD }
        )
    }

    fun settingsIntent(): PendingIntent? {
        val translationManager = manager ?: return null
        return runCatching { translationManager.onDeviceTranslationSettingsActivityIntent }.getOrNull()
    }

    private fun unavailableSummary() = TranslationSupportSummary(
        serviceAvailable = false,
        supportedPairs = 0,
        onDevicePairs = 0,
        downloadingPairs = 0,
        downloadablePairs = 0
    )
}
