# LeyData — Problemas Arquitectónicos y Deuda Técnica Pendiente

**Fecha de análisis:** 2026-06-27  
**Última actualización:** 2026-07-01 (bugs de lógica y M2M corregidos)  
**Branch:** feature/orchestrator-module  
**Contexto:** Evaluación pre-producción. La Ley 21.719 entra en vigor en diciembre de 2026.

---

## Índice

1. [Críticos — bloquean producción](#1-críticos--bloquean-producción)
2. [Altos — deben resolverse antes de go-live](#2-altos--deben-resolverse-antes-de-go-live)
3. [Medios — mejoran resiliencia y trazabilidad](#3-medios--mejoran-resiliencia-y-trazabilidad)
4. [Infraestructura proyectada — componentes faltantes](#4-infraestructura-proyectada--componentes-faltantes)
5. [Bugs de API y lógica — corregidos](#5-bugs-de-api-y-lógica--corregidos)
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

**✅ Fase 2b — RESUELTO:**
Lecturas de Spring Boot enrutadas automáticamente a la réplica vía `AbstractRoutingDataSource`.

**Archivos creados:**
- `config/DataSourceType.java` — enum `WRITE / READ`
- `config/ReadWriteRoutingDataSource.java` — routing por `TransactionSynchronizationManager.isCurrentTransactionReadOnly()`
- `config/DataSourceConfig.java` — dos pools HikariCP + `LazyConnectionDataSourceProxy` como `@Primary DataSource`

**`application.properties`:** bloque `spring.datasource.*` reemplazado por `spring.datasource.write.*` (→ PgBouncer :5435) y `spring.datasource.read.*` (→ réplica :5434).

**Cómo funciona:** cualquier método anotado con `@Transactional(readOnly = true)` recibe conexión del pool de la réplica. Todos los services de lectura ya tenían esta anotación.

Ver doc completo: [`DATASOURCE-ROUTING.md`](DATASOURCE-ROUTING.md)

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

**Fix de infra aplicado (Julio 2026):** NGINX implementado en `:80` con `$request_id` generado y propagado al backend. El campo `requestId` del audit log ya recibe el valor desde el header.

```nginx
# nginx/conf.d/leydata.conf — activo
add_header X-Request-ID $request_id always;
proxy_set_header X-Request-ID $request_id;
log_format main '$remote_addr - $request_id - $request - $status';
```

---

### ✅ ~~Ventana de inconsistencia en caché de consentimientos (Redis)~~ — RESUELTO

**Fix aplicado:** Doble mecanismo de consistencia implementado.

1. **TTL duro de 300 segundos** en todas las keys `consent:{subjectId}:{purposeId}` del Orquestador. El peor caso de desincronización queda acotado a 5 minutos, documentable ante el CPDT como "latencia técnica de propagación".

2. **`AgreementRevocationCacheListener`** (`AFTER_COMMIT`): al revocar vía `PATCH /api/agreements/{id}/revoke` directamente en el backend, se publica `AgreementRevokedEvent` y el listener elimina las keys de Redis **solo después de que Postgres haga commit**. Si Postgres hace rollback, el evento no se despacha — Redis nunca queda desincronizado.

3. **Orquestador escribe REVOKED activamente**: cuando la revocación llega vía `POST /consent/revoke` del Orquestador, este escribe el estado `REVOKED` en Redis inmediatamente tras confirmar la persistencia en Postgres.

**Archivos creados:**
- `agreement/domain/event/AgreementRevokedEvent.java` — Spring event record con `subjectIdentifier` + `List<UUID> purposeIds`
- `agreement/application/service/AgreementRevocationCacheListener.java` — `@TransactionalEventListener(phase = AFTER_COMMIT)`

Ver explicación completa en `agreement/domain/event/README.md`.

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

### ✅ ~~Falta endpoint de consulta de consentimiento para sistemas externos (Propagación B2B)~~ — RESUELTO

**Fix aplicado:** Módulo **Orquestador** implementado como servicio independiente (puerto 8081).

**Endpoints disponibles:**
```
GET  /consent/check?subjectId={id}&purposeId={uuid}  → { status: ALLOWED|REVOKED|DENIED|PENDING, legalBasisCode, validUntil }
POST /consent/capture                                  → captura acuerdo + pre-warms Redis
POST /consent/revoke                                   → revoca acuerdo + actualiza Redis
```

**Flujo de seguridad:**
- **Inbound:** JWT del IdP externo validado contra JWKS en `EXTERNAL_JWKS_URI`
- **Outbound:** M2M `client_credentials` hacia Keycloak realm `leydata` — el backend nunca recibe el token del cliente externo

**Patrón findOrCreate para titulares:** el campo `subjectId` del Orquestador es un string opaco (RUT, UUID externo, etc.). Al capturar un consentimiento el backend hace `findOrCreate` en `data_subjects` por `identifier`. Los sistemas externos nunca necesitan conocer el UUID interno.

Ver guía completa: [`orchestrator/INTEGRATION.md`](../../orchestrator/INTEGRATION.md)

---

### ✅ ~~`POST /consent/capture` exigía que el CRM ya conociera `templateId`/`documentId` internos~~ — RESUELTO

**Problema original:** el diseño previsto era que el sistema cliente solo necesitara conocer un identificador de negocio del template (ej. `ONBOARDING_CLIENTE`); en cambio, `CaptureConsentRequest` exigía `templateId` (UUID interno) y `documentId` directamente — el Orquestador actuaba como simple "pasamanos" en vez de resolver esos IDs por su cuenta. Identificado y corregido en la rama `feature/orchestrator-module`, junto con el aislamiento por dominio de `Templates` (ver sección de Templates en [`docs/templates-module.md`](../../docs/templates-module.md)).

**Fix aplicado:**
- `Templates.domainId` (FK a `Domains`, con unicidad compuesta `domain_id + template_key + version`) — un mismo `templateKey` de negocio ya no colisiona entre dominios distintos.
- Nuevo endpoint interno `GET /api/templates/resolve?domainId=&templateKey=` (`TemplateController`, `@PreAuthorize("isAuthenticated()")` — no requiere rol DPO/ADMIN, lo llama el Orquestador con su propia identidad de servicio M2M).
- `PrivacyDocumentService.publish()` ahora archiva automáticamente cualquier documento `PUBLISHED` previo con el mismo `templateId` — garantiza que "el documento vigente de un template" sea una búsqueda sin ambigüedad (`PrivacyDocumentsRepository.findByTemplateIdAndStatusAndIsActiveTrue`).
- `CaptureConsentRequest` del Orquestador cambió `templateId` (UUID) por `templateKey` (String, identificador de negocio); `documentId` pasó a opcional.
- `ConsentController.extractDomainId()` lee el claim `leydata_domain` del JWT del sistema cliente (configurado por protocol mapper en su IdP, por client) y lo pasa explícito a `ConsentService.capture()` — **el backend nunca ve este claim**, porque el Orquestador llama al backend con su propia identidad de servicio (`client_registration_id: leydata-system`), no con el JWT del cliente. La resolución de dominio ocurre íntegramente dentro del Orquestador.
- `AgreementService.create()` resuelve `documentId` automáticamente cuando no viene en el request, vía el mismo método de `PrivacyDocumentsRepository`.

**Decisión de diseño descartada:** se evaluó agregar `documentId` como FK directa en `Templates` (Template → Document), pero `PrivacyDocuments.templateId` ya existía en sentido inverso (Document → Template) desde antes de esta iteración — agregar la regla de unicidad sobre el campo existente fue un cambio más chico y evitó una relación bidireccional redundante.

Ver flujo actualizado: [`orchestrator/INTEGRATION.md`](../../orchestrator/INTEGRATION.md), [`docs/orchestrator-module.md`](../../docs/orchestrator-module.md), [`docs/agreements-module.md`](../../docs/agreements-module.md).

---

### ✅ ~~Falta el ciclo de captura de consentimiento~~ — RESUELTO

**Fix aplicado:** Módulo `agreement/` implementado con ciclo completo.

**Endpoints disponibles:**
- `POST /api/agreements` — registra decisión del titular por cada purpose; reconsent automático si ya había ACTIVE para el mismo `(dataSubjectId, templateId)`
- `GET /api/agreements/active?dataSubjectId=&templateId=` — consulta para el orquestador (200 si existe, 404 si no)
- `GET /api/agreements` — listado con filtros opcionales
- `GET /api/agreements/{id}` — detalle completo con purposes

**Verificación de integridad** — centralizada en el módulo `audit/` (branch feature/trazabilidad):
- `POST /api/audit/integrity/verify` — verificación SHA-256 bajo demanda para AGREEMENT, TEMPLATE, DOCUMENT o PURPOSE
- `GET  /api/audit/integrity/log?entityType=&entityId=` — historial de verificaciones
- `GET  /api/audit/integrity/failed?entityType=` — verificaciones fallidas para auditoría
- `GET  /api/audit/trace/agreement/{id}` — traza completa: template + documento + purposes con drift check

**Integridad:** cada agreement calcula un SHA-256 encadenado al agreement anterior (ledger análogo al de `system_audit_log`).

**Módulos creados:** `agreement/web/`, `agreement/application/service/`, `agreement/application/dto/`, `agreement/infrastructure/persistence/`, `agreement/domain/exception/`.

---

### ✅ ~~Falta el ciclo de revocación de consentimiento~~ — RESUELTO

**Fix aplicado:** Endpoint `PATCH /api/agreements/{id}/revoke` implementado con invalidación activa de Redis tras commit de Postgres.

**Archivos modificados/creados:**
- `agreement/web/AgreementController.java` — endpoint `PATCH /{id}/revoke`, resuelve IP real desde header `X-Internal-Real-IP` (inyectado por el Orquestador; WAF/NGINX debe stripear el original)
- `agreement/application/service/AgreementService.java` — método `revoke()`: valida que el agreement sea ACTIVE y pertenezca al `subjectId` del request; marca agreement y purposes como REVOKED; registra en audit log con IP; publica `AgreementRevokedEvent`
- `agreement/domain/event/AgreementRevokedEvent.java` *(nuevo)* — record con `subjectIdentifier` y `List<UUID> purposeIds`
- `agreement/application/service/AgreementRevocationCacheListener.java` *(nuevo)* — `@TransactionalEventListener(phase = AFTER_COMMIT)`: borra las keys `consent:{subjectId}:{purposeId}` de Redis **solo después de que Postgres haga commit**. Si Redis falla, la revocación legal ya está persistida — se loguea WARN sin rollback.

**Por qué `AFTER_COMMIT` y no dentro de `@Transactional`:**
Si se borra Redis dentro de la transacción y luego Postgres hace rollback, Redis queda sin la key pero Postgres dice ACTIVE — inconsistencia silenciosa. Con `AFTER_COMMIT`, el evento no se despacha si hay rollback.

---

## 4. Infraestructura proyectada — componentes faltantes

```
Estado actual del diseño vs. lo que se necesita:

[Internet]
    │
  [WAF]                              ❌ pendiente (producción)
    │
[API Gateway / NGINX]                ✅ implementado (rate limiting, security headers, X-Request-ID — puerto :80)
    │
[Load Balancer]                      ✅ planificado
   /|\
[App][App][App]                      ✅ planificado (monolito stateless, escala horizontal)
    │
[PgBouncer :5435]                    ✅ implementado en docker-compose
    │
[PostgreSQL Primary :5433] → [Replica :5434]   ✅ streaming replication implementada
                                     ✅ routing de reads a replica (AbstractRoutingDataSource — Fase 2b)
                                     ❌ failover automático (Patroni) pendiente (Fase 3)
    │
  [Redis Master]                     ✅ implementado
    │
  [Redis Replica / Sentinel]         ❌ replica NO planificada

Componentes de observabilidad:
  ✅ Logs estructurados JSON (Logback + LogstashEncoder) — backend y orquestador
  ✅ Métricas de aplicación (Micrometer → Prometheus → Grafana) — dashboard pre-configurado
  ✅ Trazas distribuidas (Zipkin/Brave) — correlaciona Orquestador → Backend → BD
  ✅ Health indicator del audit log (`/actuator/health` componente `auditChain`)
  ✅ Métricas de dominio: consentimientos capturados/revocados, integridad del audit log, latencia `/consent/check`
  ✅ Centralización de logs con Loki — loki4j appender en backend y orquestador, servicio Loki en docker-compose, datasource en Grafana
  ✅ Alertas automáticas en Grafana (audit integrity mismatch + backend DOWN) — webhook y email configurables vía .env
  ✅ NGINX access log en Docker (formato estándar) — integración con Loki pendiente (no hay loki4j en NGINX; requiere Promtail o similar)
```

---

## 5. Bugs de API y lógica — corregidos

### 5.1 Bugs de protocolo HTTP — corregidos iteración anterior

| # | Endpoint | Problema | Fix | Estado |
|---|----------|----------|-----|--------|
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

### 5.2 Bugs de lógica y configuración — corregidos Julio 2026

Identificados y corregidos en `feature/orchestrator-module`. Verificados contra el backend en ejecución.

| # | Área | Síntoma | Causa raíz | Archivo corregido | Estado |
|---|------|---------|-----------|-------------------|--------|
| 1 | Keycloak admin | `POST /api/users` → 500 genérico cuando `KC_BACKEND_SECRET` es inválido | `getAdminToken()` sin try/catch — 401 de Keycloak propagaba como `HttpClientErrorException` sin contexto | `KeycloakAdminService.java` | ✅ Corregido |
| 2 | Finalidades | `POST /api/purposes` → `legalBasisCode`, `domainCode`, `dataCategoryCode` null en respuesta | JPA L1 cache: `save()` retorna entidad del cache con relaciones `@ManyToOne` lazy no inicializadas; `findById()` devuelve el mismo objeto cacheado sin ir a BD | `PurposeService.java` | ✅ Corregido |
| 3 | Data-categories | `POST /api/purposes/{id}/data-categories` → `dataCategoryCode`, `isSensitive` null | Mismo patrón JPA L1 cache — `loadDataCategoryActive()` cargaba la entidad pero no se seteaba en el objeto guardado | `PurposeDataCategoryService.java` | ✅ Corregido |
| 4 | Documentos de privacidad | `PATCH /api/privacy-documents/{id}/archive` → 409 en documentos PUBLISHED | Catch-22: `publish()` requiere finalidades activas, `archive()` bloqueaba si había finalidades activas, `removePurpose()` solo funciona en DRAFT → estado inalcanzable | `PrivacyDocumentService.java` | ✅ Corregido |
| 5 | Orquestador M2M | Orquestador no podía obtener token para llamar al backend | `KC_TOKEN_URI` apuntaba a `localhost:8180` (inaccesible desde Docker); cliente `leydata-orchestrator` no existía en Keycloak | `docker-compose.yml`, `setup-keycloak.sh/.ps1` | ✅ Corregido |

**Patrón JPA a evitar (causa de bugs 2 y 3):**
```java
// ❌ INCORRECTO — findById() devuelve objeto del L1 cache, relaciones lazy siguen null
Entity saved = repo.save(entity);
Entity reloaded = repo.findById(saved.getId()).orElseThrow(); // L1 cache hit, no SQL

// ✅ CORRECTO — guardar la referencia cargada en validación y setearla manualmente
var relacion = relacionRepo.findById(req.getRelacionId()).orElseThrow(...);
Entity saved = repo.save(entity);
saved.setRelacion(relacion); // poblar en memoria con objeto ya cargado
```

Ver documentación de este patrón: `DEVELOPMENT_GUARDRAILS.md` §9.

---

## 6. Deuda técnica menor

| Item | Descripción | Archivo |
|------|-------------|---------|
| ~~Orden de handlers en `GlobalExceptionHandler`~~ | ✅ Resuelto — `RuntimeException` movido antes de `Exception`, código muerto eliminado, Javadocs verbosos removidos. | `GlobalExceptionHandler.java` |
| ~~`PATCH /api/purpose-requests/{id}/review` con ID inexistente~~ | ✅ Resuelto — cambiado `IllegalArgumentException` por `NoSuchElementException` (handler 404 existente). | `PurposeRequestService.java` |
| ~~`TemplateNotFoundException` sin handler en `GlobalExceptionHandler`~~ | ✅ Resuelto — `GET /api/templates/{id}` con ID inexistente devolvía 500, ahora 404. | `GlobalExceptionHandler.java` |
| ~~Usuarios de prueba JEFE_DOMINIO y DPO no existían en el script~~ | ✅ Resuelto — `setup-keycloak.sh` ahora crea `jefe@test.cl` (JEFE_DOMINIO) y `dpo@leydata.cl` (DPO). | `scripts/setup-keycloak.sh` |
| **JPA L1 cache con relaciones lazy** | Patrón documentado en `DEVELOPMENT_GUARDRAILS.md` §9 — cualquier método `@Transactional` que haga `save()` y necesite retornar relaciones `@ManyToOne` debe guardar las referencias cargadas durante validación y setearlas manualmente tras el save. `findById()` post-save devuelve el objeto del L1 cache sin inicializar las relaciones. | `PurposeService.java`, `PurposeDataCategoryService.java` (ejemplo del patrón correcto) |
| `AuditService` es un god node (26 edges en el grafo) | Considerar separar en `AuditWriter` (persistencia) y `AuditHashChain` (integridad) para facilitar testing unitario y futura migración a un servicio separado. | `AuditService.java` |
| `ddl-auto=update` en producción | Hibernate gestiona el esquema automáticamente — válido para desarrollo, peligroso en producción (no hay rollback, no hay historial, riesgo con múltiples instancias). Antes del go-live migrar a **Flyway**: (1) exportar esquema actual como `V1__baseline.sql`, (2) cambiar a `ddl-auto=validate`, (3) todo cambio futuro en scripts `V2__...sql`. | `application.properties` |
| `DomainsPage.tsx` permite asignar un jefe de dominio que ya lidera otro dominio sin advertirlo | El backend (`UserService.assignUserDomains()`/`createUser()`) ahora rechaza con 400 si a un usuario `JEFE_DOMINIO` se le intenta asignar más de un dominio (regla: un jefe de dominio = un dominio). El flujo "Asignar responsable" de `DomainsPage.tsx` (función `handleAssignSave`, ~línea 140-181) sigue armando el payload `domainIds` por **agregación** (`[...currentDomainIds, assignDomain.id]`) en vez de **reemplazo** — si un admin intenta asignar a un jefe que ya administra otro dominio, el request falla con un 400 poco claro en vez de mostrar la advertencia adecuada o reemplazar la asignación. Pendiente: (1) cambiar la llamada a `updateUser(newJefeId, { domainIds: [assignDomain.id] }, ...)` para que reemplace en vez de agregar, (2) en el `<select>` de "Responsable asignado" (~línea 394-407), marcar visualmente a los jefes que ya administran otro dominio para que el admin entienda que reasignarlos los mueve de dominio. | `frontend/src/pages/DomainsPage.tsx` |

---

## Checklist pre-producción (diciembre 2026)

### Código
- [x] Resolver race condition en `AuditService` (hash chain bajo concurrencia)
- [x] Agregar `X-Request-ID` al audit log y a la entidad `SystemAuditLog`
- [x] Mover `UserStatusFilter` a consultar Redis en vez de Postgres (cache-aside implementado)
- [x] Implementar ciclo de captura de consentimiento (`agreement/` — `POST /api/agreements`, ledger SHA-256, reconsent automático)
- [x] Implementar ciclo de revocación de consentimiento (`PATCH /api/agreements/{id}/revoke` — `AgreementService.revoke()`, audit log, IP real desde Orquestador)
- [x] Crear endpoint de consulta de consentimiento B2B (`GET /consent/check` en el Orquestador, con caché Redis y JWT externo)
- [x] Implementar `AgreementRevokedEvent` para invalidación activa de Redis (`AgreementRevocationCacheListener` con `@TransactionalEventListener(phase = AFTER_COMMIT)`)
- [x] Migraciones Flyway V5–V7 agregadas (feature/trazabilidad): trigger de inmutabilidad para `entity_integrity_log`, backfill de versionado de purposes, drop de UNIQUE en `purposes.code` — **aplicar manualmente** con psql (ver `docs/audit-module.md`)
- [x] Migrar gestión de esquema completo de `ddl-auto=update` a Flyway (`ddl-auto=validate` + scripts `V1__baseline.sql`, `V2__...`)
- [ ] Correr corrida completa de pruebas de endpoints post todos los fixes

### Infraestructura
- [x] Configurar PostgreSQL Streaming Replica (`db-replica` en docker-compose, `docker/postgres-primary/` y `docker/postgres-replica/`)
- [x] Agregar PgBouncer al Docker Compose (puerto 5435, pool → primary)
- [x] Enrutar lecturas Spring Boot a la replica (`AbstractRoutingDataSource`) — Fase 2b
- [ ] Failover automático con Patroni — Fase 3
- [x] Agregar Redis al Docker Compose (redis:7-alpine, puerto 6379)
- [x] Crear cliente M2M `leydata-orchestrator` en Keycloak — ahora automatizado por `setup-keycloak.sh/.ps1` (Julio 2026)
- [ ] Configurar Redis Sentinel o Cluster (para producción)
- [ ] Implementar WAF real con inspección de payload (producción)
- [x] Configurar NGINX con `X-Request-ID` generado y propagado al backend — implementado en `nginx/conf.d/leydata.conf`, puerto :80
- [ ] Definir TTL de keys de consentimiento en Redis (recomendado: 30–60s)

### Observabilidad (para trazabilidad exigida por Ley 21.719)
- [x] Logs estructurados JSON en backend y orquestador (Logback + LogstashEncoder, rotación automática) — ver [`docs/monitoring-module.md`](../../docs/monitoring-module.md)
- [x] Campo `requestId` como campo de correlación en todos los logs (`RequestIdFilter` — propaga `X-Request-ID` al MDC)
- [x] Dashboard de métricas en Grafana: latencia de `/consent/check`, consentimientos capturados/revocados, tasa de error 5xx, estado UP/DOWN
- [x] Métricas de integridad: panel Grafana con `audit_integrity_check_total{result="mismatch"}` — debe ser 0 siempre
- [x] Trazas distribuidas con Zipkin: correlaciona Orquestador → Backend → BD por `traceId`
- [x] Health indicator `auditChain` en `/actuator/health`
- [x] Centralización de logs con Loki — loki4j en backend y orquestador, `grafana/loki:2.9.9` en docker-compose, datasource Grafana provisionado
- [x] Alertas automáticas en Grafana: ruptura SHA-256 en audit log + backend DOWN — webhook y email configurables vía `.env` (ver [`docs/monitoring-module.md`](../../docs/monitoring-module.md) §4)
- [ ] Integración de logs NGINX → Loki (NGINX no usa loki4j; requiere Promtail o fluent-bit como sidecar — pendiente para producción)
- [ ] Documentar el SLA de propagación de revocación (TTL de Redis) para incluir en el aviso de privacidad

### Legal / Compliance
- [ ] Validar con asesor legal que el TTL de 30-60s en caché de consentimientos es aceptable bajo Ley 21.719 art. de revocación
- [ ] Documentar el flujo de respuesta a solicitudes de titulares (acceso, rectificación, supresión)
- [ ] Definir política de retención del `system_audit_log` (¿cuántos años se guardan?)
