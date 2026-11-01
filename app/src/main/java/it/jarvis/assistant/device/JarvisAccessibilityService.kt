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
        connected.value = true
        DiagLog.i("A11y", "Servizio di accessibilità connesso")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        lastEventTime = System.currentTimeMillis()
        event.packageName?.toString()?.let { if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) currentPackage = it }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) { instance = null; connected.value = false }
        DiagLog.w("A11y", "Servizio di accessibilità disconnesso")
        return super.onUnbind(intent)
    }

    override fun onDestroy() { if (instance === this) { instance = null; connected.value = false }; super.onDestroy() }

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
                    var hw: Bitmap? = null
                    var bmp: Bitmap? = null
                    var scaled: Bitmap? = null
                    try {
                        if (!cont.isActive) return
                        hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        bmp = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        val image = bmp ?: run { if (cont.isActive) cont.resume(null); return }
                        val scale = maxSide.toFloat() / maxOf(image.width, image.height)
                        val output = if (scale < 1f) Bitmap.createScaledBitmap(image, (image.width * scale).toInt(), (image.height * scale).toInt(), true) else image
                        scaled = output
                        val out = ByteArrayOutputStream()
                        output.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        if (cont.isActive) cont.resume(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                    } catch (e: Exception) { DiagLog.e("A11y", "Screenshot elaborazione", e); if (cont.isActive) cont.resume(null) }
                    finally {
                        result.hardwareBuffer.close()
                        if (scaled !== bmp) scaled?.recycle()
                        bmp?.recycle(); hw?.recycle()
                    }
                }
                override fun onFailure(errorCode: Int) { DiagLog.w("A11y", "Screenshot non riuscito (codice $errorCode)"); if (cont.isActive) cont.resume(null) }
            })
        } catch (e: Exception) { DiagLog.e("A11y", "Screenshot", e); if (cont.isActive) cont.resume(null) }
    }

    companion object {
        val connected = kotlinx.coroutines.flow.MutableStateFlow(false)
        @Volatile var instance: JarvisAccessibilityService? = null
        val isConnected get() = instance != null

        fun boundsOf(n: AccessibilityNodeInfo): Rect = Rect().also { n.getBoundsInScreen(it) }
    }
}
