# Módulo: Trazabilidad del Sistema

## Contexto

El módulo `audit/` ya resuelve el espectro completo de trazabilidad operativa mediante `SYSTEM_AUDIT_LOG`:

| Pregunta | Campo en `SYSTEM_AUDIT_LOG` |
|---|---|
| Quién | `actor_id`, `actor_role` |
| Cómo | `action`, `user_agent`, `request_id` |
| Cuándo | `created_at` |v
| Dónde | `ip_address` (remoteAddr, incluida en el hash) |
| Qué se hizo | `action`, `table_name`, `record_id` |
| Qué había antes | `old_data` (JSON) |
| Qué hay ahora | `new_data` (JSON) |

El log es inmutable (triggers PostgreSQL bloquean UPDATE y DELETE), encadenado con SHA-256 y atómico con la operación de negocio — ver [`auditoria-paradigma.md`](../auditoria-paradigma.md) para el detalle de implementación.

Lo que **no cubre** `SYSTEM_AUDIT_LOG` son dos preguntas distintas:

1. **¿El registro de la entidad fue alterado directamente en BD después de su creación?** — el audit log registra que algo se creó, pero no verifica periódicamente que ese registro siga intacto.
2. **¿Qué versión exacta de cada entidad (purpose, template, document) vio el titular al momento de consentir?** — `AGREEMENTS_PURPOSES` ya snapshotea el estado de la purpose, pero no existe un endpoint que reconstruya la cadena completa `AGREEMENT → DOCUMENT → TEMPLATE → PURPOSE` con verificación de integridad por eslabón.

Este módulo extiende `audit/` para cerrar exactamente esos dos huecos:

- **`ENTITY_INTEGRITY_LOG`**: tabla unificada que consolida la verificación de integridad de **todas** las entidades — `AGREEMENT`, `PURPOSE`, `TEMPLATE`, `DOCUMENT` — bajo un mismo mecanismo. Reemplaza `AGREEMENT_INTEGRITY_LOG` y el scheduler específico de agreements.
- **Versionado de `PURPOSES`**: única entidad sin `family_id` + `version`. `TEMPLATES` y `DOCUMENTS` ya lo tienen.
- **Endpoint `/trace`**: dado un `AGREEMENT`, reconstruye la cadena completa con la versión exacta de cada entidad y verifica la integridad de cada eslabón.

> **Estado:** diseño, aún no implementado. Punto de partida para el plan de la rama `feature/trazabilidad`.

---

## Flujo general

```
SYSTEM_AUDIT_LOG — ya cubre quién/cómo/cuándo/dónde/qué/antes/después para todas las tablas

ENTITY_INTEGRITY_LOG — lo que falta: ¿fue alterado después de creado?
  Cubre AGREEMENT, PURPOSE, TEMPLATE, DOCUMENT — reemplaza AGREEMENT_INTEGRITY_LOG

  POST /api/audit/integrity/verify
       body: { entityType: "AGREEMENT"|"PURPOSE"|"TEMPLATE"|"DOCUMENT", entityId: uuid, checkType: "MANUAL" }
       ──▶ recalcula hash de la entidad ──▶ escribe en ENTITY_INTEGRITY_LOG (is_valid)
       (reemplaza POST /api/agreements/{id}/verify-integrity)

  GET  /api/audit/integrity/log?entityType=&entityId=
       ──▶ historial de verificaciones de una entidad
       (reemplaza GET /api/agreements/{id}/integrity-log)

  GET  /api/audit/integrity/failed?entityType=
       ──▶ verificaciones fallidas (todas o filtradas por tipo)
       (reemplaza GET /api/agreements/integrity-log/failed)

VERSIONADO DE PURPOSES — lo que falta: family_id + version (TEMPLATES y DOCUMENTS ya lo tienen)

  POST /api/purposes/{id}/new-version
       ──▶ nueva fila PURPOSES (mismo purpose_family_id, version N+1, hash recalculado)
       ──▶ versión anterior → SUPERSEDED

  GET  /api/purposes/family/{purposeFamilyId}
       ──▶ historial de versiones de una familia

TRAZABILIDAD END-TO-END — lo que falta: ¿qué versión exacta vio el titular?

  GET  /api/audit/trace/agreement/{agreementId}
       ──▶ AGREEMENT → DOCUMENT (vX) → TEMPLATE (vY) → PURPOSES (vZ)
       ──▶ is_valid por eslabón + overallIntegrity
```

