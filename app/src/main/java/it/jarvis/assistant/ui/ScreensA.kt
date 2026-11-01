package it.jarvis.assistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import it.jarvis.assistant.BuildConfig
import it.jarvis.assistant.ai.ChatMsg
import it.jarvis.assistant.device.AppProfile
import it.jarvis.assistant.device.JarvisAccessibilityService
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.graph
import it.jarvis.assistant.memory.MemoryItem
import it.jarvis.assistant.models.ModelCatalog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class MenuEntry(val id: String, val title: String, val sub: String, val icon: ImageVector)

@Composable
fun MenuScreen(onBack: () -> Unit, go: (String) -> Unit) {
    val items = listOf(
        MenuEntry("history", "Cronologia", "Conversazioni e azioni recenti", Icons.Default.History),
        MenuEntry("memory", "Memoria", "Ciò che Jarvis ricorda di te", Icons.Default.Memory),
        MenuEntry("voice", "Voce e modelli", "Download modelli, voce, wake word, la tua voce", Icons.Default.RecordVoiceOver),
        MenuEntry("ai", "Modelli AI e chiavi API", "Provider gratuiti, zona backup chiavi", Icons.Default.Key),
        MenuEntry("perm", "Permessi", "Microfono, accessibilità, notifiche", Icons.Default.Security),
        MenuEntry("apps", "App e integrazioni", "WhatsApp, Foto, Booking, ChatGPT e tue app", Icons.Default.Apps),
        MenuEntry("settings", "Impostazioni", "Tema, tempi, privacy, informazioni", Icons.Default.Settings),
        MenuEntry("diag", "Diagnostica", "Stato, log, prova modello", Icons.Default.BugReport),
    )
    ScreenScaffold("Menu", onBack) {
        items.forEach { e ->
            JCard(Modifier.clickable { go(e.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(e.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Column { Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface); Hint(e.sub) }
                }
            }
        }
    }
}

@Composable
fun SubScreen(screen: String, onBack: () -> Unit, go: (String) -> Unit) {
    when (screen) {
        "history" -> HistoryScreen(onBack)
        "memory" -> MemoryScreen(onBack)
        "voice" -> VoiceScreen(onBack)
        "ai" -> AiScreen(onBack)
        "perm" -> PermissionsScreen(onBack)
        "apps" -> AppsScreen(onBack)
        "settings" -> SettingsScreen(onBack)
        "diag" -> DiagScreen(onBack)
        else -> MenuScreen(onBack, go)
    }
}

private val timeFmt = SimpleDateFormat("dd/MM HH:mm", Locale.ITALY)

@Composable
private fun HistoryScreen(onBack: () -> Unit) {
    val g = LocalContext.current.graph
    val turns by g.memory.history.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    ScreenScaffold("Cronologia", onBack) {
        if (!g.settings.memoryEnabled) Hint("La memoria è disattivata: le nuove conversazioni non vengono salvate.")
        OutlinedButton(onClick = { scope.launch { g.memory.clearHistory() } }) { Text("Cancella tutta la cronologia") }
        if (turns.isEmpty()) Hint("Nessuna conversazione salvata.")
        turns.forEach { t ->
            JCard {
                Text("${if (t.role == "user") "Tu" else "Jarvis"} · ${timeFmt.format(Date(t.ts))}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Text(t.text, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun MemoryScreen(onBack: () -> Unit) {
    val g = LocalContext.current.graph
    val tick by g.settings.tick.collectAsState()
    val items by g.memory.items.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var newText by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val t = tick
    ScreenScaffold("Memoria", onBack) {
        JCard {
            SwitchRow("Memoria attiva", g.settings.memoryEnabled, "Salva solo fatti brevi che chiedi di ricordare e la cronologia testuale. Mai schermate intere né password.") { g.settings.memoryEnabled = it }
            Hint("Tutto resta sul telefono, in un database locale non incluso nei backup di Android.")
        }
        JCard {
            OutlinedTextField(newText, { newText = it }, label = { Text("Aggiungi un ricordo") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { scope.launch { g.memory.remember(newText); newText = "" } }, enabled = newText.isNotBlank()) { Text("Salva") }
        }
        if (items.isEmpty()) Hint("Nessun ricordo salvato.")
        items.forEach { m ->
            JCard {
                Text(m.text, color = MaterialTheme.colorScheme.onSurface)
                Row {
                    IconButton(onClick = { editing = m }) { Icon(Icons.Default.Edit, "Modifica") }
                    IconButton(onClick = { scope.launch { g.memory.delete(m.id) } }) { Icon(Icons.Default.Delete, "Elimina") }
                }
            }
        }
        if (items.isNotEmpty()) OutlinedButton(onClick = { confirmClear = true }) { Text("Cancella tutta la memoria") }
    }
    editing?.let { m ->
        var txt by remember(m.id) { mutableStateOf(m.text) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Modifica ricordo") },
            text = { OutlinedTextField(txt, { txt = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { scope.launch { g.memory.edit(m, txt) }; editing = null }) { Text("Salva") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Annulla") } })
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Cancellare tutta la memoria?") },
        text = { Text("L'operazione non si può annullare.") },
        confirmButton = { TextButton(onClick = { scope.launch { g.memory.clearMemory() }; confirmClear = false }) { Text("Cancella") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } })
}

@Composable
private fun PermissionsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }
    @Suppress("UNUSED_VARIABLE") val r = refresh
    val a11y by JarvisAccessibilityService.connected.collectAsState()
    val mic = ctx.hasMic()
    fun open(i: Intent) { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { DiagLog.e("Permessi", "Impossibile aprire impostazioni", e) } }
    val micLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }

    val role = ctx.getSystemService(android.app.role.RoleManager::class.java)
    val roleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val assistant = role.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT) && role.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT)
    val overlay = AndroidSettings.canDrawOverlays(ctx)
    ScreenScaffold("Configura il tuo assistente", onBack) {
        JCard {
            Section("WHATSAPP E RUBRICA")
            Hint("Consenti la rubrica per preparare messaggi a un nome senza dover cercare la chat sullo schermo. Il messaggio resta una bozza; l’invio richiede conferma. Se ci sono più numeri, Jarvis ti chiede quale usare.")
            OutlinedButton(onClick = { micLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS)) }) { Text("Consenti rubrica per WhatsApp") }
        }
        JCard {
            Section("ASSISTENTE DI ANDROID")
            Text(if (assistant) "Jarvis è il tuo assistente predefinito ✓" else "Richiama Jarvis con il gesto assistente", fontWeight = FontWeight.SemiBold)
            Hint("Seleziona Jarvis come assistente digitale per aprire il pannello in basso con il gesto o il pulsante assistente del telefono. Per «Hey Jarvis», scarica i modelli vocali e attiva l’ascolto dalla Home: rimane una notifica con il pulsante Ferma.")
            Button(onClick = {
                try {
                    if (role.isRoleAvailable(android.app.role.RoleManager.ROLE_ASSISTANT)) roleLauncher.launch(role.createRequestRoleIntent(android.app.role.RoleManager.ROLE_ASSISTANT))
                    else open(Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS))
                } catch (e: Exception) { open(Intent(AndroidSettings.ACTION_VOICE_INPUT_SETTINGS)) }
            }) { Text(if (assistant) "Gestisci assistente" else "Scegli Jarvis come assistente") }
        }
        JCard {
            Section("PANNELLO SOPRA LE ALTRE APP")
            Text(if (overlay) "Pannello consentito ✓" else "Mostra Jarvis quando dici Hey Jarvis", fontWeight = FontWeight.SemiBold)
            Hint("Facoltativo se Jarvis è già l’assistente predefinito. Consente il pannello compatto sopra le app mentre ascolta e risponde. Il pannello si nasconde durante le azioni per lasciare libero lo schermo.")
            OutlinedButton(onClick = { open(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }) { Text("Consenti pannello in sovrapposizione") }
        }
        JCard {
            Text("Microfono: ${if (mic) "concesso ✓" else "non concesso"}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Hint("Serve per ascoltare «Hey Jarvis» e i tuoi comandi. L'audio è elaborato sul telefono; i comandi vocali non vengono inviati a nessun server (solo il testo del compito va al modello AI cloud che scegli).")
            if (!mic) Button(onClick = { micLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO, android.Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Concedi microfono e notifiche") }
        }
        JCard {
            Text("Accessibilità: ${if (a11y) "attiva ✓" else "non attiva"}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Hint("Permette a Jarvis di leggere lo schermo e di toccare, scrivere e scorrere nelle altre app, solo quando gli affidi un compito.")
            Button(onClick = { open(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Apri impostazioni accessibilità") }
            Section("SE ANDROID DICE «ACCESSO NEGATO»")
            Hint("1. Premi «Apri Info app» qui sotto.\n2. Tocca ⋮ in alto a destra → Consenti impostazioni limitate e conferma con PIN o impronta.\n3. Torna ad Accessibilità → Jarvis AI e attiva il servizio.\nQuesta autorizzazione permette di leggere e interagire con lo schermo: concedila solo alla tua build verificata. L’app non può attivarla da sola.")
            OutlinedButton(onClick = { open(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Apri Info app") }
        }
        JCard {
            Text("Restrizioni aggiuntive di Android", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Hint("Se un criterio di protezione del dispositivo continua a bloccare l’accessibilità, Jarvis lo segnala e non lo aggira. La chat e l’apertura di app e link restano utilizzabili; leggere o toccare le altre app richiede il servizio attivo.")
        }
        JCard {
            Text("Batteria", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Hint("Per mantenere l'ascolto stabile imposta Jarvis su «Senza restrizioni» nelle impostazioni batteria dell'app.")
            OutlinedButton(onClick = { open(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text("Impostazioni app") }
        }
    }
}

@Composable
private fun AppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    var refresh by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val r = refresh
    val profiles = g.launcher.allProfiles()
    ScreenScaffold("App e integrazioni", onBack) {
        Hint("Jarvis apre e controlla le app tramite scorciatoie (link) e accessibilità. Ogni profilo contiene suggerimenti per l'agente. Puoi aggiungere altre app.")
        profiles.forEach { p ->
            val installed = g.launcher.find(p.label) != null
            JCard {
                Text("${p.label} · ${if (installed) "installata" else "non trovata"}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Hint(p.hint)
                if (!p.builtIn) TextButton(onClick = { g.launcher.saveCustom(g.launcher.customProfiles().filter { it.id != p.id }); refresh++ }) { Text("Rimuovi") }
            }
        }
        Button(onClick = { adding = true }) { Text("Aggiungi un'app") }
    }
    if (adding) {
        var name by remember { mutableStateOf("") }
        var hint by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Nuova app") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Nome dell'app (come sul telefono)") })
                    OutlinedTextField(hint, { hint = it }, label = { Text("Suggerimenti per Jarvis (facoltativo)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val app = g.launcher.find(name)
                    if (app != null) {
                        g.launcher.saveCustom(g.launcher.customProfiles() + AppProfile(app.pkg, app.label, listOf(app.pkg), hint.ifBlank { "Usa l'app «${app.label}» tramite lo schermo." }, false))
                        refresh++
                    }
                    adding = false
                }) { Text("Aggiungi") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Annulla") } })
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val g = LocalContext.current.graph
    val tick by g.settings.tick.collectAsState()
    @Suppress("UNUSED_VARIABLE") val t = tick
    val s = g.settings
    ScreenScaffold("Impostazioni", onBack) {
        JCard {
            Section("ASPETTO")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "Scuro", "light" to "Chiaro", "system" to "Sistema").forEach { (id, label) ->
                    FilterChip(selected = s.themeMode == id, onClick = { s.themeMode = id }, label = { Text(label) })
                }
            }
        }
        JCard {
            Section("ASCOLTO E CONVERSAZIONE")
            SwitchRow("Parola di attivazione «Hey Jarvis»", s.wakeEnabled, "Se disattivata, il microfono non cerca la parola e Jarvis risponde solo ai comandi scritti.") { s.wakeEnabled = it }
            SliderRow("Finestra di conversazione continua", s.followUpSeconds.toFloat(), 0f..15f, { "${it.toInt()} s" }) { s.followUpSeconds = it.toInt() }
            Hint("Dopo una risposta Jarvis resta in ascolto per questo tempo senza bisogno della parola di attivazione; poi torna in attesa. 0 = sempre serve «Hey Jarvis».")
            SliderRow("Silenzio che chiude il comando", s.endSilenceMs.toFloat(), 400f..1800f, { "${it.toInt()} ms" }) { s.endSilenceMs = (it / 50).toInt() * 50 }
            SwitchRow("Voce sintetica attiva", s.ttsEnabled) { s.ttsEnabled = it }
            SliderRow("Velocità della voce", s.ttsSpeed, 0.7f..1.4f, { "%.2f×".format(it) }) { s.ttsSpeed = it }
        }
        JCard {
            Section("AGENTE")
            SliderRow("Passaggi massimi per compito", s.maxSteps.toFloat(), 5f..40f, { "${it.toInt()}" }) { s.maxSteps = it.toInt() }
            SwitchRow("Usa screenshot quando serve", s.useScreenshots, "Gli screenshot vengono inviati al modello cloud scelto solo se la schermata non è leggibile come testo o se il modello li richiede. Le schermate protette non sono catturabili.") { s.useScreenshots = it }
            SliderRow("Scarica il modello vocale dalla RAM dopo", s.sttModelAutoUnloadSec.toFloat(), 30f..600f, { "${it.toInt()} s" }) { s.sttModelAutoUnloadSec = it.toInt() }
        }
        JCard {
            Section("INFORMAZIONI")
            Text("Jarvis AI ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurface)
            Hint("Componenti open source: sherpa-onnx (Apache-2.0) con ONNX Runtime, NVIDIA Parakeet TDT 0.6B v3 (CC-BY-4.0), Silero VAD (MIT), 3D-Speaker CAM++ (Apache-2.0), voci Piper (MIT/licenze dei dataset), modello AI a tua scelta (es. Qwen3.8-27B, Apache-2.0). Il motore sherpa-onnx v1.13.8 include espeak-ng (GPL-3.0) per la fonetica italiana: uso personale.")
            Hint("Limiti: l'agente dipende dai limiti gratuiti dei provider cloud; le app protette (banca, FLAG_SECURE) non sono leggibili; il riconoscimento della tua voce e la parola «Hey Jarvis» non sono addestrati sull'italiano e vanno tarati con i tuoi test.")
        }
    }
}

@Composable
private fun DiagScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val log by DiagLog.lines.collectAsState()
    val models by g.models.states.collectAsState()
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val m = models
    fun ok(b: Boolean) = if (b) "✓" else "✗"
    ScreenScaffold("Diagnostica", onBack) {
        JCard {
            Section("STATO")
            it.jarvis.assistant.diag.CrashReport.last(ctx)?.let { Text("Ultimo arresto registrato\n$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
            val lines = listOf(
                "Microfono (permesso) ${ok(ctx.hasMic())}",
                "Accessibilità attiva ${ok(JarvisAccessibilityService.isConnected)}",
                "Pannello sopra le app ${ok(AndroidSettings.canDrawOverlays(ctx))}",
                "Assistente predefinito ${ok(ctx.getSystemService(android.app.role.RoleManager::class.java).isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT))}",
                "Ascolto avviato ${ok(g.core.micRunning)}",
                "Modello «Hey Jarvis» ${ok(g.models.isInstalled(ModelCatalog.kws))}",
                "Modello VAD ${ok(g.models.isInstalled(ModelCatalog.vad))}",
                "Riconoscimento vocale (Parakeet) ${ok(g.models.isInstalled(ModelCatalog.stt))}",
                "Voce italiana installata ${ok(g.tts.available())}",
                "Profilo della tua voce ${ok(g.speaker.hasProfile())}",
                "Provider AI utilizzabili: ${g.providers.usable().joinToString { it.id }.ifBlank { "nessuno" }}",
            )
            lines.forEach { Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) }
        }
        JCard {
            Section("PROVA MODELLO AI")
            Button(enabled = !busy, onClick = {
                busy = true; testResult = "…"
                scope.launch {
                    testResult = try { val r = g.llm.chat(listOf(ChatMsg("user", "Rispondi solo con la parola: pronto")), allowImages = false, maxTokens = 40)
                        "OK (${r.provider}): ${r.text.take(80)}" } catch (e: Exception) { "ERRORE: ${e.message}" }
                    busy = false
                }
            }) { Text("Invia messaggio di prova") }
            if (testResult.isNotBlank()) Text(testResult, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
        JCard {
            Section("LOG (ultime ${log.size} righe, nessun segreto)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("log", DiagLog.asText()))
                }) { Text("Copia") }
                OutlinedButton(onClick = { DiagLog.clear() }) { Text("Svuota") }
            }
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                log.asReversed().forEach { l ->
                    Text("${l.time} ${l.level}/${l.tag}: ${l.msg}", fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                        color = when (l.level) { 'E' -> JColors.Danger; 'W' -> JColors.Warn; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                }
            }
        }
    }
}
