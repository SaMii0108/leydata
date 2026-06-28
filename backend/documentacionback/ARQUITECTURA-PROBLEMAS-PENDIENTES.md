# LeyData — Problemas Arquitectónicos y Deuda Técnica Pendiente

**Fecha de análisis:** 2026-06-27  
**Última actualización:** 2026-06-28  
**Branch:** infra/postgres-replica-pgbouncer  
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

### ✅ ~~Race condition en el ledger de auditoría~~ — RESUELTO

**Archivo:** `backend/src/main/java/com/leydata/backend/audit/application/service/AuditService.java`

**Fix aplicado:** `pg_advisory_xact_lock(7719)` en `AuditService.log()` antes de leer el `previousHash`. Serializa escrituras al ledger — solo un thread a la vez puede ejecutar el par lectura-escritura de hash. Lock se libera automáticamente al hacer commit.

**Archivos modificados:**
- `AuditService.java` — llama a `acquireAuditChainLock()` antes del SELECT
- `SystemAuditLogRepository.java` — método `acquireAuditChainLock()` con `@Query(nativeQuery)`

---

### ✅ ~~Sin réplica de PostgreSQL (Single Point of Failure)~~ — RESUELTO PARCIALMENTE

**Fix aplicado (Fase 1 y 2):**
- `db-replica` en `docker-compose.yml` — PostgreSQL 15 en modo standby con streaming replication desde el primary.
- `docker/postgres-primary/init-replication.sh` — crea usuario `replicator`, configura `wal_level=replica` y `pg_hba.conf`.
- `docker/postgres-replica/entrypoint.sh` — hace `pg_basebackup -R` en el primer arranque, luego inicia en modo standby automáticamente.
- `pgbouncer` en `docker-compose.yml` — connection pooler frente al primary (puerto 5435). Elimina conexiones directas al contenedor de BD.

**Puertos resultantes:**
- `5433` → PostgreSQL primary (escribe y lee)
- `5434` → PostgreSQL replica (solo lectura, standby)
- `5435` → PgBouncer (punto de entrada recomendado para el backend en producción)

**Pendiente — Fase 2b (deuda técnica):**
Enrutar lecturas de Spring Boot a la replica via `AbstractRoutingDataSource`. Requiere:
- Dos `DataSource` beans: `writeDataSource` (→ primary/pgbouncer) y `readDataSource` (→ replica)
- Un `ReadWriteRoutingDataSource extends AbstractRoutingDataSource` que inspeccione `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`
- Anotar las queries de solo lectura con `@Transactional(readOnly=true)`

**Pendiente — Fase 3 (deuda técnica):**
Failover automático con **Patroni** (coordina etcd + PostgreSQL para promover la replica automáticamente si el primary cae sin intervención humana). Recomendado para producción antes del go-live de diciembre 2026.

---

## 2. Altos — deben resolverse antes de go-live

### ✅ ~~UserStatusFilter consulta Postgres en cada request~~ — RESUELTO

**Archivo:** `backend/src/main/java/com/leydata/backend/security/UserStatusFilter.java`

**Fix aplicado:** Cache-aside sobre Redis con clave `user:{keycloakId}:blocked`.
- Cache hit → usa valor en memoria, cero queries a Postgres
- Cache miss → consulta Postgres, escribe resultado en Redis
- "bloqueado=true" → sin TTL (permanente hasta desbloqueo explícito)
- "bloqueado=false" → TTL 30s (renueva periódicamente desde Postgres)
- `UserService.blockUser()` escribe `"true"` en Redis inmediatamente (mismo request que hace el bloqueo)

**Archivos modificados:**
- `UserStatusFilter.java` — inyecta `StringRedisTemplate`, método privado `isBlocked()` con lógica cache-aside
- `UserService.java` — inyecta `StringRedisTemplate`, escribe en Redis tras guardar en `user_status`

**Infraestructura:**
- ✅ Redis en `docker-compose.yml` (redis:7-alpine, puerto 6379)
- ✅ `spring-boot-starter-data-redis` en `pom.xml`
- ✅ Config `spring.data.redis.host/port` en `application.properties`

---

### ✅ ~~Ausencia de X-Request-ID en el audit log~~ — RESUELTO

**Fix aplicado:**
- `SystemAuditLog.java` — campo `requestId` agregado (Hibernate crea la columna automáticamente con `ddl-auto=update`)
- `AuditService.java` — método `extractRequestId()` lee el header `X-Request-ID` del request HTTP y lo persiste en cada log

**Pendiente de infra:** NGINX/WAF deben configurarse para generar y propagar `X-Request-ID`. Mientras tanto el campo quedará `null` en los registros — sin impacto funcional.

```nginx
# nginx.conf — cuando se configure
add_header X-Request-ID $request_id;
proxy_set_header X-Request-ID $request_id;
log_format main '$remote_addr - $request_id - $request - $status';
```

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

