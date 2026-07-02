# Guía de Instalación — Ley Data

**Stack:** Java 21 · Spring Boot 4.x · PostgreSQL 15 (primary + replica) · PgBouncer · Keycloak 26 · Redis 7 · NGINX 1.25 · Prometheus · Grafana · Loki · Zipkin · Docker  
**Repositorio:** https://github.com/SaMii0108/leydata  
**Arquitectura:** Modular DDD — cada módulo tiene capas `web/`, `application/`, `infrastructure/`, `domain/` — ver [ESTRUCTURA-PROYECTO.md](ESTRUCTURA-PROYECTO.md)

---

## Índice

1. [Requisitos previos](#1-requisitos-previos)
2. [Clonar el repositorio](#2-clonar-el-repositorio)
3. [Crear el archivo .env](#3-crear-el-archivo-env)
4. [Infraestructura Docker](#4-infraestructura-docker)
5. [Configurar Keycloak (realm leydata)](#5-configurar-keycloak)
6. [Configurar realm empresa-cliente (B2B)](#6-configurar-realm-empresa-cliente-b2b)
7. [Levantar el backend](#7-levantar-el-backend)
8. [Levantar el Orquestador](#8-levantar-el-orquestador)
9. [Verificar que todo funciona](#9-verificar-que-todo-funciona)
10. [Observabilidad](#10-observabilidad)
11. [Actualizar el proyecto](#11-actualizar-el-proyecto)
12. [Comandos del día a día](#12-comandos-del-día-a-día)
13. [Problemas frecuentes](#13-problemas-frecuentes)

---

## 1. Requisitos previos

| Herramienta | Versión mínima | Verificar |
|---|---|---|
| Docker Desktop | 4.x | `docker --version` |
| Java JDK | 21 | `java -version` |
| Git | cualquiera | `git --version` |

**Windows:** usar WSL 2 con Ubuntu. En Docker Desktop activar Settings > Resources > WSL Integration > Ubuntu.

**macOS / Linux:** Docker Desktop o Docker Engine instalado directamente.

---

## 2. Clonar el repositorio

```bash
git clone https://github.com/SaMii0108/leydata.git
cd leydata
```

---

## 3. Crear el archivo .env

El proyecto centraliza todas las credenciales en un archivo `.env` en la raíz del proyecto. Este archivo cumple dos funciones:

- **Docker Compose** lo lee automáticamente al ejecutar `docker-compose up -d` para configurar los contenedores de PostgreSQL.
- **El backend** lo usa como referencia de las variables de entorno necesarias para arrancar.

El archivo no está en el repositorio (está en `.gitignore`). Hay que crearlo a partir del ejemplo incluido:

**WSL / macOS / Linux:**
```bash
cp .env.example .env
```

**Windows PowerShell:**
```powershell
Copy-Item .env.example .env
```

El archivo `.env` resultante tiene este contenido:

```
DB_USER=admin
DB_PASS=admin
DB_NAME=leydata_db
KC_BACKEND_SECRET=
KC_ORCHESTRATOR_CLIENT_SECRET=
BACKEND_HOST=
```

Las primeras tres variables son fijas para el entorno de desarrollo local. Las dos variables `KC_*` se rellenan en el paso 5 con los valores que imprime el script `setup-keycloak.sh`. Dejarlas vacías por ahora.

`BACKEND_HOST` es solo para **WSL2**: completar con la IP del adaptador `eth0` de WSL (`ip addr show eth0 | grep 'inet '`). En Mac, Linux nativo y Windows sin WSL se deja vacía — Prometheus usa `host-gateway` por defecto.

---

## 4. Infraestructura Docker

Desde la raíz del proyecto, levantar **solo la infraestructura base** (sin el Orquestador, que requiere un secret que aún no tenés):

```bash
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis
```

Docker Compose lee el `.env` automáticamente para las credenciales de la base de datos. No es necesario pasar variables adicionales a este comando.

> El Orquestador se levanta **después** del paso 5 (cuando ya tenés el secret). Intentar `docker-compose up -d` completo antes de ese paso levanta el Orquestador con `KC_ORCHESTRATOR_CLIENT_SECRET` vacío, que no funciona contra Keycloak real.

**Opcional — observabilidad (Loki, Prometheus, Grafana, Zipkin):**

```bash
docker-compose up -d loki prometheus grafana zipkin
```

Esto levanta el stack de monitoreo en paralelo. No bloquea ningún otro paso — podés levantarlo ahora o después. Ver [sección 10](#10-observabilidad) para instrucciones de uso.

**Opcional — capa perimetral NGINX (rate limiting + security headers en `:80`):**

```bash
docker-compose up -d nginx
```

NGINX rutea `/api/*` → backend y `/consent/*` → orquestador. El backend y el orquestador siguen accesibles directamente en sus puertos para desarrollo. Ver [docs/nginx-module.md](../../docs/nginx-module.md).

Verificar que los contenedores estén corriendo:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

Resultado esperado:

```
NAMES                          STATUS    PORTS
leydata-consent-db             Up        0.0.0.0:5433->5432/tcp
leydata-consent-db-replica     Up        0.0.0.0:5434->5432/tcp
leydata-pgbouncer              Up        0.0.0.0:5435->5432/tcp
leydata-consent-keycloak-db    Up        5432/tcp
leydata-consent-keycloak       Up        0.0.0.0:8180->8080/tcp
leydata-redis                  Up        0.0.0.0:6379->6379/tcp
```

| Contenedor | Puerto | Rol |
|---|---|---|
| `leydata-consent-db` | 5433 | PostgreSQL primary — escribe y lee |
| `leydata-consent-db-replica` | 5434 | PostgreSQL replica — solo lectura, standby |
| `leydata-pgbouncer` | 5435 | Connection pooler frente al primary |

La primera vez que inicia `leydata-consent-keycloak` puede tardar entre 30 y 60 segundos. Esperar antes de continuar.

> **Primera vez:** `leydata-consent-db-replica` puede tardar unos segundos extra porque espera que el primary esté listo antes de hacer la copia inicial (`pg_basebackup`). Es normal.

### docker-compose.override.yml — configuración por plataforma

El archivo `docker-compose.override.yml` (en la raíz) se aplica automáticamente junto a `docker-compose.yml` y configura variables específicas del entorno de desarrollo local, especialmente la URL con la que el Orquestador alcanza al backend:

| Plataforma | `LEYDATA_BACKEND_URL` |
|---|---|
| Mac / Windows nativo (Docker Desktop) | `http://host.docker.internal:8080` (valor por defecto — dejar como está) |
| WSL (backend corriendo en Linux) | `http://<ip-wsl>:8080` — ver paso 4 WSL abajo |
| Linux nativo (Docker Engine) | `http://172.17.0.1:8080` (gateway docker0) |

#### WSL — actualizar la IP del backend en `.env`

En Docker Desktop + WSL2, `host.docker.internal` resuelve a `192.168.65.254` (el gateway de Docker Desktop en Windows), **no** a la VM de WSL donde corre el backend. Hay que poner la IP real de WSL en el `.env`:

```bash
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
echo "LEYDATA_BACKEND_URL=http://$WSL_IP:8080" >> .env
```

El `docker-compose.override.yml` ya lee esa variable con `${LEYDATA_BACKEND_URL:-http://host.docker.internal:8080}`, así que agregar la línea al `.env` es suficiente — no hay que editar el override directamente.

> **La IP de WSL cambia con cada reinicio de Windows.** Actualizar `LEYDATA_BACKEND_URL` y `BACKEND_HOST` en el `.env` y recrear el orquestador y Prometheus:
> ```bash
> WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
> sed -i "s|LEYDATA_BACKEND_URL=.*|LEYDATA_BACKEND_URL=http://$WSL_IP:8080|" .env
> sed -i "s|BACKEND_HOST=.*|BACKEND_HOST=$WSL_IP|" .env
> docker-compose up -d --force-recreate orchestrator prometheus
> ```

---

## 5. Configurar Keycloak (realm leydata)

### ¿Cuándo hay que ejecutar este script?

| Situación | ¿Ejecutar el script? |
|---|---|
| Primera instalación (clone nuevo) | **Sí** |
| Después de `docker-compose down -v` (reset total) | **Sí** |
| Después de borrar el volumen `keycloak_data` manualmente | **Sí** |
| `docker-compose stop` + `docker-compose up -d` (parada normal) | No |
| Reiniciar la PC y volver a levantar contenedores | No |
| `git pull` + reiniciar el backend | No |
| Reiniciar solo el backend | No |

El script es idempotente: si el realm y los clientes ya existen, los omite sin fallar. Se puede ejecutar más de una vez sin problema. Lo importante es que después de ejecutarlo siempre hay que **actualizar `KC_BACKEND_SECRET` en el `.env`** con el valor que imprime.

### Requisito previo

Los contenedores del paso 4 deben estar corriendo. El script espera Keycloak en `http://localhost:8180` con credenciales `admin / admin`. Si Keycloak todavía está iniciando, el script espera automáticamente hasta 90 segundos antes de fallar.

### Ejecutar el script

Usar el script correspondiente al sistema operativo:

**WSL / macOS / Linux:**
```bash
bash scripts/setup-keycloak.sh
```

**Windows PowerShell (sin WSL):**
```powershell
.\scripts\setup-keycloak.ps1
```

### Qué hace el script paso a paso

**1. Crea el realm `leydata`**  
Unidad de configuración en Keycloak. Todos los usuarios, roles y clientes del proyecto viven dentro de este realm. Token de acceso configurado con expiración de 5 minutos.

**2. Crea los cinco roles del sistema**

| Rol | Uso |
|---|---|
| `ADMIN` | Gestión de usuarios, dominios y auditoría |
| `DPO` | Revisión de solicitudes de propósito y gestión de documentos |
| `JEFE_DOMINIO` | Creación de solicitudes de propósito para sus dominios asignados |
| `USER` | Operador interno con acceso de solo lectura |
| `TITULAR` | Titular de datos personales (acceso al portal de consentimiento) |

**3. Crea el cliente `leydata-frontend`**  
Cliente público (sin secret). Es el que usa el frontend para autenticar usuarios. Permite el flujo `password` (necesario para pruebas con Postman o curl) y el flujo estándar de redirección.

**4. Crea el cliente `leydata-backend`**  
Cliente confidencial con service account habilitado. El backend lo usa para llamar a la API de administración de Keycloak cuando un admin crea un usuario nuevo vía `POST /api/users`. Sin este cliente, la creación de usuarios falla.

**5. Asigna permisos al service account de `leydata-backend`**  
Le otorga los roles `manage-users` y `view-realm` del cliente interno `realm-management`. Estos permisos son los que le permiten al backend crear, leer y modificar usuarios en Keycloak.

**6. Crea los usuarios de prueba**  
Crea `admin@leydata.cl` (Admin1234!), `dpo@leydata.cl` (Test1234!) y `jefe@test.cl` (Test1234!) con sus roles respectivos. Los demás usuarios del sistema se crean desde la API del backend con `POST /api/users`, que los registra simultáneamente en Keycloak y en la base de datos local.

**7. Crea el cliente M2M `leydata-orchestrator`**  
Cliente confidencial con `serviceAccountsEnabled: true`, `standardFlowEnabled: false`. El Orquestador lo usa para el flujo `client_credentials` al llamar al Backend LeyData. Si el cliente ya existe (ejecución repetida), el script omite la creación.

**8. Imprime ambos secrets**  
Al finalizar imprime `KC_BACKEND_SECRET` y `KC_ORCHESTRATOR_CLIENT_SECRET`. Ambos deben copiarse al `.env` antes de arrancar los servicios.

### Salida esperada al finalizar

```
======================================================
 Keycloak configurado correctamente.

 Credenciales de acceso:
   Admin UI:  http://localhost:8180  (admin / admin)
   App admin: admin@leydata.cl / Admin1234!

 Variables de entorno — copiar al .env:
   KC_BACKEND_SECRET=<valor generado>
   KC_ORCHESTRATOR_CLIENT_SECRET=<valor generado>
======================================================
```

### Actualizar el .env con los secrets

Copiar ambos valores que imprime el script y pegarlos en el `.env`:

```
KC_BACKEND_SECRET=<valor copiado aquí>
KC_ORCHESTRATOR_CLIENT_SECRET=<valor copiado aquí>
```

Estos valores cambian cada vez que se recrean los clientes en Keycloak (es decir, después de `docker-compose down -v`). Si el backend arranca con `KC_BACKEND_SECRET` incorrecto, `POST /api/users` retornará 500. Si el Orquestador arranca con `KC_ORCHESTRATOR_CLIENT_SECRET` incorrecto, fallará silenciosamente al llamar al backend (M2M con 401).

---

## 6. Configurar realm empresa-cliente (B2B)

Este paso es necesario únicamente si se van a probar los endpoints B2B del Orquestador (`/consent/check`, `/consent/capture`, `/consent/revoke`).

### ¿Qué hace el script?

Crea en Keycloak local el realm `empresa-cliente`, que simula el IdP externo del cliente B2B. Dentro del realm crea:
- **Cliente** `crm-sistema` — público, flujo `password`, para obtener tokens de prueba con Bruno/Postman
- **Usuario** `operador@empresa.cl` / `operador123` — usuario de prueba con el que se obtiene el token

### Ejecutar el script

**WSL / macOS / Linux:**
```bash
bash scripts/setup-empresa-cliente-realm.sh
```

**Windows PowerShell (sin WSL):**
```powershell
.\scripts\setup-empresa-cliente-realm.ps1
```

El script imprime la URL de JWKS al finalizar:

```
JWKS URL (para EXTERNAL_JWKS_URI):
  http://localhost:8180/realms/empresa-cliente/protocol/openid-connect/certs
```

Este valor ya viene en el `.env` como `EXTERNAL_JWKS_URI`. No hay que cambiarlo en desarrollo local.

El script también imprime el comando `curl` para obtener un token de prueba, que en Bruno corresponde al archivo **Orquestador → 01 Obtener Token Sistema Externo**.

> **El cliente M2M `leydata-orchestrator` es creado automáticamente por `setup-keycloak.sh`** (paso 5). El secret queda impreso al final del script y debe copiarse al `.env` como `KC_ORCHESTRATOR_CLIENT_SECRET`. No hay ningún paso manual adicional.

---

## 7. Levantar el backend

### WSL / macOS / Linux

Cargar el `.env` y levantar el backend en un solo comando:

```bash
cd backend
chmod +x mvnw   # solo la primera vez
export $(cat ../.env | xargs) && ./mvnw spring-boot:run
```

O pasar las variables directamente copiándolas desde el `.env`:

```bash
DB_USER=<DB_USER del .env> DB_PASS=<DB_PASS del .env> DB_NAME=<DB_NAME del .env> KC_BACKEND_SECRET=<KC_BACKEND_SECRET del .env> ./mvnw spring-boot:run
```

### Windows PowerShell

```powershell
# Leer el .env y setear las variables
Get-Content ..\.env | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), 'Process')
}

cd backend
.\mvnw.cmd spring-boot:run
```

O setear manualmente:

```powershell
$env:DB_USER="admin"
$env:DB_PASS="admin"
$env:DB_NAME="leydata_db"
$env:KC_BACKEND_SECRET="<secret>"
cd backend
.\mvnw.cmd spring-boot:run
```

### VS Code

El repositorio ya incluye `.vscode/launch.json` con la configuración `BackendApplication`. Esta configuración tiene `"envFile": "${workspaceFolder}/.env"`, lo que significa que VS Code **lee el `.env` automáticamente** cada vez que se arranca el backend.

No hay nada que configurar. Solo asegurarse de que el `.env` tenga el `KC_BACKEND_SECRET` correcto y luego arrancar con `F5` o desde el menú **Run > Start Debugging > BackendApplication**.

Si por algún motivo el `.vscode/launch.json` no existe, crearlo en la raíz del proyecto con este contenido exacto:

```json
{
    "version": "0.2.0",
    "configurations": [
        {
            "type": "java",
            "name": "Current File",
            "request": "launch",
            "mainClass": "${file}"
        },
        {
            "type": "java",
            "name": "BackendApplication",
            "request": "launch",
            "mainClass": "com.leydata.backend.BackendApplication",
            "projectName": "backend",
            "envFile": "${workspaceFolder}/.env"
        }
    ]
}
```

La clave es `"envFile": "${workspaceFolder}/.env"` en la configuración `BackendApplication`. Sin esa línea, VS Code no carga las variables y el backend arranca sin `KC_BACKEND_SECRET`, causando que `POST /api/users` falle con 500.

El backend está listo cuando aparece en consola:

```
Started BackendApplication in X.XXX seconds
```

> Para entender cómo está organizado el código fuente del backend, ver [ESTRUCTURA-PROYECTO.md](ESTRUCTURA-PROYECTO.md).

### Qué hace el backend al iniciar

Al arrancar, el backend ejecuta automáticamente `CatalogSeeder`, que siembra las bases de licitud de la Ley 21.719 y las categorías de datos predefinidas si las tablas están vacías. Es idempotente: si el catálogo ya existe, no hace nada.

**No se siembran roles en la BD local.** En el modelo Keycloak-first, los roles viven en el realm de Keycloak y se crean con el script del paso 5 (`setup-keycloak.sh`). No existe ninguna tabla `roles` ni `users_roles` en PostgreSQL.

**No se crea ningún usuario local.** Los usuarios de la BD local se crean exclusivamente vía `POST /api/users` (que los registra en Keycloak y en la BD simultáneamente). El único usuario que existe después de un reset limpio es `admin@leydata.cl` en Keycloak — creado por el script del paso 5.

### Migraciones manuales (feature/trazabilidad) — V5, V6, V7

Las migraciones V5–V7 se aplican **una sola vez después del primer arranque**. Hibernate crea las tablas vía `ddl-auto`, y luego hay que ejecutar estos tres scripts:

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
| `V6` | Backfill idempotente de campos de versionado en `purposes` (`purpose_family_id = id`, `version = 1`, `status = 'ACTIVE'`) |
| `V7` | Elimina la constraint `UNIQUE` sobre `purposes.code` — el versionado permite el mismo código en versiones distintas |

Estos scripts son idempotentes y no tienen efecto si se corren más de una vez.

---

## 8. Levantar el Orquestador

El Orquestador es un servicio Spring WebFlux en el directorio `orchestrator/`. Corre en el puerto **8081** y **siempre se ejecuta como contenedor Docker** — no se corre con `mvn` directamente.

### Prerrequisitos

- El backend está corriendo en `localhost:8080` (paso 7)
- Redis y Keycloak están corriendo (verificar con `docker ps`)
- El realm `empresa-cliente` existe (paso 6)
- `KC_ORCHESTRATOR_CLIENT_SECRET` está en el `.env` (copiado desde la salida del paso 5)

### ⚠️ Si estás en WSL — actualizar la IP del backend en `.env`

El Orquestador corre dentro de Docker y necesita alcanzar el backend que corre en WSL (fuera de Docker). En Docker Desktop + WSL2, `host.docker.internal` resuelve a `192.168.65.254` (el gateway de Docker Desktop en Windows), **no** a la IP de WSL.

Obtener la IP y agregarla al `.env`:

```bash
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
echo "LEYDATA_BACKEND_URL=http://$WSL_IP:8080" >> .env
```

Verificar que quedó bien:
```bash
grep LEYDATA_BACKEND_URL .env
# Esperado: LEYDATA_BACKEND_URL=http://172.30.171.177:8080  (tu IP real)
```

> En Mac y Windows nativo (Docker Desktop sin WSL) `host.docker.internal` funciona directamente — no hay que cambiar nada.

> La IP de WSL **cambia con cada reinicio de Windows**. Si el Orquestador dice "Connection refused" al backend, la IP cambió:
> ```bash
> WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
> sed -i "s|LEYDATA_BACKEND_URL=.*|LEYDATA_BACKEND_URL=http://$WSL_IP:8080|" .env
> docker-compose up -d --force-recreate orchestrator
> ```

### Construir y levantar el contenedor

```bash
# Desde la raíz del proyecto (donde está docker-compose.yml)
# Docker Compose lee KC_ORCHESTRATOR_CLIENT_SECRET del .env automáticamente

# Construir la imagen y levantar el contenedor
docker-compose up -d --build orchestrator

# Ver logs en tiempo real para confirmar que levantó
docker logs leydata-orchestrator -f
```

El Orquestador está listo cuando aparece en los logs:

```
Started OrchestratorApplication in X.XXX seconds
```

### Si solo querés reinciar sin reconstruir (cambios de config, no de código)

```bash
docker-compose up -d orchestrator
```

### Si cambiaste código en `orchestrator/src/`

Hay que reconstruir la imagen:

```bash
docker-compose up -d --build orchestrator
```

### Variables de entorno (configuradas en `docker-compose.override.yml`)

| Variable | Valor en desarrollo | Descripción |
|---|---|---|
| `KC_ORCHESTRATOR_CLIENT_ID` | `leydata-orchestrator` | Client ID M2M en realm leydata |
| `KC_ORCHESTRATOR_CLIENT_SECRET` | del `.env` (`${KC_ORCHESTRATOR_CLIENT_SECRET}`) | Secret del cliente M2M — generado por `setup-keycloak.sh`, guardado en `.env` |
| `KC_TOKEN_URI` | `http://keycloak:8080/realms/leydata/...` | Token endpoint interno de Keycloak |
| `LEYDATA_BACKEND_URL` | `http://host.docker.internal:8080` (ajustar en WSL) | URL del backend LeyData |
| `EXTERNAL_JWKS_URI` | `http://keycloak:8080/realms/empresa-cliente/...` | JWKS del IdP externo para validar tokens entrantes |
| `REDIS_HOST` | `redis` | Host de Redis (nombre del contenedor en la red Docker) |

### Agregar el claim `leydata_domain` al cliente de prueba

El endpoint `POST /consent/capture` requiere que el JWT incluya el claim `leydata_domain` con el UUID del dominio LeyData autorizado para ese sistema cliente. Sin este claim el Orquestador responde `422`.

Primero obtener un UUID de dominio real:

```bash
ADMIN_TOKEN=$(curl -s http://localhost:8180/realms/leydata/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s http://localhost:8080/api/domains/all \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(x['id'], '-', x['name']) for x in d['domains']]"
```

Luego agregar el protocol mapper al cliente `crm-sistema` del realm `empresa-cliente`:

```bash
MASTER_TOKEN=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

CRM_UUID=$(curl -s "http://localhost:8180/admin/realms/empresa-cliente/clients?clientId=crm-sistema" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

DOMAIN_ID="<pegar-uuid-del-dominio-obtenido-arriba>"

curl -s -X POST \
  "http://localhost:8180/admin/realms/empresa-cliente/clients/$CRM_UUID/protocol-mappers/models" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"name\": \"leydata-domain-mapper\",
    \"protocol\": \"openid-connect\",
    \"protocolMapper\": \"oidc-hardcoded-claim-mapper\",
    \"config\": {
      \"claim.name\": \"leydata_domain\",
      \"claim.value\": \"$DOMAIN_ID\",
      \"jsonType.label\": \"String\",
      \"id.token.claim\": \"true\",
      \"access.token.claim\": \"true\",
      \"userinfo.token.claim\": \"false\"
    }
  }"
echo "Mapper agregado"
```

> Obtener un nuevo token de `operador@empresa.cl` después de agregar el mapper — los tokens anteriores no incluirán el claim.

### Probar el flujo completo B2B

```bash
# 1. Obtener token del sistema externo (realm empresa-cliente)
TOKEN=$(curl -s -X POST "http://localhost:8180/realms/empresa-cliente/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&client_id=crm-sistema&username=operador@empresa.cl&password=operador123" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# 2. Verificar estado de consentimiento
curl -s "http://localhost:8081/consent/check?subjectId=abc123&purposeId=<uuid-purpose>" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool

# 3. Capturar consentimiento (templateKey = identificador de negocio, no UUID)
curl -s -X POST "http://localhost:8081/consent/capture" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "subjectId": "abc123",
    "templateKey": "ONBOARDING_CLIENTE",
    "purposes": [
      { "purposeId": "<uuid-purpose>", "accepted": true }
    ]
  }' | python3 -m json.tool
```

> **Prerequisito para `/consent/capture`:** el `templateKey` debe existir como template `ACTIVE` en el dominio del JWT, y ese template debe tener un documento de privacidad `PUBLISHED` asociado. Ver [guia-orquestador-dev.md](../docs/guia-orquestador-dev.md) para el flujo de creación completo.

---

## 9. Verificar que todo funciona

### Obtener un token de acceso

```bash
curl -s -X POST "http://localhost:8180/realms/leydata/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=leydata-frontend" \
  --data-urlencode "username=admin@leydata.cl" \
  --data-urlencode "password=Admin1234!"
```

Resultado esperado: JSON con `access_token`. El token comienza con `eyJ`.

### Probar un endpoint protegido

```bash
# Directo al backend
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer <access_token>"

# A través de NGINX (si está corriendo en :80)
curl -X GET http://localhost/api/users \
  -H "Authorization: Bearer <access_token>"
```

Resultado esperado: `200 OK` con la lista de usuarios. Verificar además que la respuesta vía NGINX incluye el header `X-Frame-Options: DENY`.

### Verificar NGINX (si está levantado)

```bash
# Routing funciona — debe devolver 401 sin token, pero con headers de seguridad
curl -I http://localhost/api/audit/logs

# Path no declarado → 404 de NGINX
curl -I http://localhost/foo

# Headers de seguridad presentes
curl -sI http://localhost/api/audit/logs | grep -E "X-Frame|X-Content|Referrer"
```

### Documentación interactiva

Con el backend corriendo, abrir en el navegador:

```
http://localhost:8080/swagger-ui.html
```

Para autenticarse en Swagger UI: click en el botón **Authorize**, seleccionar **oauth2**, ingresar las credenciales del operador y click en **Authorize**. El cliente `leydata-frontend` ya viene preconfigurado.

### Importar colección a Postman o Bruno

| Formato | URL |
|---|---|
| JSON | `http://localhost:8080/v3/api-docs` |
| YAML | `http://localhost:8080/v3/api-docs.yaml` |

En Postman / Bruno: **Import → Link** y pegar la URL. La colección incluye el esquema OAuth2 preconfigurado apuntando a Keycloak. Al importar, configurar la autenticación OAuth 2.0:

- Grant type: **Password Credentials**
- Token URL: `http://localhost:8180/realms/leydata/protocol/openid-connect/token`
- Client ID: `leydata-frontend`
- Client Secret: *(dejar vacío)*
- Username / Password: las del operador a probar

---

## 10. Observabilidad

> Ver documentación completa en [docs/monitoring-module.md](../../docs/monitoring-module.md).

El sistema tiene tres capas de observabilidad. Los logs funcionan siempre (sin Docker extra). Prometheus, Grafana y Zipkin son opcionales y requieren los contenedores del paso 4.

### Logs — activos desde el arranque, sin configuración adicional

Cada vez que el backend o el orquestador arrancan, empiezan a escribir logs automáticamente en dos lugares:

- **Consola** — texto plano, para leer en desarrollo
- **Archivo JSON** — para diagnóstico y evidencia de cumplimiento

**Dónde se guardan:**

```
backend/logs/leydata-backend.log              ← se crea al arrancar el backend
logs/orchestrator/leydata-orchestrator.log    ← se crea al arrancar el orquestador (Docker)
```

Ninguno va a git (están en `.gitignore`). Logback los **rota automáticamente**: comprime en `.log.gz` al llegar a 100 MB o al cambiar el día, y borra los más viejos al superar 2 GB (backend) o 1 GB (orquestador). No hay que hacer nada manual.

**Leer los logs:**

```bash
# Backend — ver en vivo
tail -f backend/logs/leydata-backend.log

# Buscar un request específico por su ID
grep "a3f1c2d4" backend/logs/leydata-backend.log

# Orquestador — desde Docker
docker logs leydata-orchestrator --tail 100 -f

# Orquestador — desde el archivo en el host
tail -f logs/orchestrator/leydata-orchestrator.log
```

Cada línea del archivo es un JSON con `@timestamp`, `level`, `message`, `requestId` y `app`. El `requestId` es el mismo valor que aparece en Zipkin, lo que permite cruzar ambas herramientas sobre un mismo request.

### Levantar el stack de monitoreo (opcional)

```bash
docker-compose up -d loki prometheus grafana zipkin
```

| Servicio | URL | Credenciales |
|---|---|---|
| NGINX | `http://localhost:80` | sin auth — entrada pública con rate limiting |
| Grafana | `http://localhost:3000` | admin / admin (o `GRAFANA_PASSWORD` del `.env`) |
| Loki | `http://localhost:3100` | sin auth (API interna — se usa desde Grafana) |
| Prometheus | `http://localhost:9090` | sin auth |
| Zipkin | `http://localhost:9411` | sin auth |

### Loki — explorar logs desde Grafana

Con el backend y el orquestador corriendo, los logs llegan a Loki automáticamente via `loki4j`.

**Explorar en Grafana:**
1. Abrir `http://localhost:3000` → **Explore** → seleccionar datasource **Loki**
2. Queries más usadas:

```logql
# Todos los logs del backend
{app="leydata-backend"}

# Solo errores
{app="leydata-backend", level="ERROR"}

# Buscar un request por ID (correlacionar con Zipkin)
{app="leydata-backend"} |= "requestId=<traceId-de-zipkin>"

# Logs del orquestador
{app="leydata-orchestrator"}
```

Si Loki no está corriendo, los logs siguen escribiéndose en `backend/logs/` y `logs/orchestrator/` — no hay pérdida de datos.

### Grafana — dashboard de compliance

Abrir `http://localhost:3000`. El dashboard **"LeyData — Compliance & Consent"** carga automáticamente. Incluye:

- **Mismatches en cadena de auditoría** — debe ser siempre 0. Si sube, hay un problema de integridad que debe notificarse al CPDT (requisito Ley 21.719).
- Consentimientos capturados vs revocados en el tiempo
- Latencia p50/p95/p99 de `/consent/check`
- Eventos de auditoría por acción
- HTTP errors 5xx del backend

### Prometheus — verificar targets

Abrir `http://localhost:9090` → **Status → Targets**. Deben aparecer `backend` y `orchestrator` en estado `UP`.

Si `backend` aparece en `DOWN`:

1. Verificar que el backend responde:
```bash
curl http://localhost:8080/actuator/prometheus | head -5
```
2. Si estás en WSL, la IP del target en `monitoring/prometheus.yml` puede estar desactualizada. Ver sección 13 "Prometheus `backend` en estado DOWN en WSL".

### Zipkin — trazas distribuidas

Cada request al Orquestador genera un trace que abarca todos los saltos:

```
CRM → Orquestador → Backend → PostgreSQL / Redis
```

Abrir `http://localhost:9411` → clic en **"Run Query"** → seleccionar el servicio → clic en un trace para ver el desglose por span. El `traceId` que muestra Zipkin coincide con el `requestId` de los logs — se pueden cruzar para ver el detalle completo de cualquier request.

### Alertas automáticas

Grafana tiene dos reglas de alerta pre-configuradas que se cargan al arrancar:

| Alerta | Cuándo dispara |
|---|---|
| **Ruptura en Cadena de Auditoría** | Si aparece algún mismatch SHA-256 en el audit log — dispara inmediatamente |
| **Backend Inaccesible** | Si el backend no responde más de 2 minutos |

Las alertas aparecen en `http://localhost:3000/alerting` aunque no se configure ningún canal externo. Para recibir notificaciones fuera de Grafana, agregar al `.env` antes de levantar Grafana:

```bash
# Webhook (Slack, Teams, Discord, endpoint propio)
GRAFANA_ALERT_WEBHOOK_URL=https://hooks.slack.com/services/T.../B.../...

# Email (requiere también las variables GRAFANA_SMTP_*)
GRAFANA_SMTP_ENABLED=true
GRAFANA_SMTP_HOST=smtp.gmail.com:587
GRAFANA_SMTP_USER=cuenta@gmail.com
GRAFANA_SMTP_PASSWORD=app-password
GRAFANA_ALERT_EMAIL_TO=ops@leydata.cl;dpo@leydata.cl
```

Después de editar el `.env`:
```bash
docker-compose up -d grafana
```

Ver detalle completo de configuración en [docs/monitoring-module.md](../../docs/monitoring-module.md) §4.

### Health check del audit log

```bash
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
```

Respuesta esperada incluye el componente `audit`:

```json
{
  "status": "UP",
  "components": {
    "audit": {
      "status": "UP",
      "details": { "totalRecords": 142, "lastRecord": "2026-07-01T10:23:11" }
    },
    "db":    { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

---

## 11. Actualizar el proyecto

Cuando alguien del equipo hace `git pull` para traer cambios nuevos, es necesario limpiar el directorio `target/` antes de volver a levantar el backend. El `target/` contiene las clases Java compiladas de la versión anterior. Si no se limpia, Maven puede levantar el backend con código viejo mezclado con código nuevo, causando errores difíciles de diagnosticar.

### Cuándo limpiar obligatoriamente

Limpiar siempre que el pull incluya cambios en:

- `pom.xml` — dependencias nuevas o versiones actualizadas
- Cualquier archivo `.java` — clases modificadas, agregadas o eliminadas
- `application.properties` — cambios de configuración

En la práctica, lo más seguro es limpiar siempre después de un `git pull`.

### Cómo actualizar correctamente

```bash
# 1. Traer los cambios
git pull

# 2. Limpiar el compilado anterior y levantar desde cero
cd backend
export $(cat ../.env | xargs) && ./mvnw clean spring-boot:run
```

El flag `clean` elimina la carpeta `target/` completa antes de compilar. Tarda un poco más en la primera compilación después del pull, pero garantiza que no hay clases obsoletas.

### Si solo quieres compilar sin levantar

```bash
cd backend
./mvnw clean package -DskipTests
```

Genera el JAR en `target/`. Útil para verificar que el código compila antes de levantar.

### Señales de que olvidaste limpiar

- El backend arranca pero se comporta diferente a lo que el código indica
- Errores como `NoSuchMethodError`, `ClassNotFoundException` o `IncompatibleClassChangeError`
- Cambios en el código que no tienen efecto al correr el backend

En cualquiera de esos casos, detener el backend, ejecutar `./mvnw clean spring-boot:run` y volver a probar.


---

## 11.1 Migración para entornos existentes — Julio 2026

Si ya tenés el proyecto funcionando y hacés `git pull` de esta versión, necesitás un paso extra porque se agregó el cliente M2M `leydata-orchestrator` al setup de Keycloak.

### ¿Te afecta este cambio?

**Mac / WSL:**
```bash
MASTER_TOKEN=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
curl -s "http://localhost:8180/admin/realms/leydata/clients?clientId=leydata-orchestrator" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('EXISTE' if d else 'NO EXISTE')"
```

- **"NO EXISTE"** → seguir los pasos de abajo
- **"EXISTE"** → nada que hacer, el Orquestador ya tiene su cliente M2M

### Pasos para entornos existentes (sin reset completo)

**Mac / WSL:**
```bash
# 1. Con Keycloak corriendo, re-ejecutar el script (es idempotente)
bash scripts/setup-keycloak.sh

# 2. Copiar KC_ORCHESTRATOR_CLIENT_SECRET de la salida del script al .env
#    KC_BACKEND_SECRET no cambia si el volumen de Keycloak no fue borrado

# 3. Reiniciar el Orquestador con el nuevo secret
docker-compose up -d orchestrator
```

**Windows PowerShell:**
```powershell
# 1. Re-ejecutar el script con Keycloak corriendo
.\scripts\setup-keycloak.ps1

# 2. Copiar KC_ORCHESTRATOR_CLIENT_SECRET de la salida
#    Abrir .env y agregar la línea:
Add-Content .env "KC_ORCHESTRATOR_CLIENT_SECRET=<valor-del-script>"

# 3. Reiniciar el Orquestador
docker-compose up -d orchestrator
```

### Bugs de código corregidos en esta versión

Estos bugs están corregidos en el código fuente. Con `git pull` + `./mvnw clean spring-boot:run` ya quedan aplicados:

| Bug | Síntoma anterior | Estado |
|---|---|---|
| **Bug 1 — KC_BACKEND_SECRET inválido** | `POST /api/users` → 500 genérico sin contexto | ✅ Devuelve mensaje claro sobre secret inválido |
| **Bug 2 — `legalBasisCode` null** | `POST /api/purposes` → respuesta con campos de relación null | ✅ Todos los campos de relación poblados correctamente |
| **Bug 3 — `dataCategoryCode` null** | `POST /api/purposes/{id}/data-categories` → `dataCategoryCode: null` | ✅ Campos poblados correctamente |
| **Bug 4 — Archivado imposible en PUBLISHED** | `PATCH /api/privacy-documents/{id}/archive` → 409 aunque no hay estado al que pasar | ✅ El archivado funciona sin remover finalidades |
| **Bug 5 — Orquestador M2M** | Orquestador no podía obtener token M2M para llamar al backend | ✅ Corregido (requiere paso manual de migración arriba) |


---

## 12. Comandos del día a día

### Levantar el entorno completo

El `.env` debe tener `KC_BACKEND_SECRET` y `KC_ORCHESTRATOR_CLIENT_SECRET` con valores reales (copiados del paso 5). Docker Compose los lee automáticamente.

**Mac / WSL:**
```bash
# 1. Levantar infraestructura + orquestador
docker-compose up -d

# 1b. Opcional — observabilidad (Loki :3100, Prometheus :9090, Grafana :3000, Zipkin :9411)
docker-compose up -d loki prometheus grafana zipkin

# 1c. Opcional — NGINX como entrada pública en :80 (rate limiting + security headers)
docker-compose up -d nginx

# 2. Levantar el backend (fuera de Docker)
cd backend
export $(cat ../.env | xargs) && ./mvnw spring-boot:run
```

**Windows PowerShell:**
```powershell
# 1. Levantar infraestructura + orquestador
docker-compose up -d

# 2. Levantar el backend
Get-Content ..\.env | Where-Object { $_ -notmatch '^#' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), 'Process')
}
cd backend; .\mvnw.cmd spring-boot:run
```

> **WSL:** si la IP de WSL cambió desde la última vez (ej. después de reiniciar), actualizar `LEYDATA_BACKEND_URL` en el `.env` antes del `docker-compose up`. Ver sección 8.

### Detener los contenedores sin perder datos

```bash
docker-compose stop
```

### Eliminar contenedores sin perder datos de base de datos

```bash
docker-compose down
```

### Reset completo — elimina todos los datos

```bash
docker-compose down -v
rm -rf ./postgres_data ./postgres_replica_data
docker-compose up -d
bash scripts/setup-keycloak.sh   # o .\scripts\setup-keycloak.ps1 en Windows
```

> Borrar `postgres_replica_data` es necesario para que la replica haga `pg_basebackup` desde cero al volver a levantar.

Después del reset, actualizar `KC_BACKEND_SECRET` y `KC_ORCHESTRATOR_CLIENT_SECRET` en el `.env` con los nuevos valores que imprime el script.

### Ver logs de un contenedor

```bash
docker logs leydata-consent-keycloak --tail 50
docker logs leydata-consent-db --tail 50
docker logs leydata-redis --tail 50
```

### Verificar Redis

```bash
# Conectarse a Redis y probar
docker exec -it leydata-redis redis-cli ping
# Resultado esperado: PONG

# Ver keys almacenadas (en desarrollo)
docker exec -it leydata-redis redis-cli keys "*"
```

### Verificar la replica de PostgreSQL

```bash
# Ver el estado de streaming desde el primary
docker exec -it leydata-consent-db psql -U admin -c "SELECT client_addr, state, sent_lsn, write_lsn, replay_lsn FROM pg_stat_replication;"
```

Resultado esperado: una fila con `state = streaming`. Si la tabla está vacía, la replica no está conectada — ver sección de problemas frecuentes.

```bash
# Confirmar que la replica está en modo standby (read-only)
docker exec -it leydata-consent-db-replica psql -U admin -c "SELECT pg_is_in_recovery();"
# Resultado esperado: t (true)
```

### Compilar sin levantar

```bash
cd backend
./mvnw clean package -DskipTests
```

---

## 13. Problemas frecuentes

---

### El backend no inicia — `Unable to obtain configuration from issuer`

El realm `leydata` no existe en Keycloak. Ocurre después de un reset de volúmenes o en una instalación nueva sin haber ejecutado el script de configuración.

Solución: ejecutar el paso 5.

```bash
bash scripts/setup-keycloak.sh
```

---

### `POST /api/users` retorna 500 — `Invalid client or Invalid client credentials`

El `KC_BACKEND_SECRET` que usa el backend no coincide con el que tiene Keycloak. Ocurre típicamente después de un reset de contenedores (`docker-compose down -v`), cuando el script genera un nuevo secret y el `.env` todavía tiene el valor anterior.

> Desde la versión actual del código, si el secret es incorrecto el backend devuelve un 500 con el mensaje `"Las credenciales del cliente Keycloak son inválidas (KC_BACKEND_SECRET)"` en lugar del genérico anterior, lo que facilita el diagnóstico.

**El secret NO cambia en reinicios normales.** Solo cambia cuando se hace `docker-compose down -v` o se borra el volumen `keycloak_data`. `GET /api/users` y otros endpoints siguen funcionando porque solo validan el JWT del usuario — no necesitan el service account.

Solución:

```bash
# 1. Obtener el secret actual
bash scripts/setup-keycloak.sh   # o .\scripts\setup-keycloak.ps1 en Windows

# 2. Copiar el KC_BACKEND_SECRET que imprime y pegarlo en el .env

# 3. Reiniciar el backend (VS Code: F5, o desde terminal con export $(cat ../.env | xargs))
```

Verificar también en Keycloak Admin (http://localhost:8180) > Clients > leydata-backend > Service account roles. Deben aparecer `manage-users` y `view-realm` del cliente `realm-management`. Si no están, ejecutar el script de configuración nuevamente.

---

### Login devuelve `"Account is not fully set up"`

Keycloak 26 exige que el campo `Last name` del usuario no esté vacío. El usuario puede obtener el token pero Keycloak bloquea el acceso.

Solución manual: Keycloak Admin > Users > seleccionar el usuario > completar el campo Last name > Save.

Al crear usuarios con `POST /api/users`, el campo `name` del request debe incluir nombre y apellido separados por espacio (por ejemplo: `"María García"`). El backend divide el string y usa la primera palabra como firstName y el resto como lastName.

---

### Contenedor `leydata-consent-db` en bucle de reinicios

Mensaje de error en los logs:

```
FATAL: database files are incompatible with server
DETAIL: The data directory was initialized by PostgreSQL version 15,
        which is not compatible with this version 16.
```

El directorio `./postgres_data` fue creado con una versión de PostgreSQL diferente a la de la imagen en `docker-compose.yml`. No hacer upgrade de versión de imagen si ya existen datos.

Solución sin perder datos: revertir la imagen a la versión original (`postgres:15`).

Solución aceptando pérdida de datos:

```bash
docker-compose down -v
rm -rf ./postgres_data
docker-compose up -d
bash scripts/setup-keycloak.sh
```

---

### `GET /api/audit/logs/verify` retorna `valid: false` sin manipulación

Los logs de auditoría fueron generados con una versión anterior del código que no truncaba el timestamp a microsegundos. Java 21 en Linux genera timestamps con nanosegundos (9 decimales) pero PostgreSQL `timestamp(6)` almacena solo microsegundos (6 decimales), causando que el hash guardado no coincida con el recalculado.

El código actual ya aplica la corrección (`truncatedTo(ChronoUnit.MICROS)`). Los logs anteriores a la corrección tienen hashes inválidos de forma permanente.

Para restablecer una cadena válida, conectarse a la base de datos y ejecutar:

```sql
TRUNCATE system_audit_log;
```

Los nuevos logs generados a partir de ese momento se verificarán correctamente.

---

### Migración de nombres de contenedores

Los contenedores fueron renombrados para evitar conflictos entre proyectos:

| Nombre anterior | Nombre actual |
|---|---|
| `leydata-keycloak` | `leydata-consent-keycloak` |
| `leydata-db` | `leydata-consent-db` |
| `keycloak-db` | `leydata-consent-keycloak-db` |

Si el entorno local todavía tiene los contenedores con los nombres anteriores, seguir este orden:

```bash
# 1. Bajar contenedores antes de hacer git pull
docker-compose down

# 2. Traer los cambios
git pull

# 3. Levantar con los nombres nuevos
docker-compose up -d
```

Si ya se hizo `git pull` antes de bajar los contenedores:

```bash
docker stop leydata-keycloak leydata-db keycloak-db 2>/dev/null
docker rm   leydata-keycloak leydata-db keycloak-db 2>/dev/null
docker-compose up -d
```

`docker-compose down` sin `-v` conserva los volúmenes, por lo que los datos de la base de datos y la configuración de Keycloak no se pierden.

---

### La BD local tiene usuarios de pruebas anteriores

`docker-compose down` **sin `-v` no borra los datos**. Los volúmenes de PostgreSQL persisten aunque el contenedor se elimine. Los usuarios que ves en la tabla `users` son registros reales creados vía `POST /api/users` durante sesiones de prueba anteriores — no desaparecen con un simple reinicio.

Esto es el comportamiento esperado. Si necesitas partir de una BD vacía (por ejemplo, para probar el flujo completo desde cero):

```bash
docker-compose down -v
docker-compose up -d
bash scripts/setup-keycloak.sh   # o .\scripts\setup-keycloak.ps1 en Windows
```

Después del reset, la BD local estará vacía (solo se siembran catálogos fijos al arrancar el backend, no roles ni usuarios). Los usuarios de prueba previos desaparecerán. Actualizar `KC_BACKEND_SECRET` en el `.env` con el nuevo valor del script.

---

### Redis no responde — `Connection refused` al iniciar el backend

El backend intenta conectarse a Redis en `localhost:6379` al arrancar. Si Redis no está corriendo, el backend falla.

Verificar que el contenedor esté activo:
```bash
docker ps | grep leydata-redis
```

Si no aparece, levantarlo:
```bash
docker-compose up -d redis
```

Si el puerto 6379 ya está en uso por otro proceso Redis local, detenerlo primero:
```bash
# Linux / WSL
sudo systemctl stop redis
# o
sudo service redis stop
```

---

### La replica no se conecta al primary — `pg_stat_replication` vacío

Verificar los logs de la replica:

```bash
docker logs leydata-consent-db-replica --tail 30
```

Si el directorio de datos no estaba vacío al arrancar, la replica no hizo `pg_basebackup`. Solución: borrar los datos de la replica y reiniciarla:

```bash
docker-compose stop db-replica
rm -rf ./postgres_replica_data
docker-compose up -d db-replica
```

Si el error es de autenticación (`password authentication failed for user "replicator"`), el primary fue recreado sin correr el script de replicación. Solución: reset completo con `docker-compose down -v && rm -rf ./postgres_data ./postgres_replica_data && docker-compose up -d`.

---

### El primary de PostgreSQL se cae — failover manual a la replica

> **Contexto:** La replica está en modo standby (solo lectura). Spring Boot apunta al primary (5433 o PgBouncer 5435). Si el primary cae, el backend deja de funcionar — el failover **no es automático** (Patroni no está implementado aún — ver deuda técnica en ARQUITECTURA-PROBLEMAS-PENDIENTES.md).

#### 1. Confirmar que el primary está caído

```bash
docker ps | grep leydata-consent-db
# Si el contenedor no aparece o dice "Restarting", está caído

# Intentar conectar directamente
docker exec -it leydata-consent-db psql -U admin -c "SELECT 1;" 2>&1
```

#### 2. Promover la replica a primary

```bash
# Ejecutar pg_promote() dentro del contenedor de la replica
docker exec -it leydata-consent-db-replica psql -U admin -c "SELECT pg_promote();"
# Resultado esperado: pg_promote → t
```

Esto hace que la replica salga del modo standby y empiece a aceptar escrituras. El archivo `standby.signal` desaparece automáticamente.

Verificar que ya no está en recovery:
```bash
docker exec -it leydata-consent-db-replica psql -U admin -c "SELECT pg_is_in_recovery();"
# Resultado esperado: f (false) — ya es primary
```

#### 3. Redirigir el backend a la replica (ahora nuevo primary)

La replica corre en el puerto **5434**. Hay dos opciones:

**Opción A — cambiar el `.env` y reiniciar el backend:**
```bash
# En .env, cambiar DB_HOST o el puerto al que apunta Spring Boot
# Si usas PgBouncer, actualizar la variable DB_HOST de pgbouncer a leydata-consent-db-replica

# Reiniciar PgBouncer con el nuevo destino
docker-compose restart pgbouncer
```

**Opción B — editar `application.properties` directamente (temporal):**
```
spring.datasource.url=jdbc:postgresql://localhost:5434/leydata_db
```
Reiniciar el backend.

#### 4. Cuando el primary original se recupere

**No volver a levantarlo como primary** — ahora existe otro primary y habría split-brain (dos nodos aceptando escrituras). Opciones:

- **Opción simple (dev):** reset completo — `docker-compose down -v && rm -rf ./postgres_data ./postgres_replica_data && docker-compose up -d`
- **Opción correcta (producción):** configurar el primary recuperado como nueva replica del nodo promovido usando `pg_basebackup`, luego re-registrarlo como standby.

> En producción esto lo gestiona Patroni automáticamente. Ver Fase 3 en ARQUITECTURA-PROBLEMAS-PENDIENTES.md.

---

### NGINX devuelve `502 Bad Gateway` en `/api/*`

El backend no está corriendo o `host.docker.internal` no resuelve desde el contenedor de NGINX.

```bash
# Verificar que el backend está UP
curl http://localhost:8080/actuator/health

# Verificar resolución desde dentro del contenedor de NGINX
docker exec leydata-nginx wget -qO- http://host.docker.internal:8080/actuator/health
```

Si el segundo comando falla en WSL, confirmar que `extra_hosts: host.docker.internal:host-gateway` está en el servicio `nginx` del docker-compose. Ver [docs/nginx-module.md](../../docs/nginx-module.md).

---

### NGINX devuelve `404` en todos los endpoints

Verificar que la configuración se montó correctamente:

```bash
docker exec leydata-nginx nginx -t
docker exec leydata-nginx cat /etc/nginx/conf.d/leydata.conf
```

Si el archivo está vacío o no existe, el volumen `./nginx/conf.d` no se montó. Asegurarse de estar ejecutando `docker-compose` desde la raíz del proyecto.

---

### Puerto 80 ya en uso

```bash
# WSL / macOS
sudo lsof -i :80
sudo kill -9 <PID>

# PowerShell
netstat -ano | findstr :80
taskkill /PID <PID> /F
```

Candidatos frecuentes: Apache, otro NGINX local, IIS (Windows). Si el puerto 80 no está disponible, cambiar el mapeo en docker-compose.yml: `"8000:80"` y acceder en `http://localhost:8000`.

---

### Puerto 8080 o 5433 ya en uso

```bash
# WSL / macOS — fuser no falla si no hay proceso
fuser -k 8080/tcp
# o
kill $(lsof -t -i:8080) 2>/dev/null || true

# PowerShell
$proc = (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue).OwningProcess
if ($proc) { Stop-Process -Id $proc -Force }
```

> **Nota:** `kill $(lsof -t -i:8080)` falla con "not enough arguments" si ningún proceso ocupa el puerto. Usar `|| true` al final para evitar que rompa un pipeline de comandos.

---

### Orquestador "Connection refused" al backend en WSL

El Orquestador (Docker) no puede alcanzar el backend (JVM en WSL) porque `host.docker.internal` resuelve a `192.168.65.254` (gateway de Docker Desktop) y no a la VM de WSL.

```bash
# Ver IP actual de WSL
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
echo "IP WSL actual: $WSL_IP"

# Ver qué tiene configurado el orquestador
docker exec leydata-orchestrator env | grep LEYDATA_BACKEND_URL

# Actualizar .env y recrear el contenedor
sed -i "s|LEYDATA_BACKEND_URL=.*|LEYDATA_BACKEND_URL=http://$WSL_IP:8080|" .env
docker-compose up -d --force-recreate orchestrator
```

**Windows PowerShell:**
```powershell
$wslIp = (wsl hostname -I).Trim().Split(' ')[0]
(Get-Content .env) -replace 'LEYDATA_BACKEND_URL=.*', "LEYDATA_BACKEND_URL=http://${wslIp}:8080" | Set-Content .env
docker-compose up -d --force-recreate orchestrator
```

> Esto ocurre **después de cada reinicio de Windows** porque la IP de WSL cambia.

---

### Prometheus `backend` en estado DOWN en WSL

En WSL2, `host.docker.internal` desde Docker no alcanza procesos corriendo en WSL. Prometheus usa la variable `BACKEND_HOST` del `.env` para sobreescribir el destino.

```bash
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
# Actualizar o agregar BACKEND_HOST en .env
sed -i "s|BACKEND_HOST=.*|BACKEND_HOST=$WSL_IP|" .env

# Recrear prometheus para que tome el nuevo valor (restart falla en WSL con bind mounts)
docker-compose up -d --force-recreate prometheus
```

Verificar en `http://localhost:9090/targets` que `backend` queda en estado `UP`.

---

### `docker-compose restart` falla con error de bind mount en WSL

```
Error response from daemon: failed to create shim task: OCI runtime create failed:
unable to start container process: error mounting "/run/desktop/mnt/host/wsl/..."
```

Los bind mounts de WSL tienen rutas que cambian entre reinicios del contenedor. `docker-compose restart` intenta usar la ruta antigua.

**Fix:** usar `down` + `up` en vez de `restart`:
```bash
docker-compose down <servicio> && docker-compose up -d <servicio>
# Ejemplo:
docker-compose down orchestrator && docker-compose up -d orchestrator
```

---

### Grafana no arranca — loop de reinicios con error de contact point

```
ERROR: Failed to provision alerting: failure parsing contact points:
       required field 'url' is not specified
```

Grafana 10.x no tolera contact points con URLs vacías en la provisión automática. Ocurre cuando `GRAFANA_ALERT_WEBHOOK_URL` o `GRAFANA_ALERT_EMAIL_TO` están vacíos en `.env` y el archivo `monitoring/grafana-provisioning/alerting/leydata-alerts.yml` tiene secciones `contactPoints` activas.

**Fix:** el archivo ya fue corregido para no tener `contactPoints`. Si el problema persiste, verificar:

```bash
cat monitoring/grafana-provisioning/alerting/leydata-alerts.yml | grep -A5 contactPoints
```

No debe aparecer ningún bloque `contactPoints`. Las reglas de alerta sí pueden estar — solo los `contactPoints` con URLs vacías son problemáticos.

Si querés configurar notificaciones, agregar las variables en `.env` **antes** de levantar Grafana:
```bash
GRAFANA_ALERT_WEBHOOK_URL=https://hooks.slack.com/...
# o
GRAFANA_SMTP_ENABLED=true
GRAFANA_ALERT_EMAIL_TO=ops@leydata.cl
```

---

### `/consent/capture` guarda `accepted: false` aunque se mandó ACCEPTED

El endpoint responde `200 / ALLOWED` pero el agreement queda con `accepted: false` en la BD.

**Causa:** el DTO del orquestador tiene el campo `boolean accepted` (no `String decision`):
```java
public record PurposeDecision(UUID purposeId, boolean accepted) {}
```

Mandar `"decision": "ACCEPTED"` hace que Jackson no mapee el campo y lo deje en `false` (valor por defecto).

**Fix:** usar el campo correcto:
```json
{ "purposeId": "<uuid>", "accepted": true }
```

---

### El orquestador no tiene `mvnw` — `zsh: no such file or directory: ./mvnw`

El directorio `orchestrator/` no incluye Maven wrapper. El orquestador solo puede ejecutarse como contenedor Docker:

```bash
docker-compose up -d --build orchestrator
```

No intentar `cd orchestrator && ./mvnw spring-boot:run`.
