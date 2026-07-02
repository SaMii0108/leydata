# Módulo: Capa Perimetral NGINX (`nginx/`)

**Stack:** NGINX 1.25-alpine · Docker  
**Fecha:** 2026-07-01  
**Estado:** ✅ Implementado

---

## Para qué sirve

NGINX actúa como la capa de entrada pública del sistema. Antes de que cualquier request llegue al orquestador o al backend, NGINX lo intercepta y aplica:

| Función | Qué hace | Por qué importa |
|---|---|---|
| **Rate limiting** | Limita requests por IP según el tipo de endpoint | Protege contra abuso, brute force y scraping del ledger de auditoría |
| **Security headers** | Agrega `X-Frame-Options`, `X-Content-Type-Options`, etc. | Defensa contra clickjacking, MIME sniffing y XSS básico |
| **Routing** | Dirige `/api/*` al backend y `/consent/*` al orquestador | Un único punto de entrada público en puerto 80 |
| **Bloqueo por defecto** | Cualquier path no declarado devuelve 404 | Principio de mínimo privilegio — nada expuesto por accidente |
| **Logs estructurados** | Escribe cada request en formato JSON compatible con Loki | Trazabilidad perimetral — quién llamó, cuándo, qué respondió |

---

## Arquitectura

```
Cliente externo (CRM, navegador, sistema B2B)
                │
         HTTP :80 (pública)
                │
        [ NGINX — Docker ]
        rate limit + headers
                │
        ┌───────┴───────────┐
        │                   │
  /consent/*          /api/*  /api/audit/*
        │                   │
[ Orquestador      [ Backend
  :8081 Docker ]     :8080 Host ]
```

NGINX no es parte de la lógica de negocio — es transparente para las aplicaciones. El backend y el orquestador no saben que existe.

---

## Zonas de Rate Limiting

Cada zona tiene una memoria compartida de 10 MB entre workers de NGINX. Con `$binary_remote_addr` (4 bytes por IPv4), 10 MB alcanza para ~2.5 millones de IPs distintas.

| Zona | Límite | Burst permitido | Aplica a |
|---|---|---|---|
| `consent` | 30 req/min por IP | 10 sin delay | `/consent/*` |
| `audit` | 10 req/min por IP | 5 sin delay | `/api/audit/*` |
| `api` | 60 req/min por IP | 20 sin delay | `/api/*` (resto) |

**`nodelay`**: los requests del burst se procesan inmediatamente (sin cola), y los que exceden el burst reciben `429` al instante. Sin `nodelay`, NGINX haría esperar los requests del burst — comportamiento incorrecto para una API REST.

**Ejemplo:** si una IP manda 35 requests en ráfaga a `/consent/*`:
- Los primeros 10 se procesan inmediatamente (burst)
- Los siguientes pasan al rate de 30/min
- Los que superan el límite combinado reciben `429 Too Many Requests`

---

## Security Headers

Incluidos en **todas** las respuestas (`always` — incluyendo respuestas 4xx y 5xx):

| Header | Valor | Protección |
|---|---|---|
| `X-Frame-Options` | `DENY` | Bloquea embedding en iframe — previene clickjacking |
| `X-Content-Type-Options` | `nosniff` | Impide que el browser interprete un JS como imagen (MIME sniffing) |
| `X-XSS-Protection` | `1; mode=block` | Activa el filtro XSS de browsers viejos |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | Limita la información enviada en el header `Referer` en requests cross-origin |

---

## Logs NGINX en Loki

NGINX escribe cada request en formato JSON compatible con Loki. Los logs incluyen:

```json
{
  "time": "2026-07-01T10:23:11+00:00",
  "app": "leydata-nginx",
  "level": "INFO",
  "remote_addr": "192.168.1.100",
  "method": "POST",
  "uri": "/consent/capture",
  "status": 200,
  "body_bytes": 142,
  "request_time": 0.082,
  "upstream_addr": "172.18.0.5:8081",
  "upstream_status": "200",
  "http_user_agent": "CRM-Sistema/1.0"
}
```

**Explorar en Grafana → Explore → Loki:**

```logql
# Todos los requests que pasaron por NGINX
{app="leydata-nginx"}

# Solo respuestas 429 (rate limit excedido)
{app="leydata-nginx"} | json | status = 429

# Requests de una IP específica
{app="leydata-nginx"} | json | remote_addr = "192.168.1.100"

# Latencia alta (más de 500ms)
{app="leydata-nginx"} | json | request_time > 0.5
```

---

## Cómo levantar

```bash
# Solo NGINX
docker-compose up -d nginx

# NGINX + stack completo
docker-compose up -d
```

NGINX disponible en `http://localhost:80`.

> **En desarrollo**: el orquestador (`:8081`) y el backend (`:8080`) siguen accesibles directamente para debugging. NGINX en `:80` es la entrada "de producción". En producción real, cerrar `:8080` y `:8081` con firewall o eliminando los `ports:` del compose.

