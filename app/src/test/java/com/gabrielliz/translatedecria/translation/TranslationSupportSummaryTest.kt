package com.gabrielliz.translatedecria.translation

import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationSupportSummaryTest {
    @Test
    fun unavailableServiceExplainsRomLimitation() {
        val summary = TranslationSupportSummary(
            serviceAvailable = false,
            supportedPairs = 0,
            onDevicePairs = 0,
            downloadingPairs = 0,
            downloadablePairs = 0
        )

        assertTrue(summary.userMessage.contains("não fornece um serviço de tradução on-device"))
    }

    @Test
    fun availableServiceReportsReadyModels() {
        val summary = TranslationSupportSummary(
            serviceAvailable = true,
            supportedPairs = 4,
            onDevicePairs = 4,
            downloadingPairs = 0,
            downloadablePairs = 0
        )

        assertTrue(summary.userMessage.contains("prontos no dispositivo"))
    }
}
