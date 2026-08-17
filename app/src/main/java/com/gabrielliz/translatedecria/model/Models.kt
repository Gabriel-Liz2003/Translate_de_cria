package com.gabrielliz.translatedecria.model

import android.graphics.Rect

enum class CaptureMode(val label: String) {
    ACCESSIBILITY("Privacidade máxima — Accessibility"),
    OCR("OCR da tela — somente em RAM")
}

enum class SourceLanguage(val label: String, val languageTag: String?) {
    AUTO("Automático", null),
    ENGLISH("Inglês", "en"),
    JAPANESE("Japonês", "ja"),
    CHINESE("Chinês", "zh"),
    KOREAN("Coreano", "ko")
}

enum class TargetLanguage(val label: String, val languageTag: String) {
    PORTUGUESE_BRAZIL("Português (Brasil)", "pt-BR")
}

data class SettingsSnapshot(
    val sourceLanguage: SourceLanguage = SourceLanguage.AUTO,
    val targetLanguage: TargetLanguage = TargetLanguage.PORTUGUESE_BRAZIL,
    val captureMode: CaptureMode = CaptureMode.ACCESSIBILITY,
    val analysesPerSecond: Int = 2,
    val fontSizeSp: Float = 16f,
    val overlayOpacity: Float = 0.82f,
    val showOriginal: Boolean = false,
    val translationEnabled: Boolean = false
)

data class ScreenTextBlock(
    val originalText: String,
    val bounds: Rect,
    val languageHint: String? = null
)

data class TranslatedBlock(
    val originalText: String,
    val translatedText: String,
    val bounds: Rect,
    val sourceLanguageTag: String
)
