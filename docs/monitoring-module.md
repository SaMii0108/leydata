# Módulo: Observabilidad (`monitoring/`)

**Stack:** Prometheus · Grafana · Zipkin · Micrometer · Logback  
**Fecha:** 2026-07-01  
**Estado:** ✅ Implementado — Fases 1, 2 y 3

---

## Para qué sirve

Cuando el sistema falla o se comporta mal en producción, hay cuatro preguntas que siempre aparecen:

| Pregunta | Herramienta que la responde |
|---|---|
| ¿Qué pasó exactamente en ese request? | **Logs (archivos locales)** |
| ¿Puedo explorar logs desde Grafana sin tocar la consola? | **Loki** |
| ¿Cuántas veces pasó? ¿El sistema está UP? ¿Está lento? | **Prometheus + Grafana** |
| ¿En qué paso del flujo se fue el tiempo o falló? | **Zipkin** |
| ¿Cómo me entero antes de que el usuario lo reporte? | **Grafana Alerting** |

Sin esto, diagnosticar un problema en producción es adivinar. Con esto, se puede ir de un gráfico en Grafana → al trace en Zipkin → a la línea exacta en el log, en menos de 2 minutos.

Para LeyData en particular, la Ley 21.719 exige trazabilidad de cada acción sobre consentimientos. Los logs estructurados y las métricas de auditoría son parte de la evidencia que se presenta al CPDT (Consejo para la Protección de Datos Personales) ante una fiscalización.

---

## Arquitectura

```
CRM / Sistema externo
        │  JWT externo
        ▼
 [ NGINX :80 ]  ←── rate limiting, security headers, routing
        │
        ├── /consent/* ──────────────────────────────┐
        │                                             ▼
        │                              [Orquestador :8081] ── spans ──► [Zipkin :9411]
        │                                  │   │                              ▲
        │                                  │   └── loki4j push ──────┐        │ spans
        │                                  │  M2M token              │        │
        │                                  ▼                         ▼        │
        └── /api/* ──────────► [Backend :8080]  ──── loki4j ► [Loki :3100] ◄─┘
                                       │                          │
                                       ├── /actuator/prometheus   │         [Grafana :3000]
                                       │   ◄── scrape 15s ── [Prometheus]  ──────► query
                                       │                                      │
                                       │                           [Alerting engine]
                                       │                            ├── Webhook
                        [Docker: Orquestador]                       └── Email (SMTP)
                               └─── /app/logs/ ← montado en logs/orchestrator/
```

---

## 1. Logs — qué pasó

### Qué son

Cada vez que el sistema hace algo relevante (guardar un consentimiento, rechazar un login, detectar un error), escribe una línea en un archivo JSON. Sirven para reconstruir exactamente qué ocurrió en un momento dado.

### Dónde se guardan

Los logs se guardan en dos lugares distintos porque los dos servicios corren en entornos distintos:

**Backend** — corre directamente en tu máquina con `./mvnw spring-boot:run`. Los logs se crean en el filesystem del host:

```
leydata/
└── backend/
    └── logs/
        ├── leydata-backend.log              ← archivo activo (se escribe aquí)
        ├── leydata-backend.2026-07-01.0.log.gz   ← comprimido automáticamente
        └── leydata-backend.2026-06-30.0.log.gz
```

**Orquestador** — corre dentro de Docker. El contenedor escribe en `/app/logs/`, que está montado como volumen hacia el host:

```
leydata/
└── logs/
    └── orchestrator/
        ├── leydata-orchestrator.log              ← archivo activo
        └── leydata-orchestrator.2026-07-01.0.log.gz
```

Ninguno de estos directorios está en git (ambos están en `.gitignore`).

### Formato de cada línea

Cada línea es un JSON independiente:

```json
{
  "@timestamp": "2026-07-01T10:23:11.847Z",
  "level": "INFO",
  "message": "Consentimiento capturado para titular abc123",
  "logger_name": "com.leydata.backend.agreement.application.service.AgreementService",
  "requestId": "a3f1c2d4-7891-4b5c-a6d2-e3f4a5b6c7d8",
  "app": "leydata-backend"
}
```

El campo `requestId` es el mismo valor que aparece en Zipkin como `traceId`, lo que permite cruzar las tres herramientas sobre un mismo request.

### Mantenimiento automático

