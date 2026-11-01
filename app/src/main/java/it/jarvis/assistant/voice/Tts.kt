package it.jarvis.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.*
import it.jarvis.assistant.core.Settings
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.models.ModelCatalog
import it.jarvis.assistant.models.ModelManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Piper senza callback JNI: la callback C++→Kotlin non è sicura con tutti i runtime/lambda. */
class TtsEngine(private val models: ModelManager, private val settings: Settings) {
    private val nativeLock = Any()
    private var tts: OfflineTts? = null
    private var loadedVoice = ""
    @Volatile private var cancelled = false
    @Volatile private var track: AudioTrack? = null
    @Volatile var lastUsed = 0L
        private set
    @Volatile var generatedSamples = 0L
        private set
    val failure = MutableStateFlow<String?>(null)
    val isSpeaking get() = track != null
    private fun currentSpec() = ModelCatalog.byId(settings.ttsVoice)?.takeIf { models.isInstalled(it) }
        ?: ModelCatalog.voices.firstOrNull { models.isInstalled(it) }
    fun available() = currentSpec() != null

    private fun ensure(): OfflineTts {
        val spec = currentSpec() ?: error("Installa una voce italiana in Voce e modelli.")
        if (tts != null && loadedVoice == spec.id) return tts!!
        release()
        val dir = models.path(spec)
        val model = dir.listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull() ?: error("File della voce mancante: riscarica ${spec.title}.")
        it.jarvis.assistant.models.ModelFiles.requireCompleteOnnx(model)
        val tokens = File(dir, "tokens.txt")
        val data = File(dir, "espeak-ng-data")
        check(model.length() > 1024 && tokens.length() > 0 && File(data, "phontab").isFile && File(data, "phondata").isFile) {
            "Download voce incompleto: rimuovi e riscarica ${spec.title}."
        }
        return OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(model = model.absolutePath, tokens = tokens.absolutePath, dataDir = data.absolutePath),
            numThreads = 2, provider = "cpu"), maxNumSentences = 1)).also { tts = it; loadedVoice = spec.id }
    }

    /** true soltanto se sono stati generati e riprodotti campioni; anteprima indipendente dall'interruttore TTS. */
    suspend fun speak(text: String, preview: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()[Job]
        synchronized(nativeLock) {
            if ((!settings.ttsEnabled && !preview) || text.isBlank() || job?.isActive == false) return@synchronized false
            cancelled = false; failure.value = null
            try {
                val engine = ensure()
                var played = false
                for (chunk in chunks(text)) {
                    if (cancelled || job?.isActive == false) break
                    // Nessun generateWithCallback: niente lambda JNI né campioni scritti da thread nativi.
                    val audio = engine.generate(chunk, sid = 0, speed = settings.ttsSpeed.coerceIn(.7f, 1.4f))
                    check(audio.samples.isNotEmpty() && audio.sampleRate in 8000..96000) { "La voce non ha prodotto audio valido." }
                    generatedSamples += audio.samples.size
                    if (cancelled || job?.isActive == false) break
                    played = play(audio.samples, audio.sampleRate, job) || played
                }
                played
            } catch (e: Exception) {
                failure.value = e.message ?: "Riproduzione vocale non riuscita."
                DiagLog.e("TTS", "Sintesi/riproduzione non riuscita", e)
                false
            } catch (e: LinkageError) {
                failure.value = "Motore vocale non compatibile: aggiorna l’app."
                DiagLog.e("TTS", "Libreria vocale", e)
                false
            } finally { lastUsed = System.currentTimeMillis() }
        }
    }

    private fun play(samples: FloatArray, sr: Int, job: Job?): Boolean {
        val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        check(min > 0) { "Formato audio non supportato dal dispositivo." }
        val at = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min, sr * 2)).setTransferMode(AudioTrack.MODE_STREAM).build()
        track = at
        try {
            at.play()
            var offset = 0
            while (offset < samples.size && !cancelled && job?.isActive != false) {
                val n = at.write(samples, offset, minOf(4096, samples.size - offset), AudioTrack.WRITE_BLOCKING)
                check(n > 0) { "Riproduzione audio interrotta ($n)." }; offset += n
            }
            val deadline = System.currentTimeMillis() + samples.size * 1000L / sr + 3000
            while (!cancelled && job?.isActive != false && at.playbackHeadPosition.toLong() < offset && System.currentTimeMillis() < deadline) Thread.sleep(15)
            return offset > 0 && !cancelled && job?.isActive != false
        } finally {
            track = null
            try { at.stop() } catch (_: Exception) {}
            at.release()
        }
    }
    fun stop() { cancelled = true; try { track?.pause(); track?.flush() } catch (_: Exception) {} }
    fun release() = synchronized(nativeLock) { tts?.release(); tts = null; loadedVoice = "" }
    private fun chunks(text: String): List<String> {
        val clean = text.replace(Regex("https?://\\S+"), "link").replace(Regex("[*_`#>]+"), "").replace(Regex("\\s+"), " ").trim()
        return clean.chunked(220).filter { it.isNotBlank() }
    }
}
