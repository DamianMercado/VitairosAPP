# VitairosAPP — VITAIROS Captura, MVP móvil (DSY1105)

**Integrantes:** Damian Mercado · Isaac Roman · Equipo 4

Prototipo funcional **Android nativo (Kotlin + Jetpack Compose)** para la captura de reportes operacionales de **VITAIROS Industrial AI**, construido según las respuestas del *Cuestionario de levantamiento de requerimientos* y la *Guía visual de referencia* entregadas por VITAIROS.

> MVP académico: usa **usuarios ficticios**, **documentos sintéticos** y una **API simulada (mock)**. No se conecta a infraestructura, credenciales ni datos productivos de VITAIROS.

---

## 1. Cómo abrir y ejecutar

1. Abrir **Android Studio** (Ladybug 2024.2 o superior) → *File › Open* → seleccionar la carpeta `VitairosAPP` (clonada desde GitHub, fuera de OneDrive).
2. Esperar la sincronización de Gradle (descarga dependencias la primera vez; requiere Internet).
3. *(Opcional, recomendado)* Copiar las tipografías de la guía visual:
   - Windows: `powershell -ExecutionPolicy Bypass -File scripts\descargar_fuentes.ps1`
   - macOS/Linux: `bash scripts/descargar_fuentes.sh`
   Sin este paso la app funciona igual, usando la tipografía del sistema.
4. Ejecutar en un emulador o teléfono con **Android 8.0 (API 26) o superior** (▶ *Run 'app'*).
5. APK de entrega: *Build › Build App Bundle(s)/APK(s) › Build APK(s)* → `app/build/outputs/apk/debug/app-debug.apk`.

Pruebas unitarias: `./gradlew testDebugUnitTest` (o clic derecho en `app/src/test` › *Run tests*).
Cobertura: configuración **Cobertura** en Android Studio (o `./gradlew koverHtmlReportDebug koverVerifyDebug`) → informe en `app/build/reports/kover/htmlDebug/index.html`.

### Usuarios de demostración (contraseña `demo123`)

| Usuario | Nombre ficticio | Perfil |
|---|---|---|
| `operador1` | Camila Rojas | Operador |
| `operador2` | Diego Fuentes | Operador |
| `supervisor` | Valentina Soto | Supervisor (vista acotada) |

### Logo oficial

`app/src/main/res/drawable/vitairos_isotipo.xml` es un **marcador de posición** inspirado en la descripción de la guía (mineral geométrico, trazos de circuito, punto cobrizo). Reemplázalo por el recurso autorizado entregado por VITAIROS con el mismo nombre (`vitairos_isotipo.png` o `.xml`) y elimina el placeholder; no requiere cambios de código.

---

## 2. Qué implementa (trazabilidad con el cuestionario)

