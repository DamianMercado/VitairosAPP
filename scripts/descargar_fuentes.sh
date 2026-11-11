#!/usr/bin/env bash
# Descarga las tipografías de la guía visual VITAIROS (licencia SIL OFL, gratuitas) a app/src/main/res/font/
# Uso (desde la raíz del proyecto):  bash scripts/descargar_fuentes.sh
set -euo pipefail
DEST="app/src/main/res/font"
BASE="https://raw.githubusercontent.com/google/fonts/main/ofl"
mkdir -p "$DEST"
curl -fL "$BASE/spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf" -o "$DEST/space_grotesk.ttf"
curl -fL "$BASE/ibmplexmono/IBMPlexMono-Regular.ttf"     -o "$DEST/ibm_plex_mono_regular.ttf"
curl -fL "$BASE/ibmplexmono/IBMPlexMono-Medium.ttf"      -o "$DEST/ibm_plex_mono_medium.ttf"
echo "Fuentes copiadas en $DEST. Recompila la app (Build > Rebuild Project)."