No hay que hacer nada manual. Logback rota los archivos automáticamente:

| Cuándo rota | Qué hace |
|---|---|
| El archivo llega a 100 MB | Comprime el actual en `.log.gz` y abre uno nuevo |
| Cambia el día (medianoche) | Igual — rotación diaria |
| Se supera el tope total | Borra los más viejos automáticamente |

Límites configurados:

| | Backend | Orquestador |
|---|---|---|
| Tamaño máximo por archivo | 100 MB | 100 MB |
| Días de historia | 30 días | 30 días |
| Tope total en disco | 2 GB | 1 GB |

### Cómo leer logs

**Ver el log en vivo (como `tail -f`):**
```bash
# Backend
tail -f backend/logs/leydata-backend.log | python3 -m json.tool

# Orquestador
tail -f logs/orchestrator/leydata-orchestrator.log | python3 -m json.tool
```

**Buscar un request específico por requestId:**
```bash
grep "a3f1c2d4" backend/logs/leydata-backend.log
```

**Buscar todos los errores de hoy:**
```bash
grep '"level":"ERROR"' backend/logs/leydata-backend.log
```

**Ver los logs del orquestador desde Docker (sin abrir el archivo):**
```bash
docker logs leydata-orchestrator --tail 100 -f
```

### Dónde sale en consola

Además del archivo, cada línea sale en consola en texto plano (más fácil de leer en desarrollo):

```
10:23:11.847 [a3f1c2d4] [http-nio-8080-exec-3] INFO  AgreementService - Consentimiento capturado
```

El `[a3f1c2d4]` es el `requestId` — el mismo que aparece en el archivo JSON y en Zipkin.

---

## 2. Loki — logs centralizados en Grafana

### Qué es

Loki es el destino central de todos los logs. Mientras los archivos locales (`backend/logs/`, `logs/orchestrator/`) siguen existiendo como backup, Loki recibe cada línea de log en tiempo real y la hace explorable desde Grafana sin tocar una terminal.

La diferencia con ELK (Elasticsearch): Loki **no indexa el contenido** del mensaje, solo los labels (`app`, `level`). Esto lo hace mucho más liviano. El contenido se filtra en el momento de la búsqueda con LogQL.

### Cómo llegan los logs a Loki

Cada servicio tiene un appender `LOKI` en `logback-spring.xml` que usa la librería `loki4j`. Al escribir una línea de log, la envía simultáneamente a:
- Consola (texto plano)
- Archivo local (JSON rotativo)
- Loki (push por HTTP en tiempo real)

Si Loki no está corriendo, `loki4j` encola internamente y reintenta. Si agota los reintentos, descarta silenciosamente. La app nunca se cae por esto.

### URLs según dónde corre cada servicio

| Servicio | Dónde corre | URL de Loki que usa |
|---|---|---|
| Backend | Host (WSL / Mac / Linux) | `http://localhost:3100` (default) |
| Orquestador | Docker (`leydata-consent-network`) | `http://loki:3100` (nombre del contenedor) |

Para el backend, la URL está configurada por defecto. Si Loki escucha en otro puerto, sobrescribir con `LOKI_URL=http://localhost:<puerto>` en el `.env`.

### Cómo levantar

```bash
docker-compose up -d loki grafana
```

Loki disponible en `http://localhost:3100` (API interna — no tiene UI propia, se usa desde Grafana).

### Cómo explorar logs en Grafana

1. Abrir `http://localhost:3000`
2. Menú lateral → **Explore** → seleccionar datasource **Loki**
3. Escribir una query LogQL:

```logql
# Todos los logs del backend
{app="leydata-backend"}

# Solo errores del orquestador
{app="leydata-orchestrator", level="ERROR"}

# Buscar un request específico por ID (correlación con Zipkin)
{app="leydata-backend"} |= "requestId=a3f1c2d4"

# Buscar logs de consentimiento capturado
{app="leydata-backend"} |= "Consentimiento capturado"

# Errores de los últimos 15 minutos
{app="leydata-backend", level="ERROR"} [15m]
```

### Dónde se guardan los datos

En el volumen Docker `loki_data`. Persiste entre reinicios del contenedor. La retención no tiene límite configurado — en producción agregar `retention_period` en `loki-config.yml`.

---

## 3. Prometheus + Grafana — cuánto y con qué frecuencia

