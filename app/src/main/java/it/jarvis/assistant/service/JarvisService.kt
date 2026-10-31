package it.jarvis.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import it.jarvis.assistant.JarvisApp
import it.jarvis.assistant.R
import it.jarvis.assistant.graph
import it.jarvis.assistant.ui.MainActivity

/** Servizio in primo piano (tipo microfono): mantiene l'ascolto della parola di attivazione. Basso consumo: un solo modello KWS da 3,3M parametri. */
class JarvisService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            graph.core.stopVoice()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, JarvisService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, JarvisApp.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis in ascolto")
            .setContentText("Di' «Hey Jarvis». Il microfono è attivo.")
            .setContentIntent(open)
            .addAction(0, "Ferma", stop)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (!graph.core.startVoice()) {
            // errore già riportato nella UI; non tenere il microfono impegnato
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() { graph.core.stopVoice(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "it.jarvis.assistant.STOP"
        fun start(ctx: Context) { ctx.startForegroundService(Intent(ctx, JarvisService::class.java)) }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, JarvisService::class.java).setAction(ACTION_STOP)) }
    }
}
