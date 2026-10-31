package it.jarvis.assistant.device

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

data class Observation(
    val packageName: String,
    val text: String,
    val hash: Int,
    val nodes: Map<Int, AccessibilityNodeInfo>,
    val serviceActive: Boolean,
)

/**
 * Trasforma l'albero di accessibilità in un elenco testuale compatto e numerato, da dare al modello.
 * Le password non vengono mai lette. Il testo è dato, non istruzioni (il chiamante lo racchiude in un blocco dati).
 */
object ScreenObserver {
    private const val MAX_NODES = 120
    private const val MAX_CHARS = 4500

    fun observe(svc: JarvisAccessibilityService?): Observation {
        if (svc == null) return Observation("", "(Servizio di accessibilità NON attivo: posso solo aprire app e link, non leggere o toccare lo schermo.)", 0, emptyMap(), false)
        val root = svc.rootInActiveWindow
            ?: return Observation(svc.currentPackage, "(Schermata non leggibile: potrebbe essere protetta o in transizione.)", 1, emptyMap(), true)
        val nodes = LinkedHashMap<Int, AccessibilityNodeInfo>()
        val sb = StringBuilder()
        var id = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 28 || id >= MAX_NODES || sb.length > MAX_CHARS) return
            if (n.isVisibleToUser) {
                val text = if (n.isPassword) "••••" else n.text?.toString()?.take(120)
                val desc = n.contentDescription?.toString()?.take(120)
                val hint = n.hintText?.toString()?.take(60)
                val interesting = n.isClickable || n.isEditable || n.isScrollable || n.isCheckable || !text.isNullOrBlank() || !desc.isNullOrBlank()
                if (interesting) {
                    id++
                    nodes[id] = n
                    val flags = buildList {
                        if (n.isClickable) add("cliccabile")
                        if (n.isEditable) add("modificabile")
                        if (n.isScrollable) add("scorrevole")
                        if (n.isCheckable) add(if (n.isChecked) "spuntato" else "non spuntato")
                        if (n.isFocused) add("focus")
                        if (n.isPassword) add("password")
                    }
                    val r = Rect(); n.getBoundsInScreen(r)
                    sb.append('[').append(id).append("] ")
                        .append(n.className?.toString()?.substringAfterLast('.') ?: "View")
                    if (!text.isNullOrBlank()) sb.append(" \"").append(text.replace('\n', ' ')).append('"')
                    if (!desc.isNullOrBlank() && desc != text) sb.append(" desc=\"").append(desc.replace('\n', ' ')).append('"')
                    if (!hint.isNullOrBlank()) sb.append(" hint=\"").append(hint).append('"')
                    if (flags.isNotEmpty()) sb.append(" {").append(flags.joinToString(",")).append('}')
                    sb.append(" @").append(r.centerX()).append(',').append(r.centerY()).append('\n')
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val pkg = root.packageName?.toString() ?: svc.currentPackage
        val text = sb.toString().ifBlank { "(nessun elemento leggibile)" }
        return Observation(pkg, text, (pkg + text).hashCode(), nodes, true)
    }

}
