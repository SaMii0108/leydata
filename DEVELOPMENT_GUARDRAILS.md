# DEVELOPMENT GUARDRAILS — LeyData

> Documento de arnés para desarrolladores y asistentes de IA.
> Última actualización: 2026-07-01

---

## 1. Filosofía del Proyecto

LeyData es un sistema de cumplimiento normativo, no solo una aplicación de negocio.
La **Ley 21.719** (plena vigencia diciembre 2026) exige que cada acción sobre un consentimiento sea trazable, verificable e inmutable ante el CPDT (Consejo para la Protección de los Datos Personales).

Dos principios son **intocables por norma legal**:

| Principio | Qué significa en código |
|---|---|
| **Inmutabilidad del ledger de auditoría** | `system_audit_log` tiene triggers en PostgreSQL que bloquean UPDATE y DELETE. Nunca emitas SQL directo sobre esta tabla. |
| **Cadena de integridad SHA-256** | Cada registro de auditoría firma el hash del registro anterior. Modificar el orden de campos o la lógica de `AuditService#computeHash()` rompe la verificación `GET /api/audit/integrity/*` y constituye una falla de compliance. |

Si una tarea parece requerir tocar alguno de estos dos puntos, **detente y consulta antes de continuar**.

---

## 2. Reglas Absolutas

```
❌ NUNCA hacer UPDATE/DELETE sobre system_audit_log — los triggers lo bloquean en BD
❌ NUNCA cambiar spring.jpa.hibernate.ddl-auto a create o update en staging/prod — solo validate
❌ NUNCA saltar una migración Flyway — el número de versión debe ser secuencial sin huecos
❌ NUNCA exponer objetos entidad (@Entity) directamente en respuestas HTTP — siempre usar DTOs
❌ NUNCA desactivar el filtro RequestIdFilter o el MDC sin propagar requestId en todos los logs
❌ NUNCA hardcodear KC_BACKEND_SECRET — se genera por script y rota cuando el cliente Keycloak se recrea
❌ NUNCA hardcodear KC_ORCHESTRATOR_CLIENT_SECRET — mismo origen y ciclo de vida que KC_BACKEND_SECRET
```

---

## 3. Matriz de Impacto

> "Si tocas A, debes revisar B."

### 3.1 Endpoints del Backend

**Si modificas, agregas o eliminas un endpoint en el Backend:**

| Qué revisar | Por qué |
|---|---|
| `orchestrator/.../ConsentController.java` | El orquestador rutea llamadas al backend — un path o método HTTP cambiado rompe el proxy |
| `backend/config/SecurityConfig.java` | Cada nuevo endpoint necesita una regla de autorización explícita o queda desprotegido |
| `@PreAuthorize` en el controller | Los permisos por rol se declaran en el propio controller con method security |
| Swagger / OpenAPI (`springdoc`) | Los DTOs de Request/Response deben tener `@Schema` si se exponen en la documentación |
| Zipkin | Un nuevo endpoint crea un nuevo span; verifica que el nombre del span sea legible en `http://localhost:9411` |
| `nginx/conf.d/leydata.conf` | Nuevos paths en `/api/*` o `/consent/*` ya están cubiertos por las zonas de rate limiting existentes. Si agregas un path en un prefijo distinto, agregar `location` block explícito — de lo contrario NGINX devuelve 404 |

### 3.2 Entidades JPA y Base de Datos

**Si modificas una entidad `@Entity` o el esquema de base de datos:**

| Qué revisar | Por qué |
|---|---|
| Crear `V{N+1}__descripcion.sql` en `backend/src/main/resources/db/migration/` | **NUNCA usar `ddl-auto=update`**. Flyway gestiona el esquema. Próxima versión: **V8** |
| `AuditService#computeHash()` | Si el campo nuevo forma parte de lo que se audita, debe incluirse en el hash. Cambiar el orden o los campos rompe la cadena de integridad histórica |
| DTOs de Request/Response | Las entidades nunca se exponen directamente — el DTO es el contrato público |
| `CatalogSeeder.java` | Si hay datos de catálogo nuevos, el seeder debe inicializarlos para entornos de desarrollo |
| Tests de integración relevantes | Verificar que los tests con datos persistidos en BD siguen pasando |

