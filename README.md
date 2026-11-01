# Jarvis AI (Android)

### Aggiornamento interfaccia e stabilità — 1 novembre 2026

Home ridisegnata, icona fornita da Alessandro, compositore chat sopra la tastiera, guida permessi aggiornata e assistente Android selezionabile. Il pannello vocale compare in basso nelle altre app scegliendo Jarvis come assistente predefinito, oppure concedendo «Mostra sopra altre app». Il caricamento dei modelli avviene fuori dal thread UI.

«Hey Jarvis» richiede modelli KWS/VAD/STT scaricati, permesso microfono e ascolto avviato esplicitamente dalla Home. Il servizio usa una notifica e un wake lock: il consumo può essere superiore a Gemini e non è equivalente al suo hotword hardware. Dopo un arresto forzato o un riavvio, riattivare dalla Home. Nessun permesso viene concesso automaticamente.


Assistente personale autonomo per Android, pensato per **Google Pixel 9 Pro XL (Android 17)**: ti ascolta con «Hey Jarvis», capisce l'italiano, parla con voce italiana naturale e controlla altre app (WhatsApp, Google Foto, Booking, ChatGPT e le tue) leggendo lo schermo e toccando/scrivendo tramite il servizio di accessibilità.

Tutti i componenti sono **open source e gratuiti**; nessun abbonamento obbligatorio. Nessuna chiave API è nel codice.

> ⚠️ **Stato onesto**: l’APK compila, i test unitari (sicurezza, cifratura backup, risposte scritte) passano in CI e 4 test UI passano su emulatore Android 15, ma **nessuna funzione è ancora stata provata su un telefono reale**. Leggi `docs/STATO.md` per lo stato di ogni funzione e `docs/TEST_PLAN.md` per i test da eseguire.

## Installazione (Pixel)
1. Apri la pagina **Releases** del repository dal telefono e scarica `Jarvis-AI-x.y.z.apk` (l'ultima versione).
2. Installa l'APK (consenti «Installa app sconosciute» per il browser/gestore file usato).
3. Apri Jarvis → **Menu → Permessi**: concedi microfono e notifiche.
4. **Accessibilità**: Menu → Permessi → «Apri impostazioni accessibilità» → Jarvis AI → attiva. Se l'interruttore è grigio: Info app → ⋮ → **Consenti impostazioni limitate**.
5. Per il pannello nelle altre app: **Menu → Permessi → Scegli Jarvis come assistente**, oppure **Consenti pannello in sovrapposizione**. Eventuali ulteriori restrizioni imposte dal dispositivo vengono rispettate; la chat resta utilizzabile.
6. **Menu → Voce e modelli** → «Scarica tutti gli essenziali» (circa 600 MB, con Wi-Fi; riprende se interrotto).
7. **Menu → Modelli AI e chiavi API**: inserisci almeno una chiave gratuita (consigliato Groq, poi Google AI Studio come riserva), premi *Prova*.
8. **Zona backup chiavi**: premi *Esporta backup*, scegli una passphrase e dove salvare il file: da lì in poi le chiavi si ripristinano in un tap anche dopo una reinstallazione.
9. Torna alla Home: il tasto di accensione in alto abilita l’ascolto di «Hey Jarvis»; il microfono in basso avvia subito un comando. La notifica **Ferma** spegne l’ascolto.

## Aggiornamenti senza reinserire le chiavi
- **Aggiornare sopra la versione installata** mantiene chiavi, memoria e impostazioni, *a condizione che la firma dell'APK sia la stessa*. La CI firma con una chiave generata una volta e conservata nella cache di GitHub (rinnovata ogni settimana dal workflow `keep-signing-key`). Per una firma **permanente** crea i secret come spiegato in `scripts/make-keystore.sh`.
- Se Android rifiuta l'aggiornamento per firma diversa o devi disinstallare, **la Zona backup** ripristina le chiavi (file cifrato AES-256-GCM, PBKDF2-SHA256 310k iterazioni). Con il «backup automatico» il file viene riscritto a ogni modifica delle chiavi.

## Architettura (moduli logici in un unico modulo Gradle)
| Pacchetto | Ruolo |
|---|---|
| `ui` | Compose: Home con chat e nuova icona, menu e schermate (cronologia, memoria, voce, modelli AI/backup, permessi, app, impostazioni, diagnostica), tema scuro/chiaro |
| `voice` | Mic (AEC/NS), wake word (sherpa-onnx KWS), VAD Silero, STT Parakeet TDT 0.6B v3 (it), TTS Piper (it), verifica parlante CAM++ |
| `core` | `JarvisCore` (stati, barge-in, conversazione a finestra limitata, stop immediato), `Settings` |
| `ai` | `LlmClient` (provider compatibili OpenAI con catena di fallback), `ProviderStore`, `BackupZone` |
| `agent` | Ciclo agente (osserva→decidi→agisci→verifica→recupera), `Safety` (conferme, blocchi) |
| `device` | Servizio di accessibilità, osservatore schermo (albero → testo), controller azioni, lancio app |
| `memory` | Room: ricordi e cronologia, cancellabili/disattivabili |
| `models` | Catalogo e download con ripresa dei modelli (nessun modello nell'APK) |
| `security` | Keystore AES-GCM, backup cifrato |
| `service` | Servizio in primo piano (tipo microfono) |
| `diag` | Log diagnostico in memoria, senza segreti |

Libreria nativa: **sherpa-onnx v1.13.8** (Apache-2.0) con ONNX Runtime; sorgenti Kotlin in `app/src/main/java/com/k2fsa/sherpa/onnx` (licenza in `LICENSE-sherpa-onnx.txt`). È l'ultima versione con il fonemizzatore espeak-ng richiesto dalle voci Piper italiane (la v2.x lo ha rimosso). espeak-ng è GPL-3.0: uso personale.

## Privacy e sicurezza
- Audio, wake word, VAD, STT, TTS e verifica voce: **sul telefono**. Il comando trascritto e il testo (o screenshot) dello schermo durante un compito vanno al provider AI che scegli (Groq/Google/…): nei piani gratuiti valgono le loro condizioni (Google può usare i dati fuori da UE/UK/SEE).
- Chiavi API: cifrate nel Keystore, mai nei log né nel repository. Memoria e cronologia: database locale escluso dai backup Android.
- Operazioni sensibili (invio messaggi, eliminazioni, condivisioni…) richiedono conferma a voce o a schermo; pagamenti e prenotazioni sono **bloccati** (Booking: solo ricerca). Password mai lette né scritte.
- Il contenuto di schermate, pagine web e risposte di altre AI è trattato come *dato*, non come istruzioni.
- Il repository è pubblico: **mai committare** chiavi, backup (`jarvis-backup*.json`), profili vocali, modelli.

## Build
La build avviene su GitHub Actions (`.github/workflows/build-apk.yml`): JDK 17, Gradle 8.14.3, AGP 8.11.1, Kotlin 2.2.0. Localmente: scarica le `.so` di sherpa-onnx v1.13.8 (arm64-v8a) in `app/src/main/jniLibs/arm64-v8a/` e lancia `gradle :app:assembleRelease`.

## Licenze dei modelli
Parakeet TDT 0.6B v3 (CC-BY-4.0), Silero VAD (MIT), 3D-Speaker CAM++ (Apache-2.0), KWS zipformer (Apache-2.0), voci Piper (MIT + licenza del dataset di ciascuna voce), Qwen3.8-27B (Apache-2.0, via provider).
