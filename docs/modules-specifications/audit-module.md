# Módulo: Auditoría (`audit/`)

**Paquete:** `com.leydata.backend.audit`  
**Endpoints base:** `/api/audit/**`  
**Acceso:** Solo `ADMIN`

---

## Responsabilidad

El módulo audit tiene dos responsabilidades independientes:

1. **Log de auditoría operacional (`system_audit_log`)** — registro inmutable de todas las operaciones sensibles del sistema, protegido por triggers de PostgreSQL.
2. **Integridad de entidades de negocio (`entity_integrity_log`)** — verificación y trazabilidad de hashes SHA-256 sobre AGREEMENT, TEMPLATE, DOCUMENT y PURPOSE.

---

## 1. Log de auditoría (`system_audit_log`)

Cada entrada es una fila en `system_audit_log` protegida por triggers que impiden UPDATE y DELETE. La tabla implementa una **cadena de hashes SHA-256** (ledger): cada registro incluye el hash del anterior. Si alguien modifica una fila directamente, la cadena se rompe y es detectable.

### Cuándo se escribe

`AuditService.log()` es llamado por los servicios de negocio en operaciones críticas:

| Operación | Acción registrada |
|---|---|
| Crear usuario | `CREAR_USUARIO` |
| Bloquear/desbloquear usuario | `BLOQUEAR_USUARIO` / `DESBLOQUEAR_USUARIO` |
| Aprobar/rechazar solicitud de propósito | `APROBAR_SOLICITUD` / `RECHAZAR_SOLICITUD` |
| Publicar documento de privacidad | `PUBLICAR_DOCUMENTO` |
| Revocar consentimiento | `REVOCAR_AGREEMENT` |
| Reconsentimiento (cierre automático) | `REVOCAR_AGREEMENT_POR_RECONSENTIMIENTO` |

Cada entrada incluye `tableName`, `recordId`, `action`, `oldData`/`newData`, `actorId`, `actorRole`, `requestId`, `hashSha256`/`previousHashSha256`.

### Protección contra race conditions

`AuditService.log()` adquiere un **advisory lock** de PostgreSQL (`pg_advisory_xact_lock(7719)`) antes de leer el `previousHash`. Esto serializa todas las escrituras al ledger — solo un thread puede ejecutar el par lectura-escritura a la vez.

### Endpoints

```
GET  /api/audit/logs           — Listar entradas (filtros: tableName, actorId, action)
GET  /api/audit/logs/{id}      — Obtener entrada por ID
GET  /api/audit/logs/verify    — Verificar integridad de la cadena completa
```

---

## 2. Integridad de entidades (`entity_integrity_log`)

Verificación centralizada de hashes SHA-256 sobre las 4 entidades de negocio críticas: **AGREEMENT**, **TEMPLATE**, **DOCUMENT**, **PURPOSE**.

### Qué hace cada componente

| Clase | Rol |
|---|---|
| `IntegrityVerifier` | Servicio central — despacha a `recalculateHash()` de cada entidad por switch, escribe en `entity_integrity_log`. Usa `"GENESIS"` como hash previo inicial. |
| `IntegrityScheduler` | `@Scheduled(cron = "0 0 3 * * *")` — corre verificación masiva diaria sobre AGREEMENT, TEMPLATE, DOCUMENT y PURPOSE. Reemplaza el anterior `AgreementIntegrityScheduler`. |
| `AgreementTraceService` | Solo lectura — traza la cadena completa de un agreement: template activo, documento publicado, purposes con drift check (`ap.getPurposeHash()` vs `purpose.getHashSha256()` actual). Devuelve `overallIntegrity: OK | MISMATCH | PARTIAL`. |
| `EntityIntegrityLog` | Entidad JPA mapeada a `entity_integrity_log`. Protegida por trigger de inmutabilidad (V5). |

### Endpoints