| Requerimiento VITAIROS | Implementación |
|---|---|
| Kotlin nativo, arquitectura por capas, API 26+ | Compose + ViewModel (UI) → Repositorio / SyncManager (lógica) → Room, DataStore, archivos, API (datos). `minSdk 26`, `targetSdk 35`. |
| Teléfono vertical, gama media | Orientación vertical, tamaños táctiles ≥ 48 dp, layouts adaptables. |
| Login simulado con usuarios ficticios | Validación local por hash SHA-256; la sesión guarda solo el ID de usuario (nunca la contraseña). |
| Perfiles Operador y Supervisor | Operador: flujo completo. Supervisor: pestaña *Revisión* para consultar y marcar «Requiere corrección». |
| Flujo: usuario → tipo/proceso → metadata → captura → revisión → guardar → cola → sincronizar → estado → historial | Asistente de 3 pasos (Contexto · Evidencia · Revisión) + cola + historial + detalle con bitácora. |
| Cámara, galería y PDF; máx. 5 adjuntos; JPG/PNG/PDF; ≤10 MB | Cámara del sistema (`TakePicture`), selector de fotos (`PickVisualMedia`), selector PDF (`OpenDocument`). Validación de formato, tamaño, vacío y cantidad **bloquea** continuar. |
| Compresión razonable de imágenes | Lado mayor ≤ 2048 px, JPEG 85 %, respeta orientación EXIF. Muestra tamaño original → optimizado. |
| Revisión de calidad básica | Vista previa a pantalla completa (imágenes y PDF página a página) + confirmación obligatoria. Rotar 90° y repetir/reemplazar. Heurística opcional de oscuridad/desenfoque que solo **advierte**. |
| Metadata mínima, campos automáticos vs. manuales | Automáticos: UUID, folio, fecha/hora, usuario, rol, ID técnico de instalación, estado y timestamps. Manuales: tipo, proceso (lista fija), área/faena, turno, campo condicional por tipo, observaciones, adjuntos. |
| Campos condicionales simples | *Orden de mantención* → código de equipo; *Reporte de incidente* → severidad; *Parte de producción* no exige área. |
| Estados Borrador, En cola, Enviando, Sincronizado, Error (+ Requiere corrección) | Siempre con **texto + ícono**, nunca solo color. |
| Editar/eliminar antes de sincronizar; no después | Editar: Borrador, En cola, Error. Eliminar (con confirmación): Borrador y En cola. Sincronizado: inmutable; la corrección crea **nueva versión** vinculada. |
| Offline-first; guardado automático | Todo se persiste en Room al avanzar de paso, al adjuntar y al salir del formulario. |
| Sincronización automática + manual | Al recuperar conectividad y vía WorkManager (aunque la app esté cerrada) + botones *Sincronizar ahora* / *Reintentar*. |
| 3 reintentos (5 s, 15 s, 30 s) y luego reintento manual | `RetryPolicy`; cuenta regresiva visible en la cola; luego estado **Error** con *Reintentar*. |
| Persistencia tras cerrar/reiniciar | Al iniciar, los envíos interrumpidos (`Enviando`) vuelven a `En cola` y se reanudan. |
| Duplicados | ID único por reporte (la API mock es idempotente) + SHA-256 de cada archivo: bloquea el mismo archivo dos veces en un reporte y advierte si ya existe en otro. |
| API mock: envío, respuesta, errores, estados | Retrofit + interceptor OkHttp: multipart (metadata JSON + archivos), 201/500/503, sin conexión, comprobante, idempotencia. Modo configurable en *Ajustes*. |
| IA fuera del dispositivo; resultados simulados | «Procesamiento VITAIROS Industrial AI» con badge **SIMULACIÓN**: En revisión → Procesado con campos de ejemplo. |
| Seguridad | Almacenamiento privado, `allowBackup=false`, sin credenciales en texto plano, copia local de los adjuntos conservada tras sincronizar (almacenamiento privado), FileProvider solo para la cache de cámara, sin logs de datos sensibles. |
| Validación visual y animaciones | Campos inválidos con borde rojo, ícono y mensaje; pantalla de carga; transiciones al cambiar de pantalla y aviso de conexión animado. |
| Alertas in-app (sin push) | Snackbars: enviado correctamente, sin conexión, error, reintento programado, conexión recuperada. |
| Pantallas mínimas | Login · Inicio/Dashboard · Nuevo reporte · Captura/Adjuntos y vista previa · Revisión/confirmación · Cola · Historial · Detalle/estado · Ajustes/perfil · (Supervisor: Revisión). |

### Qué es real y qué es simulado

| Funciona realmente | Simulado (mock) |
|---|---|
| Navegación, formularios y validaciones · persistencia Room · cola y estados · cámara/galería/PDF · compresión · historial y trazabilidad · recuperación tras cierre · detección de conectividad real · WorkManager | API REST de VITAIROS · usuarios/autenticación · estado posterior *En revisión/Procesado* · campos extraídos · vista Supervisor (mismo dispositivo) |

---

## 3. Guía rápida de demostración (≈ 5 min)

1. Ingresar como **operador1** → *Nuevo reporte*.
2. Elegir *Orden de mantención* → proceso → área → código de equipo → *Siguiente*.
3. *Tomar foto* de un documento sintético → abrir la vista previa → *Se ve bien* → *Revisar*.
4. Marcar la confirmación → **Guardar y enviar** → el detalle muestra *Enviando* → *Sincronizado* con comprobante.
5. *Ajustes* → activar **Simular sin conexión** → crear otro reporte → queda **En cola** (banner offline).
6. *Ajustes* → **Generar 10 reportes sintéticos** → la pestaña *Cola* muestra 11 pendientes.
7. Cerrar la app desde recientes, reabrirla: los pendientes siguen ahí.
8. Desactivar el modo offline → «Conexión recuperada» → la cola se vacía automáticamente.
9. *Ajustes* → API mock **Siempre falla** → enviar un reporte: verás los reintentos 1/3, 2/3, 3/3 con cuenta regresiva y luego **Error** → volver a *Normal* → **Reintentar**.
10. Cerrar sesión → ingresar como **supervisor** → *Revisión* → abrir un reporte → **Marcar requiere corrección**.
11. Volver como **operador1** → el reporte aparece *Requiere corrección* → **Crear nueva versión corregida** (queda vinculada al original).