---

## Esquema de arquitectura

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                    ARQUITECTURA: verificación de integridad                  │
└──────────────────────────────────────────────────────────────────────────────┘

   AGREEMENT          TEMPLATE          DOCUMENT          PURPOSE
   hash_sha256        hash_sha256       hash_sha256       hash_sha256
       │                   │                 │                 │
       │ recalculateHash() │                 │                 │
       ▼                   ▼                 ▼                 ▼
 AgreementService   TemplateService  PrivacyDocService   PurposeService
       │                   │                 │                 │
       └─────────────┬─────┴────────┬────────┴────────┬────────┘
                      ▼              ▼                 ▼
                 ┌─────────────────────────────────────────┐
                 │            IntegrityVerifier             │
                 │  stored_hash  vs  recalculated_hash       │
                 │  ──▶ is_valid = (stored == recalculated)  │
                 └─────────────────┬─────────────────────────┘
                                    │ escribe (encadenado SHA-256,
                                    │ previous_hash_sha256_id)
                                    ▼
                      ┌───────────────────────────┐
                      │   ENTITY_INTEGRITY_LOG     │  ◀── trigger Postgres:
                      │   (solo INSERT)             │      bloquea UPDATE/DELETE
                      └───────────┬─────────────────┘
                                  ▲
                                  │ dispara cada entidad, 1x/día (cron 3:00 AM)
                      ┌───────────┴───────────────┐
                      │     IntegrityScheduler     │
                      └────────────────────────────┘

   Disparadores manuales:
   POST /api/audit/integrity/verify   ──▶ IntegrityVerifier.verify() bajo demanda
   GET  /api/audit/integrity/log      ──▶ historial por entidad
   GET  /api/audit/integrity/failed   ──▶ solo is_valid=false


┌──────────────────────────────────────────────────────────────────────────────┐
│                    VERSIONADO: PURPOSES (family_id + version)                │
└──────────────────────────────────────────────────────────────────────────────┘

   PURPOSE v1 ──┐
   status:      │  bloqueada (template con ≥1 AGREEMENT, o doc PUBLISHED)
   ACTIVE       │
                │   PUT /api/purposes/{id}  ──▶ 422 (rechazado, no se puede editar)
                │
                │   POST /api/purposes/{id}/new-version
                ▼        │
   PURPOSE v1 ──────┐    │  misma purpose_family_id
   status:          │    ▼
   SUPERSEDED       └──▶ PURPOSE v2
                          status: ACTIVE
                          hash_sha256 recalculado

   GET /api/purposes/family/{familyId}  ──▶ [v2 ACTIVE, v1 SUPERSEDED, ...]
   GET /api/purposes/active/{familyId}  ──▶ solo la versión ACTIVE


┌──────────────────────────────────────────────────────────────────────────────┐
│              TRAZABILIDAD END-TO-END: GET /trace/agreement/{id}              │
└──────────────────────────────────────────────────────────────────────────────┘

   AGREEMENT
       │
       ├──▶ documentId ──▶ DOCUMENT (vX)  ── recalculateHash() ──▶ isValid ✓/✗
       │
       ├──▶ templateId ──▶ TEMPLATE (vY)  ── recalculateHash() ──▶ isValid ✓/✗
       │
       └──▶ AGREEMENTS_PURPOSES[] (snapshot al consentir)
                  │
                  └──▶ purposeId exacto ──▶ PURPOSE (vZ, esa versión específica)
                            │
                            └── purposeHash snapshot vs hash actual ──▶
                                  isValid ✓ / MISMATCH ✗ / UNKNOWN (sin hash)

   AgreementTraceService — 100% solo lectura, no escribe en ningún log (regla 13)

   overallIntegrity:
     MISMATCH  si algún eslabón con hash calculable no coincide
     PARTIAL   si no hay MISMATCH pero algún eslabón es UNKNOWN
     OK        si todos los eslabones son válidos
