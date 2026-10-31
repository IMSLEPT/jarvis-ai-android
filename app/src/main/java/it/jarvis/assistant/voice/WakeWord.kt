package it.jarvis.assistant.voice

import android.content.Context
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import it.jarvis.assistant.core.Settings
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.models.ModelCatalog
import it.jarvis.assistant.models.ModelManager
import java.io.File

/**
 * Wake word «Hey Jarvis» con lo spotter di parole chiave di sherpa-onnx (open vocabulary, 100% on-device).
 * Le varianti sono in assets/keywords_jarvis.txt (pre-tokenizzate con il BPE del modello).
 */
class WakeWord(private val ctx: Context, private val models: ModelManager, private val settings: Settings) {
    private var kws: KeywordSpotter? = null
    private var stream: OnlineStream? = null
    private var lastHit = 0L
    private var loadedThr = -1f

    val isLoaded get() = kws != null

    @Synchronized
    fun load(): Boolean {
        if (kws != null && loadedThr == settings.wakeThreshold) return true
        release()
        val spec = ModelCatalog.kws
        if (!models.isInstalled(spec)) return false
        return try {
            val dir = models.path(spec).absolutePath
            val kwFile = File(models.path(spec), "keywords_jarvis.txt")
            ctx.assets.open("keywords_jarvis.txt").use { i -> kwFile.outputStream().use { o -> i.copyTo(o) } }
            val cfg = KeywordSpotterConfig(
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$dir/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                        decoder = "$dir/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                        joiner = "$dir/joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
                    ),
                    tokens = "$dir/tokens.txt",
                    modelType = "zipformer2",
                    numThreads = 1,
                ),
                keywordsFile = kwFile.absolutePath,
                keywordsScore = settings.wakeScore,
                keywordsThreshold = settings.wakeThreshold,
            )
            kws = KeywordSpotter(config = cfg)
            stream = kws!!.createStream()
            loadedThr = settings.wakeThreshold
            DiagLog.i("WakeWord", "Caricato (soglia ${settings.wakeThreshold})")
            true
        } catch (e: Throwable) {
            DiagLog.e("WakeWord", "Caricamento fallito", e as? Exception ?: Exception(e.toString()))
            kws = null; stream = null
            false
        }
    }

    /** @return la parola rilevata o null. */
    fun accept(frame: FloatArray): String? {
        val k = kws ?: return null
        val s = stream ?: return null
        s.acceptWaveform(frame, 16000)
        var hit: String? = null
        while (k.isReady(s)) {
            k.decode(s)
            val r = k.getResult(s)
            if (r.keyword.isNotEmpty()) { hit = r.keyword; k.reset(s) }
        }
        if (hit != null) {
            val now = System.currentTimeMillis()
            if (now - lastHit < 1500) return null // anti-rimbalzo
            lastHit = now
        }
        return hit
    }

    fun reset() { val k = kws; val s = stream; if (k != null && s != null) k.reset(s) }

    @Synchronized
    fun release() {
        try { stream?.release() } catch (_: Throwable) {}
        try { kws?.release() } catch (_: Throwable) {}
        stream = null; kws = null; loadedThr = -1f
    }
}
