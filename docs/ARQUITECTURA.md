# Arquitectura — VITAIROS Captura (MVP DSY1105)

## 1. Diagrama de capas

```mermaid
flowchart TB
    subgraph UI["Capa UI · Jetpack Compose"]
        L[Login] --> H[Inicio]
        H --> E[Nuevo / Editar reporte<br/>Contexto · Evidencia · Revisión]
        H --> Q[Cola de envíos]
        H --> HI[Historial]
        HI --> D[Detalle + trazabilidad]
        S[Revisión Supervisor] --> D
        A[Ajustes / modo demo]
    end
    subgraph LOGICA["Capa de lógica"]
        VM[ViewModels]
        R[ReportRepository]
        SM[SyncManager<br/>cola · backoff 5/15/30 s]
        RU[domain/Rules<br/>validaciones · RetryPolicy]
        CM[ConnectivityMonitor]
        WM[SyncWorker · WorkManager]
    end
    subgraph DATOS["Capa de datos (almacenamiento privado)"]
        DB[(Room / SQLite<br/>reports · attachments · report_events)]
        DS[(DataStore<br/>sesión · install id · modo demo)]
        FS[(filesDir/attachments<br/>imágenes y PDF)]
        API[[Retrofit + MockApiInterceptor<br/>API REST simulada]]
    end
    UI --> VM --> R
    VM --> RU
    R --> DB & FS & DS
    SM --> R & API
    CM --> SM
    WM --> SM
```

## 2. Modelo de datos

```mermaid
erDiagram
    REPORTS ||--o{ ATTACHMENTS : "contiene (1..5)"
    REPORTS ||--o{ REPORT_EVENTS : "bitácora"
    REPORTS ||--o| REPORTS : "nueva versión de (parentReportId)"
    REPORTS {
        string id PK "UUID (anti-duplicado)"
        string folio "VTR-AAMMDD-XXXX"
        string reportTypeCode
        string reportTypeVersion
        string processCode "lista fija"
        string area "condicional"
        string shift "condicional"
        string extraField "condicional por tipo"
        string observations
        string createdBy
        string createdByName
        string createdByRole
        string deviceInstallId "ID técnico no sensible"
        long createdAt
        long updatedAt
        enum status
        bool qualityConfirmed
        int attemptCount
        int autoRetryCount
        long lastAttemptAt
        long nextRetryAt
        long syncedAt
        string serverReceiptId
        string lastMessage
        int version
        string parentReportId FK
        string supervisorNote
        string processingStatus "SIMULADO"
        string processingResultJson "SIMULADO"
    }
    ATTACHMENTS {
        string id PK
        string reportId FK
        string displayName
        string mimeType "image/jpeg | image/png | application/pdf"
        enum source "CAMARA | GALERIA | PDF | SINTETICO"
        long originalSizeBytes
        long storedSizeBytes
        string localPath "null tras sincronizar"
        string sha256
        int pageCount
        string qualityWarning
        long createdAt
    }
    REPORT_EVENTS {
        long id PK
        string reportId FK
        long timestamp
        enum status
        string message
        int attempt
    }
```

## 3. Máquina de estados del reporte

```mermaid
stateDiagram-v2
    [*] --> Borrador: crear / autoguardado
    Borrador --> EnCola: Guardar y enviar (validado)
    EnCola --> Borrador: Editar (se retira de la cola)
    EnCola --> Enviando: hay conexión y vence el backoff
    Enviando --> Sincronizado: 201/200 de la API
    Enviando --> EnCola: falla → reintento automático 1..3 (5/15/30 s)
    Enviando --> EnCola: sin conexión (no consume reintentos)
    Enviando --> Error: falla tras 3 reintentos
    Enviando --> EnCola: app cerrada/reiniciada (recuperación)
    Error --> EnCola: Reintentar (manual) / Corregir y enviar
    Sincronizado --> RequiereCorreccion: Supervisor observa
    RequiereCorreccion --> [*]: se conserva como registro
    RequiereCorreccion --> Borrador: «Crear nueva versión» (nuevo reporte v+1)
    Borrador --> [*]: Eliminar (confirmación)
    EnCola --> [*]: Eliminar (confirmación)
```

## 4. Flujo principal (secuencia)

```mermaid
sequenceDiagram
    actor O as Operador
    participant UI as App (Compose)
    participant R as Repositorio (Room)
    participant S as SyncManager
    participant A as API mock
    O->>UI: Nuevo reporte → tipo, proceso, metadata
    UI->>R: createDraft() · estado Borrador
    O->>UI: Captura/adjunta → vista previa
    UI->>R: adjunto (comprimido, SHA-256, privado)
    O->>UI: Confirma revisión → Guardar y enviar
    UI->>R: enqueue() · estado En cola
    UI->>S: requestSync()
    alt sin conexión
        S-->>UI: «Sin conexión: queda en cola»
        Note over S: espera evento de conectividad / WorkManager
    end
    S->>R: markSending() · Enviando (intento N)
    S->>A: POST /api/v1/reports (multipart: metadata + archivos)
    alt 201 OK
        A-->>S: comprobante + estado En revisión (simulado)
        S->>R: Sincronizado · conserva copia local de adjuntos
        S-->>UI: «Enviado correctamente»
    else error 5xx
        A-->>S: error
        S->>R: En cola + nextRetryAt (5/15/30 s) o Error
        S-->>UI: «Reintento k/3 en X s» / «Pendiente de reintento manual»
    end
```

## 5. Decisiones y justificación

| Decisión | Motivo |
|---|---|
| Kotlin + Compose, arquitectura en capas sin framework DI | Recomendación de VITAIROS; contenedor manual (`AppContainer`) mantiene el MVP simple y explicable. |
| Room para reportes/adjuntos/eventos | Datos relacionales, consultas por estado, `UPDATE … WHERE status IN (…)` atómicos que evitan carreras entre edición y envío. |
| DataStore para preferencias | Sesión (solo ID), ID de instalación, modo demostración. |
| Bitácora `report_events` | Trazabilidad completa: creación, adjuntos, intentos, errores, sincronización, correcciones y versiones. |
| UUID + SHA-256 | El UUID es la referencia principal (API idempotente); el hash detecta el mismo archivo adjuntado dos veces. |
| Política de reintentos en `SyncManager` + `WorkManager` | El backoff 5/15/30 s es más corto que el mínimo de WorkManager (10 s exponencial); WorkManager actúa como respaldo con la app cerrada. |
| API mock vía interceptor OkHttp | Mismo código de red que con una API real; permite demostrar éxito, 500/503, sin conexión e idempotencia sin backend. |
| Copia local de adjuntos tras sincronizar | Ajuste solicitado: los archivos se conservan en el almacenamiento privado para consultarlos sin conexión. |
