# Casos de Uso y Flujos del Proyecto — LeyData

> Documento consolidado. Reúne en un solo lugar los casos de uso y flujos que hoy están distribuidos en `docs/modules-specifications/*.md`, `orchestrator/INTEGRATION.md`, `docs/test-plans/` y la documentación de Experiencia 3 (DUOC UC) sobre el módulo de Gestión de Consentimientos. Para el detalle técnico de implementación de cada módulo, seguir los enlaces a esos documentos.

---

## 1. Contexto del sistema

**LeyData** es una plataforma de cumplimiento de la **Ley 21.719 de Protección de Datos Personales de Chile**. Permite a una organización capturar, administrar y auditar el consentimiento informado de los titulares de datos de forma trazable, inmutable y con soberanía total de los datos (infraestructura on-premise).

Cubre los cuatro derechos ARCO:
- **Acceso**
- **Rectificación**
- **Cancelación** (derecho al olvido / eliminación)
- **Oposición** (revocación de consentimiento)

**Principios de diseño que condicionan todos los flujos:**

| Principio | Qué implica |
|---|---|
| **Privacy by Design & by Default** | El consentimiento se pide de forma granular por finalidad, nunca "todo o nada". |
| **Pseudonimización** | El Orquestador y el backend nunca ven PII directa — solo un `subjectIdentifier` opaco (ej. `RUT:12345678-9`) que el sistema cliente define. |
| **Inmutabilidad** | Los `Agreement` nunca se editan. Cambios de voluntad generan nuevos registros (reconsentimiento) o transiciones de estado, nunca UPDATE sobre columnas de negocio ya escritas. |
| **Trazabilidad con hash chain** | `AGREEMENT`, `TEMPLATE`, `DOCUMENT` y `PURPOSE` tienen `HASH_SHA256` encadenado a su predecesor. `system_audit_log` y `entity_integrity_log` están protegidos por triggers de PostgreSQL que impiden `UPDATE`/`DELETE`. |
| **Minimización de retención** | Cada finalidad expira según la política de retención más corta entre sus categorías de dato asociadas. |

### Arquitectura de dos servicios

| Servicio | Puerto | Rol |
|---|---|---|
| **`backend/`** | 8080 | API interna. Gestiona catálogos (dominios, finalidades, plantillas, documentos), el motor de `Agreements` y la auditoría. Consumida por el frontend interno (`leydata/`, portal de DPO/ADMIN/JEFE_DOMINIO) y por el Orquestador. |
| **`orchestrator/`** | 8081 | API B2B externa. La usan sistemas cliente (CRM/ERP) para capturar y verificar consentimiento de sus propios titulares sin conocer la complejidad interna de LeyData. Cachea en Redis, resuelve `templateKey → templateId/documentId` y traduce JWT externo → M2M interno. |

---

## 2. Actores

| Actor | Dónde actúa | Qué hace |
|---|---|---|
| **ADMIN** | Backend (portal interno) | Gestiona usuarios, dominios, ve auditoría completa. Acceso total. |
| **DPO** (Data Protection Officer) | Backend (portal interno) | Aprueba finalidades y plantillas, publica documentos de privacidad, revisa integridad. |
| **JEFE_DOMINIO** | Backend (portal interno) | Solicita nuevas finalidades para su dominio (uno solo a la vez). No aprueba nada. |
| **USER / TITULAR** | Frontend embebible / portal de preferencias | El titular de datos que otorga o revoca su consentimiento. |
| **Sistema cliente (CRM/ERP)** | Orquestador (B2B) | Integra su propio flujo de negocio contra la API del Orquestador: pide textos legales, captura decisiones, consulta y revoca consentimiento, gestiona eliminación de datos vencidos. |

---

## 3. Casos de uso por módulo (backend interno)

### 3.1 Dominios (`orgdomain/`)
Unidad de segmentación organizacional sobre la que se agrupan finalidades y a la que se asigna un `JEFE_DOMINIO`. Sin workflow de aprobación — solo `ACTIVE ⇄ INACTIVE`.

**Casos de uso** (`ADMIN`):
1. Crear dominio (con o sin `JEFE_ID` asignado)
2. Listar todos los dominios, incluidos inactivos
3. Desactivar dominio
4. Reactivar dominio

Desactivar un dominio **no** cancela solicitudes pendientes ni desactiva sus finalidades — es solo una señal organizacional. No se puede desactivar un dominio ya inactivo, ni reactivar uno ya activo.

