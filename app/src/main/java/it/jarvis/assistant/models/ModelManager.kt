package it.jarvis.assistant.models

import android.content.Context
import it.jarvis.assistant.ai.await
import it.jarvis.assistant.ai.httpClient
import it.jarvis.assistant.diag.DiagLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

enum class ModelKind { KWS, VAD, STT, SPEAKER, TTS }

data class ModelSpec(
    val id: String,
    val kind: ModelKind,
    val title: String,
    val description: String,
    val url: String,
    /** true = archivio tar.bz2 da estrarre; false = singolo file */
    val archive: Boolean,
    /** cartella estratta (archivio) o nome file (singolo) */
    val localName: String,
    val approxMb: Int,
    val license: String,
    val required: Boolean,
    val bundled: Boolean = false,
)

sealed interface ModelState {
    data object Missing : ModelState
    data class Downloading(val done: Long, val total: Long) : ModelState
    data object Extracting : ModelState
    data object Installed : ModelState
    data class Failed(val message: String) : ModelState
}

object ModelCatalog {
    private const val GH = "https://github.com/k2-fsa/sherpa-onnx/releases/download"

    val kws = ModelSpec(
        "kws", ModelKind.KWS, "Hey Jarvis · rilevatore integrato",
        "openWakeWord, modello dedicato Hey Jarvis eseguito sul telefono. Già incluso: non serve scaricare il vecchio modello inglese Zipformer.",
        "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/hey_jarvis_v0.1.tflite", false,
        "hey_jarvis_v0.1.tflite", 3, "CC BY-NC-SA 4.0 / Apache-2.0", true, bundled = true
    )
    val vad = ModelSpec(
        "vad", ModelKind.VAD, "Rilevamento voce (Silero VAD)",
        "Rileva inizio e fine del comando.", "$GH/asr-models/silero_vad.onnx", false, "silero_vad.onnx", 1, "MIT", true
    )
    val stt = ModelSpec(
        "stt", ModelKind.STT, "Riconoscimento vocale italiano (Parakeet TDT 0.6B v3)",
        "NVIDIA Parakeet TDT 0.6B v3 int8, multilingue (italiano incluso). Il più accurato tra gli open source su CPU/mobile; ~640 MB su disco.",
        "$GH/asr-models/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2", true,
        "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8", 487, "CC-BY-4.0", true
    )
    val speaker = ModelSpec(
        "spk", ModelKind.SPEAKER, "Riconoscimento della tua voce (3D-Speaker CAM++)",
        "Modello di verifica del parlante (addestrato su inglese/cinese: affidabilità su italiano da verificare). Facoltativo.",
        "$GH/speaker-recongition-models/3dspeaker_speech_campplus_sv_zh_en_16k-common_advanced.onnx", false,
        "3dspeaker_campplus.onnx", 28, "Apache-2.0", false
    )

    private fun piper(id: String, name: String, mb: Int, desc: String) = ModelSpec(
        "tts_$id", ModelKind.TTS, "Voce italiana «$name» (Piper)", desc,
        "$GH/tts-models/vits-piper-it_IT-$id.tar.bz2", true, "vits-piper-it_IT-$id", mb, "MIT (Piper) / licenza del dataset della voce", false
    )
    val voices = listOf(
        piper("paola-medium", "Paola", 67, "Voce femminile, qualità media, veloce (consigliata per iniziare)."),
        piper("miro-high", "Miro", 67, "Voce maschile, qualità alta."),
        piper("dii-high", "Dii", 67, "Voce, qualità alta."),
        piper("riccardo-x_low", "Riccardo (leggera)", 26, "Voce maschile molto leggera, qualità inferiore."),
    )

    val all: List<ModelSpec> = listOf(kws, vad, stt, speaker) + voices
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Scarica (con ripresa) ed estrae i modelli in files/models. Nessun modello è incluso nell'APK. */
class ModelManager(private val ctx: Context, private val scope: CoroutineScope) {
    private val root = File(ctx.filesDir, "models").apply { mkdirs() }
    private val _states = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val states: StateFlow<Map<String, ModelState>> = _states
    private val jobs = mutableMapOf<String, Job>()

    init { refresh() }

    fun refresh() {
        _states.value = ModelCatalog.all.associate { it.id to (if (isInstalled(it)) ModelState.Installed else _states.value[it.id].takeIf { s -> s is ModelState.Downloading || s is ModelState.Extracting || s is ModelState.Failed } ?: ModelState.Missing) }
    }

