package it.jarvis.assistant.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.jarvis.assistant.ai.ProviderSpec
import it.jarvis.assistant.ai.Providers
import it.jarvis.assistant.diag.DiagLog
import it.jarvis.assistant.graph
import it.jarvis.assistant.models.ModelCatalog
import it.jarvis.assistant.models.ModelSpec
import it.jarvis.assistant.models.ModelState
import it.jarvis.assistant.security.BackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================ MODELLI AI + ZONA BACKUP

@Composable
fun AiScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val tick by g.settings.tick.collectAsState()
    @Suppress("UNUSED_VARIABLE") val t = tick
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }

    // --- stato backup ---
    var exportDialog by remember { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var exportPass by remember { mutableStateOf("") }
    var keepAuto by remember { mutableStateOf(true) }
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            msg = try {
                withContext(Dispatchers.IO) {
                    if (keepAuto) g.backup.enableAuto(uri, exportPass.toCharArray()) else g.backup.exportTo(uri, exportPass.toCharArray())
                }
                "Backup salvato${if (keepAuto) " e aggiornato in automatico a ogni modifica" else ""}."
            } catch (e: Exception) { "Backup non riuscito: ${e.message}" }
            exportPass = ""
        }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importUri = uri }

    ScreenScaffold("Modelli AI e chiavi API", onBack) {
        if (g.backup.restoreAvailable()) {
            JCard {
                Text("Nessuna chiave trovata, ma hai un backup automatico configurato.", color = JColors.Warn, fontWeight = FontWeight.SemiBold)
                Hint("Dopo una reinstallazione le chiavi vanno ripristinate dal file di backup: premi «Importa backup» qui sotto e inserisci la passphrase.")
            }
        }
        Hint("Servono solo chiavi API gratuite. Le chiavi sono cifrate nel Keystore di Android e non sono mai nel codice o su GitHub. Jarvis prova i provider nell'ordine indicato; se uno raggiunge il limite gratuito passa al successivo.")

        JCard {
            Section("ZONA BACKUP CHIAVI API")
            Hint("Le chiavi sopravvivono agli aggiornamenti dell'app se la firma resta la stessa. Alla disinstallazione, invece, Android cancella le chiavi cifrate: per non doverle reinserire salva un backup cifrato (AES-256 con passphrase) dove vuoi — Download, Google Drive, scheda SD — e reimportalo in un tap.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportDialog = true }) { Text("Esporta backup") }
                OutlinedButton(onClick = { openDoc.launch(arrayOf("application/json", "application/octet-stream", "*/*")) }) { Text("Importa backup") }
            }
            if (g.settings.autoBackup) {
                Hint("Backup automatico ATTIVO — ultimo salvataggio: ${g.settings.lastBackup.ifBlank { "—" }}")
                TextButton(onClick = { g.backup.disableAuto(); msg = "Backup automatico disattivato." }) { Text("Disattiva backup automatico") }
            } else if (g.settings.lastBackup.isNotBlank()) Hint("Ultimo backup manuale: ${g.settings.lastBackup}")
            if (msg.isNotBlank()) Text(msg, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }

        Providers.all.forEach { spec -> ProviderCard(spec) }

        JCard {
            Section("ORDINE DEI PROVIDER")
            var order by remember { mutableStateOf(g.settings.providerOrder) }
            OutlinedTextField(order, { order = it; g.settings.providerOrder = it.replace(" ", "") }, label = { Text("id separati da virgola") }, modifier = Modifier.fillMaxWidth())
            Hint("Default: groq,google,openrouter,nvidia,custom. Il provider a pagamento «openai» non è incluso: aggiungilo qui solo se vuoi usarlo come fallback, accettando i costi.")
        }
    }

    if (exportDialog) {
        AlertDialog(onDismissRequest = { exportDialog = false }, title = { Text("Proteggi il backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Hint("Scegli una passphrase di almeno 8 caratteri. Non è recuperabile: se la perdi il backup è inutilizzabile.")
                    OutlinedTextField(exportPass, { exportPass = it }, label = { Text("Passphrase") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(keepAuto, { keepAuto = it })
                        Text("Aggiorna il file automaticamente a ogni modifica delle chiavi", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextButton(enabled = exportPass.length >= 8, onClick = { exportDialog = false; createDoc.launch("jarvis-backup-chiavi.json") }) { Text("Scegli dove salvare") } },
            dismissButton = { TextButton(onClick = { exportDialog = false; exportPass = "" }) { Text("Annulla") } })
    }
    importUri?.let { uri ->
        var pass by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { importUri = null }, title = { Text("Importa backup") },
            text = { OutlinedTextField(pass, { pass = it }, label = { Text("Passphrase del backup") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = pass.isNotEmpty(), onClick = {
                    importUri = null
                    scope.launch {
                        msg = try { val n = withContext(Dispatchers.IO) { g.backup.importFrom(uri, pass.toCharArray()) }; "Ripristinate $n chiavi." }
                        catch (e: BackupException) { "Errore: ${e.message}" } catch (e: Exception) { "Errore: ${e.message}" }
                    }
                }) { Text("Ripristina") }
            },
            dismissButton = { TextButton(onClick = { importUri = null }) { Text("Annulla") } })
    }
}

@Composable
private fun ProviderCard(spec: ProviderSpec) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val tick by g.settings.tick.collectAsState()
    @Suppress("UNUSED_VARIABLE") val t = tick
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var model by remember(tick) { mutableStateOf(g.providers.model(spec)) }
    var base by remember(tick) { mutableStateOf(g.providers.baseUrl(spec)) }
    var info by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<String>?>(null) }
    val hasKey = g.providers.hasKey(spec.id)

    JCard {
        Text(spec.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Hint(spec.note)
        SwitchRow("Abilitato", g.settings.providerEnabled(spec.id)) { g.settings.setProviderEnabled(spec.id, it) }
        OutlinedTextField(
            key, { key = it }, label = { Text(if (hasKey) "Chiave salvata ✓ (scrivi per sostituirla)" else "Chiave API") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(base, { base = it }, label = { Text("URL base (compatibile OpenAI)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(model, { model = it }, label = { Text("Nome modello") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (key.isNotBlank()) g.providers.setKey(spec.id, key)
                g.providers.setBaseUrl(spec.id, base); g.providers.setModel(spec.id, model)
                key = ""; info = "Salvato."
            }) { Text("Salva") }
            OutlinedButton(enabled = hasKey, onClick = {
                g.providers.setBaseUrl(spec.id, base)
                scope.launch { info = "Cerco i modelli…"; info = try { list = g.llm.listModels(spec); "${list!!.size} modelli trovati." } catch (e: Exception) { "Errore: ${e.message}" } }
            }) { Text("Elenca modelli") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = hasKey && model.isNotBlank(), onClick = {
                g.providers.setModel(spec.id, model); g.providers.setBaseUrl(spec.id, base)
                scope.launch {
                    info = "Provo…"
                    info = try { val r = g.llm.chat(listOf(it.jarvis.assistant.ai.ChatMsg("user", "Rispondi solo: pronto")), allowImages = false, maxTokens = 30, only = spec.id); "Funziona: «${r.text.take(40)}»" } catch (e: Exception) { "Errore: ${e.message?.take(200)}" }
                }
            }) { Text("Prova") }
            if (spec.signupUrl.isNotBlank()) TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spec.signupUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Ottieni chiave") }
            if (hasKey) TextButton(onClick = { g.providers.setKey(spec.id, ""); info = "Chiave rimossa." }) { Text("Rimuovi") }
        }
        if (info.isNotBlank()) Text(info, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
    list?.let { models ->
        AlertDialog(onDismissRequest = { list = null }, title = { Text("Scegli il modello") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    models.forEach { m -> TextButton(onClick = { model = m; g.providers.setModel(spec.id, m); list = null }) { Text(m, fontSize = 13.sp) } }
                }
            },
            confirmButton = { TextButton(onClick = { list = null }) { Text("Chiudi") } })
    }
}

// ============================================================ VOCE E MODELLI

private val enrollPhrases = listOf(
    "Hey Jarvis, che tempo fa oggi a Firenze?",
    "Apri WhatsApp e cerca un contatto per nome.",
    "Ricordami di comprare il latte domani mattina.",
    "Mostrami le foto dell'ultima vacanza al mare.",
    "Cerca un albergo a Roma per il prossimo fine settimana.",
)

@Composable
fun VoiceScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val states by g.models.states.collectAsState()
    val tick by g.settings.tick.collectAsState()
    @Suppress("UNUSED_VARIABLE") val t = tick
    val scope = rememberCoroutineScope()
    var enrollMsg by remember { mutableStateOf("") }
    var enrolling by remember { mutableStateOf(false) }
    val voiceState by g.core.state.collectAsState()
    val canEnroll = !g.core.micRunning && voiceState in setOf(it.jarvis.assistant.core.JState.OFF, it.jarvis.assistant.core.JState.ERROR)
    var testMsg by remember { mutableStateOf("") }
    var previewBusy by remember { mutableStateOf(false) }
    var previewStatus by remember { mutableStateOf("") }
    val wakeScore by g.wake.score.collectAsState()
    val wakePeak by g.wake.peak.collectAsState()
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    ScreenScaffold("Voce e modelli", onBack) {
        JCard {
            Section("MODELLI VOCALI")
            Hint("Il rilevatore Hey Jarvis è già incluso. Scarica con il Wi-Fi riconoscimento italiano, VAD e una voce. Sono eseguiti sul telefono; il download riprende se interrotto.")
            Button(onClick = {
                listOf(ModelCatalog.kws, ModelCatalog.vad, ModelCatalog.stt, ModelCatalog.voices.first()).forEach { g.models.install(it) }
            }) { Text("Scarica tutti gli essenziali") }
        }
        ModelCatalog.all.forEach { spec -> ModelRow(spec, states[spec.id] ?: ModelState.Missing) }

        JCard {
            Section("VOCE DI JARVIS")
            val installed = ModelCatalog.voices.filter { g.models.isInstalled(it) }
            if (installed.isEmpty()) Hint("Installa almeno una voce italiana dalla lista sopra.")
            installed.forEach { v ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = g.settings.ttsVoice == v.id, onClick = { g.settings.ttsVoice = v.id })
                    Text(v.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
            }
            OutlinedButton(enabled = installed.isNotEmpty() && !previewBusy, onClick = {
                previewBusy = true; previewStatus = "Preparo la voce…"
                scope.launch {
                    try { val ok = g.tts.speak("Ciao Alessandro, sono Jarvis. Come posso aiutarti?", preview = true)
                        previewStatus = if (ok) "Anteprima riprodotta." else g.tts.failure.value ?: "Anteprima interrotta."
                    } finally { previewBusy = false }
                }
            }) { Text(if (previewBusy) "Riproduzione…" else "Ascolta la voce") }
            if (previewBusy) TextButton(onClick = { g.tts.stop() }) { Text("Interrompi anteprima") }
            if (previewStatus.isNotBlank()) Hint(previewStatus)
            Hint("Scegli ad orecchio la voce più naturale: la qualità percepita è soggettiva e non l'ho potuta ascoltare per te.")
        }

        JCard {
            Section("PAROLA DI ATTIVAZIONE")
            SliderRow("Sensibilità", 1f - g.settings.hotwordThreshold, 0.15f..0.85f, { "%.2f".format(it) }) { g.settings.hotwordThreshold = 1f - it }
            Hint("Modello dedicato Hey Jarvis integrato. Le regolazioni sono immediate: sensibilità maggiore riconosce più facilmente ma può aumentare i falsi richiami. L’ascolto deve essere attivo (notifica presente).")
            Hint("Segnale attuale: %.2f · massimo osservato: %.2f · soglia: %.2f".format(wakeScore, wakePeak, g.settings.hotwordThreshold))
        }

        JCard {
            Section("RICONOSCIMENTO DELLA TUA VOCE (FACOLTATIVO)")
            Hint("Jarvis può eseguire solo comandi pronunciati con la tua voce. Il profilo è un vettore matematico cifrato nel Keystore: non è audio, non viene esportato né salvato nei backup. Il modello non è stato validato su italiano: calibra la soglia con i tuoi test.")
            Text(if (g.speaker.hasProfile()) "Profilo vocale: presente ✓" else "Profilo vocale: assente", color = MaterialTheme.colorScheme.onSurface)
            Button(enabled = canEnroll && !enrolling && g.speaker.modelInstalled && g.models.isInstalled(ModelCatalog.vad), onClick = {
                if (!ctx.hasMic()) { micPerm.launch(android.Manifest.permission.RECORD_AUDIO); return@Button }
                enrolling = true
                scope.launch {
                    try {
                    val samples = mutableListOf<FloatArray>()
                    for ((i, ph) in enrollPhrases.withIndex()) {
                        enrollMsg = "Frase ${i + 1}/${enrollPhrases.size} — leggi ad alta voce:\n«$ph»"
                        val seg = g.core.recordForEnrollment(12_000)
                        if (seg != null) samples += seg else enrollMsg = "Non ho sentito la frase ${i + 1}, la salto."
                        kotlinx.coroutines.delay(400)
                    }
                    g.core.endEnrollmentSession()
                    enrollMsg = "Elaboro…"
                    val res = withContext(Dispatchers.Default) { g.speaker.enroll(samples) }
                    if (res.ok) g.settings.speakerThreshold = res.suggestedThreshold
                    enrollMsg = res.message + if (res.ok) " Soglia impostata a %.2f.".format(res.suggestedThreshold) else ""
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { enrollMsg = "Registrazione non riuscita: riprova."; DiagLog.e("Voce", "Registrazione", e) }
                    finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Default) { g.core.endEnrollmentSession() }; enrolling = false }
                }
            }) { Text(if (enrolling) "Registrazione in corso…" else "Registra la mia voce (5 frasi)") }
            if (!canEnroll && !enrolling) Hint("Spegni prima Hey Jarvis dalla Home per registrare o verificare il profilo vocale.")
            if (!g.speaker.modelInstalled) Hint("Scarica prima il modello «Riconoscimento della tua voce».")
            if (enrollMsg.isNotBlank()) Text(enrollMsg, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            OutlinedButton(enabled = canEnroll && g.speaker.hasProfile() && !enrolling, onClick = {
                enrolling = true; testMsg = "Di' una frase…"
                scope.launch {
                    try {
                    val seg = g.core.recordForEnrollment(9_000)
                    g.core.endEnrollmentSession()
                    testMsg = if (seg == null) "Non ho sentito nulla." else {
                        val s = withContext(Dispatchers.Default) { g.speaker.score(seg) }
                        if (s == null) "Frase troppo breve." else "Somiglianza %.2f (soglia %.2f) → %s".format(s, g.settings.speakerThreshold, if (s >= g.settings.speakerThreshold) "ACCETTATA" else "RIFIUTATA")
                    }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { testMsg = "Prova non riuscita: riprova."; DiagLog.e("Voce", "Verifica", e) }
                    finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Default) { g.core.endEnrollmentSession() }; enrolling = false }
                }
            }) { Text("Prova il riconoscimento") }
            if (testMsg.isNotBlank()) Text(testMsg, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            SwitchRow("Accetta solo la mia voce", g.settings.speakerVerify, "Se attivo e il profilo non c'è, i comandi vengono rifiutati.") { g.settings.speakerVerify = it }
            SliderRow("Soglia di somiglianza", g.settings.speakerThreshold, 0.25f..0.85f, { "%.2f".format(it) }) { g.settings.speakerThreshold = it }
            OutlinedButton(enabled = g.speaker.hasProfile(), onClick = { g.speaker.delete(); enrollMsg = "Profilo eliminato."; g.settings.speakerVerify = false }) { Text("Elimina il profilo vocale") }
        }
    }
}

@Composable
private fun ModelRow(spec: ModelSpec, st: ModelState) {
    val g = LocalContext.current.graph
    JCard {
        Text(spec.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
        Hint(spec.description)
        Hint("~${spec.approxMb} MB · licenza ${spec.license}${if (spec.required) " · essenziale" else ""}")
        when (st) {
            is ModelState.Installed -> {
                Text("Installato ✓", color = JColors.Ok)
                TextButton(enabled = !spec.bundled && !g.core.micRunning, onClick = { g.models.remove(spec) }) { Text("Rimuovi") }
            }
            is ModelState.Downloading -> {
                val p = if (st.total > 0) st.done.toFloat() / st.total else 0f
                if (st.total > 0) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                Hint("${st.done / 1_000_000} / ${if (st.total > 0) "${st.total / 1_000_000}" else "?"} MB")
                TextButton(onClick = { g.models.cancel(spec) }) { Text("Annulla (il download potrà riprendere)") }
            }
            is ModelState.Extracting -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Hint("Estrazione in corso…") }
            is ModelState.Failed -> {
                Text("Errore: ${st.message}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Button(onClick = { g.models.install(spec) }) { Text("Riprova") }
            }
            is ModelState.Missing -> Button(onClick = { g.models.install(spec) }) { Text("Scarica") }
        }
    }
}