### Qué son

Prometheus pregunta al sistema cada 15 segundos: *"¿cuántos consentimientos capturaste? ¿cuántos errores hubo? ¿Cuánto tarda `/consent/check`?"*. Guarda esas cifras en el tiempo. Grafana las dibuja como gráficos.

### Dónde se guardan los datos

Dentro de Docker, en el volumen `prometheus_data`. No es un archivo que vayas a abrir directamente — se accede siempre desde el navegador en `http://localhost:9090` (Prometheus) o `http://localhost:3000` (Grafana).

Prometheus retiene datos por **30 días** (configurable con `--storage.tsdb.retention.time`).

### Cómo levantar

```bash
docker-compose up -d prometheus grafana
```

| Servicio | URL | Credenciales |
|---|---|---|
| Grafana | `http://localhost:3000` | admin / admin |
| Prometheus | `http://localhost:9090` | sin auth |

### Cómo usar Grafana

1. Abrir `http://localhost:3000`
2. El dashboard **"LeyData — Compliance & Consent"** carga automáticamente
3. Ver los paneles en tiempo real — se actualizan cada 30 segundos

Paneles disponibles:

| Panel | Qué muestra | Por qué importa |
|---|---|---|
| Mismatches en cadena de auditoría | Rupturas en los hash SHA-256 del audit log | Debe ser **0** siempre — si sube, hay un problema que reportar al CPDT |
| Total registros de auditoría | Cantidad acumulada de eventos | Evidencia de operación continua |
| Estado Backend / Orquestador | UP / DOWN en tiempo real | Disponibilidad del sistema |
| Consentimientos capturados vs revocados | Series de tiempo | Tendencias de uso |
| Latencia `/consent/check` p50/p95/p99 | Tiempo de respuesta en ms | p99 > 500ms → revisar Redis |
| Eventos de auditoría por acción | Desglose por tipo de evento | Distribución de operaciones |
| HTTP errors 5xx (backend) | Tasa de errores | Cualquier spike merece investigación |

### Cómo usar Prometheus directamente

Útil para consultas puntuales o para verificar que el scrape funciona:

1. Abrir `http://localhost:9090` → **Status → Targets** — deben aparecer `backend` y `orchestrator` en verde (`UP`)
2. Si `backend` aparece en `DOWN`: el backend no está corriendo o `/actuator/prometheus` no responde

Verificar manualmente:
```bash
curl http://localhost:8080/actuator/prometheus | head -20
```

Consultas útiles en Prometheus (pestaña **Graph**):

```promql
# Consentimientos capturados totales
consent_captured_total

# Consentimientos revocados totales
consent_revoked_total

# Entradas de auditoría por acción
audit_logs_total

# Mismatches de integridad (debe ser 0 siempre)
audit_integrity_check_total{result="mismatch"}

# Latencia p99 de /consent/check en los últimos 5 minutos
histogram_quantile(0.99, rate(consent_check_seconds_bucket[5m]))

# Estado de los servicios (1 = UP, 0 = DOWN)
up{job="backend"}
up{job="orchestrator"}
```

### Métricas de dominio instrumentadas

| Métrica | Dónde se incrementa | Tags |
|---|---|---|
| `audit.logs` | `AuditService.log()` — cada entrada de auditoría | `action` (tipo de acción) |
| `audit.integrity.check` | `AuditService.verifyChainIntegrity()` | `result=ok\|mismatch` |
| `consent.captured` | `AgreementService.create()` — cada consentimiento firmado | `template` |
| `consent.revoked` | `AgreementService.revoke()` — cada revocación | `template` |
| `consent.check` | `ConsentController.check()` en el orquestador | `status=ok\|error` |

---

## 4. Zipkin — dónde se fue el tiempo

### Qué es

Cuando un request entra al orquestador y llama al backend, Zipkin registra automáticamente cuánto tardó cada paso del camino completo. Sirve para identificar exactamente en qué salto se está yendo el tiempo o en qué punto falló.

### Dónde se guardan los datos

En memoria dentro del contenedor de Zipkin. Los datos se pierden cuando el contenedor se reinicia — esto es aceptable para desarrollo. En producción se configuraría con un backend persistente (Elasticsearch o Cassandra).

### Cómo levantar

```bash
docker-compose up -d zipkin
```

URL: `http://localhost:9411`