> **Convención de migraciones Flyway:**
> - Naming: `V{N}__{descripcion_en_snake_case}.sql`
> - Último script existente: `V7__purposes_code_not_unique.sql` → el próximo es **V8**
> - Un script Flyway es inmutable una vez comiteado — si necesitas corregirlo, crea V{N+1} con el fix

### 3.3 AgreementService y Consentimientos

**Si modificas la lógica de captura, revocación o verificación de acuerdos:**

| Qué revisar | Por qué |
|---|---|
| `AuditService#log()` | Toda operación sobre acuerdos debe generar un log de auditoría en la misma transacción (`Propagation.REQUIRED`) |
| `AgreementIntegrityLog` | Al capturar un acuerdo se genera un hash SHA-256 que se almacena aquí — la lógica de hash no debe cambiar sin migración |
| Redis caché (orquestador) | El estado del consentimiento se cachea en el orquestador — al revocar, la invalidación de caché debe propagarse en el TTL legal correspondiente |
| `ConsentController` (orquestador) | Los DTOs de respuesta `ConsentCheckResponse` y `ConsentStatusResponse` deben estar alineados con lo que devuelve el backend |
| Métricas `consent.captured` / `consent.revoked` | Los contadores de Micrometer deben seguir incrementándose — verifica en `http://localhost:9090` |

### 3.4 Redis y Caché

**Si modificas el TTL, las claves, o la lógica de caché en el orquestador:**

| Qué revisar | Por qué |
|---|---|
| TTL de consentimientos revocados | El TTL define cuánto tarda en propagarse una revocación. Un TTL demasiado largo viola el derecho a revocación inmediata de la Ley 21.719 |
| `ConsentCacheProperties.java` | Centraliza la configuración de caché — no hardcodear TTLs en el código |
| `AgreementRevocationCacheListener` (si existe) | Escucha eventos de revocación para invalidar caché activamente |
| Health checks Redis | `AuditHealthIndicator` y el actuator health reportan el estado de Redis — verifica que sigan en UP después del cambio |
| Monitoreo Redis en Grafana | El dashboard tiene un panel de hit rate — un cambio de claves puede bajar el hit rate a 0 temporalmente |

### 3.5 Sistema de Logging y Observabilidad

**Si agregas, modificas o eliminas logs en cualquier servicio:**

| Qué revisar | Por qué |
|---|---|
| Formato JSON en `logback-spring.xml` | Todos los appenders JSON usan `LogstashEncoder`. Los logs estructurados en otro formato no son parseables por Loki |
| Propagación de `requestId` en MDC | El filtro `RequestIdFilter` inyecta el requestId al MDC. Cualquier log fuera del contexto de un request HTTP no tendrá este campo — es esperado, pero documentarlo |
| Appender LOKI en `logback-spring.xml` | Los logs van a consola + archivo JSON + Loki. Si agregas un appender nuevo, asegúrate de que no introduzca latencia en el hilo principal |
| Nivel de log apropiado | `org.hibernate.SQL` y `org.springframework.security` están en WARN intencionalmente para no saturar Loki con ruido |
| `com.github.loki4j` en ERROR | Está silenciado deliberadamente — si Loki no está corriendo, loki4j falla en silencio y los logs siguen yendo a archivo |

### 3.6 Seguridad y Autenticación

**Si modificas `SecurityConfig`, `KeycloakJwtAuthConverter`, o los roles RBAC:**

| Qué revisar | Por qué |
|---|---|
| `@PreAuthorize` en controllers | Method security complementa las reglas de ruta — ambos deben estar consistentes |
| `application.properties` (spring.security.\*) | El cliente Keycloak, el realm y el issuer URI deben coincidir con el realm configurado |
| `KC_BACKEND_SECRET` en `.env` | Se genera por `setup-keycloak.sh/.ps1` — rota cada vez que se recrea el cliente en Keycloak. No commitear nunca el valor real |
| Roles en Keycloak Admin Console | Los roles que usa `@PreAuthorize` deben existir en el realm de Keycloak |
| Tests de seguridad | Si agregas una regla `hasRole("X")`, agrega un test que verifique que el rol Y no puede acceder |

### 3.7 Docker Compose e Infraestructura

**Si modificas `docker-compose.yml`:**

