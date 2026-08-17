package com.gabrielliz.translatedecria

/**
 * Non-negotiable privacy invariants for screen processing.
 *
 * Captured pixels and recognized text are ephemeral session data. They must never be written to
 * disk, included in logs, analytics, crash breadcrumbs, or transmitted to any server. The app
 * deliberately does not declare INTERNET permission. OCR language data is bundled in the APK and
 * on-device translation is delegated to Android's TranslationManager service.
 */
object PrivacyContract {
    const val SCREEN_CONTENT_PERSISTED = false
    const val SCREEN_CONTENT_TRANSMITTED = false
    const val ANALYTICS_ENABLED = false
    const val TELEMETRY_ENABLED = false
    const val APP_NETWORK_ACCESS_ENABLED = false

    val forbiddenRuntimePermissions = setOf(
        "android.permission.INTERNET",
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO"
    )
}