```

---

## Entidad: `ENTITY_INTEGRITY_LOG`

Mismo patrón que `AGREEMENT_INTEGRITY_LOG` — columnas equivalentes, misma cadena de hashes, mismo trigger PostgreSQL de solo inserción.

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | UUID PK | Generado antes de persistir (incluido en el hash — mismo patrón que `SystemAuditLog`) |
| `entity_type` | VARCHAR | `PURPOSE`, `TEMPLATE`, `DOCUMENT` |
| `entity_id` | UUID | ID de la entidad verificada |
| `stored_hash` | VARCHAR | `hash_sha256` leído de la entidad al momento de la verificación |
| `recalculated_hash` | VARCHAR | Hash recalculado sobre los mismos campos usados al crear el registro |
| `is_valid` | BOOLEAN | `stored_hash == recalculated_hash` |
| `check_type` | VARCHAR | `SCHEDULED`, `MANUAL`, `ON_DEMAND` |
| `created_at` | TIMESTAMP(6) | Truncado a microsegundos — mismo criterio que `SystemAuditLog` |
| `created_by` | VARCHAR | `keycloak_id` del actor (`null` si es `SCHEDULED`) |
| `error_detail` | TEXT | Detalle si la verificación falló por error técnico (distinto de `is_valid = false`) |
| `hash_sha256` | VARCHAR UNIQUE | Hash propio de este registro (cadena del log) |
| `previous_hash_sha256_id` | VARCHAR FK → `entity_integrity_log.hash_sha256` | `"GENESIS"` en el primer registro |

El hash de cada registro del log cubre: `id`, `entity_type`, `entity_id`, `stored_hash`, `recalculated_hash`, `is_valid`, `check_type`, `created_at`, `created_by`, `previous_hash_sha256_id` — análogo a `AuditService.computeHash()`.

---

## Versionado de Purposes

`TEMPLATES` y `PRIVACY_DOCUMENTS` ya tienen `family_id` + `version`. `PURPOSES` necesita lo mismo.

Campos a agregar a la tabla `PURPOSES`:

| Campo | Tipo | Descripción |
|---|---|---|
| `purpose_family_id` | UUID | Autorreferencia. En v1: `purpose_family_id = id` propio |
| `version` | INT | Correlativo por familia, empieza en 1 |
| `status` | VARCHAR | `ACTIVE` \| `SUPERSEDED` |

Una purpose se vuelve inmutable cuando está vinculada a un template que tiene al menos un `AGREEMENT`. A partir de ese punto toda edición crea nueva versión y exige reconsentimiento de los titulares afectados (responsabilidad del orquestador pendiente). El "antes/después" de ese cambio ya queda en `SYSTEM_AUDIT_LOG`; el versionado agrega la capacidad de identificar exactamente qué versión vio el titular.

---

## Reglas de negocio

### Integridad genérica

1. `IntegrityVerifier` (dentro de `audit/`) recibe `entityType` + `entityId`, lee el `hash_sha256` almacenado, lo recalcula sobre los campos originales y escribe el resultado en `ENTITY_INTEGRITY_LOG`.
2. Los campos que entran al hash por entidad: `AGREEMENT` → los ya definidos en `AgreementService` (misma función de hash existente); `PURPOSE` → `code, name, description, short_description, required, revocable, consent_statement, legal_basis_id, domain_id`; `TEMPLATE` y `DOCUMENT` → a confirmar contra el código existente antes de implementar.
3. Cada verificación escribe en `ENTITY_INTEGRITY_LOG` sin excepción — aunque `is_valid = false`.
4. `ENTITY_INTEGRITY_LOG` es de solo inserción. Trigger PostgreSQL impide UPDATE y DELETE, igual que `system_audit_log`.
5. `is_valid = false` no lanza error HTTP — es resultado de auditoría. `/integrity/failed` permite monitorear.
6. El job `SCHEDULED` corre sobre todas las entidades registradas; `MANUAL` y `ON_DEMAND` se disparan vía endpoint.

### Versionado de Purposes

7. `purpose_family_id = id` propio en la versión 1.
8. Solo una versión `ACTIVE` por familia. Al crear nueva versión la anterior pasa a `SUPERSEDED` en la misma transacción.
9. Purpose vinculada a template con al menos un `AGREEMENT` → inmutable. Toda edición crea nueva versión.
10. Purpose sin agreements → editable in-place; `hash_sha256` se recalcula siempre.
11. `SUPERSEDED` no acepta nuevos vínculos en templates. Los vínculos existentes quedan fijos a la versión que vincularon.
12. No se elimina ninguna versión de purpose.

### Trazabilidad end-to-end

13. `/trace/agreement/{id}` es de solo lectura — no escribe en ningún log.
14. Si algún eslabón no tiene `hash_sha256` calculable se marca `UNKNOWN`.
15. `overallIntegrity`: `OK` si todos válidos, `MISMATCH` si alguno falla, `PARTIAL` si alguno es `UNKNOWN`.

---

## Estructura del módulo (extensión de `audit/`)

```
audit/
├── application/
│   ├── dto/
│   │   ├── AuditContext.java                 (existente)
│   │   ├── AuditLogResponseDto.java          (existente)
│   │   ├── VerifyIntegrityRequest.java       (nuevo)
│   │   ├── EntityIntegrityLogResponse.java   (nuevo)
│   │   └── AgreementTraceResponse.java       (nuevo)
│   └── service/
│       ├── AuditService.java                 (existente)
│       ├── IntegrityVerifier.java            (nuevo — genérico por entityType, reemplaza lógica de AgreementService.verifyIntegrity())
│       └── IntegrityScheduler.java           (nuevo — job periódico unificado, reemplaza AgreementIntegrityScheduler)
├── infrastructure/
│   └── persistence/
│       ├── SystemAuditLogRepository.java     (existente)
│       └── EntityIntegrityLogRepository.java (nuevo)
└── web/
    └── AuditController.java                  (existente — se extiende)
