package it.jarvis.assistant.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import it.jarvis.assistant.diag.DiagLog

/** Un solo lettore. La risorsa viene fermata prima di attendere il thread. */
class Mic {
    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    val isRunning get() = running

    @Synchronized
    @SuppressLint("MissingPermission")
    fun start(onFrame: (FloatArray) -> Unit, onError: () -> Unit): Boolean {
        if (running) return true
        stop()
        try {
            val minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return false
            val r = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 16000))
            rec = r
            check(r.state == AudioRecord.STATE_INITIALIZED) { "Microfono non inizializzato" }
            try {
                if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(r.audioSessionId)?.also { it.enabled = true }
                if (NoiseSuppressor.isAvailable()) ns = NoiseSuppressor.create(r.audioSessionId)?.also { it.enabled = true }
            } catch (e: Exception) { DiagLog.w("Mic", "Effetti audio non disponibili") }
            r.startRecording()
            check(r.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Registrazione non avviata" }
            running = true
            thread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                val s = ShortArray(512)
                var failed = false
                try {
                    while (running) {
                        val n = r.read(s, 0, s.size)
                        if (n < 0) { failed = running; break }
                        if (n > 0) onFrame(FloatArray(n) { s[it] / 32768f })
                    }
                } catch (e: Exception) { failed = running; DiagLog.e("Mic", "Lettura interrotta", e) }
                finally { running = false; if (failed) onError() }
            }, "jarvis-mic").also { it.start() }
            return true
        } catch (e: Exception) {
            DiagLog.e("Mic", "Avvio non riuscito", e)
            stop()
            return false
        }
    }

    @Synchronized
    fun stop() {
        running = false
        try { rec?.stop() } catch (_: Exception) {}
        try { if (thread !== Thread.currentThread()) thread?.join(1000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        try { aec?.release(); ns?.release() } catch (_: Exception) {}
        try { rec?.release() } catch (_: Exception) {}
        rec = null; thread = null; aec = null; ns = null
    }
}