### Cómo usar

1. Hacer cualquier request al orquestador (ej. `GET /consent/check`)
2. Abrir `http://localhost:9411`
3. Clic en **"Run Query"**
4. Seleccionar el servicio (`leydata-orchestrator` o `leydata-backend`)
5. Clic en un trace para ver el desglose por span

Cada trace muestra el flujo completo:

```
Request total: 320ms
  └── [leydata-orchestrator] validar JWT:        12ms
  └── [leydata-orchestrator] consultar Redis:    18ms   ← cache miss
  └── [leydata-backend]      query PostgreSQL:  290ms   ← aquí está el cuello de botella
```

Un **span rojo** indica dónde ocurrió el error. El `traceId` del span coincide con el `requestId` en los logs — permite hacer:

```bash
grep "<traceId-de-zipkin>" backend/logs/leydata-backend.log
```

Y ver el stack trace completo de ese error específico.

---

## 5. Alertas automáticas — Grafana Alerting

### Qué hace

Las alertas convierten los paneles visuales en guardianes activos. Cuando una condición se cumple (mismatch en audit log, backend caído), Grafana envía una notificación al canal configurado sin que nadie esté mirando el dashboard.

### Alertas configuradas

| Alerta | Condición | Urgencia |
|---|---|---|
| **Ruptura en Cadena de Auditoría** | `audit_integrity_check_total{result="mismatch"}` aumenta en los últimos 5 min | Crítica — dispara inmediatamente |
| **Backend Inaccesible** | `up{job="backend"} < 1` durante más de 2 minutos | Crítica — 2 min de gracia para reinicios normales |

La alerta de auditoría es el elemento de cumplimiento clave: cualquier ruptura SHA-256 en el ledger de auditoría debe evaluarse como posible alteración de registros bajo Ley 21.719 art. 14.

### Canales de notificación

Se configuran en el `.env`. Podés activar uno, ambos, o ninguno (las alertas igual aparecen en la UI de Grafana en `http://localhost:3000/alerting`):

**Webhook** — funciona con Slack, Teams, Discord, o cualquier endpoint HTTP:
```
GRAFANA_ALERT_WEBHOOK_URL=https://hooks.slack.com/services/T.../B.../...
```

**Email** — requiere credenciales SMTP:
```
GRAFANA_SMTP_ENABLED=true
GRAFANA_SMTP_HOST=smtp.gmail.com:587
GRAFANA_SMTP_USER=cuenta@gmail.com
GRAFANA_SMTP_PASSWORD=app-password
GRAFANA_ALERT_EMAIL_TO=ops@leydata.cl;dpo@leydata.cl
```

Después de cambiar el `.env`, reiniciar Grafana para aplicar:
```bash
docker-compose up -d grafana
```

### Ver el estado de las alertas

`http://localhost:3000/alerting` → lista todas las reglas con estado `Normal`, `Pending`, o `Firing`.

`http://localhost:3000/alerting/history` → historial de disparos anteriores.

### Cómo funciona internamente

Las alertas están definidas como código en [`monitoring/grafana-provisioning/alerting/leydata-alerts.yml`](../monitoring/grafana-provisioning/alerting/leydata-alerts.yml). Grafana las carga automáticamente al arrancar, igual que el datasource y el dashboard. No hay nada que configurar manualmente en la UI.

---

## 6. Health check del audit log

El componente `AuditHealthIndicator` expone el estado del audit log en `/actuator/health`:

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Respuesta esperada:

```json
{
  "status": "UP",
  "components": {
    "auditChain": {
      "status": "UP",
      "details": {
        "totalRecords": 142,
        "lastRecord": "2026-07-01T10:23:11"
      }
    },
    "db":    { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

- `auditChain: DOWN` → el audit log no es accesible (problema de BD)
- `auditChain: UP` + `audit_integrity_check_total{result="mismatch"} > 0` en Prometheus → la cadena SHA-256 está rota (problema de integridad, reportar al CPDT)

---

## 7. Flujo de diagnóstico ante un incidente

```
1. Alerta Grafana dispara (ruptura SHA-256 o backend DOWN)
          ↓
2. Grafana dashboard → spike de errores 5xx a las 03:14
          ↓
3. Zipkin → Run Query → filtrar por ese minuto → span rojo indica dónde falló
          ↓