### 3.2 Finalidades y solicitudes (`purposes/` + `purposerequest/`)
Gobernanza de las finalidades de tratamiento (para qué se usa un dato personal), con workflow de aprobación previo a producción.

**Flujo de alta de una finalidad:**
```
JEFE_DOMINIO crea solicitud (para su dominio)
        │
        ▼
DPO revisa
   ├── APPROVED → se crea automáticamente la Purpose (ACTIVE, version 1)
   └── REJECTED → requiere reviewNotes (transparencia legal obligatoria)
```

**Versionado:** una finalidad queda `locked` cuando está en un documento `PUBLISHED` o referenciada por un `Agreement`. Bloqueada, no se edita in-place — se crea una nueva versión (`new-version`), la anterior pasa a `SUPERSEDED`, y todas comparten `purposeFamilyId`.

**Casos de uso — catálogo de finalidades (`purposes/`):**
1. Crear finalidad
2. Listar todas las finalidades / listar por dominio
3. Obtener finalidad por ID
4. Actualizar finalidad (rechazado si `locked = true`)
5. Desactivar finalidad (soft-delete)
6. Nueva versión de una finalidad bloqueada (`new-version`), con herencia de `purposeFamilyId`
7. Historial de versiones de una familia (`family/{purposeFamilyId}`) y versión activa (`active/{purposeFamilyId}`)

**Casos de uso — solicitudes de finalidad (`purposerequest/`):**
8. `JEFE_DOMINIO` crea una solicitud para un dominio propio
9. `JEFE_DOMINIO` consulta sus propias solicitudes (`/my`)
10. `DPO`/`ADMIN` lista las solicitudes pendientes o todas, independientemente del estado
11. `DPO` revisa una solicitud: aprobar (crea la `Purpose`) o rechazar (con `reviewNotes` obligatorio)

### 3.3 Categorías de datos y retención (`datacategory/` + `purposedatacategory/`)
Registro de inventario de tratamiento: qué categoría de dato (`SALUD`, `FINANCIERO`, `IDENTIFICACION`, etc. — sensibles y no sensibles) trata cada finalidad, para qué uso (`COLLECTION`, `PROCESSING`, `STORAGE`, `SHARING`, `DELETION`) y con qué política de retención.

**Casos de uso — catálogo de categorías (`datacategory/`):**
1. Crear categoría personalizada
2. Listar todas (`isSystem` + personalizadas)
3. Obtener categoría por ID
4. Actualizar categoría (solo no-system)
5. Eliminar categoría (solo no-system)

**Casos de uso — vínculo finalidad↔categoría y retención (`purposedatacategory/`):**
6. Vincular categoría de datos a una finalidad (con `DataUseType`)
7. Listar categorías vinculadas a una finalidad
8. Desvincular categoría de una finalidad
9. Definir política de retención (`retentionDays` + `legalJustification`) — una vez definida queda **bloqueada**
10. Consultar política de retención vigente

Esta retención es la que luego determina `expiresAt` de cada `AgreementsPurposes` (ver 3.5) y alimenta el flujo de eliminación del Orquestador (ver 5.7).

### 3.4 Plantillas de consentimiento (`template/`)
Capa de presentación: el formulario que ve el titular, con un conjunto ordenado de finalidades.

```
DRAFT → APPROVED → ACTIVE
```

- **DRAFT**: se pueden vincular/desvincular finalidades.
- **APPROVED**: aprobado por DPO, listo para activar.
- **ACTIVE**: en uso. No editable. Al activar una nueva versión, la anterior del mismo dominio + `TEMPLATE_KEY` se desactiva automáticamente en la misma transacción.

**Aislamiento por dominio:** `domainId` es obligatorio y fijo desde la creación. Dos dominios distintos pueden tener templates con el mismo `TEMPLATE_KEY` (ej. `ONBOARDING_CLIENTE` en Comercial y en Soporte) de forma independiente.

**Casos de uso — gestión básica:**
1. Crear template (nuevo `TEMPLATE_KEY`, versión 1, requiere `domainId`)
2. Crear nueva versión de un template existente (mismo `TEMPLATE_KEY`, versión N+1)
3. Obtener template por ID
4. Listar templates (filtros: `domainId`, `templateKey`, `status`)
5. Obtener historial de versiones de un `TEMPLATE_KEY` dentro de un dominio
6. Obtener la versión activa de un `TEMPLATE_KEY` dentro de un dominio

