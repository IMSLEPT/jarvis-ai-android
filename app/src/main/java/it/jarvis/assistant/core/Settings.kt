package it.jarvis.assistant.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Impostazioni non segrete (le chiavi API stanno in SecureKeyStore). */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _tick = MutableStateFlow(0)
    /** Cambia a ogni modifica: la UI lo osserva per ricomporsi. */
    val tick: StateFlow<Int> = _tick

    private fun bump() { _tick.value = _tick.value + 1 }

    private fun str(k: String, d: String) = p.getString(k, d) ?: d
    private fun put(k: String, v: String?) { p.edit().apply { if (v == null) remove(k) else putString(k, v) }.apply(); bump() }
    private fun put(k: String, v: Boolean) { p.edit().putBoolean(k, v).apply(); bump() }
    private fun put(k: String, v: Int) { p.edit().putInt(k, v).apply(); bump() }
    private fun put(k: String, v: Float) { p.edit().putFloat(k, v).apply(); bump() }

    var themeMode: String get() = str("theme", "dark"); set(v) = put("theme", v)           // dark | light | system
    var hotwordThreshold: Float get() = p.getFloat("hotword_oww_threshold", 0.5f); set(v) = put("hotword_oww_threshold", v.coerceIn(.15f,.85f))
    var voiceEnabled: Boolean get() = p.getBoolean("voice_enabled", false); set(v) = put("voice_enabled", v)
    var wakeEnabled: Boolean get() = p.getBoolean("wake", true); set(v) = put("wake", v)
    /** 0.10 = molto sensibile … 0.45 = poco sensibile */
    var wakeThreshold: Float get() = p.getFloat("wake_thr", 0.2f); set(v) = put("wake_thr", v)
    var wakeScore: Float get() = p.getFloat("wake_score", 1.5f); set(v) = put("wake_score", v)
    var speakerVerify: Boolean get() = p.getBoolean("spk_on", false); set(v) = put("spk_on", v)
    var speakerThreshold: Float get() = p.getFloat("spk_thr", 0.55f); set(v) = put("spk_thr", v)
    var ttsVoice: String get() = str("tts_voice", "tts_paola-medium"); set(v) = put("tts_voice", v)
    var ttsSpeed: Float get() = p.getFloat("tts_speed", 1.0f); set(v) = put("tts_speed", v)
    var ttsEnabled: Boolean get() = p.getBoolean("tts_on", true); set(v) = put("tts_on", v)
    var memoryEnabled: Boolean get() = p.getBoolean("mem_on", true); set(v) = put("mem_on", v)
    var maxSteps: Int get() = p.getInt("max_steps", 25); set(v) = put("max_steps", v)
    var followUpSeconds: Int get() = p.getInt("followup", 6); set(v) = put("followup", v)
    var endSilenceMs: Int get() = p.getInt("end_silence", 800); set(v) = put("end_silence", v)
    var useScreenshots: Boolean get() = p.getBoolean("shots", true); set(v) = put("shots", v)
    var sttModelAutoUnloadSec: Int get() = p.getInt("stt_unload", 90); set(v) = put("stt_unload", v)

    // Ordine dei provider cloud (id separati da virgola)
    var providerOrder: String get() = str("prov_order", "groq,google,openrouter,nvidia,custom"); set(v) = put("prov_order", v)
    fun providerModel(id: String, default: String) = str("model_$id", default)
    fun setProviderModel(id: String, v: String) = put("model_$id", v)
    fun providerBaseUrl(id: String, default: String) = str("base_$id", default)
    fun setProviderBaseUrl(id: String, v: String) = put("base_$id", v)
    fun providerEnabled(id: String) = p.getBoolean("en_$id", true)
    fun setProviderEnabled(id: String, v: Boolean) = put("en_$id", v)

    // Zona backup chiavi API
    var backupUri: String? get() = p.getString("backup_uri", null); set(v) = put("backup_uri", v)
    var autoBackup: Boolean get() = p.getBoolean("backup_auto", false); set(v) = put("backup_auto", v)
    var lastBackup: String get() = str("backup_last", ""); set(v) = put("backup_last", v)

    var onboardingDone: Boolean get() = p.getBoolean("onboarded", false); set(v) = put("onboarded", v)
}
