# Manual de usuario — VITAIROS Captura

## Ingreso
1. Abre la app y toca un usuario de demostración (Camila, Diego o Valentina) o escribe el usuario.
2. Ingresa la contraseña de demostración `demo123` y toca **Ingresar**.

## Crear un reporte (Operador)
En **Inicio** toca **Nuevo reporte**. El formulario tiene 3 pasos:

**1 · Contexto** — Folio, fecha, usuario y dispositivo se registran solos. Elige el **tipo de reporte** y el **proceso asociado** (obligatorios). Según el tipo, completa área/faena, turno y el campo adicional (p. ej. código de equipo o severidad). Observaciones es opcional (máx. 500 caracteres).

**2 · Evidencia** — Agrega hasta **5 archivos** (JPG, PNG o PDF de hasta 10 MB):
- **Tomar foto**: abre la cámara del teléfono.
- **Galería**: elige una imagen existente.
- **PDF**: elige un documento PDF.

Toca la miniatura u **Ver** para revisar el documento a pantalla completa: verifica que sea legible, que corresponda y que esté derecho (puedes **Rotar**). Con **Reemplazar** repites la captura; con **Quitar** lo eliminas. Si la app detecta una imagen oscura o borrosa te avisará; puedes conservarla o repetirla.

**3 · Revisión** — Revisa el resumen, marca la casilla de confirmación y toca **Guardar y enviar** (o **Guardar en cola** si no hay señal). Con **Borrador** lo guardas para terminarlo después.

> No necesitas guardar manualmente: la app guarda el avance al pasar de un paso a otro y al salir.

## Sin conexión
Si aparece la franja dorada **«Sin conexión · Modo offline»**, sigue trabajando normalmente. Los reportes quedan **En cola** y se envían solos cuando vuelve la señal (aunque hayas cerrado la app).

## Cola de envíos
La pestaña **Cola** muestra lo pendiente:
- **Enviando**: se está transmitiendo.
- **En cola**: esperando conexión o un reintento automático (verás la cuenta regresiva, p. ej. «Reintento automático 2/3 en 12 s»).
- **Error**: falló 4 veces (1 intento + 3 reintentos). Toca **Reintentar** o **Corregir**.
- **Borradores**: aún no confirmados; no se envían.

**Sincronizar ahora** fuerza el envío inmediato. Puedes **Editar** o **Eliminar** (con confirmación) un reporte mientras esté en Borrador o En cola.

## Historial y detalle
**Historial** lista todos tus reportes con búsqueda y filtros por estado. Al abrir uno verás: metadata completa, datos de sincronización (intentos, comprobante), adjuntos, el **procesamiento simulado** de VITAIROS Industrial AI y la **trazabilidad** (cada evento con fecha y hora).

Un reporte **Sincronizado** ya no se puede editar ni borrar. Si el supervisor lo marca **Requiere corrección**, toca **Crear nueva versión corregida**: se crea un reporte nuevo (v2) vinculado al original, que se conserva.

## Supervisor
La pestaña **Revisión** muestra el resumen por estado de todos los operadores y los reportes sincronizados. Abre uno y toca **Marcar requiere corrección** indicando la observación.

## Ajustes
- Perfil, ID de instalación y espacio usado por adjuntos.
- **Modo demostración**: *Simular sin conexión*, comportamiento de la API mock (*Normal / Intermitente / Siempre falla*) y *Generar 10 reportes sintéticos*.
- **Cerrar sesión**: los pendientes se mantienen en el teléfono.

## Estados
Cada estado se muestra siempre con texto e ícono:

| Estado | Significado |
|---|---|
| Borrador | Guardado en el teléfono, sin enviar |
| En cola | Esperando envío |
| Enviando | Transmitiendo |
| Sincronizado | Recibido por VITAIROS |
| Error | Requiere reintento manual |
| Requiere corrección | Observado por el supervisor |