4. Copiar el traceId del span (ej: "requestId=a3f1c2d4")
          ↓
5. Grafana → Explore → Loki → {app="leydata-backend"} |= "requestId=a3f1c2d4"
          ↓
6. Ver el stack trace completo del error — sin abrir terminal
```

Cuatro herramientas, un hilo de diagnóstico. El `requestId` / `traceId` es el conector entre todas.

---

## Archivos del módulo

### Backend (`backend/`)

| Archivo | Rol |
|---|---|
| `src/main/java/.../shared/RequestIdFilter.java` | Genera o propaga `X-Request-ID`, lo inyecta en MDC — aparece en todos los logs del mismo request |
| `src/main/java/.../config/AuditHealthIndicator.java` | Expone el estado del audit log en `/actuator/health` como componente `auditChain` |
| `src/main/resources/logback-spring.xml` | Define appenders: consola (texto plano) + archivo (JSON rotativo) |
| `src/main/resources/application.properties` | Config Actuator + Zipkin (`management.*`) |

### Orquestador (`orchestrator/`)

| Archivo | Rol |
|---|---|
| `src/main/resources/logback-spring.xml` | Misma estructura que el backend |
| `src/main/resources/application.yml` | Config Actuator + Zipkin |

### Infraestructura (`/`)

| Archivo | Rol |
|---|---|
| `monitoring/prometheus.yml` | Scrape: backend en `host.docker.internal:8080`, orquestador en `orchestrator:8081` |
| `monitoring/loki-config.yml` | Config mínima de Loki: filesystem, sin auth, schema v13, puerto 3100 |
| `monitoring/grafana-dashboards/leydata.json` | Dashboard pre-configurado — carga automáticamente |
| `monitoring/grafana-provisioning/datasources/prometheus.yml` | Provisioning de Prometheus como datasource (UID `DS_PROMETHEUS`) |
| `monitoring/grafana-provisioning/datasources/loki.yml` | Provisioning de Loki como datasource (UID `DS_LOKI`) |
| `monitoring/grafana-provisioning/dashboards/leydata.yml` | Apunta Grafana al directorio de dashboards |
| `monitoring/grafana-provisioning/alerting/leydata-alerts.yml` | Contact points (webhook + email), notification policy, y 2 reglas de alerta |
| `nginx/nginx.conf` | NGINX: workers, timeouts, body limit, zonas de rate limiting, log format JSON |
| `nginx/conf.d/leydata.conf` | NGINX: upstream blocks, security headers, location routing con rate limits |

> Ver documentación completa de la capa NGINX en [docs/nginx-module.md](nginx-module.md).

---

## Configuración por entorno

| Propiedad | Dev | Producción recomendada |
|---|---|---|
| `management.tracing.sampling.probability` | `1.0` (100% de requests trackeados) | `0.05` (5% — reduce overhead) |
| `management.endpoint.health.show-details` | `when-authorized` | `never` |
| Retención Prometheus | `30d` | `90d` |
| Logs totales en disco | backend 2 GB · orquestador 1 GB | Según capacidad del servidor |
| `GRAFANA_ALERT_WEBHOOK_URL` | vacío (alertas solo en UI) | URL de Slack / Teams configurada |
| `GRAFANA_SMTP_ENABLED` | `false` | `true` con credenciales reales |
| `LOKI_URL` (backend) | `http://localhost:3100` | URL del servidor Loki accesible desde el host |

Cambiar sampling sin recompilar:

```bash
# Bash
MANAGEMENT_TRACING_SAMPLING_PROBABILITY=0.05 ./mvnw spring-boot:run

# PowerShell
$env:MANAGEMENT_TRACING_SAMPLING_PROBABILITY = "0.05"
.\mvnw.cmd spring-boot:run
```

---

## Dependencias

### `backend/pom.xml` (Spring Boot 4.0.6)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

> **Spring Boot 4.x:** `HealthIndicator` y `Health` están en `org.springframework.boot.health.contributor`, no en `org.springframework.boot.actuate.health` (que ya no existe en SB 4.x).

```xml
<!-- Loki — push de logs estructurados desde logback (1.5.x requiere logback 1.5+) -->
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>1.5.2</version>
</dependency>
```

Las mismas 5 dependencias van en `orchestrator/pom.xml` (Spring Boot 3.4.6). Actuator ya existía en el orquestador.
