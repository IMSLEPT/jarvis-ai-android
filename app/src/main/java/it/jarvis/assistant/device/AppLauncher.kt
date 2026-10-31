package it.jarvis.assistant.device

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import it.jarvis.assistant.diag.DiagLog
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

data class AppInfo(val label: String, val pkg: String)

/** Profilo di un'app: nome, pacchetti noti e suggerimenti per l'agente. Estendibile dall'utente (schermata «App e integrazioni»). */
data class AppProfile(val id: String, val label: String, val packages: List<String>, val hint: String, val builtIn: Boolean = true)

object AppProfiles {
    val builtIn = listOf(
        AppProfile("whatsapp", "WhatsApp", listOf("com.whatsapp", "com.whatsapp.w4b"),
            "Per scrivere a un contatto: apri WhatsApp, usa la lente di ricerca, scrivi il nome, apri la chat, scrivi il testo nel campo messaggio. L'invio (pulsante Invia) richiede SEMPRE conferma dell'utente. Se hai un numero usa l'azione whatsapp_message."),
        AppProfile("photos", "Google Foto", listOf("com.google.android.apps.photos"),
            "Per cercare foto: apri Google Foto, tocca «Ricerca» in basso, scrivi la richiesta (persone, luoghi, date, cose) e invia dalla tastiera, poi descrivi i risultati. Non condividere né eliminare foto senza conferma."),
        AppProfile("chatgpt", "ChatGPT", listOf("com.openai.chatgpt"),
            "Per usare l'app ChatGPT: apri l'app, tocca il campo messaggio, scrivi il testo, invia, attendi la risposta (usa wait) e leggila dallo schermo. In alternativa, se l'utente ha configurato la API OpenAI, usa l'azione ask_chatgpt."),
        AppProfile("booking", "Booking.com", listOf("com.booking"),
            "Solo RICERCA: usa l'azione booking_search (destinazione, date, adulti), poi leggi risultati e prezzi. VIETATO prenotare, inserire dati di pagamento o confermare: se serve, riferisci all'utente e fermati."),
        AppProfile("chrome", "Chrome", listOf("com.android.chrome"), "Per aprire siti usa open_url."),
        AppProfile("maps", "Google Maps", listOf("com.google.android.apps.maps"), "Per indicazioni stradali usa open_url con https://www.google.com/maps/dir/?api=1&destination=..."),
        AppProfile("youtube", "YouTube", listOf("com.google.android.youtube"), "Per cercare usa open_url con https://www.youtube.com/results?search_query=..."),
    )
}

class AppLauncher(private val ctx: Context) {
    private val pm: PackageManager get() = ctx.packageManager
    private val customPrefs = ctx.getSharedPreferences("custom_apps", Context.MODE_PRIVATE)

    fun customProfiles(): List<AppProfile> {
        val arr = try { JSONArray(customPrefs.getString("list", "[]")) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            AppProfile(o.getString("id"), o.getString("label"), listOf(o.getString("pkg")), o.optString("hint"), builtIn = false)
        }
    }

    fun saveCustom(list: List<AppProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("label", it.label).put("pkg", it.packages.first()).put("hint", it.hint)) }
        customPrefs.edit().putString("list", arr.toString()).apply()
    }

    fun allProfiles() = AppProfiles.builtIn + customProfiles()

    fun installedApps(): List<AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0).map { AppInfo(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
    }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), "").trim()

    fun find(name: String): AppInfo? {
        val q = norm(name)
        if (q.isBlank()) return null
        val apps = installedApps()
        // 1) profili noti (alias → pacchetto installato)
        for (p in allProfiles()) {
            if (norm(p.label) == q || p.id == q) {
                p.packages.forEach { pk -> apps.firstOrNull { it.pkg == pk || it.pkg.startsWith("$pk.") }?.let { return it } }
            }
        }
        // 2) etichetta esatta, poi inizio, poi contenuto
        apps.firstOrNull { norm(it.label) == q }?.let { return it }
        apps.firstOrNull { norm(it.label).startsWith(q) }?.let { return it }
        apps.firstOrNull { norm(it.label).contains(q) || q.contains(norm(it.label)) && norm(it.label).length >= 4 }?.let { return it }
        return null
    }

    fun open(app: AppInfo): Boolean {
        val i = pm.getLaunchIntentForPackage(app.pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try { ctx.startActivity(i); true } catch (e: Exception) { DiagLog.e("App", "Apertura ${app.pkg}", e); false }
    }

    /** Solo http/https. */
    fun openUrl(url: String, pkg: String? = null): Boolean {
        val u = Uri.parse(url)
        if (u.scheme != "https" && u.scheme != "http") return false
        val i = Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null && pm.getLaunchIntentForPackage(pkg) != null) i.setPackage(pkg)
        return try { ctx.startActivity(i); true } catch (e: Exception) {
            // se il pacchetto richiesto non gestisce il link, riprova senza vincolo
            if (pkg != null) { i.setPackage(null); try { ctx.startActivity(i); true } catch (e2: Exception) { false } } else false
        }
    }

    fun profileHintsForPrompt(): String = allProfiles().joinToString("\n") { "- ${it.label}: ${it.hint}" }
}
