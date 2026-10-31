package it.jarvis.assistant.voice

import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.models.ModelCatalog
import it.jarvis.assistant.models.ModelManager
import it.jarvis.assistant.security.SecureKeyStore
import java.nio.ByteBuffer
import kotlin.math.sqrt

data class EnrollResult(val ok: Boolean, val message: String, val suggestedThreshold: Float = 0f)

/**
 * Verifica del parlante (CAM++ via sherpa-onnx). Il profilo vocale è un solo vettore (media di embedding),
 * cifrato nel Keystore, mai esportato né incluso nei backup.
 * Limite noto: modello addestrato su inglese/cinese; l'affidabilità sull'italiano non è garantita e va calibrata.
 */
class SpeakerVerifier(private val models: ModelManager, private val secure: SecureKeyStore) {
    private var ext: SpeakerEmbeddingExtractor? = null
    private val keyName = "voiceprint_v1"

    val modelInstalled get() = models.isInstalled(ModelCatalog.speaker)
    fun hasProfile() = secure.has(keyName)

    @Synchronized
    private fun ensure(): SpeakerEmbeddingExtractor? {
        ext?.let { return it }
        if (!modelInstalled) return null
        return try {
            SpeakerEmbeddingExtractor(config = SpeakerEmbeddingExtractorConfig(
                model = models.path(ModelCatalog.speaker).absolutePath, numThreads = 2)).also { ext = it }
        } catch (e: Throwable) { DiagLog.e("Speaker", "Caricamento fallito", Exception(e)); null }
    }

    @Synchronized
    fun embed(samples: FloatArray): FloatArray? {
        val e = ensure() ?: return null
        if (samples.size < 16000 / 2) return null // meno di 0,5 s: inaffidabile
        val s = e.createStream()
        return try {
            s.acceptWaveform(samples, 16000)
            s.inputFinished()
            if (!e.isReady(s)) null else normalize(e.compute(s))
        } finally { s.release() }
    }

    private fun normalize(v: FloatArray): FloatArray {
        var n = 0.0; for (x in v) n += x * x
        val d = sqrt(n).toFloat().coerceAtLeast(1e-9f)
        return FloatArray(v.size) { v[it] / d }
    }

    private fun cos(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) s += a[i] * b[i]; return s }

    fun enroll(utterances: List<FloatArray>): EnrollResult {
        val embs = utterances.mapNotNull { embed(it) }
        if (embs.size < 3) return EnrollResult(false, "Servono almeno 3 frasi valide (più lunghe di mezzo secondo).")
        val dim = embs[0].size
        val mean = FloatArray(dim)
        for (e in embs) for (i in 0 until dim) mean[i] += e[i] / embs.size
        val centroid = normalize(mean)
        val sims = embs.map { cos(it, centroid) }
        val minSim = sims.min()
        val avg = sims.average().toFloat()
        // Soglia suggerita: sotto la similarità minima osservata con un margine, entro limiti ragionevoli.
        val thr = (minSim - 0.08f).coerceIn(0.35f, 0.7f)
        save(centroid)
        DiagLog.i("Speaker", "Profilo creato: ${embs.size} frasi, sim media %.2f, min %.2f, soglia suggerita %.2f".format(avg, minSim, thr))
        return EnrollResult(true, "Profilo creato (similarità media %.2f).".format(avg), thr)
    }

    private fun save(v: FloatArray) {
        val bb = ByteBuffer.allocate(v.size * 4); v.forEach { bb.putFloat(it) }
        secure.put(keyName, android.util.Base64.encodeToString(bb.array(), android.util.Base64.NO_WRAP))
    }

    private fun load(): FloatArray? {
        val s = secure.get(keyName) ?: return null
        val bb = ByteBuffer.wrap(android.util.Base64.decode(s, android.util.Base64.NO_WRAP))
        return FloatArray(bb.remaining() / 4) { bb.getFloat() }
    }

    /** Similarità coseno con il profilo (null se non verificabile). */
    fun score(samples: FloatArray): Float? {
        val prof = load() ?: return null
        val e = embed(samples) ?: return null
        return cos(e, prof)
    }

    fun delete() { secure.remove(keyName); DiagLog.i("Speaker", "Profilo vocale eliminato") }

    @Synchronized
    fun release() { try { ext?.release() } catch (_: Throwable) {}; ext = null }
}
