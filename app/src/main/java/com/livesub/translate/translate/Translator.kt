package com.livesub.translate.translate

/**
 * @property text the caption segment to translate — one sentence or clause.
 * @property context preceding caption segments, oldest first. Engines that
 *   can use context (LLMs) pass it along; dedicated MT engines ignore it.
 */
data class TranslationRequest(
    val text: String,
    val context: List<String> = emptyList(),
)

interface Translator {
    /** How long the live (still-changing) segment must stay unchanged before it is sent. */
    val debounceMs: Long get() = 450L

    /** @throws TranslateException on any failure the user should see. */
    suspend fun translate(request: TranslationRequest): String
}

class TranslateException(
    val kind: Kind,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(detail ?: kind.name, cause) {
    enum class Kind { MissingKey, Auth, Quota, RateLimit, Network, Timeout, Server, BadResponse }
}