### 🟡 Falta endpoint de consulta de consentimiento para sistemas externos (Propagación B2B)

**Problema:** No existe un endpoint optimizado para la consulta de consentimiento B2B — el caso de uso principal del sistema: "¿puede el sistema X procesar el dato del titular Y para la finalidad Z?". Los sistemas externos tendrían que construir esta lógica ellos mismos interpretando múltiples endpoints.

**Solución propuesta:**
```
GET /api/consent/check?titularId={id}&purposeId={id}&dataCategory={cat}
→ { "permitted": true/false, "validUntil": "...", "legalBasis": "...", "cachedAt": "..." }
```
Este endpoint debe ser el único punto de integración B2B, servido desde Redis (caché con TTL 30-60s), con autenticación por API Key (no JWT de usuario).

---

### ✅ ~~Falta el ciclo de captura de consentimiento~~ — RESUELTO

**Fix aplicado:** Módulo `agreement/` implementado con ciclo completo.

**Endpoints disponibles:**
- `POST /api/agreements` — registra decisión del titular por cada purpose; reconsent automático si ya había ACTIVE para el mismo `(dataSubjectId, templateId)`
- `GET /api/agreements/active?dataSubjectId=&templateId=` — consulta para el orquestador (200 si existe, 404 si no)
- `GET /api/agreements` — listado con filtros opcionales
- `GET /api/agreements/{id}` — detalle completo con purposes
- `POST /api/agreements/{id}/verify-integrity` — verificación SHA-256 bajo demanda
- `GET /api/agreements/{id}/integrity-log` — historial de verificaciones
- `GET /api/agreements/integrity-log/failed` — verificaciones fallidas para auditoría

**Integridad:** cada agreement calcula un SHA-256 encadenado al agreement anterior (ledger análogo al de `system_audit_log`).

**Módulos creados:** `agreement/web/`, `agreement/application/service/`, `agreement/application/dto/`, `agreement/infrastructure/persistence/`, `agreement/domain/exception/`.

---

### 🔴 Falta el ciclo de revocación de consentimiento

**Problema:** No existe el flujo donde un titular revoca un consentimiento previamente otorgado. La Ley 21.719 exige que la revocación sea tan fácil como el otorgamiento y que tenga efecto inmediato.

**Lo que falta implementar:**

```
Flujo de revocación:
1. Titular (autenticado con rol TITULAR) solicita revocar → PATCH /api/agreements/{id}/revoke
2. Backend valida que el acuerdo pertenece al titular autenticado
3. Marca el acuerdo como revocado:
   - revokedAt = now()
   - revokedBy = keycloakId del TITULAR
   - revocationReason (opcional, libre)
4. Invalida la key en Redis para que sistemas externos reciban "permitted: false" de inmediato
5. Dispara ConsentimientoRevocadoEvent → notificación al DPO/responsable del dominio
6. AuditService registra la revocación con todos los campos de trazabilidad
```

**Punto crítico con Redis:** la revocación debe invalidar la key `consent:{titularId}:{purposeId}` en Redis **dentro de la misma transacción** (o como compensación si Redis falla), para que el endpoint B2B `/api/consent/check` refleje el cambio de inmediato. Sin esto, la ventana de inconsistencia es el TTL completo (30-60s).

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
    │
[PgBouncer :5435]                    ✅ implementado en docker-compose
    │
[PostgreSQL Primary :5433] → [Replica :5434]   ✅ streaming replication implementada
                                     ⚠️  routing de reads a replica pendiente (Fase 2b)
                                     ❌ failover automático (Patroni) pendiente (Fase 3)
    │
  [Redis Master]                     ✅ implementado
    │
  [Redis Replica / Sentinel]         ❌ replica NO planificada

Componentes de observabilidad (ninguno planificado):
  ❌ Centralización de logs (ELK / Loki+Grafana)
  ❌ Métricas de aplicación (Micrometer → Prometheus → Grafana)
  ❌ Alertas de disponibilidad (uptime del endpoint /health)
  ❌ Trazas distribuidas (OpenTelemetry) para correlacionar WAF + App + BD
