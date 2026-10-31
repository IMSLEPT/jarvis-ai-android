package it.jarvis.assistant.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.util.Base64
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import it.jarvis.assistant.diag.DiagLog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Servizio di accessibilità: legge l'albero della finestra attiva e compie azioni (tocco, testo, scorrimento, navigazione)
 * SOLO su richiesta dell'agente. Non memorizza né trasmette nulla di per sé.
 */
class JarvisAccessibilityService : AccessibilityService() {

    @Volatile var lastEventTime = 0L
    @Volatile var currentPackage: String = ""

    override fun onServiceConnected() {
        instance = this
        DiagLog.i("A11y", "Servizio di accessibilità connesso")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        lastEventTime = System.currentTimeMillis()
        event.packageName?.toString()?.let { if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) currentPackage = it }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        DiagLog.w("A11y", "Servizio di accessibilità disconnesso")
        return super.onUnbind(intent)
    }

    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    // ---- azioni ----

    fun tapAt(x: Float, y: Float): Boolean = gesture(Path().apply { moveTo(x, y) }, 60)

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, ms: Long = 350): Boolean =
        gesture(Path().apply { moveTo(x1, y1); lineTo(x2, y2) }, ms)

    private fun gesture(path: Path, ms: Long): Boolean {
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        return dispatchGesture(g, null, null)
    }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    fun screenSize(): Pair<Int, Int> {
        val m = resources.displayMetrics
        return m.widthPixels to m.heightPixels
    }

    /** Screenshot (API 30+), ridotto e codificato JPEG base64. Null se vietato (FLAG_SECURE), troppo frequente o errore. */
    suspend fun screenshotJpegB64(maxSide: Int = 1024): String? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < 30) { cont.resume(null); return@suspendCancellableCoroutine }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        val bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        result.hardwareBuffer.close()
                        if (bmp == null) { cont.resume(null); return }
                        val scale = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
                        val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        cont.resume(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                    } catch (e: Exception) { DiagLog.e("A11y", "Screenshot elaborazione", e); cont.resume(null) }
                }
                override fun onFailure(errorCode: Int) { DiagLog.w("A11y", "Screenshot non riuscito (codice $errorCode)"); cont.resume(null) }
            })
        } catch (e: Exception) { DiagLog.e("A11y", "Screenshot", e); cont.resume(null) }
    }

    companion object {
        @Volatile var instance: JarvisAccessibilityService? = null
        val isConnected get() = instance != null

        fun boundsOf(n: AccessibilityNodeInfo): Rect = Rect().also { n.getBoundsInScreen(it) }
    }
}
