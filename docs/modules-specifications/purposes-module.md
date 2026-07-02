# Módulo: Finalidades y Solicitudes (`purposes/` + `purposerequest/`)

## Descripción

Dos módulos relacionados que juntos implementan la gobernanza de finalidades de tratamiento exigida por la Ley 21.719:

| Módulo | Paquete | Qué hace |
|---|---|---|
| `purposes/` | `com.leydata.backend.purposes` | CRUD y versionado del catálogo de Finalidades (propósitos de tratamiento) |
| `purposerequest/` | `com.leydata.backend.purposerequest` | Workflow de solicitud de nuevas finalidades: `JEFE_DOMINIO` propone, `DPO` aprueba o rechaza |

Una **Finalidad** (`Purposes`) describe para qué se va a usar un dato personal (ej. "Facturación mensual", "Envío de marketing") y está vinculada a una base de licitud de la Ley 21.719. Antes de que una finalidad pueda usarse en producción, debe pasar por el workflow de `purposerequest/`: un `JEFE_DOMINIO` la solicita para su dominio, y el `DPO` la aprueba (lo que crea automáticamente el `Purpose`) o la rechaza.

---

## Reglas de negocio

### `purposes/`

1. `code` es único por versión, pero **no globalmente** — múltiples versiones de la misma familia pueden compartir código; la unicidad real la da `purposeFamilyId` + `status = ACTIVE`.
2. `hashSha256` de la finalidad se copia al acuerdo de consentimiento (`AgreementsPurposes.purposeHash`) en el momento de la captura — cambios posteriores en la purpose no alteran acuerdos ya firmados.
3. Una finalidad queda **locked** (`isLocked()`, calculado — no es una columna) cuando está incluida en un documento de privacidad `PUBLISHED` o referenciada por al menos un `Agreement`. Bloqueada, no puede editarse in-place (`PUT /{id}` rechaza el cambio).
4. Sobre una finalidad `locked`, el `DPO` crea una nueva versión (`POST /{id}/new-version`) en vez de editar: copia con `version + 1` y `status = ACTIVE`; la versión anterior pasa a `SUPERSEDED` en la misma transacción.
5. Todas las versiones de una finalidad comparten el mismo `purposeFamilyId` (UUID auto-asignado al crear la primera versión — `id = purposeFamilyId`). No puede existir más de una versión `ACTIVE` por `purposeFamilyId`.
6. `AgreementTraceService` compara el `purposeHash` snapshot de un agreement contra el hash actual de la purpose para detectar drift posterior al consentimiento (ver [`audit-module.md`](audit-module.md)).
7. `DELETE /{id}` es un soft-delete (desactivación), no elimina la fila.

### `purposerequest/`

8. Un `JEFE_DOMINIO` solo puede crear solicitudes para dominios que tiene asignados, y solo si el dominio está activo.
9. No puede existir más de una solicitud `PENDING` con el mismo título para el mismo dominio.
10. Al aprobar (`status = APPROVED`), `PurposeRequestService` crea automáticamente la `Purpose` con los datos de la solicitud.
11. Al rechazar (`status = REJECTED`), `reviewNotes` es obligatorio — la Ley 21.719 exige transparencia al justificar un rechazo.
12. Una solicitud ya revisada (`APPROVED`/`REJECTED`) no puede modificarse ni volver a revisarse. Para reintentar, el `JEFE_DOMINIO` debe crear una solicitud nueva.

---

## Estados

**Versionado de `purposes/`:**
```
ACTIVE → SUPERSEDED   (al crear una nueva versión sobre una finalidad locked)
```

**Workflow de `purposerequest/`:**
```
PENDING → APPROVED   (crea la Purpose automáticamente)
PENDING → REJECTED   (requiere reviewNotes)
```

---

## Casos de uso

### Catálogo de finalidades (`purposes/`)
1. Crear finalidad
2. Listar todas las finalidades / listar por dominio
3. Obtener finalidad por ID
4. Actualizar finalidad (rechazado si `locked = true`)
5. Desactivar finalidad (soft-delete)
6. Nueva versión de una finalidad bloqueada (`new-version`), con herencia de `purposeFamilyId`
7. Historial de versiones de una familia (`family/{purposeFamilyId}`) y versión activa (`active/{purposeFamilyId}`)

### Solicitudes de finalidad (`purposerequest/`)
8. `JEFE_DOMINIO` crea una solicitud para un dominio propio
9. `JEFE_DOMINIO` consulta sus propias solicitudes (`/my`)
10. `DPO`/`ADMIN` lista las solicitudes pendientes o todas, independientemente del estado
11. `DPO` revisa una solicitud: aprobar (crea la `Purpose`) o rechazar (con `reviewNotes`)

---

## Endpoints

### `purposes/` — Endpoints base `/api/purposes/**` — Escritura: DPO · ADMIN — Lectura: DPO · ADMIN · JEFE_DOMINIO

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `POST` | `/api/purposes` | Crear finalidad |
| `GET` | `/api/purposes` | Listar todas |
| `GET` | `/api/purposes/{id}` | Obtener por ID |
| `GET` | `/api/purposes/domain/{domainId}` | Listar por dominio |
| `PUT` | `/api/purposes/{id}` | Actualizar (bloqueado si `locked = true`) |
| `DELETE` | `/api/purposes/{id}` | Desactivar (soft-delete) |
| `POST` | `/api/purposes/{id}/new-version` | Nueva versión de una finalidad bloqueada |
| `GET` | `/api/purposes/family/{purposeFamilyId}` | Historial de versiones de una familia |
| `GET` | `/api/purposes/active/{purposeFamilyId}` | Versión `ACTIVE` de una familia |

### `purposerequest/` — Endpoints base `/api/purpose-requests/**`

| Método | Endpoint | Caso de uso |
|--------|----------|-------------|
| `POST` | `/api/purpose-requests` | Crear solicitud [`JEFE_DOMINIO`] |
| `GET` | `/api/purpose-requests/my` | Mis solicitudes [`JEFE_DOMINIO`] |
| `GET` | `/api/purpose-requests/pending` | Solicitudes pendientes [`DPO`, `ADMIN`] |
| `GET` | `/api/purpose-requests` | Todas las solicitudes, cualquier estado [`DPO`, `ADMIN`] |
| `PATCH` | `/api/purpose-requests/{requestId}/review` | Aprobar o rechazar [`DPO`] |

**Body de revisión** (`ReviewRequestDto`):
```json
{
  "status": "APPROVED",
  "reviewNotes": "Finalidad válida bajo Art. 12 Ley 21.719"
}
```
`reviewNotes` es obligatorio cuando `status = REJECTED`.

---

## Estructura del módulo

```
purposes/
  application/
    dto/
      CreatePurposeRequest.java
      UpdatePurposeRequest.java
      PurposeResponse.java
    service/
      PurposeService.java
  domain/
    exception/
      PurposeNotFoundException.java
      PurposeNotLockedException.java
  infrastructure/
    persistence/
      PurposesRepository.java
  web/
    PurposeController.java

purposerequest/
  application/
    dto/
      PurposeRequestDto.java
      PurposeRequestSummaryDto.java
      ReviewRequestDto.java
    service/
      PurposeRequestService.java
  infrastructure/
    persistence/
      PurposeRequestsRepository.java
  web/
    PurposeRequestController.java
```