```

---

## 5. Bugs de API — todos corregidos y verificados

Todos los bugs fueron corregidos y verificados contra el backend en ejecución:

| # | Endpoint | Problema original | Fix aplicado | Archivo |
|---|----------|-------------------|--------------|---------|
| # | Endpoint | Problema original | Fix aplicado | Estado |
|---|----------|-------------------|--------------|--------|
| 1 | `POST /api/privacy-documents` sin `name` | 500 → 400 | Handler `MethodArgumentNotValidException` en `GlobalExceptionHandler` | ✅ Verificado |
| 2 | `GET /api/purpose-requests` (ADMIN) | 403 → 200 | `hasAnyRole('DPO','ADMIN')` en `PurposeRequestController` | ✅ Verificado |
| 3 | `GET /api/purpose-requests/pending` (ADMIN) | 403 → 200 | `hasAnyRole('DPO','ADMIN')` en `PurposeRequestController` | ✅ Verificado |
| 4 | `PUT /api/users/{id}` con `domainIds:[]` | 400 → 200 | Early return en `assignUserDomains` de `UserService` | ✅ Verificado |
| 5 | `GET /api/users/not-a-uuid` | 500 → 400 | Handler `MethodArgumentTypeMismatchException` en `GlobalExceptionHandler` | ✅ Verificado |
| 6 | `PATCH /api/notifications/{id}/read` (inexistente) | 400 → 404 | `NoSuchElementException` en `NotificationService` + handler 404 | ✅ Verificado |
| 7 | `POST /api/privacy-documents` con enum inválido | 500 → 400 | Handler `HttpMessageNotReadableException` en `GlobalExceptionHandler` | ✅ Verificado |
| 8 | `PUT /api/users/{id}` con `domainIds:[uuid]` | 500 → 200 | `ud.getId().getDomainId()` reemplaza lazy load en `UserService` | ✅ Verificado |

Ver reporte completo: [`endpoint-test-report.md`](endpoint-test-report.md)

---

## 6. Deuda técnica menor

| Item | Descripción | Archivo |
|------|-------------|---------|
| ~~Orden de handlers en `GlobalExceptionHandler`~~ | ✅ Resuelto — `RuntimeException` movido antes de `Exception`, código muerto eliminado, Javadocs verbosos removidos. | `GlobalExceptionHandler.java` |
| ~~`PATCH /api/purpose-requests/{id}/review` con ID inexistente~~ | ✅ Resuelto — cambiado `IllegalArgumentException` por `NoSuchElementException` (handler 404 existente). | `PurposeRequestService.java` |
| ~~`TemplateNotFoundException` sin handler en `GlobalExceptionHandler`~~ | ✅ Resuelto — `GET /api/templates/{id}` con ID inexistente devolvía 500, ahora 404. | `GlobalExceptionHandler.java` |
| ~~Usuarios de prueba JEFE_DOMINIO y DPO no existían en el script~~ | ✅ Resuelto — `setup-keycloak.sh` ahora crea `jefe@test.cl` (JEFE_DOMINIO) y `dpo@leydata.cl` (DPO). | `scripts/setup-keycloak.sh` |
| `AuditService` es un god node (26 edges en el grafo) | Considerar separar en `AuditWriter` (persistencia) y `AuditHashChain` (integridad) para facilitar testing unitario y futura migración a un servicio separado. | `AuditService.java` |
| `ddl-auto=update` en producción | Hibernate gestiona el esquema automáticamente — válido para desarrollo, peligroso en producción (no hay rollback, no hay historial, riesgo con múltiples instancias). Antes del go-live migrar a **Flyway**: (1) exportar esquema actual como `V1__baseline.sql`, (2) cambiar a `ddl-auto=validate`, (3) todo cambio futuro en scripts `V2__...sql`. | `application.properties` |

---

## Checklist pre-producción (diciembre 2026)

### Código
- [x] Resolver race condition en `AuditService` (hash chain bajo concurrencia)
- [x] Agregar `X-Request-ID` al audit log y a la entidad `SystemAuditLog`
- [x] Mover `UserStatusFilter` a consultar Redis en vez de Postgres (cache-aside implementado)
- [x] Implementar ciclo de captura de consentimiento (`agreement/` — `POST /api/agreements`, ledger SHA-256, reconsent automático)
- [ ] Implementar ciclo de revocación de consentimiento (`PATCH /api/agreements/{id}/revoke` — flujo en sección 3)
- [ ] Crear endpoint `GET /api/consent/check` para integración B2B
- [ ] Implementar `ConsentimientoRevocadoEvent` para invalidación activa de Redis
- [ ] Migrar gestión de esquema de `ddl-auto=update` a Flyway (`ddl-auto=validate` + scripts `V1__baseline.sql`, `V2__...`)
- [ ] Correr corrida completa de pruebas de endpoints post todos los fixes

### Infraestructura
- [x] Configurar PostgreSQL Streaming Replica (`db-replica` en docker-compose, `docker/postgres-primary/` y `docker/postgres-replica/`)
- [x] Agregar PgBouncer al Docker Compose (puerto 5435, pool → primary)
- [ ] Enrutar lecturas Spring Boot a la replica (`AbstractRoutingDataSource`) — Fase 2b
- [ ] Failover automático con Patroni — Fase 3
- [x] Agregar Redis al Docker Compose (redis:7-alpine, puerto 6379)
- [ ] Configurar Redis Sentinel o Cluster (para producción)
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
