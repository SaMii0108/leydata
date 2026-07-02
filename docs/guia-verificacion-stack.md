# Guía de Verificación del Stack — LeyData

Checklist paso a paso para confirmar que todos los servicios están funcionando correctamente.  
Cubre: **WSL**, **macOS/Linux**, **Windows nativo (PowerShell)**.

> Usar junto a `GUIA-INSTALACION.md`. Esta guía asume que el stack ya fue configurado — son pasos de **verificación**, no de primera instalación.

---

## Tabla de puertos

| Servicio | Puerto | URL |
|---|---|---|
| Backend (JVM) | 8080 | `http://localhost:8080` |
| Orquestador (Docker) | 8081 | `http://localhost:8081` |
| Keycloak | 8180 | `http://localhost:8180` |
| PostgreSQL primary | 5433 | `localhost:5433` |
| Redis | 6379 | `localhost:6379` |
| Prometheus | 9090 | `http://localhost:9090` |
| Grafana | 3000 | `http://localhost:3000` |
| Loki | 3100 | `http://localhost:3100` |
| Zipkin | 9411 | `http://localhost:9411` |
| NGINX | 80 | `http://localhost` |

---

## Paso 1 — Infraestructura Docker

### 1.1 Verificar contenedores corriendo

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Resultado esperado (los puertos y tiempos varían):
```
NAMES                          STATUS          PORTS
leydata-consent-db             Up 5 minutes    0.0.0.0:5433->5432/tcp
leydata-consent-db-replica     Up 5 minutes    0.0.0.0:5434->5432/tcp
leydata-pgbouncer              Up 5 minutes    0.0.0.0:5435->5432/tcp
leydata-consent-keycloak-db    Up 5 minutes    5432/tcp
leydata-consent-keycloak       Up 5 minutes    0.0.0.0:8180->8080/tcp
leydata-redis                  Up 5 minutes    0.0.0.0:6379->6379/tcp
leydata-orchestrator           Up 5 minutes    0.0.0.0:8081->8081/tcp
```

Si alguno no aparece, levantarlo:
```bash
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis
```

### 1.2 Verificar Keycloak

```bash
curl -s http://localhost:8180/realms/leydata | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK:', d['realm'])"
```

Resultado esperado: `OK: leydata`

Si falla: `bash scripts/setup-keycloak.sh` (ver GUIA-INSTALACION.md sección 5).

### 1.3 Verificar realm empresa-cliente (necesario para B2B)

```bash
curl -s http://localhost:8180/realms/empresa-cliente | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK:', d['realm'])"
```

Resultado esperado: `OK: empresa-cliente`

Si falla: `bash scripts/setup-empresa-cliente-realm.sh`

### 1.4 Verificar Redis

```bash
docker exec leydata-redis redis-cli ping
```

Resultado esperado: `PONG`

---

## Paso 2 — Backend (Spring Boot en JVM)

### 2.1 Verificar que el backend está arriba

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Resultado esperado:
```json
{
  "status": "UP",
  "components": {
    "audit": { "status": "UP" },
    "db": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

Si el backend no responde, levantarlo:

**WSL / macOS / Linux:**
```bash
cd backend
export $(grep -v '^#' ../.env | xargs) && ./mvnw spring-boot:run
```

**Windows PowerShell:**
```powershell
Get-Content ..\.env | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), 'Process')
}
cd backend; .\mvnw.cmd spring-boot:run
```

### 2.2 Verificar métricas Actuator

```bash
curl -s http://localhost:8080/actuator/prometheus | head -5
```

Resultado esperado: líneas comenzando con `# HELP` y `# TYPE`. Si devuelve `401`, Spring Security no tiene el endpoint en `permitAll()`.

### 2.3 Obtener token de admin y probar endpoint protegido

```bash
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/domains \
  -H "Authorization: Bearer $TOKEN"
```

Resultado esperado: `200`

---

## Paso 3 — Orquestador (Docker)

### 3.1 Verificar health

```bash
curl -s http://localhost:8081/actuator/health
```

Resultado esperado: `{"status":"UP"}`

Si no responde o responde `{"status":"DOWN"}`, ver logs:
```bash
docker logs leydata-orchestrator --tail 50
```

### 3.2 Levantar / reiniciar orquestador

```bash
# Solo si no está corriendo — no usa --build
docker-compose up -d orchestrator

# Con rebuild (cambios de código)
docker-compose up -d --build orchestrator

# Forzar recreación (cambios de env vars en .env)
docker-compose up -d --force-recreate orchestrator
```

