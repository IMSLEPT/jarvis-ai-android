package it.jarvis.assistant.ai

import it.jarvis.assistant.diag.DiagLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChatMsg(val role: String, val text: String, val imageJpegB64: String? = null)
data class LlmReply(val text: String, val provider: String)
class LlmException(message: String, val rateLimited: Boolean = false) : Exception(message)

val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (!cont.isCancelled) cont.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { cont.resume(response) }
    })
}

/**
 * Client per provider compatibili OpenAI, con catena di fallback ordinata (solo provider configurati dall'utente).
 * 429/5xx/timeout/401 → prossimo provider. Nessun provider a pagamento viene usato se non è nell'ordine scelto.
 */
class LlmClient(private val store: ProviderStore) {
    private val json = "application/json".toMediaType()

    fun isConfigured() = store.usable().isNotEmpty()

    private fun explicit(id: String): ProviderSpec? =
        Providers.byId(id)?.takeIf { store.hasKey(it.id) && store.model(it).isNotBlank() && store.baseUrl(it).isNotBlank() }

    /** Provider specifico (anche fuori dall'ordine di default, es. API OpenAI a pagamento scelta dall'utente). */
    fun isConfigured(only: String) = explicit(only) != null

    suspend fun chat(messages: List<ChatMsg>, allowImages: Boolean = true, maxTokens: Int = 1200, temperature: Double = 0.2, only: String? = null): LlmReply {
        val candidates = if (only != null) listOfNotNull(explicit(only)) else store.usable()
        if (candidates.isEmpty()) throw LlmException(
            if (only != null) "Provider «$only» non configurato (chiave o modello mancanti)."
            else "Nessun modello AI configurato. Apri «Modelli AI» e inserisci almeno una chiave API gratuita."
        )
        val errors = mutableListOf<String>()
        for (spec in candidates) {
            try {
                val text = call(spec, messages, allowImages && spec.vision, maxTokens, temperature)
                return LlmReply(text, spec.name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagLog.w("LLM", "${spec.id} fallito: ${e.message}")
                errors += "${spec.name}: ${e.message}"
            }
        }
        throw LlmException("Tutti i provider hanno fallito.\n" + errors.joinToString("\n"), rateLimited = errors.all { it.contains("HTTP 429") })
    }

    private suspend fun call(spec: ProviderSpec, msgs: List<ChatMsg>, images: Boolean, maxTokens: Int, temp: Double): String =
        withContext(Dispatchers.IO) {
            val arr = JSONArray()
            for (m in msgs) {
                val o = JSONObject().put("role", m.role)
                if (m.imageJpegB64 != null && images) {
                    val parts = JSONArray()
                    parts.put(JSONObject().put("type", "text").put("text", m.text))
                    parts.put(JSONObject().put("type", "image_url").put("image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,${m.imageJpegB64}")))
                    o.put("content", parts)
                } else o.put("content", m.text)
                arr.put(o)
            }
            val body = JSONObject()
                .put("model", store.model(spec))
                .put("messages", arr)
                .put("temperature", temp)
                .put("max_tokens", maxTokens)
                .toString()
            val req = Request.Builder()
                .url(store.baseUrl(spec).trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${store.key(spec.id)}")
                .post(body.toRequestBody(json))
                .build()
            httpClient.newCall(req).await().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val detail = try { JSONObject(txt).optJSONObject("error")?.optString("message") } catch (_: Exception) { null }
                    throw LlmException("HTTP ${r.code} ${detail ?: txt.take(120)}")
                }
                val content = JSONObject(txt).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
                stripThinking(content)
            }
        }

    /** Elenca i modelli del provider (GET /models) per scegliere un nome valido. */
    suspend fun listModels(spec: ProviderSpec): List<String> = withContext(Dispatchers.IO) {
        val key = store.key(spec.id) ?: throw LlmException("Inserisci prima la chiave API")
        val req = Request.Builder().url(store.baseUrl(spec).trimEnd('/') + "/models").header("Authorization", "Bearer $key").build()
        httpClient.newCall(req).await().use { r ->
            val txt = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw LlmException("HTTP ${r.code} ${txt.take(120)}")
            val data = JSONObject(txt).optJSONArray("data") ?: JSONArray()
            (0 until data.length()).map { data.getJSONObject(it).optString("id") }.filter { it.isNotBlank() }.sorted()
        }
    }

    companion object {
        fun stripThinking(s: String): String =
            s.replace(Regex("(?s)<think>.*?</think>"), "").replace(Regex("(?s)^.*?</think>"), "").trim()
    }
}
