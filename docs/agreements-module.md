# Módulo: Agreements (Acuerdos de Consentimiento)

## Flujo general

```
┌──────────────────────┐
│ Titular inicia sesión │
│  en el sistema CLIENTE│
│  (no en LeyData)      │
└──────────┬───────────┘
           │ observa
           ▼
┌──────────────────────┐
│     ORQUESTADOR       │  (externo, pendiente de construir)
│  pseudonimiza identidad│
└──────────┬───────────┘
           │ busca/crea
           ▼
┌──────────────────────┐
│    DATA_SUBJECTS      │  IDENTIFIER = valor pseudonimizado
└──────────┬───────────┘
           │
           ▼
   GET /agreements/active?dataSubjectId&templateId
           │
     ┌─────┴─────┐
     ▼           ▼
  NO ACTIVE    ACTIVE
     │           │
     ▼           ▼
 POST /agreements   GET /agreements?dataSubjectId
 (pedir consentimiento)   (revisar historial)
     │
     ├──▶ captura AGREEMENT_METADATA (ip, user agent, capture_channel, signature_token, auth_provider)
     │
     ▼
 ¿reconsiente? ── sí ──▶ cierra el ACTIVE anterior (REVOKED) + crea uno nuevo (regla 15)
     │
     ▼
 calcula HASH_SHA256 (AGREEMENTS + AGREEMENTS_PURPOSES + AGREEMENT_METADATA)
     │
     ▼
 TRAZABILIDAD ── POST /agreements/{id}/verify-integrity ──▶ recalcula hash ──▶ AGREEMENT_INTEGRITY_LOG (IS_VALID)
                 GET /agreements/{id}/integrity-log ──▶ historial de verificaciones
                 GET /agreements/integrity-log/failed ──▶ verificaciones fallidas
```

## Descripción

El módulo de Agreements registra el acto de consentimiento real de un titular de datos (DATA_SUBJECT). Es el resultado de presentarle un TEMPLATE: por cada PURPOSE visible en ese template, el titular acepta o rechaza explícitamente, y ese detalle queda congelado como snapshot inmutable en AGREEMENTS_PURPOSES.

Un AGREEMENT es evidencia legal: una vez creado no se edita. Cualquier cambio de voluntad del titular (revocar, renovar) genera un nuevo registro, nunca una modificación del existente. La integridad se protege con HASH_SHA256, calculado por el service al crear el acuerdo y revalidado por la base de datos (función `GENERATE_AGREEMENT_HASH`) y por verificaciones registradas en AGREEMENT_INTEGRITY_LOG.

> **Estado de este documento:** entity, repository, DTO, service y controller del módulo ya están implementados (`agreement/`). El `STATUS` (REVOKED/EXPIRED) más allá de la creación, y el flujo de alta de DATA_SUBJECTS, dependen de un orquestador que aún no existe.

> Ver diagrama de "Flujo general" al inicio del documento.

---

## Reglas de negocio

