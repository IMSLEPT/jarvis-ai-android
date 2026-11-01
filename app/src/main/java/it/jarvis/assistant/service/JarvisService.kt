package it.jarvis.assistant.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import it.jarvis.assistant.JarvisApp
import it.jarvis.assistant.core.JState
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.graph
import it.jarvis.assistant.ui.MainActivity
import it.jarvis.assistant.ui.hasMic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger

/** Il caricamento JNI non avviene mai sul main thread. Avvio esplicito, stop notificato e niente riavvii ciechi. */
class JarvisService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var initJob: Job? = null
    private lateinit var surface: AssistantSurface
    private var panelRequested = false
    private var commandRequested = false
    private var idleDismiss: Job? = null
    private var generation = 0
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        generation = generations.incrementAndGet()
        surface = AssistantSurface(this)
        scope.launch {
            graph.core.activations.collect {
                panelRequested = true; surface.activate()
                if (!graph.core.appVisible.value) surface.show()
            }
        }
        scope.launch {
            combine(graph.core.state, graph.core.appVisible, graph.core.pendingConfirm) { state, visible, confirm -> Triple(state, visible, confirm) }.collect { (state, visible, confirm) ->
                idleDismiss?.cancel()
                when {
                    state == JState.OFF || state == JState.ERROR -> { surface.hide(); panelRequested = false; if (state == JState.ERROR && initJob?.isActive != true) stopSelf() }
                    visible -> surface.hide()
                    state == JState.ACTING || (state == JState.THINKING && confirm == null) -> surface.hide()
                    panelRequested -> {
                        surface.show()
                        if (state == JState.WAITING) idleDismiss = scope.launch { delay(10_000); surface.hide(); panelRequested = false }
                    }
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (!hasMic()) { graph.core.reportError("Permesso microfono mancante: apri Permessi."); stopSelf(); return START_NOT_STICKY }
        try {
            val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 1, Intent(this, JarvisService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(this, JarvisApp.CHANNEL)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Hey Jarvis è attivo")
                .setContentText("Ascolto sul dispositivo · tocca per aprire · Ferma per spegnere")
                .setContentIntent(open).addAction(0, "Ferma", stop).setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE).build()
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            DiagLog.e("Servizio", "Avvio microfono negato", e)
            graph.core.reportError("Android non consente l’ascolto adesso. Apri Jarvis e riattivalo dal pulsante in alto.")
            stopSelf(); return START_NOT_STICKY
        }
        commandRequested = commandRequested || intent?.getBooleanExtra(LISTEN_NOW, false) == true
        if (initJob?.isActive != true) initJob = scope.launch {
            val ok = withContext(Dispatchers.Default) { graph.core.startVoice() }
            ensureActive()
            if (!ok) { stopSelf(); return@launch }
            if (wakeLock?.isHeld != true) {
                wakeLock = getSystemService(android.os.PowerManager::class.java).newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Jarvis:Voice").apply { setReferenceCounted(false); acquire() }
            }
            if (commandRequested) { commandRequested = false; graph.core.listenNow() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        surface.hide()
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
        val loading = initJob
        scope.cancel()
        graph.appScope.launch {
            loading?.join()
            if (generations.get() == generation) graph.core.stopVoice()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        private val generations = AtomicInteger()
        const val ACTION_STOP = "it.jarvis.assistant.STOP"
        private const val LISTEN_NOW = "listen_now"
        fun start(ctx: Context, listenNow: Boolean = false) {
            try { ctx.startForegroundService(Intent(ctx, JarvisService::class.java).putExtra(LISTEN_NOW, listenNow)) }
            catch (e: Exception) { DiagLog.e("Servizio", "Avvio non consentito", e); ctx.graph.core.reportError("Apri Jarvis per attivare il microfono e verifica i permessi.") }
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, JarvisService::class.java)) }
    }
}
