# Módulo: Auditoría (`audit/`)

**Paquete:** `com.leydata.backend.audit`  
**Endpoints base:** `/api/audit/logs/**`  
**Acceso:** Solo `ADMIN`

---

## Responsabilidad

Log de auditoría **inmutable** de todas las operaciones sensibles del sistema. Cada entrada es una fila en `system_audit_log` protegida por triggers de PostgreSQL que impiden UPDATE y DELETE — ningún código de aplicación puede alterar el historial.

La tabla implementa una **cadena de hashes SHA-256** (ledger): cada registro incluye el hash del registro anterior. Si alguien modifica una fila directamente en la BD, la cadena se rompe y es detectable.

---

## Cuándo se escribe en el audit log

`AuditService.log()` es llamado por los servicios de negocio en operaciones críticas:

| Operación | Acción registrada |
|---|---|
| Crear usuario | `CREAR_USUARIO` |
| Bloquear/desbloquear usuario | `BLOQUEAR_USUARIO` / `DESBLOQUEAR_USUARIO` |
| Aprobar/rechazar solicitud de propósito | `APROBAR_SOLICITUD` / `RECHAZAR_SOLICITUD` |
| Publicar documento de privacidad | `PUBLICAR_DOCUMENTO` |
| Revocar consentimiento | `REVOCAR_AGREEMENT` |
| Reconsentimiento (cierre automático) | `REVOCAR_AGREEMENT_POR_RECONSENTIMIENTO` |

Cada entrada incluye:
- `tableName` — tabla afectada
- `recordId` — UUID del registro afectado
- `action` — qué operación se realizó
- `oldData` / `newData` — estado antes y después (JSON)
- `actorId` — `keycloak_id` del usuario que ejecutó la acción (o `SYSTEM` / `ORCHESTRATOR`)
- `actorRole` — rol en el momento de la acción
- `requestId` — header `X-Request-ID` si está presente (para correlación con WAF/NGINX)
- `hashSha256` / `previousHashSha256` — cadena de integridad

---

## Protección contra race conditions

`AuditService.log()` adquiere un **advisory lock** de PostgreSQL (`pg_advisory_xact_lock(7719)`) antes de leer el `previousHash`. Esto serializa todas las escrituras al ledger a nivel de base de datos — solo un thread puede ejecutar el par lectura-escritura a la vez. El lock se libera automáticamente al hacer commit.

Sin este lock, dos threads podrían leer el mismo `previousHash` y generar entradas con el mismo `previousHash`, rompiendo la cadena sin que ninguna fila sea inválida individualmente.

---

## Endpoints

```
GET  /api/audit/logs           — Listar entradas (con filtros: tableName, actorId, action)
GET  /api/audit/logs/{id}      — Obtener entrada por ID
GET  /api/audit/logs/verify    — Verificar integridad de la cadena completa
```

El endpoint `/verify` recorre todas las entradas ordenadas por `createdAt` y verifica que cada `hashSha256` coincide con el hash calculado de esa fila, y que `previousHashSha256` apunta al hash de la fila anterior.

---

## Archivos clave

| Archivo | Rol |
|---|---|
| `audit/web/AuditController.java` | Endpoints de consulta y verificación |
| `audit/application/service/AuditService.java` | Escritura al ledger con advisory lock y hash chain |
| `audit/application/dto/AuditContext.java` | Builder para construir cada entrada antes de persistir |
| `audit/infrastructure/persistence/SystemAuditLogRepository.java` | JPA + método `acquireAuditChainLock()` |
| `entity/SystemAuditLog.java` | Entidad JPA — protegida por triggers en PostgreSQL |

---

## Nota sobre el trigger de PostgreSQL

La tabla `system_audit_log` tiene triggers que impiden UPDATE y DELETE. No intentar modificar filas directamente en psql — el trigger lanzará una excepción. Para correcciones, agregar una nueva entrada de `CORRECCION` con el `oldData`/`newData` correspondiente.
