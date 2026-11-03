package it.jarvis.assistant.service

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import it.jarvis.assistant.R
import it.jarvis.assistant.core.JState
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.graph
import it.jarvis.assistant.ui.MainActivity
import it.jarvis.assistant.ui.hasMic
import it.jarvis.assistant.ui.stateLabel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Assistente selezionabile nelle impostazioni Android; nessun privilegio di sistema simulato. */
class JarvisVoiceInteractionService : VoiceInteractionService() {
    private var destroyed = false
    override fun onReady() {
        super.onReady()
        if (destroyed) return
        instance = this
        // L'assistente predefinito è un'esenzione Android documentata per il servizio microfono.
        if (graph.settings.voiceEnabled && hasMic() && graph.models.isInstalled("vad")) JarvisService.start(this)
    }
    override fun onShutdown() { if (instance === this) instance = null; super.onShutdown() }
    override fun onDestroy() { destroyed = true; if (instance === this) instance = null; super.onDestroy() }
    companion object { @Volatile var instance: JarvisVoiceInteractionService? = null }
}

class JarvisSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = JarvisSession(this)
}

class JarvisSession(private val ctx: Context) : VoiceInteractionSession(ctx) {
    private var panel: AssistantPanel? = null
    override fun onCreateContentView(): View {
        val p = AssistantPanel(ctx, { hide(); ctx.graph.core.stopAll() }, { hide(); openApp(ctx) })
        panel = p
        return FrameLayout(ctx).apply {
            addView(p.view, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        }
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        active = this
        panel?.bind()
        if (args?.getBoolean("from_jarvis") != true) JarvisService.start(ctx, listenNow = true)
    }
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val v = panel?.view ?: return
        val xy = IntArray(2); v.getLocationInWindow(xy)
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(xy[0], xy[1], xy[0] + v.width, xy[1] + v.height)
        outInsets.contentInsets.set(0, xy[1], 0, 0)
    }
    override fun onHide() { panel?.unbind(); if (active === this) active = null; super.onHide() }
    override fun onDestroy() { panel?.unbind(); if (active === this) active = null; super.onDestroy() }
    companion object { var active: JarvisSession? = null }
}

/** Fallback visibile con permesso «Mostra sopra altre app», senza rubare il focus alla tastiera. */
class AssistantSurface(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var panel: AssistantPanel? = null
    private var dismissed = false
    fun activate() { dismissed = false }
    fun show() {
        if (dismissed || ctx.graph.core.appVisible.value) return
        val voice = JarvisVoiceInteractionService.instance
        if (voice != null) {
            try {
                if (JarvisSession.active == null) voice.showSession(Bundle().apply { putBoolean("from_jarvis", true) }, 0)
                return // showSession è asincrono: non aggiungere anche un overlay mentre si apre.
            } catch (e: Exception) { DiagLog.e("Assistente", "Pannello di sistema non disponibile", e) }
        }
        if (panel != null || !Settings.canDrawOverlays(ctx)) return
        val p = AssistantPanel(ctx, { dismissed = true; hide(); ctx.graph.core.stopAll() }, { dismissed = true; hide(); openApp(ctx) })
        val lp = WindowManager.LayoutParams(-1, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply { title = "Jarvis assistant"; gravity = Gravity.BOTTOM; softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
        try { wm.addView(p.view, lp); panel = p; p.bind() }
        catch (e: Exception) { p.unbind(); DiagLog.e("Assistente", "Permesso pannello non disponibile", e) }
    }
    fun hide() {
        JarvisSession.active?.hide()
        panel?.let { p -> p.unbind(); try { wm.removeView(p.view) } catch (_: Exception) {} }
        panel = null
    }
}

private fun openApp(ctx: Context) {
    try { ctx.startActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
    catch (e: Exception) { DiagLog.e("Assistente", "Apertura app", e) }
}

/** View native condivisa dalla sessione di sistema e dall'overlay: nessun LifecycleOwner mancante. */
private class AssistantPanel(private val ctx: Context, close: () -> Unit, open: () -> Unit) {
    private val g = ctx.graph
    private var scope: CoroutineScope? = null
    private val density = ctx.resources.displayMetrics.density
    private fun dp(n: Int) = (n * density).toInt()
    private val ink = Color.rgb(241, 247, 255)
    private val teal = Color.rgb(69, 230, 250)
    val view = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(16), dp(22), dp(28))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(11, 45, 89), Color.rgb(3, 16, 48))).apply {
            cornerRadii = floatArrayOf(dp(30).toFloat(),dp(30).toFloat(),dp(30).toFloat(),dp(30).toFloat(),0f,0f,0f,0f)
            setStroke(dp(1), Color.rgb(41, 100, 147))
        }
        elevation = dp(12).toFloat()
    }
    private fun label(size: Float, color: Int = ink) = TextView(ctx).apply { textSize = size; setTextColor(color); setPadding(0, dp(4), 0, dp(4)) }
    private val title = label(18f, teal)
    private val body = label(15f)
    private val transcript = label(12f, Color.rgb(173, 194, 221))
    private val confirmations = LinearLayout(ctx)
    init {
        val head = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(ImageView(ctx).apply { setImageResource(R.drawable.jarvis_portrait); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(38),dp(38)).apply { marginEnd = dp(12) })
        head.addView(title, LinearLayout.LayoutParams(0,-2,1f))
        head.addView(Button(ctx).apply { text = "Chat"; contentDescription = "Apri chat Jarvis"; setOnClickListener { open() } }, LinearLayout.LayoutParams(dp(76), dp(48)))
        head.addView(Button(ctx).apply { text = "×"; contentDescription = "Chiudi assistente e interrompi"; setOnClickListener { close() } }, LinearLayout.LayoutParams(dp(52), dp(48)))
        view.addView(head)
        view.addView(transcript)
        body.maxLines = 5
        view.addView(body)
        confirmations.addView(Button(ctx).apply { text = "Conferma"; setOnClickListener { g.core.answerConfirm(true) } })
        confirmations.addView(Button(ctx).apply { text = "Annulla"; setOnClickListener { g.core.answerConfirm(false) } })
        view.addView(confirmations)
        for (button in listOf(head.getChildAt(2), head.getChildAt(3), confirmations.getChildAt(0), confirmations.getChildAt(1))) {
            (button as Button).apply {
                setTextColor(teal)
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(16, 42, 80))
            }
        }
    }
    fun bind() {
        unbind()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { sc ->
            sc.launch {
                combine(g.core.state, g.core.status, g.core.chat, g.core.pendingConfirm) { state, status, chat, confirm ->
                    title.text = "JARVIS · ${stateLabel(state)}"
                    transcript.text = chat.lastOrNull { it.role == "user" }?.text.orEmpty().take(180)
                    body.text = confirm ?: if (state == JState.SPEAKING || state == JState.WAITING) chat.lastOrNull { it.role == "jarvis" }?.text ?: "Di’ pure, ti ascolto." else status.ifBlank { "Di’ pure, ti ascolto." }
                    confirmations.visibility = if (confirm != null) View.VISIBLE else View.GONE
                }.collect { }
            }
        }
    }
    fun unbind() { scope?.cancel(); scope = null }
}
