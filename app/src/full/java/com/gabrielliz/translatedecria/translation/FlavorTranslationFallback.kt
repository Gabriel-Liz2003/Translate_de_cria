package com.gabrielliz.translatedecria.translation

import java.io.Closeable

/** Full edition intentionally remains networkless. */
class FlavorTranslationFallback : Closeable {
    suspend fun translate(sourceTag: String, targetTag: String, text: String): String? = null
    override fun close() = Unit
}
