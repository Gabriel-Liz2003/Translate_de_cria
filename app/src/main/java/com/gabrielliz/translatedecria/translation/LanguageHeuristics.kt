package com.gabrielliz.translatedecria.translation

object LanguageHeuristics {
    fun detect(text: String): String? {
        if (text.isBlank()) return null
        var hasHangul = false
        var hasKana = false
        var hasHan = false
        var hasLatin = false

        text.codePoints().forEach { codePoint ->
            when {
                codePoint in 0xAC00..0xD7AF || codePoint in 0x1100..0x11FF -> hasHangul = true
                codePoint in 0x3040..0x30FF || codePoint in 0x31F0..0x31FF -> hasKana = true
                codePoint in 0x3400..0x4DBF || codePoint in 0x4E00..0x9FFF -> hasHan = true
                codePoint in 0x0041..0x005A || codePoint in 0x0061..0x007A -> hasLatin = true
            }
        }

        return when {
            hasHangul -> "ko"
            hasKana -> "ja"
            hasHan -> "zh"
            hasLatin -> "en"
            else -> null
        }
    }
}
