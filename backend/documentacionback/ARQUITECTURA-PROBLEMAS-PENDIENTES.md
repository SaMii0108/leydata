# LeyData — Problemas Arquitectónicos y Deuda Técnica Pendiente

**Fecha de análisis:** 2026-06-27  
**Branch:** feature/keycloak-first-model  
**Versión:** 469f730 feat: add Templates module to Bruno and Postman collections  
**Contexto:** Evaluación pre-producción. La Ley 21.719 entra en vigor en diciembre de 2026.

---

## Índice

1. [Críticos — bloquean producción](#1-críticos--bloquean-producción)
2. [Altos — deben resolverse antes de go-live](#2-altos--deben-resolverse-antes-de-go-live)
3. [Medios — mejoran resiliencia y trazabilidad](#3-medios--mejoran-resiliencia-y-trazabilidad)
4. [Infraestructura proyectada — componentes faltantes](#4-infraestructura-proyectada--componentes-faltantes)
5. [Bugs de API pendientes de resolver](#5-bugs-de-api-ya-corregidos-en-código--pendientes-de-verificación-completa)
6. [Deuda técnica menor](#6-deuda-técnica-menor)

---

## 1. Críticos — bloquean producción

### 🔴 Race condition en el ledger de auditoría

**Archivo:** `backend/src/main/java/com/leydata/backend/audit/application/service/AuditService.java:58`

**Problema:**
```java
String previousHash = auditLogRepository.findTopByOrderByCreatedAtDesc()
    .map(SystemAuditLog::getLogHash)
    .orElse("GENESIS");
```
Bajo concurrencia, dos threads pueden ejecutar este `SELECT TOP 1` simultáneamente, leer el mismo `previousHash` y generar dos registros encadenados al mismo nodo anterior. Esto **rompe la cadena de hashes SHA-256**, invalidando el paradigma de Ledger Inmutable que es el pilar de accountability exigido por la Ley 21.719.

**Impacto:** Si el CPDT (Consejo para la Protección de Datos) audita la cadena de integridad y encuentra un fork, el sistema pierde validez probatoria.

**Solución propuesta:**
- Opción A (simple): Usar `SELECT ... FOR UPDATE SKIP LOCKED` en el query del `previousHash` para serializar las escrituras de auditoría.
- Opción B (robusta): Reemplazar `findTopByOrderByCreatedAtDesc()` por una **secuencia PostgreSQL** (`audit_log_seq`) que garantiza orden atómico sin locks a nivel de aplicación.
- Opción C: Mover el encadenamiento de hashes a un trigger PostgreSQL que se ejecute dentro de la misma transacción del INSERT.

---

### 🔴 Sin réplica de PostgreSQL (Single Point of Failure)

**Problema:** La arquitectura proyectada tiene un único nodo de PostgreSQL protegido por PgBouncer. Si el primary cae, **el sistema completo deja de funcionar** — incluyendo la consulta de consentimientos de la que dependen todos los sistemas de la empresa.

**Impacto:** Caída total en producción. Inaceptable para un sistema de compliance con deadline legal.

**Solución propuesta:**
- Configurar al menos una **PostgreSQL Streaming Replica** (read replica).
- Las consultas de lectura de consentimiento van a la replica; las escrituras transaccionales van al primary.
- Usar **PgBouncer con dos pools** (write → primary, read → replica) o un proxy como Patroni/HAProxy para failover automático.

---

## 2. Altos — deben resolverse antes de go-live

### 🟠 UserStatusFilter consulta Postgres en cada request

**Archivo:** `backend/src/main/java/com/leydata/backend/security/UserStatusFilter.java`

**Problema:** El filtro de revocación híbrida hace un `SELECT` a `UsersRepository` en **cada request HTTP**, para verificar si el usuario fue bloqueado. Con múltiples instancias del monolito y alta concurrencia, esto se convierte en N × tráfico_total queries de lectura por segundo al primary de PostgreSQL, saturando PgBouncer incluso antes de llegar a la lógica de negocio.

**Solución propuesta:**
```
Redis key: user:{keycloakId}:status  →  valor: "active" | "blocked"
TTL: 30 segundos (máxima ventana de inconsistencia aceptable)

Flujo:
1. UserStatusFilter consulta Redis primero
2. Si miss → consulta Postgres → escribe en Redis con TTL
3. Si el admin bloquea un usuario → invalidar la key de Redis inmediatamente
```

Esto reduce las queries a Postgres a una fracción del tráfico real y acota el tiempo de propagación del bloqueo a 30 segundos, documentable como SLA interno.

---

### 🟠 Ausencia de X-Request-ID en el audit log

**Archivo:** `backend/src/main/java/com/leydata/backend/audit/application/service/AuditService.java`  
**Entidad:** `backend/src/main/java/com/leydata/backend/entity/SystemAuditLog.java`

**Problema:** El `AuditService` registra `ip_address` y `user_agent`, pero no un identificador de correlación de request. Sin este campo, es **imposible correlacionar** un registro del audit log con los logs del WAF o del API Gateway ante una fiscalización técnica. El auditor verá la acción en Postgres, verá el request en NGINX, pero no podrá unirlos de forma determinista.

**Solución propuesta:**

1. Configurar WAF y NGINX para generar y propagar `X-Request-ID`:
```nginx
# nginx.conf
add_header X-Request-ID $request_id;
proxy_set_header X-Request-ID $request_id;
log_format main '$remote_addr - $request_id - $request - $status';
```

2. Agregar campo a la entidad y al contexto de auditoría:
```java
// SystemAuditLog.java
@Column(name = "request_id")
private String requestId;

// AuditService.java — en extractClientIp() ya tienes acceso a HttpServletRequest
private String extractRequestId() {
    try {
        HttpServletRequest request = ((ServletRequestAttributes)
            RequestContextHolder.currentRequestAttributes()).getRequest();
        return request.getHeader("X-Request-ID");
    } catch (Exception e) {
        return "UNKNOWN";
    }
}
```

3. Script de migración SQL:
```sql
ALTER TABLE system_audit_log ADD COLUMN request_id VARCHAR(64);
```

> **Nota:** La tabla tiene triggers que bloquean UPDATE/DELETE, pero ALTER TABLE para agregar columnas sí está permitido.

---

### 🟠 Ventana de inconsistencia en caché de consentimientos (Redis)

**Problema:** El plan de invalidar Redis únicamente cuando se revoca un consentimiento crea una **ventana de inconsistencia ilimitada** si la invalidación falla (ej. timeout de red entre el servicio que revoca y Redis). Un sistema externo podría recibir "consentimiento vigente" para un consentimiento ya revocado durante horas.

**Solución propuesta:**
- Usar **TTL corto (30–60 segundos)** en todas las keys de consentimiento, combinado con invalidación activa en la revocación.
- El peor caso de inconsistencia queda acotado al TTL, documentable ante el CPDT como "latencia técnica de propagación".
- Implementar un **evento de dominio** (`ConsentimientoRevocadoEvent`) que dispare la invalidación de Redis de forma explícita y logueable.

---

## 3. Medios — mejoran resiliencia y trazabilidad

### 🟡 Redis sin réplica (Single Point of Failure de caché)

**Problema:** Si Redis cae, el `UserStatusFilter` empieza a golpear Postgres en cada request (si se implementa el punto 2.1), y los sistemas externos de consulta de consentimiento golpean Postgres directamente. Bajo carga alta esto puede tumbar el primary.

**Solución:** Redis Sentinel (3 nodos: 1 master + 2 replicas + sentinel) o Redis Cluster. Como mínimo, una replica con failover manual.

---

### 🟡 Sin circuit breaker para consumidores externos

**Problema:** Los sistemas de la empresa que consultan LeyData síncronamente (REST) no tienen protección si LeyData está lento o saturado. Un pico de latencia en LeyData hace que todos los threads de los sistemas consumidores queden bloqueados esperando respuesta, produciendo un **cascade failure** en múltiples sistemas simultáneamente.

**Solución:** Los equipos consumidores deben implementar **Resilience4j** (o equivalente) con circuit breaker + timeout en sus clientes HTTP hacia LeyData. Desde LeyData, implementar **rate limiting por client_id** en el API Gateway para proteger la BD de abuso.

---

### 🟡 Falta endpoint de consulta de consentimiento para sistemas externos

**Problema identificado en el grafo:** No existe un endpoint optimizado para la consulta de consentimiento B2B (el caso de uso principal del sistema: "¿puede el sistema X procesar el dato del titular Y para la finalidad Z?"). Los sistemas externos tendrían que construir esta lógica ellos mismos interpretando múltiples endpoints.

**Solución propuesta:**
```
GET /api/consent/check?titularId={id}&purposeId={id}&dataCategory={cat}
→ { "permitted": true/false, "validUntil": "...", "legalBasis": "...", "cachedAt": "..." }
```
Este endpoint debería ser el único punto de integración B2B, servido desde Redis, con autenticación por API Key (no JWT de usuario).

---

### 🟡 El frontend usa mockData.ts (no conectado al backend real)

**Detectado en el grafo:** `mockData.ts` y `mockUsers.ts` en el frontend están en la comunidad de componentes activos. El frontend no está integrado al backend real todavía.

**Archivos afectados:**
- `frontend/src/utils/mockData.ts`
- `frontend/src/features/auth/mockUsers.ts`

---

## 4. Infraestructura proyectada — componentes faltantes

```
Estado actual del diseño vs. lo que se necesita:

[Internet]
    │
  [WAF]                              ✅ planificado
    │
[API Gateway / NGINX]                ✅ planificado (SSL, rate limiting)
    │
[Load Balancer]                      ✅ planificado
   /|\
[App][App][App]                      ✅ planificado (monolito stateless, escala horizontal)
    │         │
[PgBouncer]  [PgBouncer read]        ⚠️  solo 1 PgBouncer planificado
    │              │
[PostgreSQL Primary] → [Replica]     ❌ replica NO planificada
    │
  [Redis Master]                     ✅ planificado
    │
  [Redis Replica / Sentinel]         ❌ replica NO planificada

Componentes de observabilidad (ninguno planificado):
  ❌ Centralización de logs (ELK / Loki+Grafana)
  ❌ Métricas de aplicación (Micrometer → Prometheus → Grafana)
  ❌ Alertas de disponibilidad (uptime del endpoint /health)
  ❌ Trazas distribuidas (OpenTelemetry) para correlacionar WAF + App + BD
```

---

## 5. Bugs de API ya corregidos en código — pendientes de verificación completa

Los siguientes bugs fueron corregidos en el código fuente pero aún no se ha ejecutado una corrida completa de pruebas post-fix:

| # | Endpoint | Problema original | Fix aplicado | Archivo |
|---|----------|-------------------|--------------|---------|
| 1 | `POST /api/privacy-documents` sin `name` | 500 → debería 400 | Handler `MethodArgumentNotValidException` | `GlobalExceptionHandler.java` |
| 2 | `GET /api/purpose-requests` (ADMIN) | 403 → debería 200 | `hasAnyRole('DPO','ADMIN')` | `PurposeRequestController.java` |
| 3 | `GET /api/purpose-requests/pending` (ADMIN) | 403 → debería 200 | `hasAnyRole('DPO','ADMIN')` | `PurposeRequestController.java` |
| 4 | `PUT /api/users/{id}` con `domainIds:[]` | 400 → debería 200 | Early return en `assignUserDomains` | `UserService.java` |
| 5 | `GET /api/users/not-a-uuid` | 500 → debería 400 | Handler `MethodArgumentTypeMismatchException` | `GlobalExceptionHandler.java` |
| 6 | `PATCH /api/notifications/{id}/read` (inexistente) | 400 → debería 404 | `NoSuchElementException` → handler 404 | `NotificationService.java` + `GlobalExceptionHandler.java` |
| 7 | `POST /api/privacy-documents` con enum inválido | 500 → debería 400 | Handler `HttpMessageNotReadableException` | `GlobalExceptionHandler.java` |
| 8 | `PUT /api/users/{id}` con `domainIds:[uuid]` | 500 → debería 200 | `ud.getId().getDomainId()` en vez de `ud.getDomain().getId()` (lazy load) | `UserService.java` |

Ver reporte completo: [`endpoint-test-report.md`](endpoint-test-report.md)

---

## 6. Deuda técnica menor

| Item | Descripción | Archivo |
|------|-------------|---------|
| Orden de handlers en `GlobalExceptionHandler` | `RuntimeException` está declarado DESPUÉS de `Exception` pero ANTES de algunos handlers específicos. Spring evalúa por especificidad, pero el orden puede generar confusión en mantenimiento. Ordenar de más específico a más genérico. | `GlobalExceptionHandler.java` |
| `PATCH /api/purpose-requests/{id}/review` con ID inexistente | Devuelve 400 en vez de 404 — el servicio lanza excepción genérica en vez de `PurposeRequestNotFoundException`. | `PurposeRequestService.java` |
| Endpoints JEFE_DOMINIO requieren usuario manual en Keycloak | Para pruebas CI/CD reproducibles se necesita un script de seed que cree el usuario `jefe@test.cl` en Keycloak automáticamente. | `DataSeeder.java` / scripts de init |
| `AuditService` es un god node (26 edges en el grafo) | Considerar separar en `AuditWriter` (persistencia) y `AuditHashChain` (integridad) para facilitar testing unitario y futura migración a un servicio separado. | `AuditService.java` |
| El grafo detectó `TemplatePurposes` y `PurposeDataCategories` como módulos parcialmente incompletos | Verificar cobertura de endpoints para estas entidades. | Varios |

---

## Checklist pre-producción (diciembre 2026)

### Código
- [ ] Resolver race condition en `AuditService` (hash chain bajo concurrencia)
- [ ] Agregar `X-Request-ID` al audit log y a la entidad `SystemAuditLog`
- [ ] Mover `UserStatusFilter` a consultar Redis en vez de Postgres
- [ ] Crear endpoint `GET /api/consent/check` para integración B2B
- [ ] Implementar `ConsentimientoRevocadoEvent` para invalidación activa de Redis
- [ ] Completar integración frontend → backend (eliminar `mockData.ts`)
- [ ] Correr corrida completa de pruebas de endpoints post todos los fixes

### Infraestructura
- [ ] Configurar PostgreSQL Streaming Replica
- [ ] Configurar PgBouncer con pool separado para reads y writes
- [ ] Configurar Redis Sentinel o Cluster
- [ ] Implementar WAF con `X-Request-ID` generado y propagado
- [ ] Configurar NGINX para log con `X-Request-ID`
- [ ] Definir TTL de keys de consentimiento en Redis (recomendado: 30–60s)

### Observabilidad (para trazabilidad exigida por Ley 21.719)
- [ ] Centralización de logs: WAF + NGINX + Spring Boot → mismo destino (ELK o Loki)
- [ ] Campo `request_id` como campo de correlación en todos los logs
- [ ] Dashboard de métricas: latencia del endpoint de consent check, tasa de error, uso de Redis
- [ ] Alerta si la cadena de hashes del audit log se rompe (endpoint `/api/audit/logs/verify`)
- [ ] Documentar el SLA de propagación de revocación (TTL de Redis) para incluir en el aviso de privacidad

### Legal / Compliance
- [ ] Validar con asesor legal que el TTL de 30-60s en caché de consentimientos es aceptable bajo Ley 21.719 art. de revocación
- [ ] Documentar el flujo de respuesta a solicitudes de titulares (acceso, rectificación, supresión)
- [ ] Definir política de retención del `system_audit_log` (¿cuántos años se guardan?)
