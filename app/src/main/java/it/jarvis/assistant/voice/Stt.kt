package it.jarvis.assistant.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.models.ModelCatalog
import it.jarvis.assistant.models.ModelManager

/** STT italiano offline: Parakeet TDT 0.6B v3 (int8) via sherpa-onnx. Il modello viene caricato al bisogno e scaricato dalla RAM dopo inattività. */
class SttEngine(private val models: ModelManager) {
    private var rec: OfflineRecognizer? = null
    @Volatile var lastUsed = 0L

    val isInstalled get() = models.isInstalled(ModelCatalog.stt)

    /** Trova il file del componente (preferisce la variante int8) invece di fissare il nome esatto. */
    private fun pick(dir: String, prefix: String): String {
        val files = java.io.File(dir).listFiles { f -> f.name.startsWith(prefix) && f.name.endsWith(".onnx") }?.toList().orEmpty()
        val f = files.firstOrNull { it.name.contains("int8") } ?: files.firstOrNull()
            ?: error("File $prefix*.onnx non trovato in $dir")
        return f.absolutePath
    }

    @Synchronized
    private fun ensure(): OfflineRecognizer {
        rec?.let { return it }
        val dir = models.path(ModelCatalog.stt).absolutePath
        val cfg = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = pick(dir, "encoder"),
                    decoder = pick(dir, "decoder"),
                    joiner = pick(dir, "joiner"),
                ),
                tokens = "$dir/tokens.txt",
                modelType = "nemo_transducer",
                numThreads = 4,
            ),
            decodingMethod = "greedy_search",
        )
        val t0 = System.currentTimeMillis()
        val r = OfflineRecognizer(config = cfg)
        DiagLog.i("STT", "Parakeet caricato in ${System.currentTimeMillis() - t0} ms")
        rec = r
        return r
    }

    @Synchronized
    fun transcribe(samples: FloatArray): String {
        val r = ensure()
        lastUsed = System.currentTimeMillis()
        val s = r.createStream()
        try {
            s.acceptWaveform(samples, 16000)
            val t0 = System.currentTimeMillis()
            r.decode(s)
            val text = r.getResult(s).text.trim()
            DiagLog.i("STT", "Trascrizione in ${System.currentTimeMillis() - t0} ms (${samples.size / 16000f}s audio)")
            return text
        } finally {
            s.release()
            lastUsed = System.currentTimeMillis()
        }
    }

    @Synchronized
    fun unload() {
        try { rec?.release() } catch (_: Throwable) {}
        rec = null
        DiagLog.i("STT", "Modello STT scaricato dalla RAM")
    }

    val isLoaded get() = rec != null
}
