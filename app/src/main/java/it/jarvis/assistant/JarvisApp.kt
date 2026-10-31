package it.jarvis.assistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import it.jarvis.assistant.agent.Agent
import it.jarvis.assistant.ai.BackupZone
import it.jarvis.assistant.ai.LlmClient
import it.jarvis.assistant.ai.ProviderStore
import it.jarvis.assistant.device.AndroidController
import it.jarvis.assistant.device.AppLauncher
import it.jarvis.assistant.core.JarvisCore
import it.jarvis.assistant.core.Settings
import it.jarvis.assistant.memory.JarvisDb
import it.jarvis.assistant.memory.MemoryRepository
import it.jarvis.assistant.models.ModelManager
import it.jarvis.assistant.security.SecureKeyStore
import it.jarvis.assistant.voice.CommandVad
import it.jarvis.assistant.voice.SpeakerVerifier
import it.jarvis.assistant.voice.SttEngine
import it.jarvis.assistant.voice.TtsEngine
import it.jarvis.assistant.voice.WakeWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Contenitore delle dipendenze (iniezione manuale). */
class AppGraph(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(app)
    val secure = SecureKeyStore(app)
    val providers = ProviderStore(settings, secure)
    val backup = BackupZone(app, settings, providers, secure)
    val llm = LlmClient(providers)
    val models = ModelManager(app, appScope)
    val db = JarvisDb.create(app)
    val memory = MemoryRepository(db, settings)
    val launcher = AppLauncher(app)
    val controller = AndroidController(app, launcher)
    val wake = WakeWord(app, models, settings)
    val vad = CommandVad(models, settings)
    val stt = SttEngine(models)
    val tts = TtsEngine(models, settings)
    val speaker = SpeakerVerifier(models, secure)
    val core = JarvisCore(app, settings, wake, vad, stt, tts, speaker, memory)

    init {
        core.agent = Agent(settings, llm, controller, memory) { llm }
        providers.onChanged = { appScope.launch(Dispatchers.IO) { backup.autoWrite() } }
    }
}

class JarvisApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Jarvis in ascolto", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mostra che Jarvis sta ascoltando la parola di attivazione"
            setShowBadge(false)
        })
    }

    companion object { const val CHANNEL = "jarvis_listening" }
}

val android.content.Context.graph: AppGraph get() = (applicationContext as JarvisApp).graph