> **WSL — problema frecuente:** si los logs muestran `Connection refused` hacia el backend, la IP de WSL cambió. Ver sección 4.1 de esta guía.

> **WSL — NO usar `docker-compose restart`** — puede fallar con error de bind mount. Usar `docker-compose down orchestrator && docker-compose up -d orchestrator` en su lugar.

### 3.3 Verificar métricas del orquestador

```bash
curl -s http://localhost:8081/actuator/prometheus | head -5
```

Resultado esperado: líneas `# HELP`. Si devuelve `401`, el endpoint no está en `permitAll()` de `SecurityConfig.java` del orquestador.

---

## Paso 4 — Flujo B2B completo

### 4.1 (WSL únicamente) Actualizar IP de WSL

La IP de WSL cambia con cada reinicio de Windows. Verificar y actualizar si es necesario:

```bash
# Ver IP actual de WSL
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
echo "IP WSL: $WSL_IP"

# Ver IP configurada en .env
grep -E "LEYDATA_BACKEND_URL|BACKEND_HOST" .env

# Si cambió, actualizar ambas variables:
sed -i "s|LEYDATA_BACKEND_URL=.*|LEYDATA_BACKEND_URL=http://$WSL_IP:8080|" .env
sed -i "s|BACKEND_HOST=.*|BACKEND_HOST=$WSL_IP|" .env

# Recrear orquestador y prometheus con los nuevos valores
docker-compose up -d --force-recreate orchestrator prometheus
```

**Windows PowerShell (para editar .env):**
```powershell
# Obtener IP de WSL desde PowerShell
$wslIp = (wsl hostname -I).Trim().Split(' ')[0]
Write-Host "IP WSL: $wslIp"

# Actualizar .env
(Get-Content .env) -replace 'LEYDATA_BACKEND_URL=.*', "LEYDATA_BACKEND_URL=http://${wslIp}:8080" | Set-Content .env

# Recrear orquestador
docker-compose up -d --force-recreate orchestrator
```

### 4.2 Obtener token del sistema externo

```bash
EXT_TOKEN=$(curl -s -X POST "http://localhost:8180/realms/empresa-cliente/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=crm-sistema&username=operador@empresa.cl&password=operador123" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

echo "Token obtenido: ${EXT_TOKEN:0:20}..."
```

Resultado esperado: token que comienza con `eyJ`.

### 4.3 Verificar (o agregar) el mapper leydata_domain

El claim `leydata_domain` es obligatorio para `/consent/capture`. Verificar si está en el token:

```bash
# Decodificar el payload del token (base64)
echo $EXT_TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool | grep leydata_domain
```

Si no aparece `leydata_domain`, agregar el mapper:

```bash
# Paso 1: obtener UUID de un dominio del backend
ADMIN_TOKEN=$(curl -s "http://localhost:8180/realms/leydata/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s "http://localhost:8080/api/domains" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(x['id'], '-', x['name']) for x in d]"

# Paso 2: agregar el mapper con el UUID elegido
bash scripts/setup-empresa-cliente-realm.sh --domain-id <uuid-del-dominio>

# Paso 3: obtener token NUEVO (los tokens anteriores no tienen el claim)
EXT_TOKEN=$(curl -s -X POST "http://localhost:8180/realms/empresa-cliente/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=crm-sistema&username=operador@empresa.cl&password=operador123" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

**Windows PowerShell:**
```powershell
.\scripts\setup-empresa-cliente-realm.ps1 -DomainId "<uuid-del-dominio>"
```

### 4.4 Obtener un templateKey activo

```bash
curl -s "http://localhost:8081/consent/template-content?domainId=<uuid-dominio>" \
  -H "Authorization: Bearer $EXT_TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(x.get('templateKey'), '-', x.get('status')) for x in d]"
```

### 4.5 Capturar consentimiento

```bash
PURPOSE_ID="<uuid-de-la-finalidad>"
TEMPLATE_KEY="<templateKey-del-paso-4.4>"

curl -s -X POST "http://localhost:8081/consent/capture" \
  -H "Authorization: Bearer $EXT_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"subjectId\": \"test-titular-001\",
    \"templateKey\": \"$TEMPLATE_KEY\",
    \"purposes\": [{ \"purposeId\": \"$PURPOSE_ID\", \"accepted\": true }]
  }" | python3 -m json.tool
```

Resultado esperado:
```json
{
  "status": "ALLOWED",
  "agreementId": "<uuid>"
}
```

> **Importante:** usar `"accepted": true` (boolean). El campo es `accepted`, NO `decision`. Mandar `"decision": "ACCEPTED"` guarda el consentimiento con `accepted=false`.

### 4.6 Verificar estado de consentimiento (con cache Redis)

```bash
curl -s "http://localhost:8081/consent/check?subjectId=test-titular-001&purposeId=$PURPOSE_ID" \
  -H "Authorization: Bearer $EXT_TOKEN" | python3 -m json.tool