**Casos de uso — workflow de estados:**
7. Aprobar template (`APPROVED_BY` + `APPROVED_AT`)
8. Activar template (desactiva la versión anterior del mismo dominio + `TEMPLATE_KEY` en la misma transacción)

**Casos de uso — gestión de finalidades del template:**
9. Vincular finalidad al template (con `ORDER_POSITION` e `IS_VISIBLE`)
10. Desvincular finalidad del template (hard delete, solo en estado DRAFT)
11. Listar finalidades del template ordenadas por `ORDER_POSITION`
12. Actualizar `ORDER_POSITION` o `IS_VISIBLE` de una finalidad en el template

`GET /api/templates/resolve` es de uso interno B2B (lo llama el Orquestador para resolver `templateKey + domainId → templateId + documentId`, sin requerir rol DPO/ADMIN).

### 3.5 Documentos de privacidad (`privacydoc/`)
Textos legales que respaldan el consentimiento (política de privacidad, aviso de cookies, datos sensibles, marketing directo, menores de edad, transferencia a terceros).

```
DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED
              └── REJECTED → DRAFT (revisión)
```

Solo el DPO transiciona estados. Un documento `PUBLISHED` es inmutable — un cambio exige nueva versión. Solo puede haber un `PUBLISHED` por `templateId` (al publicar uno nuevo, se archiva automáticamente el anterior del mismo template).

**Relación con Agreements:** un `Agreement` requiere un documento `PUBLISHED` que cubra (vía `document_purposes`) todas las finalidades que se están consintiendo — si falta cobertura, `422 Unprocessable Entity`.

**Casos de uso** (escritura: `DPO`; lectura: cualquier usuario autenticado):
1. Crear documento (`DRAFT`)
2. Listar documentos (con filtros)
3. Obtener documento por ID
4. Actualizar documento (solo en `DRAFT`)
5. Enviar a revisión (`DRAFT → IN_REVIEW`)
6. Aprobar (`IN_REVIEW → APPROVED`)
7. Rechazar con motivo (`→ REJECTED`, vuelve a `DRAFT` en la revisión)
8. Publicar (`APPROVED → PUBLISHED`; archiva automáticamente cualquier otro `PUBLISHED` del mismo `templateId`)
9. Archivar documento obsoleto (`PUBLISHED → ARCHIVED`)
10. Verificar integridad del documento
11. Vincular / desvincular finalidades al documento
12. Descargar PDF generado (`PdfGeneratorService`)

### 3.6 Agreements — acuerdos de consentimiento (`agreement/`)
El registro del acto real de consentimiento. Por cada finalidad visible del template, el titular acepta o rechaza explícitamente; ese detalle queda congelado como snapshot inmutable en `AgreementsPurposes`.

```
ACTIVE → REVOKED
ACTIVE → EXPIRED   (por finalidad, evaluación lazy — no hay job que lo persista)
```

Reglas clave:
- Solo puede existir un `Agreement` `ACTIVE` por (`dataSubjectId`, `templateId`) — reconsentir cierra el anterior automáticamente y encadena `previousAgreementsId`.
- Cada finalidad dentro del agreement tiene su propio `expiresAt` (retención más corta entre sus categorías de dato) y su propio `status`, independiente del agreement padre. `REVOKED` se hereda cuando el padre se revoca.
- No se puede eliminar un `Agreement` (`ON DELETE RESTRICT`) — el derecho al olvido se resuelve anonimizando el `identifier`, nunca borrando la fila.
- `hashSha256` cubre `Agreements` + `AgreementsPurposes` + `AgreementMetadata`; cualquier alteración posterior rompe la verificación de integridad.

**Casos de uso — consulta y creación:**
1. Crear `Agreement` (el titular acepta/rechaza cada finalidad visible del template activo) — genera `AgreementsPurposes` y calcula `hashSha256`. `documentId` es opcional: si no se envía, se resuelve automáticamente el documento `PUBLISHED` vinculado al template.
2. Obtener `Agreement` por ID (incluye detalle de finalidades)
3. Listar `Agreements` (filtros: `dataSubjectId`, `templateId`, `status`)
4. Consultar si existe un `Agreement` `ACTIVE` para (`dataSubjectId`, `templateId`)

**Casos de uso — revocación:**
5. Revocar un `Agreement` — marca `REVOKED` en cascada (agreement + finalidades), registra en auditoría y publica `AgreementRevokedEvent` para invalidar Redis tras commit

