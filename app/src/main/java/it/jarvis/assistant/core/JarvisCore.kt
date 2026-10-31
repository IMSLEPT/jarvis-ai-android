package it.jarvis.assistant.core

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import it.jarvis.assistant.agent.Agent
import it.jarvis.assistant.agent.AgentIO
import it.jarvis.assistant.agent.Safety
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.voice.CommandVad
import it.jarvis.assistant.voice.Mic
import it.jarvis.assistant.voice.SpeakerVerifier
import it.jarvis.assistant.voice.SttEngine
import it.jarvis.assistant.voice.TtsEngine
import it.jarvis.assistant.voice.WakeWord
import it.jarvis.assistant.memory.MemoryRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

enum class JState { OFF, WAITING, LISTENING, THINKING, ACTING, SPEAKING, ERROR }
data class Msg(val role: String, val text: String, val ts: Long = System.currentTimeMillis())

private class CaptureRequest(val result: CompletableDeferred<FloatArray?>, val softDeadline: Long, val hardDeadline: Long)

/**
 * Orchestratore: microfono → wake word → VAD → (verifica voce) → STT → agente → TTS, con barge-in,
 * conversazione continua a finestra limitata (niente ascolto infinito) e stop immediato.
 */
class JarvisCore(
    private val ctx: Context,
    private val settings: Settings,
    private val wake: WakeWord,
    private val vad: CommandVad,
    private val stt: SttEngine,
    private val tts: TtsEngine,
    private val speaker: SpeakerVerifier,
    private val memory: MemoryRepository,
) : AgentIO {
    private val tag = "Core"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var agent: Agent

    val state = MutableStateFlow(JState.OFF)
    val chat = MutableStateFlow<List<Msg>>(emptyList())
    val status = MutableStateFlow("")
    val level = MutableStateFlow(0f)
    val error = MutableStateFlow<String?>(null)
    /** Domanda di conferma in attesa (la UI mostra Sì/No; si può anche rispondere a voce). */
    val pendingConfirm = MutableStateFlow<String?>(null)

    private val mic = Mic()
    private val frames = Channel<FloatArray>(64, BufferOverflow.DROP_OLDEST)
    private var consumer: Job? = null
    private var housekeeping: Job? = null
    @Volatile private var work: Job? = null
    @Volatile private var capture: CaptureRequest? = null
    @Volatile private var pendingAnswer: CompletableDeferred<String>? = null
    @Volatile private var micOnlyForEnroll = false

    val micRunning get() = mic.isRunning

    // ------------------------------------------------------------ ciclo di vita

    /** Avvia microfono + wake word. Richiede permesso RECORD_AUDIO e modelli KWS/VAD installati. */
    fun startVoice(): Boolean {
        if (mic.isRunning && !micOnlyForEnroll) return true
        error.value = null
        if (!wake.load()) return fail("Modello «Hey Jarvis» non installato: scaricalo in Modelli e voce.")
        if (!vad.load()) return fail("Modello di rilevamento voce non installato: scaricalo in Modelli e voce.")
        micOnlyForEnroll = false
        if (!mic.isRunning && !startMic()) return fail("Impossibile avviare il microfono (permesso concesso?).")
        state.value = JState.WAITING
        DiagLog.i(tag, "In ascolto della parola di attivazione")
        return true
    }

    private fun fail(msg: String): Boolean { error.value = msg; state.value = JState.ERROR; DiagLog.w(tag, msg); return false }

    private fun startMic(): Boolean {
        consumer?.cancel()
        consumer = scope.launch {
            for (f in frames) {
                try { process(f) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Throwable) { DiagLog.e(tag, "Errore nel ciclo audio", Exception(e)) }
            }
        }
        val ok = mic.start { f ->
            var s = 0f; for (x in f) s += x * x
            level.value = (sqrt(s / f.size) * 8f).coerceIn(0f, 1f)
            frames.trySend(f)
        }
        if (housekeeping?.isActive != true) housekeeping = scope.launch { housekeepingLoop() }
        return ok
    }

    fun stopVoice() {
        stopAll()
        mic.stop()
        consumer?.cancel(); consumer = null
        housekeeping?.cancel(); housekeeping = null
        wake.release(); vad.release(); speaker.release()
        state.value = JState.OFF
        level.value = 0f
    }

    /** STOP immediato: ferma voce, agente e qualunque attesa. */
    fun stopAll() {
        tts.stop()
        work?.cancel(); work = null
        capture?.result?.complete(null); capture = null
        pendingAnswer?.cancel(); pendingAnswer = null
        pendingConfirm.value = null
        status.value = ""
        if (state.value != JState.OFF && state.value != JState.ERROR) state.value = if (mic.isRunning) JState.WAITING else JState.OFF
        DiagLog.i(tag, "Stop")
    }

    private suspend fun housekeepingLoop() {
        while (scope.isActive) {
            delay(15_000)
            val idle = state.value == JState.WAITING || state.value == JState.OFF
            if (!idle) continue
            val lim = settings.sttModelAutoUnloadSec * 1000L
            if (stt.isLoaded && System.currentTimeMillis() - stt.lastUsed > lim) { stt.unload(); speaker.release() }
            if (System.currentTimeMillis() - tts.lastUsed > 3 * lim) tts.release()
        }
    }

    // ------------------------------------------------------------ elaborazione audio

    private fun process(frame: FloatArray) {
        val req = capture
        if (req != null) {
            val seg = vad.feed(frame)
            val now = System.currentTimeMillis()
            when {
                seg != null -> { capture = null; req.result.complete(seg) }
                now > req.hardDeadline -> { capture = null; req.result.complete(null) }
                now > req.softDeadline && !vad.speechNow() -> { capture = null; req.result.complete(null) }
            }
            return
        }
        if (!settings.wakeEnabled && !micOnlyForEnroll) return
        val hit = wake.accept(frame) ?: return
        DiagLog.i(tag, "Parola di attivazione: $hit (stato ${state.value})")
        onWake()
    }

    private fun onWake() {
        vibrate()
        tts.stop() // barge-in: interrompe la voce
        val old = work
        work = scope.launch {
            old?.cancelAndJoinSafe()
            conversation()
        }
    }

    private suspend fun Job.cancelAndJoinSafe() { cancel(); try { join() } catch (_: Exception) {} }

    /** Registra un enunciato (VAD). null = timeout. */
    private suspend fun capture(timeoutMs: Long): FloatArray? {
        vad.reset()
        val now = System.currentTimeMillis()
        val d = CompletableDeferred<FloatArray?>()
        val req = CaptureRequest(d, now + timeoutMs, now + timeoutMs + 20_000)
        capture = req
        try { return d.await() } finally { if (capture === req) capture = null }
    }

    private suspend fun transcribe(seg: FloatArray, verify: Boolean): String? = withContext(Dispatchers.Default) {
        if (verify && settings.speakerVerify && speaker.hasProfile()) {
            val sc = speaker.score(seg)
            if (sc == null || sc < settings.speakerThreshold) {
                DiagLog.w(tag, "Voce non riconosciuta (score=${sc?.let { "%.2f".format(it) }} soglia=${settings.speakerThreshold})")
                say("Non riconosco la tua voce, quindi ignoro questo comando.")
                return@withContext null
            }
        }
        if (!stt.isInstalled) { say("Il modello di riconoscimento vocale non è ancora scaricato. Aprilo in Modelli e voce."); return@withContext null }
        status.value = "Trascrivo…"
        try { stt.transcribe(seg).ifBlank { null } } catch (e: Throwable) {
            DiagLog.e(tag, "STT fallito", Exception(e)); say("Ho avuto un problema col riconoscimento vocale."); null
        }
    }

    private suspend fun conversation() {
        try {
            var first = true
            while (scope.isActive) {
                state.value = JState.LISTENING
                status.value = if (first) "Ti ascolto…" else "Ti ascolto ancora per qualche secondo…"
                val timeout = if (first) 7_000L else settings.followUpSeconds * 1000L
                val seg = capture(timeout) ?: break
                first = false
                state.value = JState.THINKING
                val text = transcribe(seg, verify = true) ?: continue
                handle(text)
                if (!settings.wakeEnabled) break
            }
        } finally {
            if (state.value != JState.OFF) state.value = if (mic.isRunning) JState.WAITING else JState.OFF
            status.value = ""
        }
    }

    private val stopWords = Regex("(?i)^\\s*(stop|basta|annulla|fermati|ferma|lascia stare|niente|nulla)[.!\\s]*$")

    private suspend fun handle(text: String) {
        addChat("user", text)
        memory.log("user", text)
        if (stopWords.matches(text)) { say("Va bene."); return }
        state.value = JState.THINKING
        status.value = "Ci penso…"
        val res = try { agent.run(text, this) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            DiagLog.e(tag, "Agente", e); it.jarvis.assistant.agent.AgentResult(false, "Si è verificato un errore interno: ${e.message}", 0)
        }
        memory.log("jarvis", res.message)
        say(res.message)
    }

    /** Comando digitato. Se Jarvis sta aspettando una risposta, la usa come risposta. */
    fun submitText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val pa = pendingAnswer
        if (pa != null && pa.isActive) { addChat("user", t); pa.complete(t); return }
        tts.stop()
        val old = work
        work = scope.launch {
            old?.cancelAndJoinSafe()
            try { handle(t) } finally {
                state.value = if (mic.isRunning) JState.WAITING else JState.OFF; status.value = ""
            }
        }
    }

    fun answerConfirm(yes: Boolean) { pendingAnswer?.complete(if (yes) "sì" else "no") }

    // ------------------------------------------------------------ AgentIO

    private fun addChat(role: String, text: String) { chat.update { (it + Msg(role, text)).takeLast(80) } }

    override suspend fun say(text: String) {
        if (text.isBlank()) return
        addChat("jarvis", text)
        val prev = state.value
        state.value = JState.SPEAKING
        status.value = ""
        try { tts.speak(text) } finally { if (state.value == JState.SPEAKING) state.value = if (prev == JState.SPEAKING) JState.THINKING else prev }
    }

    override suspend fun ask(question: String): String? {
        say(question)
        return listenForAnswer(9_000)
    }

    private suspend fun listenForAnswer(timeoutMs: Long): String? = coroutineScope {
        val typed = CompletableDeferred<String>()
        pendingAnswer = typed
        state.value = JState.LISTENING
        status.value = "Aspetto la tua risposta…"
        val voice = async { if (!mic.isRunning) null else capture(timeoutMs)?.let { transcribe(it, verify = true) } }
        try {
            select<String?> {
                typed.onAwait { it }
                voice.onAwait { it }
            }
        } finally {
            pendingAnswer = null
            capture?.result?.complete(null); capture = null
            voice.cancel()
            state.value = JState.THINKING
        }
    }

    override suspend fun confirm(summary: String): Boolean {
        pendingConfirm.value = summary
        try {
            say("$summary Rispondi sì o no.")
            repeat(2) {
                val a = listenForAnswer(9_000)
                Safety.parseYesNo(a)?.let { return it }
                if (a != null) say("Non ho capito: sì o no?")
            }
            return false // nessuna risposta chiara = non confermato
        } finally { pendingConfirm.value = null }
    }

    override fun status(text: String) {
        status.value = text
        state.value = if (text.startsWith("Osservo") || text.startsWith("Interrogo") || text.startsWith("Ci penso")) JState.THINKING else JState.ACTING
    }

    // ------------------------------------------------------------ registrazione per l'enrollment vocale

    suspend fun recordForEnrollment(timeoutMs: Long = 9_000): FloatArray? {
        if (!vad.load()) return null
        if (!mic.isRunning) {
            micOnlyForEnroll = true
            if (!startMic()) { micOnlyForEnroll = false; return null }
        }
        return capture(timeoutMs)
    }

    fun endEnrollmentSession() {
        if (micOnlyForEnroll) { mic.stop(); consumer?.cancel(); consumer = null; micOnlyForEnroll = false; level.value = 0f }
    }

    private fun vibrate() {
        try {
            val v = (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }
}