| Qué revisar | Por qué |
|---|---|
| Red `leydata-consent-network` | Todos los contenedores deben estar en esta red para comunicarse por nombre de servicio |
| `monitoring/prometheus.yml` scrape targets | Si agregas un servicio nuevo que expone métricas, agregar su job en Prometheus |
| Volúmenes nombrados al final del archivo | Declarar cada nuevo volumen en la sección `volumes:` raíz |
| Variables de entorno vs `.env.example` | Cada nueva variable de entorno debe documentarse en `.env.example` con su descripción y valor por defecto |
| `KC_ORCHESTRATOR_CLIENT_SECRET` en `.env` | Docker Compose la lee del `.env` vía `${KC_ORCHESTRATOR_CLIENT_SECRET}` — **no** exportar en la sesión de terminal ni hardcodear en compose |
| `depends_on` | Grafana depende de Prometheus y Loki — si agregas servicios con dependencias de inicio, declararlo |

---

## 4. Reglas Multi-OS y Red (Mac / Windows nativo / WSL2)

### 4.1 El Problema de Red

El stack corre en Docker, pero el **backend corre en el host** (no en Docker) durante desarrollo. Esto crea dos direcciones distintas según la dirección del tráfico:

```
Host → Docker (backend → Loki, backend → Zipkin)
  → Usar: http://localhost:{puerto}
  → Docker Desktop expone puertos al localhost del host en los 3 OS ✅

Docker → Host (Prometheus → /actuator/prometheus del backend)
  → Usar: http://host.docker.internal:8080
  → En Linux/WSL sin Docker Desktop: requiere el mapeo extra_hosts
```

### 4.2 `host.docker.internal` en docker-compose.yml

En `prometheus` (y cualquier contenedor que deba alcanzar el host):

```yaml
prometheus:
  extra_hosts:
    - "host.docker.internal:host-gateway"
```

- **Mac / Windows con Docker Desktop**: `host.docker.internal` se resuelve automáticamente — la entrada `extra_hosts` es inofensiva pero no necesaria
- **WSL2 con Docker Desktop (integración habilitada)**: el `host-gateway` mapea a la IP del host de WSL — **requerido**
- **WSL2 con Docker Engine nativo**: mismo comportamiento que el punto anterior

### 4.3 Variables de Entorno por OS

| Variable | Quién la usa | Valor en Mac/Windows | Valor en WSL2 |
|---|---|---|---|
| `LOKI_URL` | Backend (host → Docker) | `http://localhost:3100` (default) | `http://localhost:3100` (mismo — Docker Desktop expone el puerto) |
| `LOKI_URL` en docker-compose | Orquestador (Docker → Docker) | `http://loki:3100` (hardcodeado en compose) | `http://loki:3100` (mismo) |
| `BACKEND_HOST` | Prometheus (Docker → host) | `host.docker.internal` | `host.docker.internal` (con `host-gateway`) |

> El backend no necesita override de `LOKI_URL` en ningún OS: el default `http://localhost:3100` funciona en los tres entornos porque Docker Desktop expone el puerto a localhost del host, sea macOS, Windows o WSL2.

### 4.4 Volúmenes y Filesystem

**WSL2 tiene dos filesystems distintos:**
- `/mnt/c/...` — filesystem Windows (NTFS montado en WSL) — **rendimiento degradado y problemas de permisos**
- `/home/lucho/...` — filesystem Linux nativo en WSL — **usar siempre este**

Reglas:
- El proyecto debe estar en el filesystem Linux (`~/leydata`), nunca en `/mnt/c/`
- Los volúmenes de Docker Compose que montan directorios locales (logs, config) apuntan a rutas relativas — esto funciona correctamente mientras `docker-compose up` se ejecute desde el filesystem Linux
- `./logs/orchestrator:/app/logs` — funciona en los tres OS porque el path relativo se resuelve en el contexto del `docker-compose.yml`

### 4.5 Por qué loki4j en vez de Promtail

Promtail (el agente de Loki tradicional) necesita montar los archivos de log del host como volúmenes en el contenedor. En WSL2, montar paths del host Linux en Docker Desktop requiere rutas absolutas del tipo `/home/lucho/leydata/logs` que varían por usuario y OS.