**Casos de uso — ciclo de vida B2B (uso interno, llamado por el Orquestador):**
6. `lifecycle-check` — evalúa el estado agregado del consentimiento de un titular para un template (`PENDING` / `ALLOWED` / `REQUIRES_RECONSENT` / `EXPIRED`)
7. `subject-summary` — estado de todas las finalidades de un titular en un dominio, para el portal de preferencias
8. `pending-deletions` + `confirm-deletion` — finalidades vencidas cuyos datos el CRM aún no eliminó, y confirmación de eliminación (evidencia de cumplimiento)

**Casos de uso — integridad:**
9. Verificación de integridad de un `Agreement` — centralizada en el módulo `audit/` (ver 3.7)

### 3.7 Auditoría e integridad (`audit/`)
Dos responsabilidades:

1. **`system_audit_log`** — ledger inmutable de operaciones sensibles (crear/bloquear usuario, aprobar/rechazar solicitud, publicar documento, revocar agreement, etc.), con cadena de hashes SHA-256 y `pg_advisory_xact_lock` para serializar escrituras.
2. **`entity_integrity_log`** — verificación de integridad de `AGREEMENT`, `TEMPLATE`, `DOCUMENT`, `PURPOSE`: recalcula el hash actual y lo compara contra el almacenado. Corre manual, on-demand o vía scheduler diario (3am). `AgreementTraceService` traza la cadena completa de un agreement (template, documento, cada finalidad) y detecta drift (`overallIntegrity: OK | MISMATCH | PARTIAL`).

**Casos de uso** (acceso restringido a `ADMIN`):
1. Listar entradas del log operacional (filtros: `tableName`, `actorId`, `action`)
2. Obtener entrada del log por ID
3. Verificar integridad de la cadena completa del log operacional
4. Verificar integridad de una entidad puntual (`AGREEMENT`/`TEMPLATE`/`DOCUMENT`/`PURPOSE`) — recalcula y compara hash, registra el resultado
5. Consultar historial de verificaciones de una entidad
6. Listar verificaciones fallidas (`isValid = false`)
7. Trazar la cadena completa de un agreement (template, documento y cada finalidad, con `overallIntegrity`)

### 3.8 Usuarios (`user/`)
Capa delgada sobre Keycloak (modelo Keycloak-first): identidad, contraseña y roles viven en Keycloak; la BD local solo guarda `keycloak_id`, dominio asignado y estado activo/bloqueado.

**Casos de uso** (acceso: `ADMIN`):
1. Crear usuario — simultáneo en Keycloak + BD local; si falla la BD tras crear en Keycloak, se hace rollback compensatorio eliminándolo de Keycloak
2. Listar usuarios — combina datos de Keycloak (nombre, email, roles) con datos locales (dominios, estado)
3. Obtener usuario por ID (UUID local)
4. Actualizar usuario — nombre y dominios en BD local, roles en Keycloak (el email no es editable, es el username en Keycloak)
5. Bloquear usuario — deshabilita en Keycloak (invalida sesión/refresh), marca `blocked=true` en BD y en Redis (sin TTL); `UserStatusFilter` corta el acceso aunque el JWT siga vigente
6. Desbloquear usuario — habilita en Keycloak, limpia `blocked` en BD y Redis

Un `JEFE_DOMINIO` solo puede tener un dominio asignado a la vez.

### 3.9 Notificaciones y bases de licitud (`notification/` + `legalbasis/`)

**Notificaciones** — in-app, propias del usuario autenticado (extraídas del JWT; un usuario no ve las de otros). Tipos: `PURPOSE_REQUEST_APPROVED`, `PURPOSE_REQUEST_REJECTED`, `DOCUMENT_PUBLISHED`, `AGREEMENT_REVOKED`, `USER_BLOCKED`.

**Casos de uso:**
1. Listar notificaciones del usuario autenticado
2. Listar solo las no leídas
3. Marcar una notificación como leída
4. Eliminar una notificación

**Bases de licitud** — catálogo de solo lectura sembrado por `CatalogSeeder` al iniciar (Art. 12-13 Ley 21.719), no modificable desde la API. Solo la base `CONSENTIMIENTO` exige formulario de consentimiento al titular; las demás (`CONTRATO`, `OBLIGACION_LEGAL`, `INTERES_VITAL`, `INTERES_PUBLICO`, `INTERES_LEGITIMO`) no.

**Casos de uso:**
5. Listar todas las bases de licitud disponibles
6. Obtener una base de licitud por ID

