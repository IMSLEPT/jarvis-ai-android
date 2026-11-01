package it.jarvis.assistant.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import it.jarvis.assistant.diag.DiagLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    fun whatsappMessage(recipient: String, text: String): String {
        if (text.isBlank()) return "ERRORE: manca il testo del messaggio. Chiedilo all’utente."
        var number = ContactResolver.phone(recipient)
        if (number == null) {
            val matches = ContactResolver.find(ctx, recipient)
            if (matches.isEmpty()) return "ERRORE: contatto non risolto. Se il permesso Rubrica non è concesso, chiedi di abilitarlo in Jarvis → Permessi, oppure cerca il nome in WhatsApp tramite lo schermo. Non inventare numeri."
            if (matches.size > 1) return "ERRORE: più numeri corrispondono al nome. Chiedi quale usare: " + matches.joinToString { "${it.name} (numero che termina in ${it.phone.takeLast(4)})" }
            number = matches.single().phone
        }
        val url = android.net.Uri.parse("https://api.whatsapp.com/send").buildUpon().appendQueryParameter("phone", number).appendQueryParameter("text", text).build().toString()
        val pkg = launcher.find("whatsapp")?.pkg ?: return "ERRORE: WhatsApp non installato."
        return if (launcher.openUrl(url, pkg)) "OK: aperta la chat WhatsApp con il testo come bozza. Verifica il campo messaggio sullo schermo. L’invio richiede conferma."
        else "ERRORE: impossibile aprire la chat WhatsApp."
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

    suspend fun typeText(text: String, id: Int?, submit: Boolean): String = withContext(Dispatchers.Main) {
        val service = svc ?: return@withContext "ERRORE: attiva il servizio in Jarvis → Permessi → Accessibilità per scrivere nelle altre app."
        delay(150) // lascia chiudere il pannello assistente prima di individuare il campo esterno
        fun editables(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
            val found = mutableListOf<AccessibilityNodeInfo>()
            var visited = 0
            fun walk(n: AccessibilityNodeInfo?, depth: Int) {
                if (n == null || depth > 30 || visited++ > 500) return
                if (n.isEditable && n.isVisibleToUser && n.isEnabled && !n.isPassword) found += n
                for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
            }
            walk(root, 0); return found
        }
        fun select(): AccessibilityNodeInfo? {
            val root = ScreenObserver.activeRoot(service) ?: return null
            val pkg = root.packageName?.toString()
            if (pkg == ctx.packageName) return null
            val explicit = id?.let { last?.nodes?.get(it) }
            if (explicit?.refresh() == true && explicit.isEditable && !explicit.isPassword && explicit.packageName?.toString() == pkg) return explicit
            val fields = editables(root)
            return fields.firstOrNull { it.viewIdResourceName == "com.whatsapp:id/entry" || it.viewIdResourceName == "com.whatsapp.w4b:id/entry" }
                ?: fields.firstOrNull { it.isFocused }
                ?: fields.singleOrNull()
        }
        var selected: AccessibilityNodeInfo? = null
        // Aperture di app/chat e animazioni non rendono subito disponibile la nuova finestra.
        for (attempt in 0 until 15) {
            selected = select()
            if (selected != null) break
            delay(200)
        }
        var node = selected ?: return@withContext "ERRORE: nessun campo messaggio modificabile trovato. Apri prima la chat del destinatario, poi rileggi lo schermo."
        if (node.isPassword) return@withContext "RIFIUTATO: non scrivo nei campi password."
        repeat(3) { attempt ->
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            delay(180)
            node = select() ?: node
            node.refresh()
            if (node.text?.toString() == text) {
                if (!submit) return@withContext "OK: testo inserito e verificato nel campo messaggio; non ancora inviato."
                val submitted = node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
                return@withContext if (submitted) "OK: testo verificato e richiesta di invio dalla tastiera eseguita. Verifica l’effettivo invio sullo schermo."
                else "OK: testo scritto e verificato. L’app richiede il pulsante Invia; usa click dopo conferma."
            }
            if (attempt == 1) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Bozza Jarvis", text))
                node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                delay(180)
            }
        }
        node.refresh()
        if (node.text?.toString() == text) "OK: testo scritto e verificato; non ancora inviato."
        else "ERRORE: l’app non ha applicato la scrittura. Non dichiarare il messaggio scritto o inviato. Rileggi lo schermo e prova whatsapp_message con contatto/numero."
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
