package com.livesub.translate

import android.content.Context
import com.livesub.translate.translate.TranslateException
import com.livesub.translate.translate.TranslateException.Kind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Observable state of the accessibility service, shared with the UI and the tile. */
object ServiceStatus {
    data class State(
        /** The accessibility service is enabled and bound. */
        val connected: Boolean = false,
        /** Translation is switched on. */
        val running: Boolean = false,
    )

    private val state = MutableStateFlow(State())
    val flow: StateFlow<State> = state.asStateFlow()

    internal fun set(transform: (State) -> State) {
        synchronized(this) { state.value = transform(state.value) }
    }
}

enum class StartResult { STARTED, NO_SERVICE, NO_CREDENTIALS }

/** User-facing text for a translation failure. */
fun Context.describeError(e: Throwable): String {
    if (e !is TranslateException) return e.message ?: getString(R.string.err_unknown)
    val base = getString(
        when (e.kind) {
            Kind.MissingKey -> R.string.err_missing_key
            Kind.Auth -> R.string.err_auth
            Kind.Quota -> R.string.err_quota
            Kind.RateLimit -> R.string.err_rate_limit
            Kind.Network -> R.string.err_network
            Kind.Timeout -> R.string.err_timeout
            Kind.Server -> R.string.err_server
            Kind.BadResponse -> R.string.err_bad_response
        }
    )
    val detail = e.detail?.takeIf { e.kind == Kind.Server || e.kind == Kind.Auth || e.kind == Kind.Quota }
    return if (detail.isNullOrBlank()) base else "$base ($detail)"
}