---

## 4. Casos de uso formales — Widget de Consentimiento (frontend, RF.01–RF.23)

> Fuente: *LeyData — Documentación Experiencia 3, Módulo de Gestión de Consentimientos* (Taller Aplicado de Programación, DUOC UC, 16-06-2026). Estos 22 casos de uso formales (`CU-01`–`CU-22`, con actor/precondiciones/flujo/postcondiciones) describen el **widget embebible de consentimiento y su portal DPO/ADMIN** (`leydata/` — `ConsentWidget.tsx`, `CreateTemplatePage.tsx`, `ConsentimientosPage.tsx`, `Dashboard.tsx`, `AuditPage.tsx`, etc.), no el backend/orchestrator de las secciones 3 y 5.
>
> **Nota de estado (según el propio documento fuente, auditado contra el repositorio del frontend a esa fecha):** de los 22 RF, solo 5 estaban "implementados con cambios", 8 "parcialmente implementados" (UI existente pero con datos mock o lógica incompleta) y 9 "no implementados" — entre ellos versionado de plantillas, bloqueo de edición, snapshot de plantilla, pseudonimización (HMAC-SHA256/AES-256-GCM) y hash de integridad **a nivel de frontend**. Esto contrasta con el backend (sección 3), donde varias de esas mismas capacidades (versionado de templates y purposes, hash SHA-256 encadenado, inmutabilidad de agreements) **sí existen e implementadas**. La brecha reportada es específica del widget/portal evaluado, no del backend.

