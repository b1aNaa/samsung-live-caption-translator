package com.livesub.translate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HistoryEntry(
    val id: Long,
    val source: String,
    val translation: String,
    val timeMillis: Long,
)

/**
 * Finished sentences translated in this process, newest last. Kept in memory
 * only — nothing about what you watch is ever written to disk.
 */
object TranslationHistory {
    private const val MAX = 300

    private val state = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val flow: StateFlow<List<HistoryEntry>> = state.asStateFlow()
    private var nextId = 0L

    fun add(source: String, translation: String) {
        synchronized(this) {
            val list = state.value
            // ASR sometimes re-emits the same sentence; don't log it twice in a row.
            if (list.lastOrNull()?.source == source) return
            state.value = (list + HistoryEntry(nextId++, source, translation, System.currentTimeMillis())).takeLast(MAX)
        }
    }

    fun clear() {
        synchronized(this) { state.value = emptyList() }
    }
}
