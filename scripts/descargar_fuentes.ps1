# Descarga las tipografías de la guía visual VITAIROS (licencia SIL OFL) a app\src\main\res\font\
# Uso (PowerShell, desde la raíz del proyecto):  powershell -ExecutionPolicy Bypass -File scripts\descargar_fuentes.ps1
$ErrorActionPreference = "Stop"
$dest = "app\src\main\res\font"
$base = "https://raw.githubusercontent.com/google/fonts/main/ofl"
New-Item -ItemType Directory -Force -Path $dest | Out-Null
Invoke-WebRequest "$base/spacegrotesk/SpaceGrotesk%5Bwght%5D.ttf" -OutFile "$dest\space_grotesk.ttf"
Invoke-WebRequest "$base/ibmplexmono/IBMPlexMono-Regular.ttf"     -OutFile "$dest\ibm_plex_mono_regular.ttf"
Invoke-WebRequest "$base/ibmplexmono/IBMPlexMono-Medium.ttf"      -OutFile "$dest\ibm_plex_mono_medium.ttf"
Write-Host "Fuentes copiadas en $dest. Recompila la app (Build > Rebuild Project)."
