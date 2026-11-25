# Plan de pruebas y evidencia — VITAIROS Captura

Métricas de validación acordadas (cuestionario §10): creación y persistencia sin pérdida, sincronización exitosa en condiciones normales, recuperación tras cierre/reinicio y comportamiento ante pérdida/recuperación de conectividad.

## A. Pruebas unitarias automatizadas
`./gradlew testDebugUnitTest` → `app/src/test/java/cl/vitairos/captura/RulesTest.kt` (12 casos):
política 5/15/30 s · estados editables/eliminables · formatos y tamaños de adjuntos · normalización MIME · metadata obligatoria y condicional · adjunto y confirmación obligatorios · login ficticio · formato de folio.

## B. Pruebas manuales (registrar resultado y captura de pantalla)

| ID | Caso | Pasos | Resultado esperado | OK |
|---|---|---|---|---|
| P01 | Login ficticio | Usuario `operador1` + clave incorrecta; luego `demo123` | Error claro; luego ingresa a Inicio | ☐ |
| P02 | Flujo completo en línea | Nuevo reporte → tipo/proceso/área/turno → foto → preview → confirmar → Guardar y enviar | Detalle muestra Enviando → Sincronizado con comprobante `VIA-…`; adjunto visible con su copia local | ☐ |
| P03 | Validaciones de contexto | Intentar «Siguiente» sin tipo/proceso; tipo Mantención sin código de equipo | Errores bajo cada campo; no avanza | ☐ |
| P04 | Validación de adjuntos | Elegir HEIC/WebP; PDF > 10 MB; 6.º archivo | Mensaje de rechazo; no se agrega | ☐ |
| P05 | Duplicado | Adjuntar dos veces la misma imagen de galería | «Ese archivo ya está adjunto…» | ☐ |
| P06 | Vista previa y rotación | Abrir preview de foto → Rotar → Reemplazar | Imagen rota; reemplazo sustituye el adjunto | ☐ |
| P07 | PDF | Adjuntar PDF de varias páginas → preview | Muestra páginas con Anterior/Siguiente y n.º de páginas | ☐ |
| P08 | Offline | Ajustes → Simular sin conexión → crear reporte | Banner offline; estado En cola; mensaje «Sin conexión…» | ☐ |
| P09 | ≥10 pendientes | (Offline) Ajustes → Generar 10 sintéticos | Cola con ≥10 reportes En cola | ☐ |
| P10 | Persistencia tras cierre | Con P09 activo, cerrar la app desde recientes / reiniciar emulador; reabrir | Los pendientes siguen en la cola, sin pérdida | ☐ |
| P11 | Reconexión automática | Desactivar «Simular sin conexión» (o activar Wi-Fi real) | «Conexión recuperada»; la cola se vacía sola; todos Sincronizado | ☐ |
| P12 | Reintentos | API mock = Siempre falla → enviar un reporte | Reintento 1/3 (5 s), 2/3 (15 s), 3/3 (30 s) con cuenta regresiva → Error | ☐ |
| P13 | Reintento manual | API mock = Normal → Reintentar | Sincronizado; bitácora muestra los 5 intentos | ☐ |
| P14 | Intermitente | API mock = Intermitente → 5 reportes | Todos terminan Sincronizado o Error sin perder datos | ☐ |
| P15 | Editar en cola | (Offline) reporte En cola → Editar → cambiar observación → Guardar y enviar | Cambio guardado; vuelve a En cola; bitácora registra la edición | ☐ |
| P16 | Eliminar | Eliminar un reporte En cola | Pide confirmación; desaparece; un Sincronizado no ofrece Eliminar | ☐ |
| P17 | Corrección / versión | Supervisor marca Requiere corrección → operador crea nueva versión | Nuevo folio v2 vinculado; original se conserva | ☐ |
| P18 | Procesamiento simulado | Sincronizado → Consultar estado (antes y después de 20 s) | En revisión → Procesado con campos de ejemplo y badge SIMULACIÓN | ☐ |
| P19 | Cierre durante envío | Enviar y cerrar la app de inmediato; reabrir | Evento «Envío interrumpido…» y el reporte se reenvía | ☐ |
| P20 | Resoluciones | Probar en emulador pequeño (5") y grande (6,7") | Sin cortes; botones visibles | ☐ |

## C. Métricas sugeridas para el informe
- **Persistencia sin pérdida**: reportes creados vs. encontrados tras P10 (meta 100 %).
- **Sincronización en condiciones normales**: % Sincronizado en el primer intento con API Normal (meta 100 %).
- **Recuperación ante conectividad**: % de reportes en cola sincronizados tras P11 sin intervención (meta 100 %).
- **Tiempo captura → envío** (complementaria): desde «Nuevo reporte» hasta «Sincronizado» (ver timestamps en la trazabilidad del detalle).
