# Piano di test (da eseguire sul Pixel)

Per ogni test annota: data, esito (OK / KO / parziale), note. Scarica il log da Menu → Diagnostica → Copia.

| ID | Prova | Passi | Esito atteso |
|---|---|---|---|
| T01 | Installazione e avvio | Installa APK, apri | Home con sfera, nessun crash |
| T02 | Permessi | Menu → Permessi, concedi mic | «Microfono: concesso ✓» |
| T03 | Accessibilità | Attiva servizio (anche «impostazioni limitate») | Diagnostica: «Accessibilità attiva ✓» |
| T04 | Download modelli | Voce e modelli → essenziali | Tutti «Installato ✓»; riavvia il download a metà: riprende |
| T05 | Chiave API + prova | Inserisci chiave Groq, Prova | «Funziona: pronto» |
| T06 | Backup chiavi | Esporta con passphrase; cancella chiave; Importa | Chiave ripristinata; passphrase errata → errore |
| T07 | Aggiornamento | Installa la build successiva sopra la precedente | Chiavi e memoria presenti |
| T08 | Wake word | Avvia ascolto; di' «Hey Jarvis» 20 volte a 1 m | Annota riconosciute/20 e falsi allarmi in 10 min di TV/parlato |
| T09 | Comando semplice | «Hey Jarvis, che ore sono?» | Risposta parlata in italiano |
| T10 | Fine comando | Pausa dopo la frase | Elaborazione entro ~1 s dal silenzio |
| T11 | Nessun ascolto infinito | Di' «Hey Jarvis» e taci | Dopo 7 s torna in attesa |
| T12 | Conversazione continua | Dopo una risposta, fai un'altra domanda entro 6 s senza wake word | Risposta con contesto |
| T13 | Interruzione | Durante una risposta lunga di' «Hey Jarvis, stop» / premi STOP | Voce ferma subito |
| T14 | Aprire app | «Apri Google Foto» | App aperta |
| T15 | Foto | «Cerca nelle foto il mare» | Ricerca eseguita e descritta |
| T16 | WhatsApp | «Scrivi a <contatto> ciao» | Bozza scritta; richiesta di conferma prima di Invia; rifiutando non invia |
| T17 | Booking | «Cerca hotel a Roma dal … al …» | Risultati letti; nessuna prenotazione; «Prenota» bloccato |
| T18 | ChatGPT | «Chiedi a ChatGPT …» | Risposta letta (app) |
| T19 | Multi-app | «Cerca X su Booking e mandalo a <contatto> su WhatsApp» | Passa tra le app, conferma prima dell'invio |
| T20 | Voce dell'utente | Registra 5 frasi; prova da te e da un'altra persona | Soglia: tua ACCETTATA, altra RIFIUTATA (annota punteggi) |
| T21 | Memoria | «Ricorda che…»; Menu → Memoria; modifica/elimina/azzera/disattiva | Coerente |
| T22 | Blocco | Schermata ferma / app che non risponde | Si ferma entro limiti con spiegazione |
| T23 | Batteria | 8 ore con ascolto attivo e schermo spento | Annota % consumata |
| T24 | Android 17 Protezione avanzata | Attivala | Accessibilità bloccata (comportamento atteso del sistema) |
