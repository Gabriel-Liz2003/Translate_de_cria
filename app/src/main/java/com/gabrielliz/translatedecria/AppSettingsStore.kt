package com.gabrielliz.translatedecria

import android.content.Context
import com.gabrielliz.translatedecria.model.CaptureMode
import com.gabrielliz.translatedecria.model.SettingsSnapshot
import com.gabrielliz.translatedecria.model.SourceLanguage
import com.gabrielliz.translatedecria.model.TargetLanguage

class AppSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): SettingsSnapshot = SettingsSnapshot(
        sourceLanguage = enumValueOrDefault(prefs.getString(KEY_SOURCE, null), SourceLanguage.AUTO),
        targetLanguage = enumValueOrDefault(prefs.getString(KEY_TARGET, null), TargetLanguage.PORTUGUESE_BRAZIL),
        captureMode = enumValueOrDefault(prefs.getString(KEY_MODE, null), CaptureMode.ACCESSIBILITY),
        analysesPerSecond = prefs.getInt(KEY_FREQUENCY, 2).coerceIn(1, 5),
        fontSizeSp = prefs.getFloat(KEY_FONT_SIZE, 16f).coerceIn(12f, 28f),
        overlayOpacity = prefs.getFloat(KEY_OPACITY, 0.82f).coerceIn(0.35f, 1f),
        showOriginal = prefs.getBoolean(KEY_SHOW_ORIGINAL, false),
        translationEnabled = prefs.getBoolean(KEY_ENABLED, false)
    )

    fun save(settings: SettingsSnapshot) {
        prefs.edit()
            .putString(KEY_SOURCE, settings.sourceLanguage.name)
            .putString(KEY_TARGET, settings.targetLanguage.name)
            .putString(KEY_MODE, settings.captureMode.name)
            .putInt(KEY_FREQUENCY, settings.analysesPerSecond)
            .putFloat(KEY_FONT_SIZE, settings.fontSizeSp)
            .putFloat(KEY_OPACITY, settings.overlayOpacity)
            .putBoolean(KEY_SHOW_ORIGINAL, settings.showOriginal)
            .putBoolean(KEY_ENABLED, settings.translationEnabled)
            .apply()
    }

    fun setTranslationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        runCatching { enumValueOf<T>(raw ?: fallback.name) }.getOrDefault(fallback)

    private companion object {
        const val PREFS = "translate_de_cria_settings"
        const val KEY_SOURCE = "source"
        const val KEY_TARGET = "target"
        const val KEY_MODE = "mode"
        const val KEY_FREQUENCY = "frequency"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_OPACITY = "opacity"
        const val KEY_SHOW_ORIGINAL = "show_original"
        const val KEY_ENABLED = "enabled"
    }
}
