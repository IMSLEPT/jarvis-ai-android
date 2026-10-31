# Validazioni eseguite (31/10/2026)

Eseguite in un ambiente Linux (CPU desktop), **non sul Pixel**, con le stesse versioni di libreria (sherpa-onnx 1.13.8) e gli stessi file modello che l'app scarica. Usano **voci sintetiche**, quindi indicano che la pipeline è corretta, non la qualità con la tua voce.

| Prova | Risultato |
|---|---|
| Archivio Parakeet v3 int8: nomi file | `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`, `tokens.txt` (come cercati dall'app) |
| Piper `it_IT-paola-medium`: contenuto | `it_IT-paola-medium.onnx`, `tokens.txt`, `espeak-ng-data/` (come richiesto dall'app) |
| TTS Piper IT (paola, miro) con sherpa-onnx 1.13.8 | Funziona; 2,4 s di audio generati in ~1 s su CPU desktop |
| STT Parakeet (`model_type=nemo_transducer`) su TTS italiano | «Apri WhatsApp e cerca Alessandra, per favore.» → trascritto correttamente per entrambe le voci (0,7–1,3 s su CPU desktop) |
| Verifica parlante CAM++ | Stessa voce: coseno 0,79; voci diverse: 0,08–0,09 (separazione netta su voci sintetiche) |
| Wake word (file `keywords_jarvis.txt`) su TTS inglese (15 prove con velocità diverse) | 15/15 riconosciute (soglia 0,15–0,25, score 1,5) con silenzio prima/dopo |
| Wake word su 20 frasi neutre (IT/EN, incl. «Hey Siri», «Okay Google») | 0 falsi allarmi |
| Wake word su TTS **italiano** che legge «Hey/Ehi Jarvis» (8 prove) | 0–1/8 → il modello è inglese; con pronuncia italiana l'affidabilità è **sconosciuta/probabilmente bassa**; varianti ortografiche aggiuntive non hanno aiutato. Per questo esiste l'avvio manuale (tocco sulla sfera) |
| Test unitari in CI | `SafetyTest` e `BackupCryptoTest` passano |

Non misurati: latenza e consumo su Pixel 9 Pro XL, qualità percepita delle voci, accuratezza con la tua voce, comportamento di accessibilità reale.
