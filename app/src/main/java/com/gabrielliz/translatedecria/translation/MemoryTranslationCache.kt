package com.gabrielliz.translatedecria.translation

class MemoryTranslationCache(private val maxEntries: Int = 300) {
    private val entries = object : LinkedHashMap<String, String>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(key: String): String? = entries[key]

    @Synchronized
    fun put(key: String, value: String) {
        entries[key] = value
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size
}
