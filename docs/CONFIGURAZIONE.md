# Esempi di configurazione

Nessun file contiene chiavi. Le chiavi si inseriscono nell'app (Menu → Modelli AI e chiavi API).

## Provider (valori da inserire nell'app)
| Provider | URL base | Modello | Dove ottenere la chiave |
|---|---|---|---|
| Groq | `https://api.groq.com/openai/v1` | `qwen/qwen3.8-27b` (verifica con «Elenca modelli») | https://console.groq.com/keys |
| Google AI Studio | `https://generativelanguage.googleapis.com/v1beta/openai` | un modello Gemma da «Elenca modelli» | https://aistudio.google.com/apikey |
| OpenRouter | `https://openrouter.ai/api/v1` | un modello con suffisso `:free` | https://openrouter.ai/keys |
| NVIDIA NIM | `https://integrate.api.nvidia.com/v1` | da «Elenca modelli» | https://build.nvidia.com/ |
| Server tuo (Ollama, llama.cpp…) | `http://IP-DEL-PC:11434/v1` | nome del modello locale | — (la chiave può essere qualunque testo) |

Ordine di default: `groq,google,openrouter,nvidia,custom`. Il provider a pagamento `openai` è escluso salvo tua scelta.

## Parola di attivazione
`app/src/main/assets/keywords_jarvis.txt`: una riga per variante, token BPE del modello KWS seguiti da `@NOME`. Generate con `sentencepiece` e il `bpe.model` del pacchetto `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`.

## Formato del file di backup
JSON: `{ app, version, kdf, iterations, salt, iv, data }`; `data` è AES-256-GCM del JSON `{keys, models, baseUrls}`.
