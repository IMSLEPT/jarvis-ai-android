package it.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.app.UiAutomation
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.k2fsa.sherpa.onnx.*
import it.jarvis.assistant.core.JState
import it.jarvis.assistant.device.JarvisAccessibilityService
import it.jarvis.assistant.models.*
import it.jarvis.assistant.service.JarvisService
import it.jarvis.assistant.ui.MainActivity
import it.jarvis.assistant.voice.WakeWord
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** App separata nell'APK di test: simula un campo chat esterno, senza usare account o inviare messaggi reali. */
class TargetChatActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val field = EditText(this).apply { hint = "Messaggio di prova"; id = android.R.id.edit; minLines = 2; requestFocus() }
        setContentView(LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(field) })
    }
}

class NativeRegressionTest {
    @get:Rule val microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val ctx get() = ui.activity
    private val g get() = ctx.graph
    private suspend fun install(spec: ModelSpec) {
        if (g.models.isInstalled(spec)) return
        g.models.install(spec)
        val state = withTimeout(240_000) { g.models.states.first { it[spec.id] is ModelState.Installed || it[spec.id] is ModelState.Failed }[spec.id] }
        assertTrue("${spec.id}: $state", state is ModelState.Installed)
    }
    private fun shell(cmd: String) {
        val automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(cmd)).use { it.readBytes() }
    }
    @After fun stop() = runBlocking { JarvisService.stop(ctx); withTimeout(10_000) { while (g.core.micRunning) delay(100) } }

    @Test fun contactsAndItalianInternationalNumbersAreNormalized() {
        assertEquals("alessandra campisi", it.jarvis.assistant.device.ContactResolver.normalize("Alessàndra Campisi ❤️"))
        for (number in listOf("3331234567", "+39 333 1234567", "393331234567")) assertEquals("393331234567", it.jarvis.assistant.device.ContactResolver.phone(number))
    }

    @Test fun downloadedItalianVoicesGenerateAndPlayWithoutJniCallback() = runBlocking {
        for (voice in ModelCatalog.voices) {
            install(voice)
            g.settings.ttsVoice = voice.id
            repeat(2) {
                val before = g.tts.generatedSamples
                assertTrue("${voice.id}: ${g.tts.failure.value}", withTimeout(90_000) { g.tts.speak("Ciao Alessandro, sono Jarvis. Questa è una prova della voce.", preview = true) })
                assertTrue("Nessun campione per ${voice.id}", g.tts.generatedSamples > before)
            }
        }
        g.tts.release()
    }

    private suspend fun englishWakeAudio(): FloatArray {
        val voice = ModelSpec("test_en", ModelKind.TTS, "English fixture", "Only for emulator tests",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2", true,
            "vits-piper-en_US-lessac-medium", 65, "MIT", false)
        install(voice)
        return withContext(Dispatchers.Default) {
            val dir = g.models.path(voice)
            val model = dir.listFiles { f -> f.name.endsWith(".onnx") }!!.first()
            val tts = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
                model = model.absolutePath, tokens = File(dir,"tokens.txt").absolutePath, dataDir = File(dir,"espeak-ng-data").absolutePath), numThreads = 2)))
            try {
                val audio = tts.generate("Hey Jarvis!", speed = .9f)
                val size = audio.samples.size * 16000 / audio.sampleRate
                val resampled = FloatArray(size) { i -> audio.samples[(i.toLong() * audio.sampleRate / 16000).toInt().coerceAtMost(audio.samples.lastIndex)] }
                FloatArray(16000) + resampled + FloatArray(16000)
            } finally { tts.release() }
        }
    }
    @Test fun dedicatedWakeModelRecognizesRealPcmAndRejectsSilence() = runBlocking {
        val wake = WakeWord(ctx, g.models, g.settings)
        assertTrue("Modello Hey Jarvis non caricato", wake.load())
        try {
            repeat(100) { assertNull(wake.accept(FloatArray(512))) }
            var detected = false
            val samples = englishWakeAudio()
            for (offset in samples.indices step 512) {
                if (wake.accept(samples.copyOfRange(offset, minOf(offset+512,samples.size))) != null) detected = true
            }
            assertTrue("Hey Jarvis non rilevato, picco=${wake.peak.value}", detected)
        } finally { wake.release() }
    }
    @Test fun microphoneAndListeningContinueOutsideMainActivity() = runBlocking {
        install(ModelCatalog.vad)
        shell("appops set it.jarvis.assistant SYSTEM_ALERT_WINDOW allow")
        ui.runOnIdle { JarvisService.start(ctx) }
        withTimeout(15_000) { while (!g.core.micRunning) delay(100) }
        val intent = Intent().setClassName("it.jarvis.assistant.test", "it.jarvis.assistant.TargetChatActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        withTimeout(10_000) { while (g.core.appVisible.value) delay(100) }
        delay(2000)
        assertTrue("Microfono fermato in background", g.core.micRunning)
        assertTrue("Nessun frame audio in background", android.os.SystemClock.elapsedRealtime() - g.core.lastAudioFrame.value < 1500)
        val before = g.core.lastWake.value
        // Il riconoscitore PCM è verificato nel test dedicato; qui si prova la continuazione del servizio e il pannello.
        g.core.listenNow()
        withTimeout(10_000) { while (g.core.lastWake.value <= before) delay(100) }
        assertTrue("Richiamo non attivato fuori dall’app", g.core.lastWake.value > before)
    }
    @Test fun controllerWritesAndVerifiesTextInExternalChat() = runBlocking {
        shell("settings put secure enabled_accessibility_services it.jarvis.assistant/it.jarvis.assistant.device.JarvisAccessibilityService")
        shell("settings put secure accessibility_enabled 1")
        withTimeout(15_000) { while (!JarvisAccessibilityService.isConnected) delay(100) }
        ctx.startActivity(Intent().setClassName("it.jarvis.assistant.test", "it.jarvis.assistant.TargetChatActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        delay(1000)
        g.controller.observe()
        val result = g.controller.typeText("Ciao, questa è soltanto una bozza di prova.", null, false)
        assertTrue(result, result.startsWith("OK:"))
        assertTrue(g.controller.observe().text.contains("questa è soltanto una bozza di prova"))
    }
}
