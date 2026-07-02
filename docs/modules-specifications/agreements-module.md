# Módulo: Agreements (Acuerdos de Consentimiento)

## Descripción

El módulo de Agreements registra el acto de consentimiento real de un titular de datos (`DataSubject`). Es el resultado de presentarle un `Template`: por cada `Purpose` visible en ese template, el titular acepta o rechaza explícitamente, y ese detalle queda congelado como snapshot inmutable en `AgreementsPurposes`.

Un `Agreement` es evidencia legal: una vez creado no se edita. Cualquier cambio de voluntad del titular (revocar, reconsentir) genera un nuevo registro o una transición de estado, nunca una modificación de las columnas de negocio ya escritas. La integridad se protege con `HASH_SHA256`, calculado por el service al crear el acuerdo y revalidado por verificaciones registradas en `ENTITY_INTEGRITY_LOG` (módulo `audit/`).

El módulo no interactúa directamente con sistemas cliente (CRM/ERP) — esa capa la resuelve el **Orquestador** (`orchestrator/`, puerto 8081), que pseudonimiza la identidad del titular en un `subjectIdentifier` opaco (ej. `RUT:12345678-9`) antes de llamar a los endpoints B2B de este módulo. Ver [`orchestrator-module.md`](orchestrator-module.md) para el flujo completo captura → verificación → revocación → eliminación.

---

## Reglas de negocio

1. Un `Agreement` es inmutable una vez creado: no se actualizan sus columnas de negocio (solo se inserta, o se transiciona su `status`).
2. Un `Agreement` solo puede crearse contra un `Template` en estado `ACTIVE` (no se puede consentir sobre un template `DRAFT` o `APPROVED`).
3. `templateVersion` debe coincidir con la versión del `templateId` referenciado al momento de la creación (snapshot de qué versión vio el titular).
4. Por cada `Purpose` visible (`isVisible = true`) del template, debe crearse exactamente un registro en `AgreementsPurposes`. No puede faltar ninguna ni duplicarse (única por `agreementId` + `purposeId`).
5. Si una `Purpose` es `required = true`, no puede registrarse `accepted = false` para ella.
6. `AgreementsPurposes` guarda un snapshot de los atributos de la purpose al momento del consentimiento (código, nombre, descripción, required, revocable, hash) — no se lee en vivo desde `Purposes`, para que cambios posteriores en la purpose no alteren acuerdos ya firmados.
7. `purposeHash` en el snapshot debe coincidir con el `hashSha256` de la purpose original al momento de la creación del agreement (permite detectar alteraciones vía `AgreementTraceService`).
8. `AgreementsPurposes` tiene `expiresAt` y `status` (`ACTIVE`/`EXPIRED`/`REVOKED`) propios, independientes del `status` del `Agreement` padre — cada purpose puede tener su propia política de retención (vía `purposeDataCategory` → `DataRetentionPolicies`), y por lo tanto su propia fecha de vencimiento. `AgreementService.calculateExpiresAt()` la calcula usando el período de retención **más corto** entre todas las categorías de dato de esa purpose (principio de minimización). `REVOKED` se hereda: cuando el `Agreement` padre se revoca o se reemplaza por reconsentimiento, todas sus `AgreementsPurposes` pasan a `REVOKED` en la misma transacción.
9. `status` del `Agreement` inicia siempre en `ACTIVE`. Transiciona a `REVOKED` explícitamente vía `PATCH /{id}/revoke` (llamado por el Orquestador) o implícitamente al reconsentir (regla 15). `EXPIRED` no es un estado propio del `Agreement` — se evalúa de forma lazy por purpose en `GET /lifecycle-check` (ver sección Estados).
10. `previousAgreementsId` enlaza un agreement con el que reemplaza (reconsentimiento), formando una cadena histórica — se asigna automáticamente en `AgreementService.create()` cuando ya existe un `Agreement` `ACTIVE` para el mismo (`dataSubjectId`, `templateId`).
11. `hashSha256` cubre `Agreements` + `AgreementsPurposes` + `AgreementMetadata` (IP, user agent, capture channel, signature token, auth provider) — si la metadata de captura se altera después de creado el agreement, la verificación de integridad falla. `extraVariables` y `createdAt` de metadata quedan fuera del hash (el primero por ser JSON libre sin orden garantizado, el segundo por ser solo un timestamp de registro).
12. Toda verificación de integridad (programada, manual o bajo demanda) sobre un `Agreement` se registra en `EntityIntegrityLog` (módulo `audit/`) — ver [`audit-module.md`](audit-module.md).
13. `CreateAgreementRequest` acepta dos formas de identificar al titular: `dataSubjectId` (UUID interno) o `subjectIdentifier` (string opaco, para llamadas desde el Orquestador). Si se usa `subjectIdentifier`, `AgreementService` hace `findOrCreate` en `DataSubjects`. Si ninguno de los dos viene, se lanza `BusinessValidationException`.
14. No se puede eliminar (`DELETE`) un `Agreement` — protegido a nivel de BD (`ON DELETE RESTRICT` en `templateId`, `documentId` y `dataSubjectId`). Si el titular ejerce su derecho al olvido, se anonimiza su `identifier` en vez de borrar la fila — el agreement permanece como evidencia legal.
15. Solo puede existir un `Agreement` en `status = ACTIVE` por combinación (`dataSubjectId`, `templateId`). Reconsentir implica que `AgreementService.create()` cierre el agreement activo anterior (`closeActiveAgreementForReconsent`) en la misma operación de crear el nuevo, encadenando `previousAgreementsId`.
16. Al crear un `Agreement`, se valida que el `documentId` (recibido o resuelto automáticamente) cubra, vía `DocumentPurposes`, todas las `Purposes` que se están aceptando/rechazando en esa operación.
17. `Agreements.previousHashSha256` y `AgreementsPurposes.previousHashSha256` encadenan cada fila con el `hashSha256` real de su predecesora inmediata, mismo patrón que `SystemAuditLog`.