| CU | RF | Actor | Descripción | Flujo principal (resumen) | Postcondición |
|---|---|---|---|---|---|
| CU-01 | RF.01 | DPO | Crear plantilla de consentimiento definiendo contenido, finalidad y base de licitud | Selecciona una Finalidad aprobada → el sistema auto-completa datos de tratamiento (bloqueados) → DPO ingresa `descripcionTitular` por dato → guarda | Plantilla registrada y disponible para asociar a dominios |
| CU-02 | RF.02 | DPO | Versionar una plantilla al modificarla | Modifica contenido → guarda → sistema genera versión N+1 y archiva la anterior | Historial de versiones con fecha y autor de cada cambio |
| CU-03 | RF.03 | DPO | Activar/desactivar una versión específica de plantilla | Accede al historial → selecciona versión → confirma activación/desactivación | La versión seleccionada queda en el estado indicado |
| CU-04 | RF.04 | Sistema | Bloquear edición de una plantilla ya usada para capturar consentimientos | DPO intenta editar → sistema detecta uso previo → bloquea el formulario y muestra aviso | Intento de edición registrado en el log de auditoría |
| CU-05 | RF.05 | Sistema | Presentar los controles de selección en estado neutro al cargar | Titular abre el widget → sistema carga la plantilla activa → controles se renderizan sin marcar | Titular puede expresar una decisión explícita (no hay default implícito) |
| CU-06 | RF.06 | Titular | Visualizar el propósito del tratamiento en lenguaje claro antes de decidir | Titular accede al formulario → sistema muestra el propósito en sección destacada → decide | Titular cuenta con la información necesaria para una decisión informada |
| CU-07 | RF.07 | DPO | Usar la plantilla por defecto (`DEFAULT_CONFIG`) del sistema | DPO selecciona "usar plantilla por defecto" → sistema carga configuración predefinida conforme a Ley 21.719 | Plantilla por defecto activa y lista para capturar consentimientos |
| CU-08 | RF.09 | Sistema | Registrar la decisión del titular con evidencia técnica (timestamp, IP, versión de plantilla) | Titular envía el formulario → sistema captura metadatos → registra decisión → genera ID único | Registro persistido con los metadatos exigidos por la Ley 21.719 |
| CU-09 | RF.10 | Sistema | Registrar la base de licitud asociada a cada consentimiento capturado | Sistema lee la base de licitud de la plantilla → la asocia al registro de consentimiento | Consentimiento asociado a una base de licitud conforme al Art. 12 |
| CU-10 | RF.11 | Sistema | Verificar consentimiento previo del titular antes de re-solicitarlo | Sistema identifica al titular → consulta estado vigente → si ya otorgó, no vuelve a mostrar el formulario | Titular no recibe solicitudes redundantes |
| CU-11 | RF.12 | Administrador | Asociar una plantilla a uno o más dominios organizacionales | Administrador selecciona plantilla → elige dominios → guarda asociación | Plantilla vinculada a los dominios y usada al capturar consentimientos ahí |
| CU-12 | RF.13 | Sistema | Almacenar cada consentimiento como registro único e inmutable | Sistema recibe la decisión → crea registro con ID único, metadatos y hash → almacena en repositorio de solo escritura | Consentimiento existe como registro inmutable accesible para auditoría |
| CU-13 | RF.14 | Sistema | Conservar snapshot íntegro de la plantilla y el aviso de privacidad al momento del consentimiento | Sistema renderiza la plantilla al titular → al recibir la decisión, captura snapshot → lo asocia al registro | Registro incluye evidencia exacta de lo que vio el titular |
| CU-14 | RF.15 | Sistema | Generar un identificador único (UUID v4) por consentimiento | Sistema inicia el registro → genera UUID v4 → verifica colisión → lo asigna | Cada consentimiento es rastreable de forma inequívoca |
| CU-15 | RF.16 | Sistema | Pseudonimizar los datos identificatorios del titular | Sistema recibe datos del titular → aplica HMAC-SHA256 (búsqueda) y AES-256-GCM (recuperación) → almacena solo las versiones pseudonimizadas | Datos del titular protegidos; claves de re-identificación en sistema separado |
| CU-16 | RF.17 | Sistema | Verificar integridad de un consentimiento mediante hash | Al crear, calcula SHA-256 → al consultar, recalcula y compara → si difiere, genera alerta | Cada registro tiene un hash verificable que detecta alteraciones |
| CU-17 | RF.18 | Sistema | Impedir modificación o eliminación directa de registros de consentimiento | Un actor intenta modificar → sistema verifica que no hay autorización expresa → rechaza y registra el intento | Registro permanece inalterado; intento documentado en auditoría |
| CU-18 | RF.19 | DPO | Consultar el historial de estados de un consentimiento | DPO busca por ID → sistema muestra secuencia de estados (otorgado → revocado → expirado) con fechas y responsables | DPO visualiza el ciclo de vida completo del consentimiento |
| CU-19 | RF.20 | Sistema | Registrar de forma persistente todos los eventos de consentimiento | Ocurre un evento → sistema crea registro (ID, tipo, actor, entidad, IP, timestamp) → lo almacena en repositorio de solo escritura | Repositorio de eventos refleja todas las acciones sobre consentimientos |
| CU-20 | RF.21 | DPO | Consultar el estado actual de un consentimiento por ID | DPO ingresa el ID → sistema localiza el registro → muestra estado actual (activo/revocado/expirado) | DPO obtiene el estado vigente del consentimiento solicitado |
| CU-21 | RF.22 | DPO / Jefe de Dominio | Filtrar consentimientos por estado, fecha y dominio | Usuario aplica filtros combinados → sistema actualiza el listado en tiempo real → puede exportar | Listado muestra solo los consentimientos que cumplen los criterios |
| CU-22 | RF.23 | Administrador | Visualizar dashboard de métricas y estado general de cumplimiento | Administrador accede al dashboard → sistema carga KPIs, tendencia de 14 días y score de cumplimiento | Administrador tiene visibilidad del estado de cumplimiento de la organización |

> RF.08 no genera caso de uso — es una fila vacía en la especificación de requerimientos original y se marca como obsoleta.

Cada `CU` tiene además una Historia de Usuario asociada (`HU-RF01`–`HU-RF23`, formato "Como [actor] / quiero [acción] / para [beneficio]" con criterios de aceptación) y casos de prueba funcionales (`CP-01`–`CP-24`) en el documento fuente, con matriz de trazabilidad completa RF→HU→CU→CP. No se duplican aquí por no ser flujos de negocio — ver el PDF original o replicar en `docs/test-plans/` si se decide incorporarlos de forma permanente al repositorio.

---

## 5. Flujos B2B del Orquestador (`orchestrator/`, puerto 8081)

El Orquestador es la puerta de entrada para sistemas cliente externos (CRM/ERP). Todos los endpoints exigen un JWT válido del sistema cliente con el claim `leydata_domain` (ver sección 6). Internamente traduce cada llamada a una o más llamadas al backend, con Redis como caché de lectura rápida.

### Mapa de casos de uso

