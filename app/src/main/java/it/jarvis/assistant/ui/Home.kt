package it.jarvis.assistant.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.jarvis.assistant.R
import it.jarvis.assistant.core.JState
import it.jarvis.assistant.device.JarvisAccessibilityService
import it.jarvis.assistant.graph
import it.jarvis.assistant.service.JarvisService

@Composable
fun HomeScreen(onMenu: () -> Unit, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val state by g.core.state.collectAsState()
    val status by g.core.status.collectAsState()
    val chat by g.core.chat.collectAsState()
    val error by g.core.error.collectAsState()
    val confirm by g.core.pendingConfirm.collectAsState()
    val models by g.models.states.collectAsState()
    val connected by JarvisAccessibilityService.connected.collectAsState()
    var text by rememberSaveable { mutableStateOf("") }
    var startListening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val on = g.core.micRunning && state != JState.ERROR
    val busy = state in setOf(JState.LISTENING, JState.THINKING, JState.ACTING, JState.SPEAKING)
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ctx.hasMic()) JarvisService.start(ctx, startListening)
        else g.core.reportError("Consenti il microfono in Permessi per parlare con Jarvis. La chat resta disponibile.")
    }
    fun voice(now: Boolean) {
        startListening = now
        if (ctx.hasMic()) JarvisService.start(ctx, now)
        else perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
    }
    fun send() { if (text.isNotBlank()) { g.core.submitText(text); text = "" } }
    LaunchedEffect(chat.size, confirm) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
    }
    @Suppress("UNUSED_VARIABLE") val modelRefresh = models
    val voiceReady = listOf("kws", "vad", "stt").all { g.models.isInstalled(it) }
    val aiReady = g.llm.isConfigured()

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.jarvis_portrait), "Jarvis", Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("JARVIS", letterSpacing = 3.sp, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (on) "Hey Jarvis è attivo" else "Il tuo assistente personale", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            IconButton(onClick = { if (on) JarvisService.stop(ctx) else voice(false) }) {
                Icon(Icons.Default.PowerSettingsNew, if (on) "Disattiva Hey Jarvis" else "Attiva Hey Jarvis", tint = if (on) JColors.Ok else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onMenu) { Icon(Icons.Default.Tune, "Impostazioni e strumenti") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .18f))
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Ciao Alessandro.", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
                        Text("Cosa facciamo\noggi?", fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Light, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(94.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .3f), RoundedCornerShape(30.dp)).padding(5.dp).clip(RoundedCornerShape(25.dp)).clickable { voice(true) }) {
                        Image(painterResource(R.drawable.jarvis_portrait), "Tocca per parlare", Modifier.fillMaxSize())
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = .08f), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .15f))) {
                    Row(Modifier.fillMaxWidth().clickable { voice(true) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            Text(stateLabel(state), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(status.ifBlank { if (on) "Di’ Hey Jarvis, anche nelle altre app" else "Tocca il microfono oppure scrivi qui sotto" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                }
            }
            if (error != null) item { JCard { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error); TextButton(onClick = { go("diag") }) { Text("Apri diagnostica") } } }
            if (!aiReady || !voiceReady || !connected) item {
                JCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Completa Jarvis", fontWeight = FontWeight.SemiBold)
                        Text("${listOf(aiReady, voiceReady, connected).count { it }}/3", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }
                    if (!aiReady) SetupLine("01", "Collega il modello AI", "Chiavi e backup", { go("ai") })
                    if (!voiceReady) SetupLine("02", "Prepara la voce", "Scarica i modelli italiani", { go("voice") })
                    if (!connected) SetupLine("03", "Abilita il controllo delle app", "Permessi e impostazioni limitate", { go("perm") })
                }
            }
            if (chat.isEmpty()) item {
                Text("INIZIA DA QUI", fontSize = 10.sp, letterSpacing = 2.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickCard("Apri un’app", "WhatsApp, Foto e altro", Icons.Default.Apps, Modifier.weight(1f)) { text = "Apri WhatsApp" }
                    QuickCard("Chiedi a Jarvis", "Idee, risposte e aiuto", Icons.Default.AutoAwesome, Modifier.weight(1f)) { text = "Cosa puoi fare sul mio telefono?" }
                }
            }
            itemsIndexed(chat) { _, m ->
                val mine = m.role == "user"
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
                    Text(if (mine) "TU" else "JARVIS", Modifier.padding(horizontal = 4.dp, vertical = 5.dp), fontSize = 10.sp, letterSpacing = 1.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(shape = RoundedCornerShape(20.dp), color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(m.text, Modifier.padding(15.dp), color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, lineHeight = 23.sp)
                        }
                    }
                }
            }
            if (confirm != null) item {
                JCard {
                    Text("Serve la tua conferma", color = JColors.Warn, fontWeight = FontWeight.Bold)
                    Text(confirm.orEmpty())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { g.core.answerConfirm(true) }) { Text("Conferma") }
                        OutlinedButton(onClick = { g.core.answerConfirm(false) }) { Text("Annulla") }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f).heightIn(max = 144.dp),
                    placeholder = { Text("Chiedi a Jarvis…", fontSize = 14.sp) }, minLines = 1, maxLines = 4, shape = RoundedCornerShape(25.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface, cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .4f)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
                    trailingIcon = { if (text.isNotBlank()) IconButton(onClick = { send() }) { Icon(Icons.AutoMirrored.Filled.Send, "Invia comando", tint = MaterialTheme.colorScheme.primary) } })
                FilledIconButton(onClick = { if (busy) g.core.stopAll() else voice(true) }, modifier = Modifier.size(56.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = if (busy) JColors.Danger else MaterialTheme.colorScheme.primary)) {
                    Icon(if (busy) Icons.Default.Stop else Icons.Default.Mic, if (busy) "Interrompi" else "Parla con Jarvis", tint = if (busy) Color.White else MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

@Composable
private fun SetupLine(n: String, title: String, sub: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(n, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp); Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickCard(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, click: () -> Unit) {
    Surface(modifier.clickable(onClick = click), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun stateLabel(s: JState) = when (s) {
    JState.OFF -> "Sono qui, quando vuoi"
    JState.WAITING -> "In attesa di Hey Jarvis"
    JState.LISTENING -> "Ti ascolto"
    JState.THINKING -> "Ci penso…"
    JState.ACTING -> "Sto eseguendo il comando"
    JState.SPEAKING -> "Ecco la mia risposta"
    JState.ERROR -> "Controlliamo la configurazione"
}