---

## Estados

```
ACTIVE → REVOKED
ACTIVE → EXPIRED   (por purpose, evaluación lazy)
```

- **ACTIVE**: consentimiento vigente.
- **REVOKED**: el titular retiró el consentimiento — vía `PATCH /{id}/revoke` (revocación total explícita) o automáticamente cuando el `Agreement` se reemplaza por reconsentimiento.
- **EXPIRED**: no hay job/scheduler que transicione el estado — `GET /lifecycle-check` calcula en el momento de la consulta si `AgreementsPurposes.expiresAt` de alguna purpose `ACTIVE` ya pasó, y devuelve el estado agregado sin persistirlo. El estado por purpose sí queda registrado en `AgreementsPurposes.status`.

---

## Casos de uso

### Consulta y creación
1. Crear `Agreement` (titular acepta/rechaza cada purpose visible del template activo) — genera `AgreementsPurposes` y calcula `hashSha256`. `documentId` es opcional: si no se envía, se resuelve automáticamente el documento `PUBLISHED` vinculado al `templateId` — esto permite que el Orquestador arme el request sin que el CRM conozca el UUID del documento, solo el `templateKey` de negocio.
2. Obtener `Agreement` por ID (incluye su detalle de purposes).
3. Listar `Agreements` (filtros: `dataSubjectId`, `templateId`, `status`).
4. Consultar si existe un `Agreement` `ACTIVE` para (`dataSubjectId`, `templateId`) — usado antes de pedir o no un nuevo consentimiento.

### Revocación
5. Revocar un `Agreement`: valida que pertenezca al `subjectId` opaco enviado, marca `REVOKED` en cascada (agreement + sus purposes), registra en auditoría y publica `AgreementRevokedEvent` para que `AgreementRevocationCacheListener` invalide Redis después del commit (`AFTER_COMMIT`).

### Ciclo de vida B2B (uso interno — llamado por el Orquestador)
6. `lifecycle-check`: evalúa el estado agregado del consentimiento de un titular para un template — `PENDING` (no existe agreement), `ALLOWED`, `REQUIRES_RECONSENT` (el template activo es una versión más nueva con `forceReconsent = true`) o `EXPIRED` (alguna purpose venció según su política de retención).
7. `subject-summary`: estado de todas las purposes de un titular en un dominio — alimenta los switches del portal de preferencias.
8. `pending-deletions` + `confirm-deletion`: lista de purposes vencidas cuyos datos el CRM aún no eliminó, y el endpoint con el que el CRM confirma la eliminación (queda registrado en auditoría como evidencia de cumplimiento).

### Integridad
9. Verificación de integridad de un `Agreement` — centralizada en el módulo `audit/` (`POST /api/audit/integrity/verify`, `GET /api/audit/trace/agreement/{id}`), ver [`audit-module.md`](audit-module.md).

---

## Endpoints

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `POST` | `/api/agreements` | Crear agreement |
| `GET` | `/api/agreements/{id}` | Obtener por ID |
| `GET` | `/api/agreements` | Listar (filtros: `dataSubjectId`, `templateId`, `status`) |
| `GET` | `/api/agreements/active?dataSubjectId=&templateId=` | Consultar si hay un agreement `ACTIVE` |
| `PATCH` | `/api/agreements/{id}/revoke` | Revocar agreement [uso interno B2B] |
| `GET` | `/api/agreements/lifecycle-check?subjectIdentifier=&domainId=&templateKey=` | Estado del ciclo de vida [uso interno B2B] |
| `GET` | `/api/agreements/subject-summary?subjectIdentifier=&domainId=` | Estado de todas las purposes del titular [uso interno B2B] |
| `GET` | `/api/agreements/pending-deletions?domainId=` | Purposes vencidas pendientes de eliminación [uso interno B2B] |
| `POST` | `/api/agreements/confirm-deletion` | Confirmar eliminación de datos [uso interno B2B] |

---

## Estructura del módulo

```
agreement/
  application/
    dto/
      CreateAgreementRequest.java
      AgreementResponse.java
      AgreementPurposeResponse.java
      AgreementMetadataRequest.java / AgreementMetadataResponse.java
      ConsentLifecycleResponse.java
      SubjectSummaryResponse.java / PurposeSummaryItem.java
      PendingDeletionItem.java
      ConfirmDeletionRequest.java
      VerifyIntegrityRequest.java / AgreementIntegrityLogResponse.java
    service/
      AgreementService.java
      AgreementRevocationCacheListener.java
  domain/
    event/
      AgreementRevokedEvent.java
      AgreementIntegrityFailedEvent.java
    exception/
      AgreementNotFoundException.java
  infrastructure/
    persistence/
      AgreementsRepository.java
      AgreementsPurposesRepository.java
      AgreementMetadataRepository.java
      AgreementIntegrityLogRepository.java
  web/
    AgreementController.java
```
