#!/bin/bash
# Crea una chiave di firma PERMANENTE per Jarvis AI e stampa i valori da inserire come "Secrets" del repository GitHub
# (Settings → Secrets and variables → Actions → New repository secret):
#   JARVIS_KEYSTORE_BASE64   JARVIS_KEYSTORE_PASSWORD   JARVIS_KEY_ALIAS
# CONSERVA il file .jks e la password in un posto sicuro (NON nel repository): senza di essi non potrai più aggiornare l'app.
set -e
read -r -s -p "Scegli una password (min 8 caratteri): " PW; echo
keytool -genkeypair -keystore jarvis-release.jks -storetype PKCS12 -alias jarvis -keyalg RSA -keysize 3072 \
  -validity 10950 -storepass "$PW" -keypass "$PW" -dname "CN=Jarvis AI personale, O=Personale, C=IT"
echo "JARVIS_KEY_ALIAS=jarvis"
echo "JARVIS_KEYSTORE_PASSWORD=<la password che hai scelto>"
echo "JARVIS_KEYSTORE_BASE64=$(base64 -w0 jarvis-release.jks)"