| # | Endpoint | Caso de uso | Quién lo dispara |
|---|---|---|---|
| 0 | `GET /consent/check?subjectId=&purposeId=` | Verificar consentimiento activo | Cualquier acceso a datos personales del CRM (alto volumen) |
| 1 | `GET /consent/template-content?templateKey=` | Obtener textos legales + finalidades del template activo | UI del CRM antes de mostrar el formulario |
| 2 | `POST /consent/capture` | Registrar las decisiones de consentimiento del titular | Después de que el titular firma |
| 3a | `GET /consent/subject/{subjectId}` | Portal de preferencias — estado de todas las finalidades | Dashboard de preferencias del titular |
| 3b | `POST /consent/revoke-purpose` | Revocar una finalidad puntual sin afectar las demás | Titular apaga un switch en el portal |
| 4 | `POST /consent/revoke` | Revocar el agreement completo | Titular retira todo su consentimiento |
| 6 | `GET /consent/pending-deletions` | Listar finalidades vencidas con datos aún no eliminados | Job periódico del CRM |
| 7 | `POST /consent/confirm-deletion` | Confirmar que el CRM eliminó/anonimizó los datos | CRM tras ejecutar la eliminación |

### 5.1 Caso 0 — Verificar consentimiento
```
CRM ──GET /consent/check──▶ Orquestador
                               │
                        ¿Redis tiene consent:{subjectId}:{purposeId}?
                               │
                    Sí ────────┼──────── No
                    │                     │
             Retorna (~1ms)      GET /api/agreements/lifecycle-check
                                  (backend evalúa lazy)
                                          │
                                  Escribe en Redis (TTL 900s)
                                          │
                                     Retorna
```
Estados posibles: `ALLOWED`, `EXPIRED`, `REQUIRES_RECONSENT` (template con `forceReconsent=true` en versión más nueva que la del agreement), `PENDING` (no existe agreement).

### 5.2 Caso 1 — Textos legales del template
1. `GET /api/templates/active/{templateKey}` con `domainId` del JWT.
2. Por cada finalidad del template, `GET /api/templates/{id}/purposes`.
3. Arma `TemplateContentResponse` (título, contenido, versión, lista de finalidades).

El CRM nunca puede pedir templates de otro dominio — el `domainId` no viaja en el request, se extrae del JWT.

### 5.3 Caso 2 — Capturar consentimiento
```
CRM ──POST /consent/capture──▶ Orquestador
  { subjectIdentifier, templateKey, purposes[], captureChannel }
                               │
              extrae domainId del claim leydata_domain
                               │
        GET /api/templates/resolve?domainId=&templateKey= 
              (resuelve templateId + documentId)
                               │
                POST /api/agreements (backend)
         findOrCreate DataSubject por subjectIdentifier opaco
                               │
        Por cada purpose aceptado → pre-warm Redis (ALLOWED)
                               │
        Retorna { subjectId, agreementId, status: ALLOWED }
```
Si el titular ya tenía un agreement `ACTIVE` para el mismo template, el backend lo cierra automáticamente y encadena el nuevo (reconsentimiento).

### 5.4 Caso 3a — Portal de preferencias
`GET /api/agreements/subject-summary?subjectIdentifier=&domainId=` — devuelve todos los agreements `ACTIVE` del titular en el dominio con el estado de cada finalidad, pensado para renderizar switches activo/revocado.

### 5.5 Caso 3b — Revocación granular
```
CRM ──POST /consent/revoke-purpose──▶ Orquestador
  { subjectId, purposeId, templateKey }
                               │
        GET subject-summary (estado actual de todas las finalidades)
                               │
    Reconstruye el body con purposeId objetivo en accepted=false
                               │
        POST /api/agreements (backend archiva el agreement anterior)
                               │
        Redis: consent:{subjectId}:{purposeId} → REVOKED
```
No se hace `UPDATE` sobre la fila existente — se usa re-consent para no romper el hash SHA-256 del agreement original (evidencia legal íntegra).

### 5.6 Caso 4 — Revocación total
`POST /consent/revoke` con `{ agreementId, subjectIdentifier, reason }` → `PATCH /api/agreements/{id}/revoke` en el backend → cascada a `REVOKED` sobre el agreement y todas sus finalidades → invalidación de Redis tras commit.

### 5.7 Casos 6 y 7 — Eliminación de datos vencidos
```
CRM ──GET /consent/pending-deletions──▶ Orquestador
                               │
        GET /api/agreements/pending-deletions?domainId=
   (finalidades ACTIVE con expiresAt < now, en el dominio)
                               │
   [{ subjectIdentifier, purposeId, purposeCode, expiredAt, anonymizeAfter }]
                               │
              CRM elimina/anonimiza en su propio sistema
                               │
CRM ──POST /consent/confirm-deletion──▶ Orquestador
  { subjectId, purposeId, deletedAt }
                               │
        POST /api/agreements/confirm-deletion (backend)
   Inserta en system_audit_log (entityType = DELETION_CONFIRMATION)
                               │
                    204 No Content
```
Esta confirmación queda como evidencia de cumplimiento ante la Agencia de Protección de Datos Personales.

