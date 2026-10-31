package it.jarvis.assistant.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import it.jarvis.assistant.core.Settings
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.security.ApiKeyBackup
import it.jarvis.assistant.security.BackupPayload
import it.jarvis.assistant.security.SecureKeyStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ProviderSpec(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val vision: Boolean,
    val signupUrl: String,
    val note: String,
    val paid: Boolean = false,
)

object Providers {
    val all = listOf(
        ProviderSpec(
            "groq", "Groq (piano gratuito)", "https://api.groq.com/openai/v1", "qwen/qwen3.8-27b", true,
            "https://console.groq.com/keys",
            "Modello open source Qwen3.8-27B. Piano gratuito con limiti (circa 30 richieste/min e 8K token/min): per questo l'agente privilegia il testo dell'albero di accessibilità agli screenshot. Se il nome modello non risulta valido usa «Elenca modelli»."
        ),
        ProviderSpec(
            "google", "Google AI Studio (Gemma, gratuito)", "https://generativelanguage.googleapis.com/v1beta/openai", "", true,
            "https://aistudio.google.com/apikey",
            "Scegli un modello Gemma con «Elenca modelli». Attenzione: nel piano gratuito Google può usare i dati per migliorare i prodotti (fuori da UE/UK/SEE)."
        ),
        ProviderSpec(
            "openrouter", "OpenRouter (modelli :free)", "https://openrouter.ai/api/v1", "", true,
            "https://openrouter.ai/keys",
            "Scegli un modello con suffisso :free (open source) con «Elenca modelli»."
        ),
        ProviderSpec(
            "nvidia", "NVIDIA NIM (gratuito per sviluppatori)", "https://integrate.api.nvidia.com/v1", "", true,
            "https://build.nvidia.com/",
            "Crediti gratuiti per sviluppatori. Scegli il modello con «Elenca modelli»."
        ),
        ProviderSpec(
            "custom", "Personalizzato (compatibile OpenAI)", "", "", true, "",
            "Qualsiasi server compatibile con l'API OpenAI, anche sul tuo PC/NAS (es. Ollama, llama.cpp)."
        ),
        ProviderSpec(
            "openai", "OpenAI / ChatGPT API (A PAGAMENTO, opzionale)", "https://api.openai.com/v1", "", true,
            "https://platform.openai.com/api-keys",
            "A pagamento e non open source: NON è nell'ordine di default. Serve solo per l'azione «Chiedi a ChatGPT». Abilitalo e aggiungilo all'ordine solo se vuoi.",
            paid = true,
        ),
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Gestione chiavi/modelli dei provider. Le chiavi sono cifrate nel Keystore; ogni modifica può attivare il backup automatico. */
class ProviderStore(private val settings: Settings, private val secure: SecureKeyStore) {
    var onChanged: (() -> Unit)? = null

    fun key(id: String): String? = secure.get("key_$id")
    fun hasKey(id: String) = secure.has("key_$id")
    fun setKey(id: String, value: String) {
        if (value.isBlank()) secure.remove("key_$id") else secure.put("key_$id", value.trim())
        onChanged?.invoke()
    }
    fun model(spec: ProviderSpec) = settings.providerModel(spec.id, spec.defaultModel)
    fun baseUrl(spec: ProviderSpec) = settings.providerBaseUrl(spec.id, spec.baseUrl)
    fun setModel(id: String, v: String) { settings.setProviderModel(id, v.trim()); onChanged?.invoke() }
    fun setBaseUrl(id: String, v: String) { settings.setProviderBaseUrl(id, v.trim().trimEnd('/')); onChanged?.invoke() }

    /** Provider utilizzabili, nell'ordine scelto. Nessun fallback a pagamento implicito: i provider `paid` vanno messi nell'ordine a mano. */
    fun usable(): List<ProviderSpec> {
        val order = settings.providerOrder.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        return order.mapNotNull { Providers.byId(it) }.filter {
            settings.providerEnabled(it.id) && hasKey(it.id) && model(it).isNotBlank() && baseUrl(it).isNotBlank()
        }
    }

    fun snapshot(): BackupPayload {
        val keys = mutableMapOf<String, String>()
        val models = mutableMapOf<String, String>()
        val urls = mutableMapOf<String, String>()
        for (p in Providers.all) {
            key(p.id)?.let { keys[p.id] = it }
            models[p.id] = model(p)
            urls[p.id] = baseUrl(p)
        }
        return BackupPayload(keys, models, urls)
    }

    fun restore(p: BackupPayload): Int {
        var n = 0
        for ((id, k) in p.keys) if (Providers.byId(id) != null) { secure.put("key_$id", k); n++ }
        for ((id, m) in p.models) if (Providers.byId(id) != null) settings.setProviderModel(id, m)
        for ((id, u) in p.baseUrls) if (Providers.byId(id) != null) settings.setProviderBaseUrl(id, u)
        return n
    }
}

/**
 * ZONA BACKUP: esporta/importa chiavi API e configurazione provider in un file cifrato con passphrase,
 * salvato dove vuoi (Download, Drive, …) tramite il selettore file di Android. Sopravvive a disinstallazioni e aggiornamenti.
 * Con il backup automatico, il file viene riscritto a ogni modifica delle chiavi.
 */
class BackupZone(private val ctx: Context, private val settings: Settings, private val store: ProviderStore, private val secure: SecureKeyStore) {
    private val tag = "Backup"

    fun exportTo(uri: Uri, passphrase: CharArray) {
        val text = ApiKeyBackup.encrypt(store.snapshot(), passphrase)
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Impossibile scrivere il file")
        settings.lastBackup = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date())
        DiagLog.i(tag, "Backup chiavi esportato")
    }

    /** @return numero di chiavi ripristinate */
    fun importFrom(uri: Uri, passphrase: CharArray): Int {
        val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
            ?: error("Impossibile leggere il file")
        val payload = ApiKeyBackup.decrypt(text, passphrase)
        val n = store.restore(payload)
        DiagLog.i(tag, "Backup importato: $n chiavi")
        return n
    }

    /** Attiva il backup automatico: ricorda il file scelto e la passphrase (cifrata nel Keystore). */
    fun enableAuto(uri: Uri, passphrase: CharArray) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) { DiagLog.w(tag, "Permesso persistente non concesso: ${e.message}") }
        secure.put("_backup_pass", String(passphrase))
        settings.backupUri = uri.toString()
        settings.autoBackup = true
        autoWrite()
    }

    fun disableAuto() {
        settings.autoBackup = false
        secure.remove("_backup_pass")
    }

    fun autoWrite() {
        if (!settings.autoBackup) return
        val uri = settings.backupUri?.let(Uri::parse) ?: return
        val pass = secure.get("_backup_pass")?.toCharArray() ?: return
        try { exportTo(uri, pass) } catch (e: Exception) { DiagLog.e(tag, "Backup automatico fallito", e) }
    }

    /** True se non ci sono chiavi ma esiste un file di backup automatico configurato (utile dopo reinstallazione). */
    fun restoreAvailable() = settings.backupUri != null && Providers.all.none { store.hasKey(it.id) }
}
