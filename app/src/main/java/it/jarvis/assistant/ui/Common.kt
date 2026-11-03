package it.jarvis.assistant.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import it.jarvis.assistant.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ScreenScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
            Text(title, modifier = Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Image(painterResource(R.drawable.jarvis_portrait), null, Modifier.size(42.dp), contentScale = ContentScale.Fit)
        }
        BrandWave(Modifier.fillMaxWidth().height(16.dp).padding(horizontal = 24.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun JCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
    ) { Column(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f), MaterialTheme.colorScheme.surface))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }
}

@Composable
fun Section(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))

@Composable
fun Hint(text: String) = Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)

@Composable
fun SwitchRow(label: String, checked: Boolean, desc: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            if (desc != null) Hint(desc)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(fmt(value), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value, onChange, valueRange = range)
    }
}

/** A quiet ribbon echoing the supplied app icon; no perpetual animation. */
@Composable
fun BrandWave(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val blue = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val wave = Path().apply {
            moveTo(0f, size.height * .48f)
            cubicTo(size.width * .30f, -size.height * .25f, size.width * .51f, size.height * 1.28f, size.width, size.height * .32f)
        }
        drawPath(wave, Brush.horizontalGradient(listOf(blue.copy(alpha = .05f), accent.copy(alpha = .12f), blue.copy(alpha = .02f))), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round))
        drawPath(wave, Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = .7f), Color.Transparent)), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