    private fun marker(s: ModelSpec) = File(root, s.localName + ".ok")
    fun path(s: ModelSpec): File = File(root, s.localName)
    private val bundledWake by lazy { ctx.assets.list("wake")?.toSet()?.containsAll(setOf("melspectrogram.tflite", "embedding_model.tflite", "hey_jarvis_v0.1.tflite")) == true }
    fun isInstalled(s: ModelSpec) = if (s.bundled) bundledWake else marker(s).exists() && path(s).exists()
    fun isInstalled(id: String) = ModelCatalog.byId(id)?.let(::isInstalled) ?: false

    private fun set(id: String, st: ModelState) { _states.update { it + (id to st) } }

    fun install(spec: ModelSpec) {
        if (spec.bundled) { set(spec.id, if (isInstalled(spec)) ModelState.Installed else ModelState.Failed("Rilevatore integrato mancante: aggiorna l’app.")); return }
        if (jobs[spec.id]?.isActive == true) return
        jobs[spec.id] = scope.launch(Dispatchers.IO) {
            try {
                download(spec)
                coroutineContext.ensureActive()
                if (spec.archive) {
                    set(spec.id, ModelState.Extracting)
                    extract(spec)
                } else {
                    val part = File(root, spec.localName + ".part")
                    path(spec).delete()
                    check(part.renameTo(path(spec))) { "Rinomina fallita" }
                }
                marker(spec).writeText("ok")
                set(spec.id, ModelState.Installed)
                DiagLog.i("Modelli", "${spec.title} installato")
            } catch (e: kotlinx.coroutines.CancellationException) {
                set(spec.id, ModelState.Missing); throw e
            } catch (e: Exception) {
                DiagLog.e("Modelli", "Errore ${spec.id}", e)
                set(spec.id, ModelState.Failed(e.message ?: e.javaClass.simpleName))
            }
        }
    }

    fun cancel(spec: ModelSpec) { jobs[spec.id]?.cancel(); set(spec.id, ModelState.Missing) }

    fun remove(spec: ModelSpec) {
        if (spec.bundled) return
        jobs[spec.id]?.cancel()
        marker(spec).delete()
        path(spec).deleteRecursively()
        File(root, spec.localName + ".part").delete()
        File(root, spec.localName + ".tar.bz2").delete()
        set(spec.id, ModelState.Missing)
    }

    private suspend fun download(spec: ModelSpec) = withContext(Dispatchers.IO) {
        val part = File(root, if (spec.archive) spec.localName + ".tar.bz2" else spec.localName + ".part")
        var have = if (part.exists()) part.length() else 0L
        val rb = Request.Builder().url(spec.url)
        if (have > 0) rb.header("Range", "bytes=$have-")
        httpClient.newBuilder().readTimeout(java.time.Duration.ofSeconds(60)).build().newCall(rb.build()).await().use { r ->
            if (r.code == 416) {
                val expected = r.header("Content-Range")?.substringAfter("*/", "")?.toLongOrNull()
                check(expected != null && expected > 0 && part.length() == expected) { "Download parziale non valido: elimina il modello e riprova." }
                return@withContext
            }
            check(r.isSuccessful) { "Download fallito: HTTP ${r.code}" }
            val resumed = r.code == 206
            if (!resumed) have = 0L
            val total = (r.body?.contentLength() ?: -1L).let { if (it > 0) it + have else -1L }
            val body = r.body ?: error("Risposta vuota")
            RandomAccessFile(part, "rw").use { raf ->
                if (resumed) raf.seek(have) else raf.setLength(0)
                val buf = ByteArray(256 * 1024)
                var done = have
                var lastUi = 0L
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        raf.write(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - lastUi > 300) { set(spec.id, ModelState.Downloading(done, total)); lastUi = now }
                    }
                }
                if (total > 0) check(done == total) { "Download incompleto ($done/$total): riprova per riprendere" }
            }
        }
    }

    private suspend fun extract(spec: ModelSpec) {
        val archive = File(root, spec.localName + ".tar.bz2")
        val target = path(spec)
        target.deleteRecursively()
        val canonicalRoot = root.canonicalPath
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream(), 1 shl 16))).use { tar ->
            while (true) {
                kotlin.coroutines.coroutineContext.ensureActive()
                val e = tar.nextEntry ?: break
                val out = File(root, e.name)
                if (!out.canonicalPath.startsWith(canonicalRoot + File.separator)) continue // anti path-traversal
                if (e.isSymbolicLink || e.isLink) continue
                if (e.isDirectory) { out.mkdirs(); continue }
                out.parentFile?.mkdirs()
                FileOutputStream(out).use { tar.copyTo(it, 1 shl 16) }
            }
        }
        check(target.exists()) { "Cartella attesa non trovata dopo l'estrazione: ${spec.localName}" }
        archive.delete()
    }
}
