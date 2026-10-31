package it.jarvis.assistant.voice

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import it.jarvis.assistant.core.Settings
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.models.ModelCatalog
import it.jarvis.assistant.models.ModelManager

/**
 * Rilevamento fine comando con Silero VAD: restituisce un segmento di parlato quando c'è stato parlato
 * seguito da silenzio (endSilenceMs). Nessun ascolto infinito: i timeout li gestisce il chiamante.
 */
class CommandVad(private val models: ModelManager, private val settings: Settings) {
    private var vad: Vad? = null
    private var loadedSilence = -1

    @Synchronized
    fun load(): Boolean {
        if (vad != null && loadedSilence == settings.endSilenceMs) return true
        release()
        val spec = ModelCatalog.vad
        if (!models.isInstalled(spec)) return false
        return try {
            vad = Vad(config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = models.path(spec).absolutePath,
                    threshold = 0.5f,
                    minSilenceDuration = settings.endSilenceMs / 1000f,
                    minSpeechDuration = 0.2f,
                    windowSize = 512,
                    maxSpeechDuration = 20f,
                ),
                sampleRate = 16000, numThreads = 1, provider = "cpu",
            ))
            loadedSilence = settings.endSilenceMs
            true
        } catch (e: Throwable) { DiagLog.e("VAD", "Caricamento fallito", Exception(e)); false }
    }

    @Synchronized fun reset() { vad?.reset(); vad?.clear() }

    /** Alimenta un frame; ritorna il segmento completo quando il comando è finito. */
    @Synchronized
    fun feed(frame: FloatArray): FloatArray? {
        val v = vad ?: return null
        v.acceptWaveform(frame)
        if (!v.empty()) {
            val seg = v.front()
            v.pop()
            v.clear()
            return seg.samples
        }
        return null
    }

    @Synchronized fun speechNow(): Boolean = vad?.isSpeechDetected() ?: false

    @Synchronized fun release() { try { vad?.release() } catch (_: Throwable) {}; vad = null; loadedSilence = -1 }
}