```

Versionado de purposes vive en `purposes/` — no en `audit/`:

```
purposes/
└── web/
    └── PurposeController.java   (existente — se agregan endpoints new-version y family)
```

---

## Endpoints

Extensión de `AuditController` (existentes + nuevos):

| Método | Endpoint | Estado | Reemplaza |
|---|---|---|---|
| `GET` | `/api/audit/logs` | existente | — |
| `GET` | `/api/audit/logs/verify` | existente | — |
| `POST` | `/api/audit/integrity/verify` | **nuevo** | `POST /api/agreements/{id}/verify-integrity` |
| `GET` | `/api/audit/integrity/log` | **nuevo** — `?entityType=&entityId=` | `GET /api/agreements/{id}/integrity-log` |
| `GET` | `/api/audit/integrity/failed` | **nuevo** — `?entityType=` opcional | `GET /api/agreements/integrity-log/failed` |
| `GET` | `/api/audit/trace/agreement/{id}` | **nuevo** | — |

Extensión de `PurposeController`:

| Método | Endpoint | Estado |
|---|---|---|
| `POST` | `/api/purposes/{id}/new-version` | **nuevo** |
| `GET` | `/api/purposes/family/{purposeFamilyId}` | **nuevo** |
| `GET` | `/api/purposes/active/{purposeFamilyId}` | **nuevo** |

---

## Contrato de API

### `POST /api/audit/integrity/verify`

**Request:**
```json
{ "entityType": "PURPOSE", "entityId": "uuid", "checkType": "MANUAL" }
```

**Response 200:**
```json
{
  "status": "success",
  "entityType": "PURPOSE",
  "entityId": "uuid",
  "storedHash": "abc123...",
  "recalculatedHash": "abc123...",
  "isValid": true,
  "checkType": "MANUAL",
  "createdAt": "2026-06-29T14:00:00Z"
}
```

### `GET /api/audit/integrity/failed`

**Query params:** `entityType` (opcional), `page`, `size`.

**Response 200:**
```json
{
  "status": "success",
  "total": 2,
  "page": 0,
  "totalPages": 1,
  "logs": [
    {
      "id": "uuid",
      "entityType": "PURPOSE",
      "entityId": "uuid",
      "storedHash": "abc...",
      "recalculatedHash": "xyz...",
      "isValid": false,
      "checkType": "SCHEDULED",
      "createdAt": "2026-06-28T03:00:00Z"
    }
  ]
}
```

### `GET /api/audit/trace/agreement/{agreementId}`

**Response 200:**
```json
{
  "status": "success",
  "agreementId": "uuid",
  "dataSubjectId": "uuid",
  "createdAt": "2026-05-06T09:14:22Z",
  "chain": {
    "document": {
      "documentId": "uuid",
      "version": 3,
      "isValid": true
    },
    "template": {
      "templateId": "uuid",
      "templateKey": "CONSENT_MARKETING",
      "version": 2,
      "isValid": true
    },
    "purposes": [
      {
        "purposeId": "uuid",
        "purposeFamilyId": "uuid",
        "version": 2,
        "code": "MKT_EMAIL",
        "accepted": true,
        "isValid": false,
        "integrityStatus": "INTEGRITY_MISMATCH"
      }
    ]
  },
  "overallIntegrity": "MISMATCH"
}
```

### `POST /api/purposes/{id}/new-version`

**Request body:** mismos campos que `UpdatePurposeRequest`.

**Response 201:** `PurposeResponse` con `version` incrementado y `hashSha256` recalculado.
- `409` si la purpose no está lockeada (corresponde editar in-place).

### `GET /api/purposes/family/{purposeFamilyId}`

**Response 200:**
```json
{
  "status": "success",
  "purposeFamilyId": "uuid",
  "versions": [
    { "purposeId": "uuid", "version": 1, "status": "SUPERSEDED", "hashSha256": "...", "createdAt": "...", "createdByName": "..." },
    { "purposeId": "uuid", "version": 2, "status": "ACTIVE",     "hashSha256": "...", "createdAt": "...", "createdByName": "..." }
  ]
}
```

---

## Pendiente de definir en el plan de implementación

- DDL `ENTITY_INTEGRITY_LOG` con trigger PostgreSQL de solo inserción.
- Migración `agreement_integrity_log`: tabla existente queda **read-only** como archivo histórico. Nuevas verificaciones de agreements van a `entity_integrity_log` con `entity_type = 'AGREEMENT'`, cadena nueva desde `GENESIS`. Eliminar `AgreementIntegrityScheduler` y la lógica de `verifyIntegrity()` de `AgreementService` — pasan a `IntegrityVerifier` e `IntegrityScheduler`.
- Los endpoints `POST /api/agreements/{id}/verify-integrity`, `GET /api/agreements/{id}/integrity-log` y `GET /api/agreements/integrity-log/failed` se eliminan de `AgreementController` — reemplazados por los nuevos en `AuditController`.
- Migración `PURPOSES`: agregar `purpose_family_id`, `version`, `status`; default `version=1`, `purpose_family_id=id`, `status=ACTIVE` para filas existentes. Recalcular `hash_sha256` faltantes.
- Confirmar campos del hash para `TEMPLATE` y `DOCUMENT` revisando código existente antes de implementar.
- Frecuencia del job `SCHEDULED` de `IntegrityScheduler` (hoy `AgreementIntegrityScheduler` corre a las 3:00 AM diario — mantener o ajustar).
