# VitairosAPP — Prototipo VITAIROS (DSY1105)

**Integrantes:** Damian Mercado · Isaac Roman · Equipo 4

Versión **mínima funcional** de la app de captura de reportes operacionales para VITAIROS Industrial AI.
Android nativo con Kotlin + Jetpack Compose (Android 8.0 / API 26 o superior). Usa **usuarios ficticios** y una
**API simulada**; no se conecta a ningún servidor real.

## Cómo ejecutarlo
1. Clona este repositorio (o descomprime el .zip) en una carpeta fuera de OneDrive, por ejemplo `C:\Proyectos\VitairosAPP`.
2. En Android Studio ve a *File › Open* y elige la carpeta que contiene `settings.gradle.kts`.
3. Espera la sincronización de Gradle y ejecuta ▶ *Run 'app'* con el celular conectado.
4. Ingresa con `operador1` o `operador2`; la contraseña es `demo123`.

Esta versión tiene otro identificador (`cl.vitairos.app`), así que puede instalarse junto a la versión completa.

## Qué incluye
| Función | Detalle |
|---|---|
| Login simulado | Usuarios ficticios validados en el teléfono (solo se guarda el hash de la contraseña). |
| Lista de reportes | Tipo, proceso, folio, fecha, miniatura de la foto y estado con texto + ícono. |
| Nuevo reporte (1 pantalla) | Tipo y proceso desde lista fija, observaciones y **foto con cámara o galería**. |
| Verificar foto | Pantalla completa con **Rotar / Repetir / Se ve bien**; botones adaptativos y dentro del área segura en cualquier celular. Solo se guarda con la foto verificada. |
| Base de datos local | **SQLite con Room**: tabla `reportes` en `vitairos.db`. Funciona sin Internet. |
| Estados | Borrador · En cola · Sincronizado · Error. |
| Envío simulado | «Guardar y enviar», «Enviar», «Enviar pendientes» y «Reintentar». |
| Validación visual | Campo inválido con **borde rojo + ícono de error + mensaje** (login, tipo, proceso, observaciones y foto). |
| Animaciones | Transición al cambiar de pantalla (deslizar al avanzar/volver, fundido en el login) y aviso «Sin conexión» animado. |
| Modo demostración (⚙) | *Simular sin conexión* → queda **En cola**. *Simular falla del servidor* → queda en **Error**. |

**Fuera de esta versión** (están en la versión completa `Vitairos`): perfil supervisor, PDF, varios adjuntos,
reintentos automáticos, sincronización en segundo plano, historial con trazabilidad y versiones corregidas.

## Dónde se guarda la base de datos
```
/data/data/cl.vitairos.app/databases/vitairos.db      ← SQLite (tabla «reportes»)
/data/data/cl.vitairos.app/files/fotos/                ← fotos comprimidas (JPEG)
/data/data/cl.vitairos.app/shared_prefs/vitairos_prefs.xml  ← sesión y modo demostración
```
Para verla con el celular conectado: Android Studio › *View › Tool Windows › App Inspection* ›
**Database Inspector**. Para copiarla al PC: *View › Tool Windows › Device Explorer* →
`data/data/cl.vitairos.app/databases/` → clic derecho en `vitairos.db`, `vitairos.db-wal` y `vitairos.db-shm`
→ *Save As* (copia los tres juntos) y ábrela con **DB Browser for SQLite**.

## Estructura (MVVM)
```
app/src/main/java/cl/vitairos/app/
├── VitairosApp.kt · MainActivity.kt
├── data/   Reglas.kt (catálogo, usuarios, validación) · BaseDeDatos.kt (Room)
│           Contratos.kt (interfaces ServicioEnvio y AlmacenFotos) · Repositorio.kt
│           Servicios.kt (preferencias, fotos, API simulada)
└── ui/     AppRoot · CargaScreen · LoginScreen · InicioScreen · NuevoReporteScreen
            VerificarFoto · DetalleScreen · Componentes · MainViewModel · theme/
```

## Pruebas unitarias
Están en `app/src/test/java/cl/vitairos/app/data/` y se ejecutan **sin celular ni Internet**:

| Archivo | Qué prueba | Pruebas |
|---|---|---|
| `CatalogoTest.kt` | Login simulado, hash de la contraseña, usuarios, listas fijas, formato del folio y textos de estado | 10 |
| `ValidacionTest.kt` | Reglas del formulario «Nuevo reporte»: campos obligatorios, foto verificada, máximo de 300 caracteres y mensajes | 8 |
| `RepositorioTest.kt` | Guardar, enviar, cola sin conexión, error y reintento, eliminar y filtros por usuario | 14 |
| `Fakes.kt` | Base de datos, servidor y fotos **falsos** (en memoria) que usan las pruebas del Repositorio | — |

El Repositorio recibe interfaces (`ReporteDao`, `ServicioEnvio`, `AlmacenFotos`) en vez de clases de Android;
por eso en las pruebas se le entregan versiones falsas y se controla qué responde el servidor.

**Cómo ejecutarlas**
- Android Studio: clic derecho en la carpeta `app/src/test` › **Run 'Tests in…'**.
- Terminal: `./gradlew testDebugUnitTest` (en Windows: `gradlew.bat testDebugUnitTest`).
  El informe queda en `app/build/reports/tests/testDebugUnitTest/index.html`.
