#!/bin/sh
# Finan+ — Copyright (C) 2026 Juscelino Be
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Copia o Finan+ web (PWA) para dentro do app Android, para o "Acesso pela rede (beta)".
# O navegador do computador abre exatamente esta versão do PWA, servida pelo celular.
#
# Uso: tools/sync-pwa.sh ../finan_plus        (pasta do repositório do PWA)
# Antes, no PWA: npm install && npm run build   (gera js/app.bundle.js)
set -eu
PWA="${1:?informe a pasta do repositório do PWA (ex.: ../finan_plus)}"
DEST="$(dirname "$0")/../app/src/beta/assets/lan/pwa"
[ -f "$PWA/js/app.bundle.js" ] || { echo "Falta $PWA/js/app.bundle.js: rode 'npm run build' no PWA."; exit 1; }
grep -q "finanplus-remote" "$PWA/js/app.bundle.js" || { echo "Este PWA não tem o modo remoto (js/remote.js)."; exit 1; }
rm -rf "$DEST"
mkdir -p "$DEST/js" "$DEST/icons"
cp "$PWA/index.html" "$PWA/style.css" "$PWA/manifest.webmanifest" "$DEST/"
cp "$PWA/js/app.bundle.js" "$DEST/js/"
cp "$PWA"/icons/*.png "$DEST/icons/"
# o service worker NÃO é copiado: no modo remoto nada fica em cache no navegador
echo "PWA copiado para $DEST ($(du -sh "$DEST" | cut -f1))."
