package it.jarvis.assistant.core

import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

/** La risposta scritta resta disponibile anche se il microfono è spento. */
suspend fun awaitUserAnswer(
    typed: Deferred<String>, hasMic: Boolean, voiceTimeoutMs: Long, typedTimeoutMs: Long = 120_000,
    listen: suspend () -> String?,
): String? = coroutineScope {
    val voice = async { if (hasMic) listen() else awaitCancellation() }
    try {
        withTimeoutOrNull(if (hasMic) voiceTimeoutMs else typedTimeoutMs) {
            select<String?> { typed.onAwait { it }; voice.onAwait { it } }
        }
    } finally { voice.cancel() }
}