1. Un AGREEMENT es inmutable una vez creado: no se actualizan sus columnas de negocio (solo se inserta).
2. Un AGREEMENT solo puede crearse contra un TEMPLATE en estado `ACTIVE` (no se puede consentir sobre un template `DRAFT` o `APPROVED`).
3. `TEMPLATE_VERSION` debe coincidir con la versión del `TEMPLATE_ID` referenciado al momento de la creación (snapshot de qué versión vio el titular).
4. Por cada PURPOSE visible (`IS_VISIBLE = TRUE`) del template, debe crearse exactamente un registro en `AGREEMENTS_PURPOSES`. No puede faltar ninguna ni duplicarse (única por `AGREEMENT_ID` + `PURPOSE_ID`).
5. Si una PURPOSE es `REQUIRED = TRUE`, no puede registrarse `ACCEPTED = FALSE` para ella (constraint de BD). Esto es coherente con la regla de Templates de que una purpose `REQUIRED` no puede asociarse a un template de base "consentimiento".
6. `AGREEMENTS_PURPOSES` guarda un snapshot de los atributos de la purpose al momento del consentimiento (código, nombre, descripción, required, revocable, hash) — no se lee en vivo desde `PURPOSES`, para que cambios posteriores en la purpose no alteren acuerdos ya firmados.
6.1. `AGREEMENTS_PURPOSES` incluye `EXPIRES_AT` y `STATUS` (`ACTIVE`/`EXPIRED`/`REVOKED`) propios, independientes del `STATUS`/`EXPIRATION` del `AGREEMENT` padre — porque cada purpose puede tener su propia política de retención (vía `PURPOSE_DATA_CATEGORIES` → `DATA_RETENTION_POLICIES`), y por lo tanto su propia fecha de vencimiento. `STATUS` por defecto es `ACTIVE`. `REVOKED` se hereda: cuando el `AGREEMENT` padre se revoca, todas sus `AGREEMENTS_PURPOSES` pasan a `REVOKED` en la misma operación (responsabilidad del orquestador, no de este módulo). `EXPIRED` es independiente por purpose, calculado por el futuro `AgreementService` (módulo `agreement/application/service/`, aún no creado) usando la política de retención más corta entre las data categories de esa purpose. Mientras ese service no exista, `EXPIRES_AT` queda nulo y `STATUS` fijo en `ACTIVE` (salvo herencia de `REVOKED` cuando exista el orquestador). **DDL actualizado** (`AGREEMENTS_PURPOSES.sql` y `Principal.sql`).
7. `PURPOSE_HASH` en el snapshot debe coincidir con el `HASH_SHA256` de la purpose original al momento de la creación del agreement (permite detectar alteraciones).
8. `STATUS` inicia siempre en `ACTIVE`. El tránsito a `REVOKED` o `EXPIRED` está fuera del alcance de este documento: corresponde a un orquestador/job aún no diseñado.
9. `PREVIOUS_AGREEMENTS_ID` enlaza un agreement con el que reemplaza (renovación o cambio de voluntad), formando una cadena histórica. La definición de cuándo se genera esa cadena también depende del orquestador pendiente.
10. `HASH_SHA256` del AGREEMENT se calcula dos veces como doble resguardo: el service lo calcula y persiste al crear el agreement; la base de datos expone `GENERATE_AGREEMENT_HASH(agreement_id)` para recalcularlo y compararlo en verificaciones de integridad. El hash cubre `AGREEMENTS`, `AGREEMENTS_PURPOSES` **y `AGREEMENT_METADATA`** (IP, user agent, capture_channel, signature_token, auth_provider) — si la metadata de captura se altera después de creado el agreement, la verificación de integridad debe fallar. `EXTRA_VARIABLES` y `CREATED_AT` de metadata quedan fuera del hash (el primero por ser JSON libre sin orden garantizado, el segundo por ser solo un timestamp de registro).
11. Toda verificación de integridad (programada, manual o bajo demanda) sobre un AGREEMENT debe registrar un `AGREEMENT_INTEGRITY_LOG`, con el hash almacenado, el recalculado, si coinciden (`IS_VALID`) y el tipo de chequeo.
12. Un DATA_SUBJECT debe existir previamente a la creación del AGREEMENT. Su alta no es responsabilidad de este módulo: el orquestador (pendiente de construir) identifica al titular dentro del sistema del cliente (ej. login en la base de datos de una farmacia) y decide cuándo solicitarle consentimiento o reconsentimiento. Este módulo asume que ya recibe un `dataSubjectId` válido.
13. No se puede eliminar (`DELETE`) un AGREEMENT — el `ON DELETE RESTRICT` en `TEMPLATE_ID`, `DOCUMENT_ID` **y `DATA_SUBJECT_ID`** lo protege a nivel de BD. Un `DATA_SUBJECT` con `AGREEMENTS` asociados **no se puede eliminar físicamente**. Si el titular ejerce su derecho al olvido, se anonimiza su `IDENTIFIER` (se reemplaza por un valor opaco) en vez de borrar la fila — el agreement permanece como evidencia legal.
14. La metadata técnica (`IP_ORIGIN`, `USER_AGENT`) se captura en el controller de `POST /api/agreements` a partir del `HttpServletRequest` (IP resuelta vía `getRemoteAddr()` únicamente — igual que `AuditService.extractClientIp()` — porque `X-Forwarded-For` puede ser falsificado por el cliente; en producción se asume `RemoteIpValve` configurado para reescribir el `remoteAddr` real detrás de un proxy. User agent desde el header `User-Agent`) y se pasa como parámetros simples al service — el service no depende de objetos HTTP. El resto de campos de `AGREEMENT_METADATA` (`capture_channel`, `signature_token`, `auth_provider`, `extra_variables`) se reciben en el body del request.
15. Solo puede existir un `AGREEMENT` en `STATUS = 'ACTIVE'` por combinación (`DATA_SUBJECT_ID`, `TEMPLATE_ID`) — forzado con índice único parcial en BD. Reconsentir implica que el `AgreementService` cierre/reemplace el agreement activo anterior (vía `PREVIOUS_AGREEMENTS_ID`) antes o en la misma operación de crear el nuevo.
16. El `AgreementService` debe validar, al crear un `AGREEMENT`, que el `DOCUMENT_ID` recibido cubra (vía `DOCUMENT_PURPOSES`) todas las `PURPOSES` que se están aceptando/rechazando en esa operación. No es un constraint de BD — es una validación de aplicación porque requiere cruzar dos tablas no relacionadas directamente entre sí.
17. `AGREEMENTS.PREVIOUS_HASH_SHA256` y `AGREEMENTS_PURPOSES.PREVIOUS_HASH_SHA256` deben corresponder a un `HASH_SHA256` real y único de un registro previo de la misma tabla (cadena de hashes), igual patrón que `AUDIT_LOG` — forzado con FK a la propia tabla sobre una columna `UNIQUE`.

