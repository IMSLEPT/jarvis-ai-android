package it.jarvis.assistant.service

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import it.jarvis.assistant.graph
import it.jarvis.assistant.voice.CommandVad
import it.jarvis.assistant.voice.Mic
import it.jarvis.assistant.voice.SttEngine
import kotlinx.coroutines.*

/** Endpoint Android SpeechRecognizer, con pipeline locale isolata dal ciclo Hey Jarvis. */
class JarvisRecognitionService : RecognitionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var audio: CompletableDeferred<FloatArray?>? = null
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        if (job?.isActive == true || graph.core.micRunning) { listener.error(SpeechRecognizer.ERROR_RECOGNIZER_BUSY); return }
        job = scope.launch {
            val vad = CommandVad(graph.models, graph.settings)
            val mic = Mic()
            val stt = SttEngine(graph.models)
            val result = CompletableDeferred<FloatArray?>()
            audio = result
            try {
                val ready = withContext(Dispatchers.Default) { vad.load() && stt.isInstalled }
                if (!ready) { listener.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE); return@launch }
                listener.readyForSpeech(Bundle())
                val started = withContext(Dispatchers.Default) {
                    mic.start(onFrame = { frame -> vad.feed(frame)?.let { result.complete(it) } }, onError = { result.completeExceptionally(IllegalStateException("Microfono interrotto")) })
                }
                if (!started) { listener.error(SpeechRecognizer.ERROR_AUDIO); return@launch }
                val samples = withTimeoutOrNull(15_000) { result.await() }
                withContext(Dispatchers.Default) { mic.stop() }
                listener.endOfSpeech()
                if (samples == null) { listener.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT); return@launch }
                val text = withContext(Dispatchers.Default) { stt.transcribe(samples) }
                if (text.isBlank()) listener.error(SpeechRecognizer.ERROR_NO_MATCH)
                else listener.results(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) })
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { try { listener.error(SpeechRecognizer.ERROR_AUDIO) } catch (_: Exception) {} }
            finally {
                audio = null
                withContext(NonCancellable + Dispatchers.Default) { mic.stop(); vad.release(); stt.unload() }
            }
        }
    }
    override fun onStopListening(listener: Callback) { audio?.complete(null) }
    override fun onCancel(listener: Callback) { job?.cancel() }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
