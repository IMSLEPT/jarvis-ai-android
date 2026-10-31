package it.jarvis.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import it.jarvis.assistant.core.Settings
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.models.ModelCatalog
import it.jarvis.assistant.models.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Sintesi vocale italiana offline con voci Piper (VITS) tramite sherpa-onnx, riprodotta in streaming. */
class TtsEngine(private val models: ModelManager, private val settings: Settings) {
    private var tts: OfflineTts? = null
    private var loadedVoice: String = ""
    @Volatile private var cancelled = false
    @Volatile private var track: AudioTrack? = null
    @Volatile var lastUsed = 0L

    val isSpeaking get() = track != null

    private fun currentSpec() = ModelCatalog.byId(settings.ttsVoice)?.takeIf { models.isInstalled(it) }
        ?: ModelCatalog.voices.firstOrNull { models.isInstalled(it) }

    fun available() = currentSpec() != null

    @Synchronized
    private fun ensure(): OfflineTts? {
        val spec = currentSpec() ?: return null
        if (tts != null && loadedVoice == spec.id) return tts
        release()
        val dir = models.path(spec)
        val onnx = dir.listFiles { f -> f.name.endsWith(".onnx") }?.firstOrNull() ?: return null
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = onnx.absolutePath,
                    tokens = File(dir, "tokens.txt").absolutePath,
                    dataDir = File(dir, "espeak-ng-data").absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return try {
            val t0 = System.currentTimeMillis()
            val t = OfflineTts(config = cfg)
            tts = t; loadedVoice = spec.id
            DiagLog.i("TTS", "Voce ${spec.id} caricata in ${System.currentTimeMillis() - t0} ms (sr=${t.sampleRate()})")
            t
        } catch (e: Throwable) {
            DiagLog.e("TTS", "Caricamento voce fallito", Exception(e))
            null
        }
    }

    /** Parla e ritorna a fine riproduzione (o a stop()). */
    suspend fun speak(text: String) = withContext(Dispatchers.IO) {
        if (!settings.ttsEnabled || text.isBlank()) return@withContext
        val engine = ensure() ?: run { DiagLog.w("TTS", "Nessuna voce installata: salto la sintesi"); return@withContext }
        cancelled = false
        val sr = engine.sampleRate()
        val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val at = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(minBuf, sr * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = at
        at.play()
        var frames = 0L
        try {
            engine.generateWithCallback(clean(text), sid = 0, speed = settings.ttsSpeed) { samples ->
                if (cancelled) return@generateWithCallback 0
                var off = 0
                while (off < samples.size && !cancelled) {
                    val n = at.write(samples, off, samples.size - off, AudioTrack.WRITE_BLOCKING)
                    if (n < 0) return@generateWithCallback 0
                    off += n
                }
                frames += samples.size
                if (cancelled) 0 else 1
            }
            // attende lo svuotamento del buffer
            var guard = 0
            while (!cancelled && at.playbackHeadPosition < frames && guard < 600) { Thread.sleep(25); guard++ }
        } catch (e: Throwable) {
            DiagLog.e("TTS", "Errore sintesi", Exception(e))
        } finally {
            try { at.stop() } catch (_: Exception) {}
            at.release()
            track = null
            lastUsed = System.currentTimeMillis()
        }
    }

    fun stop() {
        cancelled = true
        try { track?.pause(); track?.flush() } catch (_: Exception) {}
    }

    @Synchronized
    fun release() {
        try { tts?.release() } catch (_: Throwable) {}
        tts = null; loadedVoice = ""
    }

    private fun clean(t: String): String = t
        .replace(Regex("https?://\\S+"), "link")
        .replace(Regex("[*_`#>]+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
