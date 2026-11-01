#!/usr/bin/env bash
set -euo pipefail
# openWakeWord v0.5.1 pretrained feature models + Hey Jarvis classifier.
TASK_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TASK_DEST="$TASK_ROOT/app/src/main/assets/wake"
mkdir -p "$TASK_DEST"
for name in melspectrogram embedding_model hey_jarvis_v0.1; do
  curl --retry 3 -fsSL "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/$name.tflite" -o "$TASK_DEST/$name.tflite"
done
(cd "$TASK_DEST" && sha256sum -c <<'CHECKSUMS'
c0aea21eb84a4ce90a08c870da41b7a7173b45269e6a3207c71d67c40f3a59d8  embedding_model.tflite
14bff778604985e1b5c19f0f7bbe477a69cf281d8db34b232b3b972411f710e2  hey_jarvis_v0.1.tflite
96fa0adccb6e8cf95cb14465409a1a2898ee4a96a85bb9ed3c7eb0e68bf163e8  melspectrogram.tflite
CHECKSUMS
)
