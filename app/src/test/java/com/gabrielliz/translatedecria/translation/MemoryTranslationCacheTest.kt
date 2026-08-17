package com.gabrielliz.translatedecria.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryTranslationCacheTest {
    @Test
    fun evictsLeastRecentlyUsedEntryAndClearsSessionData() {
        val cache = MemoryTranslationCache(maxEntries = 2)
        cache.put("a", "A")
        cache.put("b", "B")
        assertEquals("A", cache.get("a"))
        cache.put("c", "C")

        assertNull(cache.get("b"))
        assertEquals(2, cache.size())

        cache.clear()
        assertEquals(0, cache.size())
    }
}