```

Resultado esperado:
```json
{
  "status": "ALLOWED",
  "legalBasisCode": "CONSENTIMIENTO_EXPLICITO",
  "validUntil": "..."
}
```

### 4.7 Confirmar que Redis tiene la clave

```bash
docker exec leydata-redis redis-cli keys "consent:*"
```

Resultado esperado: `1) "consent:test-titular-001:<purposeId>"`

---

## Paso 5 — Observabilidad

### 5.1 Prometheus — verificar targets

```bash
curl -s "http://localhost:9090/api/v1/targets" \
  | python3 -c "
import sys,json
d=json.load(sys.stdin)
for t in d['data']['activeTargets']:
    print(t['labels']['job'], '-', t['health'], '-', t.get('lastError',''))
"
```

Resultado esperado:
```
backend - up -
orchestrator - up -
```

Si `backend` aparece `down` en WSL, actualizar `BACKEND_HOST` en `.env` y recrear Prometheus (ver paso 4.1).

### 5.2 Grafana — verificar que carga el dashboard

Abrir `http://localhost:3000` (admin / admin o `GRAFANA_PASSWORD` del `.env`).

El dashboard **"LeyData — Compliance & Consent"** debe cargar automáticamente.

Si Grafana no arranca o entra en loop de reinicios:
```bash
docker logs leydata-grafana --tail 30
```

Si los logs muestran `failed to validate integration ... required field 'url' is not specified`, hay contact points con URLs vacías en la provisión. El archivo `monitoring/grafana-provisioning/alerting/leydata-alerts.yml` no debe tener secciones `contactPoints` con variables vacías.

### 5.3 Generar métricas reales para ver en Grafana

Cualquier acción en el backend genera audit logs que aparecen en Grafana:

```bash
# Crear un dominio (genera métrica audit_logs_total{action="CREAR_DOMINIO"})
curl -s -X POST "http://localhost:8080/api/domains" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name": "Dominio Test Verificacion", "description": "Prueba"}' \
  | python3 -m json.tool
```

En Prometheus (`http://localhost:9090/graph`), buscar: `audit_logs_total`

### 5.4 Verificar Zipkin

Abrir `http://localhost:9411` → **Run Query** → seleccionar servicio `leydata-orchestrator`.

Cada llamada a `/consent/check` o `/consent/capture` genera un trace visible aquí.

---

## Resumen rápido — comandos de diagnóstico

```bash
# Estado de todos los contenedores
docker ps --format "table {{.Names}}\t{{.Status}}"

# Health de todos los servicios
curl -s http://localhost:8080/actuator/health | python3 -m json.tool  # backend
curl -s http://localhost:8081/actuator/health                          # orquestador
docker exec leydata-redis redis-cli ping                               # redis

# Logs en vivo
docker logs leydata-orchestrator -f --tail 30
docker logs leydata-consent-keycloak -f --tail 20

# IP de WSL (cambia con cada reinicio en Windows)
ip addr show eth0 | grep 'inet '

# Keys de consentimiento en Redis
docker exec leydata-redis redis-cli keys "consent:*"

# Prometheus targets
curl -s http://localhost:9090/api/v1/targets | python3 -c \
  "import sys,json; [print(t['labels']['job'],t['health']) for t in json.load(sys.stdin)['data']['activeTargets']]"
```

---

## Problemas conocidos por plataforma

| Problema | Plataforma | Causa | Fix |
|---|---|---|---|
| Orquestador "Connection refused" al backend | WSL | IP de WSL cambió | Paso 4.1 |
| Prometheus `backend` en DOWN | WSL | `host.docker.internal` no llega a WSL | Setear `BACKEND_HOST` en `.env`, ver paso 4.1 |
| `docker-compose restart` falla con error de mount | WSL | Bind mount path obsoleto | Usar `down && up` |
| Grafana en loop de reinicios | Todos | Contact point con URL vacía | Ver 5.2 |
| `/consent/capture` → `leydata_domain` claim faltante | Todos | Mapper no configurado en KC | Paso 4.3 |
| `/consent/check` devuelve `PENDING` aunque se capturó | Todos | Se mandó `decision` en lugar de `accepted` | Usar `"accepted": true` |
| Orquestador no tiene `mvnw` | Todos | Es solo Docker, no JVM directo | Usar `docker-compose up -d --build orchestrator` |