---

## Routing completo

| Path de entrada | Upstream | Rate limit | Notas |
|---|---|---|---|
| `/consent/*` | `orchestrator:8081` | 30 req/min | WebFlux — respuestas reactivas |
| `/api/audit/*` | `host.docker.internal:8080` | 10 req/min | Más estricto — ledger inmutable |
| `/api/*` | `host.docker.internal:8080` | 60 req/min | Admin, DPO, propósitos, usuarios |
| `/actuator/health` | `host.docker.internal:8080` | sin límite | Para monitoreo — Prometheus, uptime checks |
| Cualquier otro path | — | — | `404 Not Found` |

---

## Archivos de configuración

```
nginx/
├── nginx.conf          ← config global
│                          workers, timeouts, client_max_body_size,
│                          zonas de rate limiting, formato de log JSON
└── conf.d/
    └── leydata.conf    ← server block
                           upstream definitions, location blocks,
                           security headers, proxy settings
```

### `nginx.conf` — parámetros globales

| Parámetro | Valor | Cambiar si... |
|---|---|---|
| `worker_processes` | `auto` | Raramente — auto = 1 worker por CPU core |
| `client_max_body_size` | `1m` | Algún endpoint recibe payloads mayores (ej. upload de PDF de política de privacidad) |
| `keepalive_timeout` | `65s` | Optimizar para conexiones persistentes de alta frecuencia |
| `proxy_read_timeout` | `30s` | Operaciones lentas (ej. generación de PDF, consulta pesada de auditoría) |

### `leydata.conf` — agregar un nuevo path

Si agregas un endpoint en un prefijo distinto a `/api/` o `/consent/`:

```nginx
# Ejemplo: agregar /webhooks/* → servicio externo
location /webhooks/ {
    limit_req zone=api burst=5 nodelay;
    limit_req_status 429;

    proxy_pass http://webhook-handler:9000;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Si el path queda sin `location` block explícito, NGINX devuelve `404` — no hay exposición accidental.

---

## Cross-OS — cómo NGINX llega al backend

El backend corre en el host (fuera de Docker). NGINX corre en Docker. Para que NGINX pueda hacer `proxy_pass` al backend:

```yaml
# docker-compose.yml — servicio nginx
extra_hosts:
  - "host.docker.internal:host-gateway"
```

| OS | Cómo resuelve `host.docker.internal` | ¿Requiere config extra? |
|---|---|---|
| Mac (Docker Desktop) | Automático — Docker Desktop lo inyecta | No — `extra_hosts` es redundante pero inofensivo |
| Windows nativo (Docker Desktop) | Automático | No |
| WSL2 (Docker Desktop con integración) | Via `host-gateway` del `extra_hosts` | Sí — el `extra_hosts` es necesario |

Si el backend no está corriendo en el host, NGINX devuelve `502 Bad Gateway` en los endpoints de `/api/`.

---

## Verificación

```bash
# 1. NGINX levanta correctamente
docker-compose up -d nginx
docker logs leydata-nginx --tail 20

# 2. Routing funciona (debe devolver 401 — sin token, pero llega al backend)
curl -I http://localhost/api/audit/logs

# 3. Security headers presentes
curl -I http://localhost/api/audit/logs | grep -E "X-Frame|X-Content|Referrer"

# 4. Path no declarado → 404
curl -I http://localhost/foo

# 5. Rate limiting funciona (el request 36 debe devolver 429)
for i in $(seq 1 36); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost/consent/check
done
```

---

## Problemas frecuentes

### `502 Bad Gateway` en `/api/*`

El backend no está corriendo o `host.docker.internal` no resuelve.

```bash
# Verificar que el backend está UP
curl http://localhost:8080/actuator/health

# Verificar resolución desde dentro del contenedor de NGINX
docker exec leydata-nginx wget -qO- http://host.docker.internal:8080/actuator/health
```

Si el segundo comando falla en WSL: confirmar que `extra_hosts: host.docker.internal:host-gateway` está en el servicio `nginx` del docker-compose.

### `502 Bad Gateway` en `/consent/*`

El orquestador no está corriendo.

```bash
docker logs leydata-orchestrator --tail 30
docker-compose up -d orchestrator
```

### Puerto 80 ya en uso

```bash
# WSL / macOS
sudo lsof -i :80
sudo kill -9 <PID>

# PowerShell
netstat -ano | findstr :80
taskkill /PID <PID> /F
```

Candidatos frecuentes: Apache, otro NGINX local, IIS (Windows).

### Config de NGINX no se recarga tras cambios

Los archivos `nginx.conf` y `leydata.conf` están montados como `:ro` (read-only). Para recargar sin reiniciar el contenedor:

```bash
# Validar la config primero
docker exec leydata-nginx nginx -t

# Recargar sin downtime
docker exec leydata-nginx nginx -s reload
```

Si hay un error de sintaxis, `nginx -t` lo reporta y el contenedor sigue corriendo con la config anterior.