---

## Estados

```
ACTIVE → REVOKED
ACTIVE → EXPIRED
```

- **ACTIVE**: consentimiento vigente.
- **REVOKED**: el titular (o un proceso interno) retiró el consentimiento. Disparador pendiente de diseño (orquestador).
- **EXPIRED**: venció `EXPIRATION`. Disparador pendiente de diseño (orquestador/job).

---

## Casos de uso (propuestos para una futura iteración de implementación)

### Consulta y creación
1. Crear AGREEMENT (titular acepta/rechaza cada purpose visible del template activo) — genera AGREEMENTS_PURPOSES y calcula HASH_SHA256.
2. Obtener AGREEMENT por ID (incluye sus AGREEMENTS_PURPOSES).
3. Listar AGREEMENTS de un DATA_SUBJECT (historial, incluye cadena vía PREVIOUS_AGREEMENTS_ID).
4. Listar AGREEMENTS por TEMPLATE_ID / STATUS (filtros administrativos).

### Integridad
5. Verificar integridad de un AGREEMENT bajo demanda (recalcula hash, compara, registra en AGREEMENT_INTEGRITY_LOG).
6. Listar verificaciones fallidas (`IS_VALID = FALSE`) para investigación.

### Fuera de alcance por ahora (dependen del orquestador)
- Revocar AGREEMENT.
- Expirar AGREEMENT automáticamente.
- Renovar AGREEMENT (crear nuevo enlazado vía PREVIOUS_AGREEMENTS_ID).

---

## Endpoints propuestos

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `POST` | `/api/agreements` | Crear agreement con el detalle de purposes aceptadas/rechazadas |
| `GET` | `/api/agreements/{id}` | Obtener por ID con su detalle de purposes |
| `GET` | `/api/agreements` | Listar (filtros: `dataSubjectId`, `templateId`, `status`) |
| `POST` | `/api/agreements/{id}/verify-integrity` | Verificar integridad bajo demanda, registra `AGREEMENT_INTEGRITY_LOG` |
| `GET` | `/api/agreements/{id}/integrity-log` | Historial de verificaciones de integridad del agreement |
| `GET` | `/api/agreements/integrity-log/failed` | Listar verificaciones de integridad fallidas (`IS_VALID = FALSE`), caso de uso 6 |
| `GET` | `/api/agreements/active?dataSubjectId=&templateId=` | Consultar si hay un agreement ACTIVE — usado por el orquestador antes de pedir o no consentimiento |

> No se incluyen endpoints de revocación/expiración/renovación: dependen del orquestador no definido aún.

---

## Preguntas abiertas (pendientes de definición antes de planear código)