Más detalle en [`docs/MANUAL_USUARIO.md`](docs/MANUAL_USUARIO.md) y [`docs/PLAN_DE_PRUEBAS.md`](docs/PLAN_DE_PRUEBAS.md). Arquitectura y modelo de datos en [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).

---

## 4. Estructura del código

```
app/src/main/java/cl/vitairos/captura/
├── VitairosApp.kt · MainActivity.kt · AppContainer.kt   (arranque y DI manual)
├── domain/Rules.kt                  reglas puras: reintentos, validaciones, adjuntos
├── data/
│   ├── model/                       estados, catálogo cerrado, usuarios ficticios
│   ├── local/                       Room: entidades, DAOs, base de datos
│   ├── prefs/SettingsStore.kt       DataStore: sesión, ID instalación, modo demo
│   ├── files/                       adjuntos privados, compresión, PDF, sintéticos
│   ├── remote/                      contrato Retrofit + API mock (interceptor)
│   └── repo/ReportRepository.kt     única puerta a los datos + bitácora
├── sync/                            SyncManager, conectividad, WorkManager, alertas
└── ui/                              tema VITAIROS, componentes y pantallas
```

## 5. Decisiones técnicas (resumen)

- **Room/SQLite** para reportes, adjuntos y bitácora: relaciones, consultas por estado, actualizaciones condicionales atómicas (evitan carreras entre edición y envío) y `Flow` reactivo para la UI. **DataStore** solo para preferencias simples.
- **Retrofit + interceptor mock**: el código de red es el mismo que usaría una API real (multipart con metadata + archivos); basta quitar el interceptor y cambiar `API_BASE_URL`.
- **WorkManager + SyncManager**: el SyncManager aplica la política 5/15/30 s mientras la app está abierta; WorkManager garantiza que la cola se procese al haber red aunque la app esté cerrada.
- **Sin dependencias pagadas** ni servicios externos: todo es open source y funciona sin backend.

## 6. Pruebas unitarias y cobertura

**128 pruebas** (JUnit 4 + Robolectric + Room en memoria + WorkManager de prueba) que cubren el **98 % de las líneas de la lógica** de la app, medido con **Kover**. La regla `koverVerifyDebug` exige un mínimo de **80 %**.

| Archivo de prueba | Qué verifica |
|---|---|
| `RulesTest`, `ReglasDetalleTest` | Reintentos 5/15/30 s, validaciones de formulario y adjuntos, catálogo, login con hash, estados, antirrebote |
| `ReportRepositoryTest` | Borradores, edición condicionada al estado, cola, bitácora, versiones, corrección del supervisor, datos sintéticos |
| `MockApiInterceptorTest` | API simulada: 201/200 sin duplicar, 500/503, 400/404/422, sin conexión y procesamiento simulado |
| `AttachmentStorageTest`, `AttachmentRulesStorageTest` | Importación JPG/PNG/PDF, compresión ≤ 2048 px, hash SHA-256, calidad (oscura/desenfoque), rotación, rechazos |
| `SyncManagerTest` | Envío exitoso, backoff, error tras 3 reintentos, sin conexión, cancelación (nunca queda en «Enviando»), procesamiento |
| `AppContainerTest` | Sesión, conectividad efectiva, envío de punta a punta con la API mock y respaldo con WorkManager |
| `ViewModelsTest`, `DetailViewModelTest`, `EditorViewModelTest` | Lógica de cada pantalla: login, inicio, cola, historial, ajustes, detalle y el formulario completo |

Se mide la **lógica** (reglas, datos, sincronización y ViewModels). Las funciones `@Composable`, el tema, la navegación y el código generado (R, BuildConfig, DAOs de Room) quedan fuera de la medición (`app/build.gradle.kts › kover`). Dos pruebas de la cámara usan `FileProvider`, que solo acepta rutas tipo Unix: en Windows aparecen como *omitidas* y se ejecutan en Linux/macOS.

## 7. Limitaciones conocidas del MVP

- El catálogo de tipos/procesos es de demostración hasta que VITAIROS entregue el oficial (`data/model/Catalog.kt`).
- El Supervisor ve los reportes del mismo dispositivo (no hay backend compartido).
- No se cifra la base de datos local (mejora sugerida: SQLCipher / EncryptedFile).
- Sin recorte de imagen ni detección de bordes (fuera del alcance obligatorio).