**loki4j** resuelve esto enviando logs por HTTP push directamente desde la JVM al puerto `3100` expuesto por Docker. Sin volúmenes, sin paths absolutos, cross-platform por diseño.

---

## 5. Matriz RBAC

Roles disponibles en Keycloak: `ADMIN`, `DPO`, `JEFE_DOMINIO`, `TITULAR`, `USER`

| Endpoint | ADMIN | DPO | JEFE_DOMINIO | TITULAR | USER |
|---|:---:|:---:|:---:|:---:|:---:|
| `GET/POST /api/users/**` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET/POST /api/domains/**` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST /api/purpose-requests` | ✅ | ❌ | ✅ | ❌ | ❌ |
| `GET /api/purpose-requests/my` | ✅ | ❌ | ✅ | ❌ | ❌ |
| `GET /api/purpose-requests/**` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `PATCH /api/purpose-requests/*/review` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/audit/**` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET /api/data-categories/**` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `POST/PUT/DELETE /api/data-categories/**` | ✅ | ✅ | ❌ | ❌ | ❌ |
| `GET /api/legal-basis/**` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `GET /actuator/health` | público | público | público | público | público |
| `GET /actuator/prometheus` | ✅ | ❌ | ❌ | ❌ | ❌ |
| Swagger UI / OpenAPI | público | público | público | público | público |

> Los roles se inyectan desde Keycloak via JWT. `KeycloakJwtAuthConverter` mapea el claim `realm_access.roles` al `GrantedAuthority` con prefijo `ROLE_`. Si agregas un rol nuevo, debe existir en el realm Keycloak **y** en el `@PreAuthorize` del controller.

---

## 6. Stack de Observabilidad — Guía Rápida

| Servicio | URL local | Para qué |
|---|---|---|
| **NGINX** | `http://localhost:80` | Entrada pública — rate limiting, security headers, routing |
| Grafana | `http://localhost:3000` | Dashboards, alertas activas |
| Prometheus | `http://localhost:9090` | Métricas en crudo, targets UP/DOWN |
| Loki (vía Grafana) | Grafana → Explore → Loki | Logs centralizados con LogQL |
| Zipkin | `http://localhost:9411` | Trazas distribuidas backend↔orquestador |
| Backend (directo, dev) | `http://localhost:8080` | Debug directo sin pasar por NGINX |
| Orquestador (directo, dev) | `http://localhost:8081` | Debug directo sin pasar por NGINX |
| Actuator backend | `http://localhost:8080/actuator/health` | Estado de BD, Redis, cadena de auditoría |
| Actuator orquestador | `http://localhost:8081/actuator/health` | Estado del proxy WebFlux |

### Levantar el stack completo:
```bash
docker-compose up -d
```

### Levantar solo observabilidad (sin DB ni app):
```bash
docker-compose up -d prometheus grafana loki zipkin
```

### Métricas de compliance clave en Prometheus:
```promql
# Verificaciones de integridad fallidas (debe ser siempre 0)
increase(audit_integrity_check_total{result="mismatch"}[5m])

# Consentimientos capturados en la última hora
increase(consent_captured_total[1h])

# Hit rate de caché Redis del orquestador
rate(consent_check_total{cache="hit"}[5m]) / rate(consent_check_total[5m])
```

---

## 7. Deuda Técnica Conocida

> Estas son limitaciones conocidas y **aceptadas temporalmente**. No refactorices estas áreas sin coordinación.

| Item | Estado | Riesgo si se toca sin coordinación |
|---|---|---|
| **`AuditService` god node** | ❌ Pendiente | Mezcla escritura del ledger + verificación de integridad. Refactorizar requiere pruebas exhaustivas de la cadena hash — un bug silencioso puede pasar compliance |
| **Flyway `ddl-auto=update`** | ❌ Pendiente (bloquea producción) | En desarrollo usa `update` — en staging/prod DEBE ser `validate`. No mezclar; crea V8 para cualquier cambio de esquema |
| **Redis sin Sentinel (SPOF)** | ❌ Pendiente | Un único nodo Redis — si cae, el orquestador pierde caché de consentimientos. Las llamadas caen al backend pero con latencia elevada |
| **WAF/NGINX** | ✅ Implementado | NGINX en `:80` con rate limiting y security headers. Ver sección 3.7 y `nginx/` |
| **Patroni (PostgreSQL HA)** | ❌ Pendiente | Solo hay Primary. La réplica de lectura no tiene failover automático |

### Bugs corregidos — Julio 2026

Los siguientes bugs fueron identificados y corregidos. Se documentan aquí para referencia histórica y como ejemplos de patrones a evitar:

| Bug | Causa raíz | Archivo corregido |
|---|---|---|
| **Bug 1 — KC_BACKEND_SECRET** | `getAdminToken()` sin manejo de error → 401 de Keycloak propagaba como 500 genérico | `KeycloakAdminService.java` |
| **Bug 2 — legalBasisCode null** | JPA L1 cache: `save()` retorna entidad del L1 cache con relaciones lazy no inicializadas; `findById()` devuelve el mismo objeto cacheado | `PurposeService.java` |
| **Bug 3 — dataCategoryCode null** | Mismo patrón JPA L1 cache en `link()` de categorías de datos | `PurposeDataCategoryService.java` |
| **Bug 4 — archivado imposible** | `archive()` bloqueaba si había finalidades activas; `publish()` las requiere; `removePurpose()` solo funciona en DRAFT → estado inalcanzable | `PrivacyDocumentService.java` |
| **Bug 5 — Orquestador M2M** | `KC_TOKEN_URI` apuntaba a `localhost:8180` (inaccesible desde Docker); cliente `leydata-orchestrator` no existía en Keycloak | `docker-compose.yml`, `setup-keycloak.sh/.ps1` |

---

## 8. Rotación de `KC_BACKEND_SECRET`

El secret del cliente `leydata-backend` en Keycloak **es generado automáticamente** por Keycloak y cambia cada vez que el cliente se recrea o se rota manualmente. No hay un valor fijo — cada entorno tiene el suyo.

### Cuándo rota

| Evento | Rota el secret |
|---|---|
| `docker-compose down -v` + `up` (borra volumen de Keycloak) | ✅ Sí |
| Recrear el cliente `leydata-backend` en Keycloak Admin UI | ✅ Sí |
| Clic en "Regenerar secret" en Keycloak Admin UI | ✅ Sí |
| `docker-compose restart` (sin borrar volumen) | ❌ No — el secret persiste |
| Cambiar configuración del realm sin tocar el cliente | ❌ No |

### Procedimiento de rotación

**Mac / WSL (bash):**
```bash
# 1. Asegurarse de que Keycloak esté corriendo
docker-compose up -d keycloak

# 2. Ejecutar el script (idempotente — no recrea lo que ya existe)
bash scripts/setup-keycloak.sh

# 3. Copiar el valor impreso al final:
#    KC_BACKEND_SECRET=<valor>
#    → pegarlo en .env

# 4. Reiniciar el backend con el nuevo secret
DB_USER=admin DB_PASS=admin DB_NAME=leydata_db \
KC_BACKEND_SECRET=<valor_copiado> \
./mvnw spring-boot:run
```

**Windows nativo (PowerShell):**
```powershell
# 1. Asegurarse de que Keycloak esté corriendo
docker-compose up -d keycloak

# 2. Ejecutar el script
.\scripts\setup-keycloak.ps1

# 3. Copiar el valor KC_BACKEND_SECRET impreso en amarillo al final
#    → pegarlo en .env

# 4. Reiniciar el backend
$env:DB_USER='admin'
$env:DB_PASS='admin'
$env:DB_NAME='leydata_db'
$env:KC_BACKEND_SECRET='<valor_copiado>'
.\mvnw.cmd spring-boot:run
```

### Qué hace el script internamente

1. Crea el realm `leydata` (si no existe)
2. Crea los 5 roles: `ADMIN`, `DPO`, `JEFE_DOMINIO`, `USER`, `TITULAR`
3. Crea el cliente `leydata-frontend` (público, para el frontend)
4. Crea el cliente `leydata-backend` (confidencial, service account)
5. Asigna permisos `manage-users` + `view-realm` al service account del backend (necesario para que el backend pueda crear/bloquear usuarios en Keycloak)
6. Crea `admin@leydata.cl` (Admin1234!), `dpo@leydata.cl` (Test1234!), `jefe@test.cl` (Test1234!)
7. Crea el cliente `leydata-orchestrator` (confidencial, `client_credentials`) — es idempotente
8. Imprime `KC_BACKEND_SECRET` **y** `KC_ORCHESTRATOR_CLIENT_SECRET` — **ambos deben copiarse al `.env`**

> El script es **idempotente**: si el realm y los recursos ya existen, los omite y solo imprime el secret actual. Podés correrlo en cualquier momento para recuperar el secret sin recrear nada.

### Reglas

```
❌ NUNCA commitear KC_BACKEND_SECRET ni KC_ORCHESTRATOR_CLIENT_SECRET en git
❌ NUNCA hardcodear ninguno de los dos en application.properties o docker-compose.yml
✅ Ambos van en .env (que está en .gitignore) — Docker Compose los lee automáticamente
✅ Si el backend devuelve 401 con token válido de Keycloak → primer sospechoso: secret desactualizado
✅ Si el Orquestador devuelve 500 al llamar al backend → verificar KC_ORCHESTRATOR_CLIENT_SECRET en .env
```


---

## 9. Patrones JPA — Footguns Conocidos

### L1 Cache y relaciones lazy en entidades recién creadas

**El problema:** Después de `entityManager.persist()` / `repository.save()`, Hibernate registra la entidad en el L1 cache de la sesión. Las llamadas subsecuentes a `repository.findById()` **no van a la base de datos** — devuelven el objeto del L1 cache, que NO tiene inicializadas las relaciones `@ManyToOne` lazy (esas relaciones solo llevan el FK, no el objeto completo). El resultado es que campos como `entity.getRelacion().getCodigo()` devuelven `null`.

**`entityManager.refresh()` tampoco funciona** si la transacción no hizo flush todavía — lanza `EntityNotFoundException` porque busca la fila en la BD antes de que el INSERT se haya escrito.

**La solución correcta:**

```java
// ✅ CORRECTO — guardar el objeto ya cargado durante la validación y setearlo manualmente
var legalBasis = legalBasisRepo.findById(req.getLegalBasisId())
        .orElseThrow(() -> new BusinessValidationException("..."));

// ... lógica de creación ...
Entity saved = repo.save(entity);

// Poblar relaciones lazy con los objetos ya cargados — save() solo persiste FK IDs
saved.setLegalBasis(legalBasis);
```

```java
// ❌ INCORRECTO — findById() devuelve el objeto del L1 cache, relaciones siguen null
Entity saved = repo.save(entity);
Entity reloaded = repo.findById(saved.getId()).orElseThrow(); // ← L1 cache hit, no SQL
reloaded.getLegalBasis(); // null — nunca fue inicializada
```

**Cuándo aplica:** cualquier método `@Transactional` que haga `save()` y luego necesite retornar una respuesta que incluya campos de relaciones `@ManyToOne`. Ver `PurposeService.createPurpose()` y `PurposeDataCategoryService.link()` como ejemplos del patrón correcto.

---

## 10. Flujo de Diagnóstico Rápido

```
1. ¿Hay un error 5xx?
   → Grafana → panel "HTTP error rate" → identificar endpoint
   → Zipkin: buscar el traceId del log del error
   → Loki: {app="leydata-backend"} |= "<requestId>"

2. ¿Falló una verificación de integridad de auditoría?
   → GET /api/audit/integrity/{agreementId}
   → Si devuelve mismatch: PARAR — posible alteración del ledger
   → Escalar al DPO y revisar los triggers de PostgreSQL en system_audit_log

3. ¿El backend está DOWN en Prometheus?
   → Verificar que el backend esté corriendo: curl http://localhost:8080/actuator/health
   → Verificar target en Prometheus: http://localhost:9090/targets
   → Verificar que host.docker.internal resuelve desde el contenedor de Prometheus

4. ¿Los logs no aparecen en Loki?
   → Verificar que Loki está UP: curl http://localhost:3100/ready
   → Si está DOWN: los logs siguen yendo a archivo — no hay pérdida de datos
   → loki4j fallará en silencio (nivel ERROR configurado) — revisar logs de la JVM

5. ¿Alerta de Grafana disparada?
   → Grafana → Alerting → Alert rules
   → Ver anotación en el dashboard para el timestamp exacto
   → Correlacionar con Zipkin usando el timestamp del incidente
```
