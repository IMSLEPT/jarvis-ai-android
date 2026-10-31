# Stato delle funzioni

Legenda: **C** = compila, coperto da test automatici in CI · **I** = implementato, compila, **non provato su dispositivo** · **P** = parziale · **N** = non implementato / non disponibile.
Data aggiornamento: build 0.1.x (31/10/2026). *Nessuna funzione è stata provata su un Pixel reale: la colonna «I» significa che il codice esiste e compila, non che sia verificato.*

| Area | Funzione | Stato | Note |
|---|---|---|---|
| Sicurezza | Conferma per azioni sensibili, blocco pagamenti/prenotazioni | C | `SafetyTest` (regole su etichette); l'integrazione nel ciclo agente è I |
| Sicurezza | Chiavi API cifrate (Keystore AES-GCM) | I | |
| Backup | Cifratura/decifratura backup (PBKDF2 + AES-256-GCM), passphrase errata, file non valido | C | `BackupCryptoTest` |
| Backup | Esporta/importa/automatico via selettore file Android | I | SAF non testato su dispositivo |
| Voce | Wake word «Hey Jarvis» (sherpa-onnx KWS, open vocabulary) + avvio manuale toccando la sfera | I | Verifica su voci sintetiche (vedi `docs/VALIDAZIONE.md`): ottima con pronuncia inglese, scarsa con voce sintetica italiana → la tua pronuncia va provata; il tocco sulla sfera funziona sempre |
| Voce | VAD fine comando, nessun ascolto infinito | I | Timeout 7 s al primo ascolto, finestra di conversazione configurabile |
| Voce | STT italiano offline (Parakeet TDT 0.6B v3 int8) | I | Nomi file modello cercati dinamicamente; latenza su Pixel da misurare |
| Voce | TTS italiano offline (Piper, 4 voci) | I | Qualità/naturalezza da giudicare all'ascolto |
| Voce | Verifica del parlante (CAM++) con enrollment 5 frasi e soglia calibrata | I | Modello non validato su italiano; fail-closed se attivo |
| Voce | Barge-in (interrompere con «Hey Jarvis»), STOP immediato | I | Eco TTS→mic mitigata con AEC di sistema, non garantita |
| Voce | Fallback STT/TTS di Android (non open source) | N | Non implementato |
| Agente | Ciclo osserva→decidi→agisci→verifica, max passi, timeout, rilevamento cicli, retry rate-limit | I | |
| Agente | Compiti multi-app con stato condiviso | P | Traccia dei passi + memoria; nessuno stato strutturato condiviso tra compiti |
| Controllo app | Aprire app per nome, link, tocchi per nodo, scrittura testo, scroll, indietro/home | I | Mai coordinate fisse: gesti solo sui limiti reali del nodo |
| Controllo app | Screenshot + modello multimodale | I | `takeScreenshot` dell'accessibilità; vietato su schermate protette |
| Integrazioni | WhatsApp (bozza via link, invio con conferma) | I | Ricerca contatto per nome via UI, non via rubrica |
| Integrazioni | Google Foto (ricerca via UI) | I | Guidata da suggerimenti, non da API |
| Integrazioni | Booking (solo ricerca, nessuna prenotazione/pagamento) | I | Blocco assoluto su «Prenota/Paga» |
| Integrazioni | ChatGPT: app via UI oppure API OpenAI (a pagamento, facoltativa) | I | API non nell'ordine di default |
| Integrazioni | App aggiuntive definite dall'utente | I | Schermata «App e integrazioni» |
| Cervello | Provider cloud gratuiti compatibili OpenAI con fallback (Groq/Qwen3.8-27B, Google, OpenRouter, NVIDIA, personalizzato) | I | Limiti gratuiti (es. Groq 8K token/min) rallentano compiti lunghi |
| Cervello | Modello offline sul telefono (Gemma 4 / LiteRT-LM) | N | Non implementato in questa versione |
| Memoria | Ricordi + cronologia (Room), cerca/modifica/elimina/azzera/disattiva | I | |
| UI | Home con sfera animata, chat, STOP, tema chiaro/scuro, tutte le schermate in italiano | I | Non verificata visivamente su dispositivo |
| Sistema | Servizio in primo piano di tipo microfono, basso consumo (un solo modello KWS da 3,3M parametri; STT/TTS caricati al bisogno) | I | Consumo reale da misurare |
| Sistema | Avvio automatico dopo riavvio | N | Android non consente di avviare un servizio microfono da boot: apri l'app una volta |
| Android 17 | Accessibilità con «Protezione avanzata» attiva | N | Bloccata dal sistema; Jarvis non la aggira |

## Limiti noti
- Il modello AI è in cloud (piani gratuiti con limiti di richieste/token): compiti lunghi possono attendere o fermarsi.
- App con schermate protette (banche, DRM) non sono leggibili né catturabili.
- Il riconoscimento di «Hey Jarvis» e della tua voce non è stato addestrato/validato sull'italiano.
- Nessun test automatico di interfaccia o su dispositivo è stato eseguito.