---

## 6. Flujo de autenticación

Dos esquemas independientes, uno en cada dirección del Orquestador:

**Inbound (sistema cliente → Orquestador):**
- El CRM firma su JWT con su propio IdP (no tiene que ser Keycloak — cualquier IdP con JWKS sirve).
- `SecurityConfig` valida la firma contra `EXTERNAL_JWKS_URI`.
- El claim `leydata_domain` (fijado por un protocol mapper en el IdP del cliente, configuración operativa — no código) identifica el dominio LeyData autorizado. El cliente **no puede declarar su propio dominio** en el body; siempre viene firmado por su IdP. Si falta, el request falla con 500 antes de tocar el backend.

**Outbound (Orquestador → Backend):**
- `WebClientConfig` usa `client_credentials` contra Keycloak (realm `leydata`) para obtener un token M2M (`client_id = leydata-orchestrator`), cacheado hasta expirar.
- El backend nunca ve el JWT del sistema cliente — solo la identidad de servicio del Orquestador. Por eso `domainId` viaja explícito como parámetro en cada llamada al backend, nunca como claim relay.

**Backend interno:** Spring Security con JWT RS256, dos realms Keycloak (`leydata` para usuarios internos + Orquestador, `empresa-cliente` como IdP externo simulado para dev/testing), roles `ADMIN` / `DPO` / `JEFE_DOMINIO` / `USER` / `TITULAR` vía `@PreAuthorize`.

---

## 7. Flujo end-to-end de referencia (alta a eliminación)

Secuencia completa que atraviesa todos los módulos, tal como la ejercitan los test-plans manuales en `docs/test-plans/manuel-test-flow/`:

1. **ADMIN** crea el dominio.
2. **JEFE_DOMINIO** solicita una finalidad para su dominio → **DPO** la aprueba → se crea la `Purpose`.
3. **DPO** vincula categorías de datos a la finalidad y define su política de retención.
4. **DPO** crea un documento de privacidad, lo redacta, lo envía a revisión, lo aprueba y lo publica.
5. **DPO** crea un template, vincula la finalidad, lo aprueba y lo activa (queda `ACTIVE` para ese dominio).
6. **Sistema cliente** (vía Orquestador) pide `GET /consent/template-content` para mostrarle el formulario al titular.
7. **Titular** decide → CRM llama `POST /consent/capture` → se crea el `Agreement` + `AgreementsPurposes` + pre-warm en Redis.
8. **Sistema cliente** valida accesos futuros con `GET /consent/check` (cache-first).
9. **Titular** cambia de opinión sobre una finalidad puntual → `POST /consent/revoke-purpose` (reconsentimiento, no edición).
10. Con el paso del tiempo, la finalidad vence según su política de retención → aparece en `GET /consent/pending-deletions`.
11. **CRM** elimina/anonimiza sus datos y confirma con `POST /consent/confirm-deletion` → queda en `system_audit_log` como evidencia.
12. En paralelo, `IntegrityScheduler` corre diariamente y **ADMIN** puede auditar en cualquier momento vía `GET /api/audit/trace/agreement/{id}` que la cadena de hashes de todo lo anterior sigue íntegra.

---

## 8. Referencias

| Documento | Contenido |
|---|---|
| `docs/modules-specifications/*.md` | Detalle técnico de implementación por módulo (entidades, reglas, estructura de paquetes) |
| `orchestrator/INTEGRATION.md` | Guía de integración externa para equipos de sistemas cliente |
| `docs/guia-orquestador-dev.md` | Setup de desarrollo local del stack Orquestador |
| `docs/api-reference.md` | Catálogo completo de los 98 endpoints (90 backend + 8 orquestador) |
| `docs/test-plans/` | Planes de prueba manuales por módulo, flujo por flujo |
| *LeyData — Documentación Experiencia 3* (PDF, DUOC UC, 16-06-2026) | Historias de usuario, casos de uso formales, plan de pruebas y matriz de trazabilidad RF→HU→CU→CP del módulo de Gestión de Consentimientos (widget frontend) |
