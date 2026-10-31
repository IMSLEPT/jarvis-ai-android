package it.jarvis.assistant.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.jarvis.assistant.core.JState
import it.jarvis.assistant.device.JarvisAccessibilityService
import it.jarvis.assistant.graph
import it.jarvis.assistant.service.JarvisService

@Composable
fun HomeScreen(onMenu: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val state by g.core.state.collectAsState()
    val level by g.core.level.collectAsState()
    val status by g.core.status.collectAsState()
    val chat by g.core.chat.collectAsState()
    val error by g.core.error.collectAsState()
    val confirm by g.core.pendingConfirm.collectAsState()
    val models by g.models.states.collectAsState()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.RECORD_AUDIO] == true) JarvisService.start(ctx)
    }
    LaunchedEffect(chat.size) { if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1) }

    val active = state == JState.LISTENING || state == JState.THINKING || state == JState.ACTING || state == JState.SPEAKING
    val on = state != JState.OFF && state != JState.ERROR

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("JARVIS", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
        }
        Box(
            Modifier.fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (on) g.core.listenNow() else perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
            },
            contentAlignment = Alignment.Center
        ) { JarvisOrb(state, level, 220.dp) }
        Text(if (state == JState.WAITING) "In attesa di «Hey Jarvis» — o tocca la sfera" else stateLabel(state), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
        if (status.isNotBlank()) Text(status, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) Text(error!!, Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)

        // Configurazione mancante
        val needModels = !(g.models.isInstalled("kws") && g.models.isInstalled("vad") && g.models.isInstalled("stt"))
        val needKey = !g.llm.isConfigured()
        val needA11y = !JarvisAccessibilityService.isConnected
        @Suppress("UNUSED_VARIABLE") val u = models
        if (needKey || needModels || needA11y) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (needKey) SetupChip("Manca un modello AI: inserisci una chiave API gratuita") { go("ai") }
                if (needModels) SetupChip("Scarica i modelli vocali (wake word, VAD, italiano)") { go("voice") }
                if (needA11y) SetupChip("Attiva l'accessibilità per controllare le altre app") { go("perm") }
            }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(chat) { m ->
                val mine = m.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (mine) Brush.linearGradient(listOf(JColors.Indigo.copy(alpha = 0.55f), JColors.Azure.copy(alpha = 0.45f))) else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text(m.text, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }

        if (confirm != null) {
            JCard(Modifier.padding(vertical = 6.dp)) {
                Text("Conferma richiesta", fontWeight = FontWeight.Bold, color = JColors.Warn)
                Text(confirm!!, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { g.core.answerConfirm(true) }) { Text("Sì, conferma") }
                    OutlinedButton(onClick = { g.core.answerConfirm(false) }) { Text("No") }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Scrivi un comando…") }, singleLine = true, shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { g.core.submitText(text); text = "" } }),
            )
            IconButton(onClick = { if (text.isNotBlank()) { g.core.submitText(text); text = "" } }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia", tint = MaterialTheme.colorScheme.primary)
            }
            FilledIconButton(
                onClick = {
                    if (on) JarvisService.stop(ctx)
                    else perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
                },
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (on) JColors.Azure else MaterialTheme.colorScheme.surfaceVariant),
            ) { Icon(if (on) Icons.Default.Mic else Icons.Default.MicOff, contentDescription = if (on) "Ferma ascolto" else "Avvia ascolto", tint = if (on) Color(0xFF00212E) else MaterialTheme.colorScheme.onSurface) }
        }
        if (active) {
            Button(
                onClick = { g.core.stopAll() }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = JColors.Danger, contentColor = Color.White),
            ) { Icon(Icons.Default.Stop, null); Text("  STOP", fontWeight = FontWeight.Bold) }
        }
    }
}


@Composable
private fun SetupChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(JColors.Warn.copy(alpha = 0.16f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)
    ) { Text("⚠  $text", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface) }
}

private fun stateLabel(s: JState) = when (s) {
    JState.OFF -> "Non in ascolto"
    JState.WAITING -> "In attesa di «Hey Jarvis»"
    JState.LISTENING -> "Ti ascolto"
    JState.THINKING -> "Sto pensando"
    JState.ACTING -> "Sto agendo sul telefono"
    JState.SPEAKING -> "Sto parlando"
    JState.ERROR -> "Qualcosa non va"
}
