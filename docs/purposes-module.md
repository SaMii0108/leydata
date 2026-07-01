# Módulo: Finalidades y Solicitudes (`purposes/` + `purpose/`)

---

## Dos módulos relacionados

| Módulo | Paquete | Qué hace |
|---|---|---|
| `purposes/` | `com.leydata.backend.purposes` | CRUD del catálogo de finalidades (propósitos de tratamiento) |
| `purpose/` | `com.leydata.backend.purpose` | Workflow de solicitud de nuevas finalidades (JEFE → DPO) |

---

## Módulo `purposes/` — Catálogo de Finalidades

**Endpoints base:** `/api/purposes/**`  
**Escritura:** DPO · ADMIN  
**Lectura:** DPO · ADMIN · JEFE_DOMINIO

Una **finalidad** (`Purposes`) describe para qué se va a usar un dato personal (ej: "Facturación mensual", "Envío de marketing"). Cada finalidad está vinculada a una **base de licitud** de la Ley 21.719.

### Endpoints

```
POST   /api/purposes                              — Crear finalidad
GET    /api/purposes                              — Listar todas las finalidades
GET    /api/purposes/{id}                         — Obtener finalidad por ID
PUT    /api/purposes/{id}                         — Actualizar finalidad (bloqueado si locked=true)
DELETE /api/purposes/{id}                         — Eliminar finalidad (soft-delete)

# Versionado (feature/trazabilidad)
POST   /api/purposes/{id}/new-version             — Crear nueva versión de una finalidad bloqueada
GET    /api/purposes/family/{purposeFamilyId}     — Historial de versiones de una familia (desc)
GET    /api/purposes/active/{purposeFamilyId}     — Versión ACTIVE de una familia
```

### Campos clave de una Finalidad

| Campo | Descripción |
|---|---|
| `code` | Código único (ej: `LEGAL_FACTURACION`) |
| `name` | Nombre legible (ej: "Facturación y cobro de primas") |
| `description` | Descripción completa para el titular |
| `shortDescription` | Texto breve para el widget de consentimiento |
| `legalBasisId` | Vínculo a `legal_basis_catalog` (Art. 12-13 Ley 21.719) |
| `required` | Si es `true`, el titular no puede rechazarla en el formulario |
| `revocable` | Si es `false`, el titular no puede revocar este purpose una vez aceptado |
| `hashSha256` | Hash del estado de la finalidad — se sella en `agreements_purposes` al consentir |

El `hashSha256` de la finalidad se copia al acuerdo de consentimiento en el momento de captura. Esto garantiza que, ante un cambio posterior en la finalidad, el historial refleje exactamente qué texto vio el titular cuando consintió.

### Versionado de finalidades

Cuando una finalidad está **locked** (incluida en un template con agreements, o en un documento PUBLISHED), no puede editarse in-place. El DPO puede crear una nueva versión:

- `POST /api/purposes/{id}/new-version` — crea una copia con `version+1` y estado `ACTIVE`; la versión anterior pasa a `SUPERSEDED` en la misma transacción. Solo aplica si `locked=true` — si no está bloqueada, usar `PUT /{id}`.
- Todas las versiones de una finalidad comparten el mismo `purposeFamilyId` (UUID auto-asignado al crear la primera versión — `id = purposeFamilyId`).
- El campo `code` ya no es único globalmente — múltiples versiones de la misma familia pueden tener el mismo código. La unicidad se mantiene a nivel de aplicación: no puede existir más de una versión `ACTIVE` por `purposeFamilyId`.
- `AgreementsPurposes` guarda un snapshot del `hashSha256` de la purpose al momento del consentimiento (`purposeHash`). `AgreementTraceService` compara este snapshot contra el hash actual de la purpose para detectar drift posterior al consentimiento.

| Campo | Tipo | Descripción |
|---|---|---|
| `purposeFamilyId` | UUID | Agrupa todas las versiones de una misma finalidad |
| `version` | Integer | Número de versión dentro de la familia (empieza en 1) |
| `status` | String | `ACTIVE` o `SUPERSEDED` |

---

## Módulo `purpose/` — Solicitudes de Propósito

**Endpoints base:** `/api/purpose-requests/**`  
**Crear:** JEFE_DOMINIO  
**Revisar (aprobar/rechazar):** DPO · ADMIN

Workflow de gobernanza: antes de que una finalidad pueda usarse en producción, el JEFE_DOMINIO debe solicitarla al DPO/ADMIN para aprobación.

### Estados del workflow

```
PENDING  →  APPROVED  →  (se puede usar en Templates)
         →  REJECTED
```

### Endpoints

```
POST   /api/purpose-requests                    — Crear solicitud (JEFE_DOMINIO)
GET    /api/purpose-requests                    — Listar todas (DPO · ADMIN)
GET    /api/purpose-requests/pending            — Solo pendientes (DPO · ADMIN)
GET    /api/purpose-requests/{id}               — Detalle de una solicitud
PATCH  /api/purpose-requests/{id}/review        — Aprobar o rechazar (DPO · ADMIN)
```

### Request de revisión

```json
{
  "decision": "APPROVED",   // o "REJECTED"
  "comment": "Finalidad válida bajo Art. 12 Ley 21.719"
}
```

---

## Archivos clave

| Archivo | Rol |
|---|---|
| `purposes/web/PurposeController.java` | CRUD de finalidades + endpoints de versionado |
| `purposes/application/service/PurposeService.java` | Lógica + hash SHA-256 + `newVersion()`, `getFamily()`, `getActiveByFamily()`, `isLocked()` |
| `purposes/domain/exception/PurposeNotLockedException.java` | Excepción HTTP 409 — se lanza al intentar `new-version` sobre una finalidad no bloqueada |
| `purposes/infrastructure/persistence/PurposesRepository.java` | Incluye `findByPurposeFamilyIdOrderByVersionDesc`, `findByPurposeFamilyIdAndStatus` |
| `purposerequest/web/PurposeRequestController.java` | Workflow de solicitudes |
| `purposerequest/application/service/PurposeRequestService.java` | Transiciones de estado |
