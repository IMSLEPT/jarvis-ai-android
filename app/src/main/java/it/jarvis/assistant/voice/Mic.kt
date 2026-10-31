package it.jarvis.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import it.jarvis.assistant.diag.DiagLog

/** Cattura microfono 16 kHz mono, frame da 512 campioni (32 ms) normalizzati in [-1,1]. */
class Mic {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    val isRunning get() = running

    @SuppressLint("MissingPermission")
    fun start(onFrame: (FloatArray) -> Unit): Boolean {
        if (running) return true
        val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return false
        val r = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 16000)
            )
        } catch (e: Exception) { DiagLog.e("Mic", "AudioRecord non creato", e); return false }
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); DiagLog.e("Mic", "AudioRecord non inizializzato (permesso?)"); return false }
        try {
            if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(r.audioSessionId)?.also { it.enabled = true }
            if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(r.audioSessionId)?.also { it.enabled = true }
        } catch (e: Exception) { DiagLog.w("Mic", "Effetti audio non disponibili: ${e.message}") }
        rec = r
        running = true
        r.startRecording()
        thread = Thread({
            val s = ShortArray(512)
            while (running) {
                val n = r.read(s, 0, s.size)
                if (n <= 0) { if (n < 0) break else continue }
                val f = FloatArray(n) { s[it] / 32768f }
                try { onFrame(f) } catch (e: Exception) { DiagLog.e("Mic", "Errore elaborazione frame", e) }
            }
        }, "jarvis-mic").also { it.priority = Thread.MAX_PRIORITY; it.start() }
        DiagLog.i("Mic", "Microfono avviato (AEC=${aec != null}, NS=${ns != null})")
        return true
    }

    fun stop() {
        running = false
        try { thread?.join(500) } catch (_: Exception) {}
        try { rec?.stop() } catch (_: Exception) {}
        try { aec?.release(); ns?.release() } catch (_: Exception) {}
        rec?.release(); rec = null; thread = null; aec = null; ns = null
        DiagLog.i("Mic", "Microfono fermato")
    }
}
