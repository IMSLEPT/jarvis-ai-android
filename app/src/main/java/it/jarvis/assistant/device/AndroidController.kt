package it.jarvis.assistant.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import it.jarvis.assistant.diag.DiagLog
import kotlinx.coroutines.delay
import java.net.URLEncoder

/** Esecutore delle azioni sul telefono. Ogni metodo restituisce una descrizione testuale del risultato per l'agente. */
class AndroidController(private val ctx: Context, val launcher: AppLauncher) {
    private var last: Observation? = null

    private val svc get() = JarvisAccessibilityService.instance

    fun observe(): Observation = ScreenObserver.observe(svc).also { last = it }

    fun currentLabelOf(id: Int): String? = last?.nodes?.get(id)?.let { n ->
        listOfNotNull(n.text?.toString(), n.contentDescription?.toString()).joinToString(" / ").ifBlank { null }
    }

    fun isPasswordNode(id: Int) = last?.nodes?.get(id)?.isPassword == true

    suspend fun screenshot(): String? = svc?.screenshotJpegB64()

    suspend fun waitIdle(timeoutMs: Long = 3500, quietMs: Long = 450) {
        val s = svc ?: run { delay(600); return }
        val start = System.currentTimeMillis()
        delay(250)
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (System.currentTimeMillis() - s.lastEventTime >= quietMs) return
            delay(100)
        }
    }

    // ---------- azioni ----------

    fun openApp(name: String): String {
        val app = launcher.find(name) ?: return "ERRORE: app «$name» non trovata tra quelle installate."
        return if (launcher.open(app)) "OK: aperta ${app.label} (${app.pkg})" else "ERRORE: impossibile aprire ${app.label}."
    }

    fun openUrl(url: String): String =
        if (launcher.openUrl(url)) "OK: aperto $url" else "ERRORE: URL non valido o non apribile (solo http/https)."

    fun bookingSearch(dest: String, checkin: String?, checkout: String?, adults: Int): String {
        val sb = StringBuilder("https://www.booking.com/searchresults.it.html?ss=").append(URLEncoder.encode(dest, "UTF-8"))
        if (!checkin.isNullOrBlank()) sb.append("&checkin=").append(checkin)
        if (!checkout.isNullOrBlank()) sb.append("&checkout=").append(checkout)
        sb.append("&group_adults=").append(adults.coerceIn(1, 10)).append("&no_rooms=1&group_children=0")
        val pkg = launcher.find("booking")?.pkg
        return if (launcher.openUrl(sb.toString(), pkg)) "OK: ricerca Booking aperta per «$dest». Leggi i risultati dallo schermo. NON prenotare." else "ERRORE: impossibile aprire la ricerca."
    }

    fun whatsappMessage(phone: String, text: String): String {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 8) return "ERRORE: numero non valido (usa formato internazionale, es. 39333...)."
        val url = "https://wa.me/$digits?text=" + URLEncoder.encode(text, "UTF-8")
        val pkg = launcher.find("whatsapp")?.pkg
        return if (launcher.openUrl(url, pkg)) "OK: chat WhatsApp aperta con bozza già scritta. Per inviare serve toccare «Invia» (richiede conferma)." else "ERRORE: WhatsApp non disponibile."
    }

    fun click(id: Int): String {
        val s = svc ?: return "ERRORE: servizio di accessibilità non attivo."
        val node = last?.nodes?.get(id) ?: return "ERRORE: elemento [$id] inesistente. Rileggi lo schermo."
        var n: AccessibilityNodeInfo? = node
        var hops = 0
        while (n != null && !n.isClickable && hops < 5) { n = n.parent; hops++ }
        if (n != null && n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "OK: toccato [$id]"
        // fallback: tocco sul centro dei limiti reali dell'elemento (mai coordinate inventate)
        val r = JarvisAccessibilityService.boundsOf(node)
        return if (r.width() > 0 && s.tapAt(r.exactCenterX(), r.exactCenterY())) "OK: toccato [$id] (gesto sui limiti dell'elemento)" else "ERRORE: tocco su [$id] non riuscito."
    }

    fun clickText(text: String): String {
        val l = last ?: return "ERRORE: nessuna osservazione."
        val q = text.lowercase()
        val hit = l.nodes.entries.firstOrNull { (_, n) ->
            n.text?.toString()?.lowercase() == q || n.contentDescription?.toString()?.lowercase() == q
        } ?: l.nodes.entries.firstOrNull { (_, n) ->
            n.text?.toString()?.lowercase()?.contains(q) == true || n.contentDescription?.toString()?.lowercase()?.contains(q) == true
        } ?: return "ERRORE: nessun elemento con testo «$text»."
        return click(hit.key)
    }

    fun typeText(text: String, id: Int?, submit: Boolean): String {
        val s = svc ?: return "ERRORE: servizio di accessibilità non attivo."
        var node: AccessibilityNodeInfo? = id?.let { last?.nodes?.get(it) }
        if (node == null) node = s.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (node == null) node = last?.nodes?.values?.firstOrNull { it.isEditable }
        if (node == null) return "ERRORE: nessun campo di testo trovato. Tocca prima il campo."
        if (node.isPassword) return "RIFIUTATO: non scrivo nei campi password."
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        var ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) { // fallback: incolla dagli appunti
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("jarvis", text))
            ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        if (!ok) return "ERRORE: scrittura non riuscita."
        if (submit) node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        return "OK: scritto${if (submit) " e inviato dalla tastiera" else ""}"
    }

    fun scroll(direction: String): String {
        val s = svc ?: return "ERRORE: servizio di accessibilità non attivo."
        val sc = last?.nodes?.values?.firstOrNull { it.isScrollable }
        val fwd = direction.lowercase() in setOf("giu", "giù", "down", "avanti")
        if (sc != null) {
            val a = if (fwd) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (sc.performAction(a)) return "OK: scorso ${if (fwd) "in basso" else "in alto"}"
        }
        val (w, h) = s.screenSize()
        val ok = if (fwd) s.swipe(w / 2f, h * 0.7f, w / 2f, h * 0.3f) else s.swipe(w / 2f, h * 0.3f, w / 2f, h * 0.7f)
        return if (ok) "OK: scorso con gesto" else "ERRORE: scorrimento non riuscito."
    }

    fun back(): String = if (svc?.back() == true) "OK: indietro" else "ERRORE: indietro non riuscito."
    fun home(): String = if (svc?.home() == true) "OK: home" else "ERRORE: home non riuscita."
    fun currentPackage(): String = svc?.rootInActiveWindow?.packageName?.toString() ?: svc?.currentPackage.orEmpty()
}