```
POST /api/audit/integrity/verify
     body: { "entityType": "AGREEMENT|TEMPLATE|DOCUMENT|PURPOSE",
             "entityId": "<uuid>",
             "checkType": "MANUAL|ON_DEMAND|SCHEDULED" }
     → Recalcula el hash, compara con el almacenado, registra en entity_integrity_log

GET  /api/audit/integrity/log?entityType=<tipo>&entityId=<uuid>
     → Historial de verificaciones de una entidad específica, orden desc

GET  /api/audit/integrity/failed?entityType=<tipo-opcional>
     → Verificaciones con isValid=false. Sin filtro: todas las entidades.

GET  /api/audit/trace/agreement/{id}
     → Traza completa: template (hash actual), documento (hash actual), cada purpose
       (hash al consentir vs. hash actual). Campo overallIntegrity: OK | MISMATCH | PARTIAL
```

### `entity_integrity_log` — campos clave

| Campo | Descripción |
|---|---|
| `entityType` | `AGREEMENT`, `TEMPLATE`, `DOCUMENT`, `PURPOSE` |
| `entityId` | UUID de la entidad verificada |
| `storedHash` | Hash guardado en la entidad al momento de la verificación |
| `recalculatedHash` | Hash recalculado en el momento de la verificación |
| `isValid` | `true` si coinciden |
| `checkType` | `MANUAL`, `ON_DEMAND`, `SCHEDULED` |
| `hashSha256` | Hash de esta fila del log |
| `previousHashSha256Id` | FK al hash de la fila anterior (cadena) |

### Migraciones Flyway (V5–V7)

Estas migraciones se aplican **manualmente** después del primer arranque (Hibernate crea las tablas, luego se corren los scripts):

```bash
psql -U admin -d leydata_db -h localhost -p 5433 \
  -f backend/src/main/resources/db/migration/V5__entity_integrity_log_trigger.sql
psql -U admin -d leydata_db -h localhost -p 5433 \
  -f backend/src/main/resources/db/migration/V6__purposes_versioning_backfill.sql
psql -U admin -d leydata_db -h localhost -p 5433 \
  -f backend/src/main/resources/db/migration/V7__purposes_code_not_unique.sql
```

| Script | Qué hace |
|---|---|
| `V5` | Trigger de inmutabilidad sobre `entity_integrity_log` (impide UPDATE/DELETE) |
| `V6` | Backfill idempotente: `purpose_family_id = id`, `version = 1`, `status = 'ACTIVE'` para purposes existentes |
| `V7` | Elimina la constraint `UNIQUE` sobre `purposes.code` (el versionado permite el mismo código en versiones distintas de la misma familia) |

---

## Archivos clave

| Archivo | Rol |
|---|---|
| `audit/web/AuditController.java` | Endpoints de auditoría e integridad |
| `audit/application/service/AuditService.java` | Escritura al ledger operacional con advisory lock y hash chain |
| `audit/application/service/IntegrityVerifier.java` | Verificación y registro en entity_integrity_log |
| `audit/application/service/IntegrityScheduler.java` | Scheduler diario (3am) — cubre las 4 entidades |
| `audit/application/service/AgreementTraceService.java` | Traza la cadena completa de un agreement |
| `audit/application/dto/AgreementTraceResponse.java` | Respuesta con DocumentLink, TemplateLink, PurposeLink, overallIntegrity |
| `audit/application/dto/EntityIntegrityLogResponse.java` | Respuesta de verificación individual |
| `audit/application/dto/VerifyIntegrityRequest.java` | Request de verificación (entityType, entityId, checkType) |
| `audit/infrastructure/persistence/EntityIntegrityLogRepository.java` | Repositorio de entity_integrity_log |
| `audit/infrastructure/persistence/SystemAuditLogRepository.java` | Repositorio del ledger operacional |
| `entity/EntityIntegrityLog.java` | Entidad JPA — protegida por trigger V5 |
| `entity/SystemAuditLog.java` | Entidad JPA — protegida por triggers de auditoría |

---

## Nota sobre triggers de PostgreSQL

Ambas tablas (`system_audit_log` y `entity_integrity_log`) tienen triggers que impiden UPDATE y DELETE. No intentar modificar filas directamente en psql — el trigger lanzará una excepción.
