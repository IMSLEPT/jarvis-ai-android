package it.jarvis.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import it.jarvis.assistant.core.JState
import kotlin.math.PI
import kotlin.math.sin

/** Sfera animata centrale: l'aspetto cambia con lo stato (attesa, ascolto, pensiero, azione, voce, errore). */
@Composable
fun JarvisOrb(state: JState, level: Float, size: Dp = 250.dp, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "orb")
    val speed = when (state) { JState.THINKING -> 1800; JState.ACTING -> 1200; JState.SPEAKING -> 2500; JState.LISTENING -> 3500; else -> 7000 }
    val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(speed, easing = LinearEasing)), label = "rot")
    val rot2 by t.animateFloat(360f, 0f, infiniteRepeatable(tween(speed * 2, easing = LinearEasing)), label = "rot2")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (state == JState.SPEAKING) 700 else 2200, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")

    val core = when (state) {
        JState.ERROR -> JColors.Danger
        JState.OFF -> Color(0xFF5B6B94)
        JState.ACTING -> JColors.Violet
        JState.SPEAKING -> JColors.Ok
        else -> JColors.Azure
    }
    val accent = when (state) { JState.ERROR -> Color(0xFFFF9AAA); JState.ACTING -> JColors.Azure; else -> JColors.Indigo }

    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2
            val lv = if (state == JState.LISTENING) level else if (state == JState.SPEAKING) 0.35f + 0.35f * pulse else 0f
            val breathe = 0.92f + 0.05f * pulse + 0.18f * lv
            // alone
            drawCircle(Brush.radialGradient(listOf(core.copy(alpha = 0.42f), Color.Transparent), c, r), r, c)
            // anelli onda
            if (state == JState.LISTENING || state == JState.SPEAKING) {
                for (i in 1..3) {
                    val k = (pulse + i * 0.33f) % 1f
                    drawCircle(core.copy(alpha = (1f - k) * 0.35f), r * (0.55f + 0.45f * k + 0.2f * lv), c, style = Stroke(2.5f))
                }
            }
            // sfera
            val rr = r * 0.52f * breathe
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), core, core.copy(alpha = 0.55f), Color(0xFF071030)), Offset(c.x - rr * 0.3f, c.y - rr * 0.35f), rr * 1.5f), rr, c)
            // archi rotanti
            fun arc(radius: Float, start: Float, sweep: Float, col: Color, w: Float) =
                drawArc(col, start, sweep, false, Offset(c.x - radius, c.y - radius), Size(radius * 2, radius * 2), style = Stroke(w, cap = StrokeCap.Round))
            val busy = state == JState.THINKING || state == JState.ACTING
            arc(r * 0.72f, rot, if (busy) 110f else 60f, accent.copy(alpha = 0.9f), 5f)
            arc(r * 0.72f, rot + 180f, if (busy) 110f else 40f, accent.copy(alpha = 0.6f), 5f)
            arc(r * 0.86f, rot2, if (busy) 70f else 30f, core.copy(alpha = 0.7f), 3f)
            arc(r * 0.86f, rot2 + 120f, 25f, core.copy(alpha = 0.5f), 3f)
            if (state == JState.SPEAKING) {
                // onda vocale
                val w = r * 0.9f
                for (j in 0..2) {
                    val path = androidx.compose.ui.graphics.Path()
                    for (x in -50..50) {
                        val xx = c.x + x / 50f * w
                        val yy = c.y + sin((x / 50f * 3f + pulse * 2f + j) * PI.toFloat()) * (6f + 28f * lv) * (1f - kotlin.math.abs(x) / 55f)
                        if (x == -50) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                    }
                    drawPath(path, Color.White.copy(alpha = 0.55f - j * 0.15f), style = Stroke(2.5f))
                }
            }
        }
    }
}
