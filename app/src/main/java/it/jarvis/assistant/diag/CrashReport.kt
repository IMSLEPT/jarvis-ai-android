package it.jarvis.assistant.diag

import android.content.Context
import java.io.File
import java.time.Instant

/** Conserva solo tipo dell'eccezione e frame dell'app, mai messaggi, audio, chiavi o testo utente. */
object CrashReport {
    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = generateSequence(error) { it.cause }.take(5).joinToString("\n") { t ->
                    t.javaClass.name + "\n" + t.stackTrace.filter { it.className.startsWith("it.jarvis.") || it.className.startsWith("com.k2fsa.") }.take(12).joinToString("\n")
                }
                File(ctx.filesDir, "last-crash.txt").writeText("${Instant.now()} · ${thread.name}\n$trace")
            } catch (_: Exception) { }
            if (previous != null) previous.uncaughtException(thread, error)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(1) }
        }
    }
    fun last(ctx: Context): String? = try { File(ctx.filesDir, "last-crash.txt").takeIf { it.isFile }?.readText()?.take(6000) } catch (_: Exception) { null }
}
