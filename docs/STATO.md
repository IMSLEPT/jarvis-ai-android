# Stato delle funzioni

Legenda: **C** = compila, coperto da test automatici in CI · **I** = implementato, compila, **non collaudato sul Pixel** · **E** = verificato su emulatore Android 15 · **P** = parziale · **N** = non implementato / non disponibile.
Data aggiornamento: correzioni voce/WhatsApp (01/11/2026). *Nessuna funzione è stata provata su un Pixel reale: la colonna «I» significa che il codice esiste e compila, non che sia verificato.*

| Area | Funzione | Stato | Note |
|---|---|---|---|
| Sicurezza | Conferma per azioni sensibili, blocco pagamenti/prenotazioni | C | `SafetyTest` (regole su etichette); l'integrazione nel ciclo agente è I |
| Sicurezza | Chiavi API cifrate (Keystore AES-GCM) | I | |
| Backup | Cifratura/decifratura backup (PBKDF2 + AES-256-GCM), passphrase errata, file non valido | C | `BackupCryptoTest` |
| Backup | Esporta/importa/automatico via selettore file Android | I | SAF non testato su dispositivo |
| Voce | Hey Jarvis dedicato (openWakeWord/LiteRT) integrato | E | Riconoscimento da PCM sintetico, silenzio senza richiamo; anche prova Linux con voce sintetica italiana. Non validato sulla voce dell’utente. |
| Voce | VAD fine comando, nessun ascolto infinito | I | Timeout 7 s al primo ascolto, finestra di conversazione configurabile |
| Voce | STT italiano offline (Parakeet TDT 0.6B v3 int8) | I | Nomi file modello cercati dinamicamente; latenza su Pixel da misurare |
| Voce | TTS italiano offline (Piper, 4 voci), senza callback JNI | E | Due anteprime per ognuna delle quattro voci: campioni generati e riprodotti su Android. Qualità percepita da giudicare sul Pixel. |
| Voce | Verifica del parlante (CAM++) con enrollment 5 frasi e soglia calibrata | I | Modello non validato su italiano; fail-closed se attivo |
| Voce | Barge-in (interrompere con «Hey Jarvis»), STOP immediato | I | Eco TTS→mic mitigata con AEC di sistema, non garantita |
| Voce | Fallback STT/TTS di Android (non open source) | N | Non implementato |
| Agente | Ciclo osserva→decidi→agisci→verifica, max passi, timeout, rilevamento cicli, retry rate-limit | I | |
| Agente | Compiti multi-app con stato condiviso | P | Traccia dei passi + memoria; nessuno stato strutturato condiviso tra compiti |
| Controllo app | Scrittura testo e verifica campo esterno | E | Test con APK chat separato e servizio accessibilità reale. WhatsApp reale non installato nell’emulatore. |
| Controllo app | Screenshot + modello multimodale | I | `takeScreenshot` dell'accessibilità; vietato su schermate protette |
| Integrazioni | WhatsApp (bozza via link, invio con conferma) | I | Ricerca contatto per nome via UI, non via rubrica |
| Integrazioni | Google Foto (ricerca via UI) | I | Guidata da suggerimenti, non da API |
| Integrazioni | Booking (solo ricerca, nessuna prenotazione/pagamento) | I | Blocco assoluto su «Prenota/Paga» |
| Integrazioni | ChatGPT: app via UI oppure API OpenAI (a pagamento, facoltativa) | I | API non nell'ordine di default |
| Integrazioni | App aggiuntive definite dall'utente | I | Schermata «App e integrazioni» |
| Cervello | Provider cloud gratuiti compatibili OpenAI con fallback (Groq/Qwen3.8-27B, Google, OpenRouter, NVIDIA, personalizzato) | I | Limiti gratuiti (es. Groq 8K token/min) rallentano compiti lunghi |
| Cervello | Modello offline sul telefono (Gemma 4 / LiteRT-LM) | N | Non implementato in questa versione |
| Memoria | Ricordi + cronologia (Room), cerca/modifica/elimina/azzera/disattiva | I | |
| UI | Home ridisegnata, nuova icona, chat con IME, STOP, tema chiaro/scuro, schermate in italiano | I | 4 test automatici passati su emulatore Android 15: digitazione/IME, chat lunga, permessi mancanti e navigazione; nessun test su Pixel fisico |
| Sistema | Servizio in primo piano di tipo microfono, wake lock esplicito, openWakeWord integrato, STT/TTS caricati al bisogno | I | Consumo reale da misurare |
| Sistema | Assistente Android predefinito e pannello in basso / overlay facoltativo | I | Compila; ruolo, hotword e controllo app da collaudare sul Pixel |
| Sistema | Avvio automatico dopo riavvio | N | Android non consente di avviare un servizio microfono da boot: apri l'app una volta |
| Android 17 | Accessibilità con «Protezione avanzata» attiva | N | Bloccata dal sistema; Jarvis non la aggira |

## Limiti noti
- Il modello AI è in cloud (piani gratuiti con limiti di richieste/token): compiti lunghi possono attendere o fermarsi.
- App con schermate protette (banche, DRM) non sono leggibili né catturabili.
- La robustezza del richiamo sulla voce reale dell’utente e la verifica del parlante richiedono calibrazione sul Pixel.
- Quattro test di interfaccia passati su emulatore Android 15. Richiamo vocale, sessione assistente, accessibilità reale, latenza e consumi richiedono ancora collaudo sul Pixel.

Dettagli delle correzioni e dei limiti delle prove: [DEBUG_2026-11-01.md](DEBUG_2026-11-01.md).
