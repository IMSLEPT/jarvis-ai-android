## 1 novembre 2026 — Rilevatore dedicato, voci e scrittura
- openWakeWord Hey Jarvis integrato con LiteRT; il vecchio KWS inglese non è più usato.
- Anteprima Piper senza callback JNI, errori espliciti e pulsante Interrompi.
- Ripresa del servizio autorizzato, microfono VOICE_RECOGNITION, orari ultimo frame/richiamo per la diagnostica.
- Scrittura nella finestra esterna corretta, rilettura del testo, collegamento WhatsApp con bozza e contatto/numero.
- Aggiunti test con modelli nativi reali, PCM del richiamo, microfono in background e campo di chat in un APK separato.

## 1 novembre 2026 — Interfaccia, assistente e stabilità
- Nuova Home e icona fornita, chat con gestione IME e contrasto esplicito, schermate impostazioni scorrevoli con tastiera.
- VoiceInteractionService, sessione assistente in basso e overlay facoltativo per Hey Jarvis sopra altre app.
- Avvio modelli fuori dal thread UI, gestione errori microfono/FGS, rilascio audio e sintesi protetti da concorrenza.
- Conferme scritte utilizzabili a microfono spento, gestione anticipata del pulsante conferma, timeout conversazione a zero rispettato.
- Buffer screenshot rilasciati, aggiornamenti download atomici e cancellazione controllata, diagnostica dell’ultimo arresto Java.
- Aggiunti test di regressione sull’attesa della risposta. Test su telefono reale ancora necessari: nessuna promessa di assenza totale di crash.

# Changelog

## 0.1.x — primo rilascio (31/10/2026)
- Primo APK: UI Compose, voce (KWS/VAD/STT/TTS/verifica parlante) con sherpa-onnx v1.13.8, agente con accessibilità, memoria Room, provider AI gratuiti compatibili OpenAI, zona backup chiavi cifrata, CI GitHub Actions.
- Non provato su dispositivo: vedi `docs/STATO.md`.
