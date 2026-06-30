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
POST   /api/purposes              — Crear finalidad
GET    /api/purposes              — Listar todas las finalidades
GET    /api/purposes/{id}         — Obtener finalidad por ID
PUT    /api/purposes/{id}         — Actualizar finalidad
DELETE /api/purposes/{id}         — Eliminar finalidad (soft-delete)
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
| `purposes/web/PurposeController.java` | CRUD de finalidades |
| `purposes/application/service/PurposeService.java` | Lógica + hash SHA-256 |
| `purposerequest/web/PurposeRequestController.java` | Workflow de solicitudes |
| `purposerequest/application/service/PurposeRequestService.java` | Transiciones de estado |
