package com.gabrielliz.translatedecria.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageHeuristicsTest {
    @Test fun detectsEnglish() = assertEquals("en", LanguageHeuristics.detect("Hello world"))
    @Test fun detectsJapanese() = assertEquals("ja", LanguageHeuristics.detect("こんにちは世界"))
    @Test fun detectsChinese() = assertEquals("zh", LanguageHeuristics.detect("你好世界"))
    @Test fun detectsKorean() = assertEquals("ko", LanguageHeuristics.detect("안녕하세요 세계"))
    @Test fun unknownForSymbolsOnly() = assertNull(LanguageHeuristics.detect("123 !?"))
}
