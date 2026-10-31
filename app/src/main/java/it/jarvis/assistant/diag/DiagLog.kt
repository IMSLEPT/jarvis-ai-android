package it.jarvis.assistant.diag

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogLine(val time: String, val level: Char, val tag: String, val msg: String)

/** Log diagnostico in memoria (ultime 600 righe). Non registra mai chiavi API né contenuto integrale delle schermate. */
object DiagLog {
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.ITALY)
    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines

    private fun add(level: Char, tag: String, msg: String) {
        val l = LogLine(fmt.format(Date()), level, tag, msg.take(400))
        synchronized(this) { _lines.value = (_lines.value + l).takeLast(600) }
        when (level) {
            'E' -> Log.e("Jarvis/$tag", msg)
            'W' -> Log.w("Jarvis/$tag", msg)
            else -> Log.i("Jarvis/$tag", msg)
        }
    }

    fun i(tag: String, msg: String) = add('I', tag, msg)
    fun w(tag: String, msg: String) = add('W', tag, msg)
    fun e(tag: String, msg: String, t: Throwable? = null) =
        add('E', tag, if (t != null) "$msg: ${t.javaClass.simpleName} ${t.message}" else msg)

    fun clear() { _lines.value = emptyList() }
    fun asText(): String = _lines.value.joinToString("\n") { "${it.time} ${it.level}/${it.tag}: ${it.msg}" }
}