1. ~~**AGREEMENT_METADATA**~~ — **Resuelto.** Se confirmó que en este caso el código tenía la razón: el DDL fue actualizado para agregar la tabla `AGREEMENT_METADATA` (`ip_origin`, `user_agent`, `capture_channel`, `signature_token`, `auth_provider`, `extra_variables`), con FK a `AGREEMENTS(ID) ON DELETE CASCADE`. Las columnas `IP_ORIGIN`/`USER_AGENT` se quitaron de `AGREEMENTS` porque ahora viven en la tabla separada. DDL y entity ya están alineados.
2. ~~**AGREEMENT_INTEGRITY_LOG**~~ — **Resuelto.** A diferencia de `agreement_metadata`, aquí el DDL es la fuente de verdad (`stored_hash`, `recalculated_hash`, `is_valid`, `check_type`, `error_detail`). La entity Java (`expected_hash`, `actual_hash`, `detected_by`, `detected_at`) queda pendiente de corregirse en otra tarea para alinearse al DDL.
3. ~~**Expiración por purpose**~~ — **Resuelto (diseño).** `AGREEMENTS_PURPOSES` ahora tiene `STATUS`/`EXPIRES_AT` propios en el DDL (ver regla 6.1). La regla de negocio es usar la política de retención más corta entre las data categories de cada purpose (no la más larga), para evitar que la purpose siga "vigente" mientras alguna de sus categorías ya superó su período legal de retención. Pendiente solo la implementación en el futuro `AgreementService` (cálculo de `EXPIRES_AT` y transición a `EXPIRED`/herencia de `REVOKED`), no el modelo de datos.
4. **Alta de DATA_SUBJECTS y orquestador (sigue abierto)**: ambos quedaron formalmente ligados al mismo componente pendiente — el orquestador que identifica al titular en el sistema cliente (ej. login en la BD de una farmacia) y decide cuándo pedir consentimiento, reconsentimiento, o revocar/expirar un agreement. No se diseña en este documento; es la siguiente pieza a definir cuando se aborde ese componente.
5. ~~**Cascada destructiva en DATA_SUBJECT_ID**~~ — **Resuelto.** Se detectó que `FK_AGREEMENT_SUBJECT` tenía `ON DELETE CASCADE`, lo que borraría en cascada todos los `AGREEMENTS` (evidencia legal) de un titular eliminado. Se cambió a `ON DELETE RESTRICT` (regla 13): un `DATA_SUBJECT` con agreements no se puede eliminar; se anonimiza en su lugar.
6. ~~**`AGREEMENTS_PURPOSES.PURPOSE_ID` sin Foreign Key**~~ — **Resuelto.** Se agregó `FK_AP_PURPOSE` hacia `PURPOSES(ID) ON DELETE RESTRICT`, consistente con `TEMPLATE_PURPOSES`/`DOCUMENT_PURPOSES`/`PURPOSE_DATA_CATEGORIES`.
7. ~~**Bugs de sintaxis en `AGREEMENT_INTEGRITY_LOG`**~~ — **Resuelto.** Faltaba una coma tras `CREATED_BY UUID`, y el FK `FK_AIL_PREVIOUS_HASH` referenciaba una tabla inexistente y un nombre de columna que no coincidía (`PREVIOUS_HASH_SHA256` vs `PREVIOUS_HASH_SHA256_ID`). Corregido para referenciar `AGREEMENT_INTEGRITY_LOG(HASH_SHA256)` correctamente.
8. ~~**Agreements duplicados ACTIVE**~~ — **Resuelto.** Se agregó índice único parcial `UX_AGREEMENTS_ACTIVE_PER_SUBJECT_TEMPLATE` — solo un `AGREEMENT` `ACTIVE` por `(DATA_SUBJECT_ID, TEMPLATE_ID)` (regla 15).
9. ~~**Validación DOCUMENT_ID vs PURPOSES del agreement**~~ — **Resuelto (a nivel de diseño).** Se documenta como regla de negocio (regla 16) a validar en `AgreementService`; no se puede expresar como constraint de BD.
10. ~~**Cadena de hash sin validar**~~ — **Resuelto.** `AGREEMENTS` y `AGREEMENTS_PURPOSES` ahora tienen `PREVIOUS_HASH_SHA256` validado por FK contra su propio `HASH_SHA256` (con `UNIQUE` agregado), igual patrón que `AUDIT_LOG` (regla 17).
11. **Pendiente de actualizar cuando exista el orquestador con DATA_SUBJECTS:**
    - `AgreementController` no tiene ningún endpoint protegido con `@PreAuthorize` por ahora (decisión temporal, sin modelo de autenticación definido para este módulo). Hay que decidir quién llama a `POST /api/agreements` (¿el titular autenticado en el sistema cliente? ¿el orquestador como servicio interno?) y ajustar la seguridad de cada endpoint en consecuencia.
    - `AgreementService.resolveActorIdOrNull()` y el `createdBy = null` hardcodeado en `AgreementController.verifyIntegrity()` son placeholders a la espera de saber quién es el actor real en cada operación.
    - La transición `ACTIVE → REVOKED/EXPIRED` fuera del flujo de reconsentimiento (regla 8), el cálculo de `EXPIRES_AT` por purpose (regla 6.1) y la cadena de renovación vía `PREVIOUS_AGREEMENTS_ID` (regla 9) siguen sin implementarse — son responsabilidad del orquestador, no de `AgreementService`.
    - El alta de `DATA_SUBJECTS` (regla 12) sigue sin un flujo propio; `AgreementService.create()` asume que el `dataSubjectId` recibido ya existe.
