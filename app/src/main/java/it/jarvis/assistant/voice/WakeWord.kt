package it.jarvis.assistant.voice

import android.content.Context
import it.jarvis.assistant.core.Settings
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.models.ModelManager
import kotlinx.coroutines.flow.MutableStateFlow
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** openWakeWord: PCM16 → mel (8×32) → embedding (96) → classificatore dedicato Hey Jarvis. Tutto locale. */
class WakeWord(private val ctx: Context, private val models: ModelManager, private val settings: Settings) {
    private var mel: Interpreter? = null
    private var embedding: Interpreter? = null
    private var detector: Interpreter? = null
    private val pending = FloatArray(1280)
    private var pendingSize = 0
    private val overlap = FloatArray(480)
    private val melFrames = FloatArray(76 * 32) { 1f }
    private var features = FloatArray(16 * 96)
    private var steps = 0
    private var lastHit = 0L
    val score = MutableStateFlow(0f)
    val peak = MutableStateFlow(0f)
    val isLoaded get() = detector != null

    @Synchronized fun load(): Boolean {
        if (isLoaded) return true
        return try {
            fun open(name: String): Interpreter {
                val file = File(ctx.cacheDir, "wake-v051/$name.tflite")
                file.parentFile!!.mkdirs()
                ctx.assets.open("wake/$name.tflite").use { input -> file.outputStream().use { input.copyTo(it) } }
                return Interpreter(file, Interpreter.Options().setNumThreads(1))
            }
            mel = open("melspectrogram").apply { resizeInput(0, intArrayOf(1, 1760)); allocateTensors() }
            embedding = open("embedding_model").apply { allocateTensors() }
            detector = open("hey_jarvis_v0.1").apply { allocateTensors() }
            val shape = detector!!.getInputTensor(0).shape()
            require(shape.last() == 96) { "Formato del rilevatore Hey Jarvis non valido" }
            features = FloatArray(shape.reduce { a,b -> a*b })
            reset()
            DiagLog.i("WakeWord", "openWakeWord Hey Jarvis pronto (locale, modello dedicato)")
            true
        } catch (e: Exception) { release(); DiagLog.e("WakeWord", "Caricamento rilevatore", e); false }
        catch (e: LinkageError) { release(); DiagLog.e("WakeWord", "Runtime rilevatore", e); false }
    }
    private fun run(model: Interpreter, input: FloatArray): FloatArray {
        val inBuf = ByteBuffer.allocateDirect(input.size * 4).order(ByteOrder.nativeOrder())
        inBuf.asFloatBuffer().put(input)
        val out = ByteBuffer.allocateDirect(model.getOutputTensor(0).numBytes()).order(ByteOrder.nativeOrder())
        model.run(inBuf, out)
        return FloatArray(out.capacity()/4).also { out.rewind(); out.asFloatBuffer().get(it) }
    }
    @Synchronized fun accept(frame: FloatArray): String? {
        if (!isLoaded) return null
        var detected = false
        for (sample in frame) {
            pending[pendingSize++] = (sample.coerceIn(-1f,1f) * 32767f).toInt().toFloat()
            if (pendingSize == pending.size) {
                pendingSize = 0
                val input = FloatArray(1760)
                overlap.copyInto(input); pending.copyInto(input, 480)
                pending.copyInto(overlap, 0, 800, 1280)
                val m = run(mel!!, input)
                require(m.size == 8*32) { "Mel frame inatteso: ${m.size}" }
                melFrames.copyInto(melFrames, 0, 8*32)
                for (i in m.indices) melFrames[melFrames.size - m.size + i] = m[i] / 10f + 2f
                val feature = run(embedding!!, melFrames)
                require(feature.size == 96)
                features.copyInto(features, 0, 96)
                feature.copyInto(features, features.size - 96)
                val value = run(detector!!, features).first().coerceIn(0f,1f)
                steps++
                score.value = if (steps < 5) 0f else value
                peak.value = maxOf(peak.value, score.value)
                if (score.value >= settings.hotwordThreshold && System.currentTimeMillis() - lastHit > 2000) {
                    lastHit = System.currentTimeMillis(); detected = true
                }
            }
        }
        return if (detected) "HEY_JARVIS" else null
    }
    @Synchronized fun reset() {
        pendingSize = 0; pending.fill(0f); overlap.fill(0f); melFrames.fill(1f); features.fill(0f); steps = 0; score.value = 0f
        embedding?.let { e -> val silence = run(e, melFrames); for (i in features.indices) features[i] = silence[i % 96] }
    }
    @Synchronized fun release() {
        mel?.close(); embedding?.close(); detector?.close()
        mel = null; embedding = null; detector = null; pendingSize = 0; score.value = 0f
    }
}
