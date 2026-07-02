# Guía de Levantamiento y Pruebas del Orquestador

---

## Plataformas soportadas

| Plataforma | `host.docker.internal` | Qué terminal usar |
|---|---|---|
| **Mac** | ✅ funciona | Terminal / iTerm2 para todo. |
| **Windows** | ✅ funciona | PowerShell para Docker y el backend (`mvnw.cmd`). Git Bash para los scripts `.sh` y comandos `curl`. |
| **Windows + WSL** | ❌ no alcanza WSL | WSL Ubuntu para todo. Docker Desktop puede controlarse desde ahí también. |

### Cómo leer esta guía según tu plataforma

Los bloques de código están etiquetados así:

- **"Mac / Windows Git Bash / WSL Ubuntu"** → comandos bash. En Windows correlos en **Git Bash** (no PowerShell). En Mac y WSL funcionan en la terminal nativa.
- **"PowerShell"** → comando alternativo para Windows cuando bash no aplica (arrancar el backend, cargar el `.env`).
- **"cualquier terminal"** → `docker-compose` funciona igual en PowerShell, Git Bash y Terminal de Mac.

> **¿Cuándo usar Windows + WSL?** Solo si preferís que el backend corra dentro de WSL. Si estás empezando en Windows, usá Git Bash + PowerShell — es más simple y `host.docker.internal` funciona sin configuración extra.

---

## Qué corre dónde

```
[Orquestador :8081] (Docker)  ←→  [Redis :6379] (Docker)
         ↓  llama al backend via M2M
[Backend :8080] (JVM local, fuera de Docker)
         ↓
[Keycloak :8180] [PostgreSQL :5433] (Docker)
```

El **backend** corre fuera de Docker — en tu terminal, con `./mvnw spring-boot:run`. Los demás servicios son contenedores.

---

## Índice

- [A. Primera vez — setup completo](#a-primera-vez--setup-completo)
- [B. Arranque diario](#b-arranque-diario)
- [C. Probar los endpoints](#c-probar-los-endpoints)
- [D. Flujo end-to-end completo](#d-flujo-end-to-end-completo)
- [E. Flujo de ciclo de vida](#e-flujo-de-ciclo-de-vida)
- [F. Troubleshooting](#f-troubleshooting)

---

## A. Primera vez — setup completo

> Seguir estos pasos en orden. Solo hace falta hacerlo una vez (o si borrás los volúmenes de Docker).

---

### A.1 — Requisitos previos

- **Docker Desktop** instalado y corriendo.
  - Windows + WSL: activar backend WSL2 en Settings → General → "Use WSL2 based engine".
- **Java 21** instalado en el entorno donde corre el backend.
  - Mac: `brew install openjdk@21`
  - Windows (PowerShell): descargar desde [adoptium.net](https://adoptium.net) e instalar en Windows.
  - Windows + WSL: `sudo apt install openjdk-21-jdk` dentro de WSL Ubuntu.
- **Python 3** (para parsear JSON en los scripts).
  - Mac / WSL Ubuntu: preinstalado.
  - Windows (PowerShell): descargar desde [python.org](https://www.python.org/downloads/) o `winget install Python.Python.3`.
- **Git Bash** (solo Windows con PowerShell, para correr los scripts `.sh`): se instala junto con [Git for Windows](https://git-scm.com/download/win).

---

### A.2 — Clonar y crear el `.env`

**Mac / Windows Git Bash / WSL Ubuntu:**
```bash
cd <ruta-al-proyecto>
cp .env.example .env
```

**Windows (PowerShell):**
```powershell
cd <ruta-al-proyecto>
Copy-Item .env.example .env
```

El `.env` inicial tiene estos valores (ya correctos para desarrollo local):

```
DB_USER=admin
DB_PASS=admin
DB_NAME=leydata_db
KC_BACKEND_SECRET=        ← dejar vacío por ahora, se completa en el paso A.4
```

---

### A.3 — Levantar la infraestructura base

**Cualquier terminal (Mac / Windows / WSL Ubuntu):**
```bash
cd <ruta-al-proyecto>
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis
```

Esperar que Keycloak levante (puede tardar 30-60 segundos la primera vez):

**Mac / Windows Git Bash / WSL Ubuntu:**
```bash
curl -s http://localhost:8180/realms/master | python3 -m json.tool | head -3
# Debe mostrar JSON con "realm": "master"
```

**Windows (PowerShell):**
```powershell
(Invoke-WebRequest http://localhost:8180/realms/master).Content | python3 -m json.tool | Select-Object -First 3
```

---

### A.4 — Configurar Keycloak y completar el `.env`

**Mac / Windows Git Bash / WSL Ubuntu:**
```bash
cd <ruta-al-proyecto>
bash scripts/setup-keycloak.sh
```

**Windows (PowerShell):** abrir Git Bash y correr el mismo comando de arriba — este script no tiene equivalente en PowerShell.

Al finalizar, el script imprime:
```
======================================================
 Keycloak configurado correctamente.
 KC_BACKEND_SECRET: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
======================================================
```

**Abrir el `.env` y completar esa línea ahora:**
```
KC_BACKEND_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> Este secret es el que el backend usa para autenticarse como cliente en Keycloak. Si el archivo queda vacío, el backend arranca pero falla al llamar a Keycloak.

---

### A.5 — Crear el cliente del Orquestador en Keycloak

El Orquestador se autentica contra Keycloak con sus propias credenciales (flujo M2M `client_credentials`) para poder llamar al backend en nombre del sistema externo. Hay que crear ese cliente manualmente una sola vez.

**Mac / Windows Git Bash / WSL Ubuntu:**
```bash
# Token de admin de Keycloak
MASTER_TOKEN=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Crear cliente
curl -s -X POST http://localhost:8180/admin/realms/leydata/clients \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "leydata-orchestrator",
    "enabled": true,
    "publicClient": false,
    "serviceAccountsEnabled": true,
    "standardFlowEnabled": false,
    "directAccessGrantsEnabled": false
  }'

# Obtener UUID del cliente recién creado
CLIENT_UUID=$(curl -s "http://localhost:8180/admin/realms/leydata/clients?clientId=leydata-orchestrator" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

# Obtener el secret generado por Keycloak
KC_ORCHESTRATOR_SECRET=$(curl -s \
  "http://localhost:8180/admin/realms/leydata/clients/$CLIENT_UUID/client-secret" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['value'])")

echo "=============================="
echo " KC_ORCHESTRATOR_SECRET: $KC_ORCHESTRATOR_SECRET"
echo "=============================="
echo "Anotalo — lo necesitás cada vez que levantás el Orquestador."
```

**Guardar ese valor** (en un gestor de passwords, en tus notas, donde prefieras). No va al `.env` — se pasa como variable de entorno al levantar el contenedor (paso A.8).

Luego, asignar el rol `ADMIN` a la service account del Orquestador (sin esto, el backend devuelve 403):

```bash
SA_USER=$(curl -s "http://localhost:8180/admin/realms/leydata/clients/$CLIENT_UUID/service-account-user" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

ADMIN_ROLE=$(curl -s "http://localhost:8180/admin/realms/leydata/roles/ADMIN" \
  -H "Authorization: Bearer $MASTER_TOKEN")

curl -s -X POST "http://localhost:8180/admin/realms/leydata/users/$SA_USER/role-mappings/realm" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "[$ADMIN_ROLE]"

echo "Rol ADMIN asignado."
```

---

### A.6 — Configurar el realm de prueba `empresa-cliente`

Este realm simula el IdP externo del cliente B2B. Se usa para obtener el JWT de un operador CRM en las pruebas.

**Mac / Windows Git Bash / WSL Ubuntu:**
```bash
cd <ruta-al-proyecto>
bash scripts/setup-empresa-cliente-realm.sh
```

**Windows (PowerShell):** abrir Git Bash y correr el mismo comando — este script no tiene equivalente en PowerShell.

---

### A.7 — Levantar el backend

El backend necesita las variables del `.env` en su entorno. No las lee automáticamente — hay que cargarlas antes de correr Maven.

**Mac / Windows Git Bash / WSL Ubuntu:**
```bash
cd <ruta-al-proyecto>/backend
set -a && source ../.env && set +a
./mvnw spring-boot:run
```

**Windows (PowerShell):**
```powershell
# Cargar variables del .env
Get-Content .\.env | Where-Object { $_ -match '=' -and $_ -notmatch '^#' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process')
}
# Arrancar el backend
cd <ruta-al-proyecto>\backend
.\mvnw.cmd spring-boot:run
```

> **Si usás un IDE (VS Code, IntelliJ, etc.):** configurá la run configuration para que cargue el archivo `.env` de la raíz del proyecto como fuente de variables de entorno. El comando de arriba es la alternativa sin IDE.

Esperar hasta ver en consola:
```
Started BackendApplication in X.XXX seconds
```

Verificar (debe responder 401, no connection refused):

**Mac / Windows Git Bash / WSL Ubuntu:**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/domains/all
```

**PowerShell:**
```powershell
(Invoke-WebRequest -Uri http://localhost:8080/api/domains/all -SkipHttpErrorCheck).StatusCode
# Debe ser 401
```

---

### A.8 — Levantar el Orquestador

El Orquestador corre en Docker y necesita alcanzar al backend. La URL correcta depende de la plataforma.

#### Mac y Windows (PowerShell)

En Mac y Windows con PowerShell, `host.docker.internal` resuelve correctamente al host desde dentro de los contenedores — el backend es alcanzable sin configuración adicional.

**Mac / Git Bash:**
```bash
cd <ruta-al-proyecto>
KC_ORCHESTRATOR_CLIENT_SECRET=<secret-del-paso-A.5> \
docker-compose up -d --build orchestrator
```

**Windows (PowerShell):**
```powershell
cd <ruta-al-proyecto>
$env:KC_ORCHESTRATOR_CLIENT_SECRET = "<secret-del-paso-A.5>"
docker-compose up -d --build orchestrator
```

#### Windows + WSL

En Docker Desktop + WSL2, `host.docker.internal` resuelve al gateway de Docker Desktop, **no a la VM de WSL** donde corre el backend. Hay que pasar la IP real de WSL.

> La IP de WSL **cambia con cada reinicio de Windows**. Siempre detectarla antes de levantar el Orquestador.

**WSL Ubuntu:**
```bash
cd <ruta-al-proyecto>
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
echo "IP de WSL: $WSL_IP"

KC_ORCHESTRATOR_CLIENT_SECRET=<secret-del-paso-A.5> \
LEYDATA_BACKEND_URL=http://$WSL_IP:8080 \
docker-compose up -d --build orchestrator
```

**PowerShell (alternativa para el docker-compose):**
```powershell
$WSL_IP = (wsl -d Ubuntu -- ip addr show eth0 | Select-String 'inet ').ToString().Trim().Split()[1].Split('/')[0]
Write-Host "IP de WSL: $WSL_IP"

$env:KC_ORCHESTRATOR_CLIENT_SECRET = "<secret-del-paso-A.5>"
$env:LEYDATA_BACKEND_URL = "http://${WSL_IP}:8080"
docker-compose up -d --build orchestrator
```

---

Verificar que levantó:
```bash
docker logs leydata-orchestrator --tail 20
# Debe terminar con: Started OrchestratorApplication in X.XXX seconds

curl -s http://localhost:8081/actuator/health
# {"status":"UP"}
```

---

### A.9 — Configurar el claim `leydata_domain` en Keycloak

El Orquestador extrae el UUID del dominio del claim `leydata_domain` dentro del JWT entrante. Sin este claim, `POST /consent/capture` falla.

Primero obtener el UUID del dominio:
```bash
ADMIN_TOKEN=$(curl -s http://localhost:8180/realms/leydata/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s http://localhost:8080/api/domains/all \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(x['id'], x['name']) for x in d['domains']]"
```

Elegir el dominio que va a usar el sistema CRM de prueba y guardar su UUID. Luego agregar el mapper en Keycloak:

```bash
MASTER_TOKEN=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

CRM_UUID=$(curl -s "http://localhost:8180/admin/realms/empresa-cliente/clients?clientId=crm-sistema" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

DOMAIN_ID="<UUID-del-paso-anterior>"

curl -s -X POST "http://localhost:8180/admin/realms/empresa-cliente/clients/$CRM_UUID/protocol-mappers/models" \
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
echo "Mapper OK"
```

---

**Setup completo.** Pasá a la sección C para probar los endpoints, o B para ver cómo arrancar cada día.

---

## B. Arranque diario

> Una vez hecho el setup de la sección A, esto es lo que corrés cada vez que reiniciás la máquina.

### Mac

```bash
# Terminal 1 — infraestructura y orquestador
cd <ruta-al-proyecto>
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis orchestrator

# Terminal 2 — backend (con variables del .env cargadas)
cd <ruta-al-proyecto>/backend
set -a && source ../.env && set +a
./mvnw spring-boot:run
```

### Windows (PowerShell)

```powershell
# Terminal 1 (PowerShell) — infraestructura
cd <ruta-al-proyecto>
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis

# Terminal 1 — Orquestador (host.docker.internal funciona en Windows)
$env:KC_ORCHESTRATOR_CLIENT_SECRET = "<tu-secret>"
docker-compose up -d --force-recreate orchestrator

# Terminal 2 (PowerShell) — backend con variables del .env
cd <ruta-al-proyecto>
Get-Content .\.env | Where-Object { $_ -match '=' -and $_ -notmatch '^#' } | ForEach-Object {
    $k, $v = $_ -split '=', 2
    [System.Environment]::SetEnvironmentVariable($k.Trim(), $v.Trim(), 'Process')
}
cd backend
.\mvnw.cmd spring-boot:run
```

### Windows + WSL

> La IP de WSL cambia con cada reinicio. El Orquestador hay que recrearlo con la IP nueva.

**WSL Ubuntu:**
```bash
# Terminal 1 — infraestructura base
cd <ruta-al-proyecto>
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis

# Terminal 1 — Orquestador con IP de WSL
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
KC_ORCHESTRATOR_CLIENT_SECRET=<tu-secret> \
LEYDATA_BACKEND_URL=http://$WSL_IP:8080 \
docker-compose up -d --force-recreate orchestrator

# Terminal 2 — backend con variables del .env
cd <ruta-al-proyecto>/backend
set -a && source ../.env && set +a
./mvnw spring-boot:run
```

**PowerShell (solo para el paso del Orquestador, si preferís PowerShell para Docker):**
```powershell
$WSL_IP = (wsl -d Ubuntu -- ip addr show eth0 | Select-String 'inet ').ToString().Trim().Split()[1].Split('/')[0]
$env:KC_ORCHESTRATOR_CLIENT_SECRET = "<tu-secret>"
$env:LEYDATA_BACKEND_URL = "http://${WSL_IP}:8080"
docker-compose up -d --force-recreate orchestrator
```

### Verificación rápida

```bash
curl -s http://localhost:8080/actuator/health   # Backend
curl -s http://localhost:8081/actuator/health   # Orquestador
# Ambos deben responder {"status":"UP"}
```

---

## C. Probar los endpoints

### Opción A: Bruno

1. Abrir Bruno y cargar la carpeta `bruno/` del proyecto.
2. Seleccionar el environment `local`.
3. Editar `domainId` en el environment con el UUID de un dominio activo.
4. Ejecutar los requests de `bruno/Orquestador/` en orden.

### Opción B: curl

**Mac / Windows Git Bash / WSL Ubuntu.** Obtener primero el token externo:

```bash
EXTERNAL_TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/empresa-cliente/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=crm-sistema&username=operador@empresa.cl&password=operador123' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
echo "Token OK: ${EXTERNAL_TOKEN:0:40}..."
```

#### Verificar consentimiento — GET /consent/check

```bash
PURPOSE_ID="<uuid-finalidad>"
curl -s "http://localhost:8081/consent/check?subjectId=test-user-001&purposeId=$PURPOSE_ID" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
# { "status": "PENDING" } si no hay consentimiento previo
```

#### Capturar consentimiento — POST /consent/capture

> Prerequisito: debe existir un template `ACTIVE` en el dominio del JWT y un documento de privacidad `PUBLISHED` asociado.

```bash
TEMPLATE_KEY="MI_TEMPLATE"
curl -s -X POST http://localhost:8081/consent/capture \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"subjectId\": \"test-user-001\",
    \"templateKey\": \"$TEMPLATE_KEY\",
    \"purposes\": [{\"purposeId\": \"$PURPOSE_ID\", \"accepted\": true}]
  }" | python3 -m json.tool
# { "status": "ALLOWED", "agreementId": "..." }
```

#### Textos legales del template — GET /consent/template-content

```bash
curl -s "http://localhost:8081/consent/template-content?templateKey=$TEMPLATE_KEY" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
```

#### Estado del titular — GET /consent/subject/{subjectId}

```bash
curl -s "http://localhost:8081/consent/subject/test-user-001" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
```

#### Revocar una finalidad — POST /consent/revoke-purpose

```bash
curl -s -X POST http://localhost:8081/consent/revoke-purpose \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"subjectId\": \"test-user-001\", \"purposeId\": \"$PURPOSE_ID\", \"templateKey\": \"$TEMPLATE_KEY\"}" \
  | python3 -m json.tool
```

#### Revocar todo el acuerdo — POST /consent/revoke

```bash
AGREEMENT_ID="<agreementId>"
curl -s -X POST http://localhost:8081/consent/revoke \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"subjectId\": \"test-user-001\", \"agreementId\": \"$AGREEMENT_ID\"}" \
  | python3 -m json.tool
```

#### Eliminaciones pendientes — GET /consent/pending-deletions

```bash
curl -s "http://localhost:8081/consent/pending-deletions" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
```

#### Confirmar eliminación — POST /consent/confirm-deletion

```bash
curl -s -X POST http://localhost:8081/consent/confirm-deletion \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"subjectId\": \"test-user-001\",
    \"purposeId\": \"$PURPOSE_ID\",
    \"deletedAt\": \"$(date -u +%Y-%m-%dT%H:%M:%S)\"
  }"
# 204 No Content
```

---

## D. Flujo end-to-end completo

Crea datos desde cero y prueba todos los casos. **Mac / Windows Git Bash / WSL Ubuntu:**

```bash
DPO_TOKEN=$(curl -s http://localhost:8180/realms/leydata/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=leydata-frontend&username=dpo@leydata.cl&password=Test1234!' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

DOMAIN_ID="<uuid-dominio>"
PURPOSE_ID="<uuid-finalidad>"
```

**1. Crear y activar template:**
```bash
TEMPLATE=$(curl -s -X POST http://localhost:8080/api/templates \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"domainId\":\"$DOMAIN_ID\",\"templateKey\":\"MI_TEMPLATE\",\"name\":\"Mi Template\"}")
TEMPLATE_ID=$(echo $TEMPLATE | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

curl -s -X POST "http://localhost:8080/api/templates/$TEMPLATE_ID/purposes" \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"purposeId\":\"$PURPOSE_ID\",\"orderPosition\":1}"

curl -s -X POST "http://localhost:8080/api/templates/$TEMPLATE_ID/approve" \
  -H "Authorization: Bearer $DPO_TOKEN" > /dev/null

curl -s -X POST "http://localhost:8080/api/templates/$TEMPLATE_ID/activate" \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d '{"forceReconsent": false}' \
  | python3 -c "import sys,json; print('Template status:', json.load(sys.stdin)['status'])"
```

**2. Crear y publicar documento de privacidad:**
```bash
DOC=$(curl -s -X POST http://localhost:8080/api/privacy-documents \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Aviso\",\"category\":\"POLITICA_PRIVACIDAD\",\"content\":\"Contenido...\",\"templateId\":\"$TEMPLATE_ID\"}")
DOC_ID=$(echo $DOC | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/purposes/$PURPOSE_ID" \
  -H "Authorization: Bearer $DPO_TOKEN" > /dev/null
curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/submit" \
  -H "Authorization: Bearer $DPO_TOKEN" > /dev/null
curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/approve" \
  -H "Authorization: Bearer $DPO_TOKEN" > /dev/null
curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/publish" \
  -H "Authorization: Bearer $DPO_TOKEN" \
  | python3 -c "import sys,json; print('Doc status:', json.load(sys.stdin)['status'])"
```

**3. Capturar desde el Orquestador:**
```bash
EXTERNAL_TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/empresa-cliente/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=crm-sistema&username=operador@empresa.cl&password=operador123' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s -X POST http://localhost:8081/consent/capture \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"subjectId\":\"test-001\",\"templateKey\":\"MI_TEMPLATE\",\"purposes\":[{\"purposeId\":\"$PURPOSE_ID\",\"accepted\":true}]}" \
  | python3 -m json.tool
```

> También podés correr el script que hace todo esto automatizado:
> ```bash
> bash scripts/test-flujo-captura-completo.sh
> ```

---

## E. Flujo de ciclo de vida

| Estado | Qué significa | Acción del CRM |
|---|---|---|
| `ALLOWED` | Consentimiento activo | Permitir acceso |
| `EXPIRED` | Vencido | Bloquear, iniciar eliminación |
| `REQUIRES_RECONSENT` | Nueva versión del template con `forceReconsent=true` | Mostrar nuevo formulario |
| `PENDING` | Sin consentimiento | Mostrar formulario inicial |

### Probar REQUIRES_RECONSENT

```bash
NEW_TEMPLATE=$(curl -s -X POST "http://localhost:8080/api/templates/$TEMPLATE_ID/new-version" \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' -d '{}')
NEW_ID=$(echo $NEW_TEMPLATE | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

curl -s -X POST "http://localhost:8080/api/templates/$NEW_ID/approve" -H "Authorization: Bearer $DPO_TOKEN" > /dev/null
curl -s -X POST "http://localhost:8080/api/templates/$NEW_ID/activate" \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d '{"forceReconsent": true}' | python3 -c "import sys,json; d=json.load(sys.stdin); print('v'+str(d['version']), d['status'])"

docker exec leydata-redis redis-cli DEL "consent:test-user-001:$PURPOSE_ID"

curl -s "http://localhost:8081/consent/check?subjectId=test-user-001&purposeId=$PURPOSE_ID" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
# { "status": "REQUIRES_RECONSENT" }
```

### Probar EXPIRED

```bash
psql -U admin -d leydata_db -h localhost -p 5433 -c "
  UPDATE agreements_purposes SET expires_at = NOW() - INTERVAL '1 day'
  WHERE agreement_id = '<uuid>' AND purpose_id = '$PURPOSE_ID';"

docker exec leydata-redis redis-cli DEL "consent:test-user-001:$PURPOSE_ID"

curl -s "http://localhost:8081/consent/check?subjectId=test-user-001&purposeId=$PURPOSE_ID" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
# { "status": "EXPIRED" }
```

---

## F. Troubleshooting

### El Orquestador no alcanza el backend (`Connection refused`)

**Mac / Windows (PowerShell):** el backend no está corriendo. Levantarlo con `./mvnw spring-boot:run` (Mac) o `.\mvnw.cmd spring-boot:run` (PowerShell).

**Windows + WSL:** la IP de WSL cambió al reiniciar. Recrear el Orquestador con la IP nueva (ver [B. Arranque diario](#b-arranque-diario)).

Verificar qué URL tiene el contenedor ahora:
```bash
docker exec leydata-orchestrator env | grep LEYDATA_BACKEND_URL
```

---

### El Orquestador responde 403 al llamar al backend

La service account de `leydata-orchestrator` no tiene el rol `ADMIN` en Keycloak. Repetir la parte de asignación de roles del paso A.5.

---

### `POST /consent/capture` devuelve error

| Síntoma | Causa | Fix |
|---|---|---|
| 403 del backend | Service account sin rol ADMIN | Paso A.5 — asignar rol ADMIN |
| 422 / 500 | JWT sin claim `leydata_domain` | Paso A.9 — agregar mapper en Keycloak |
| 422 / 500 | No existe template ACTIVE en el dominio | Crear y activar un template |
| 422 / 500 | Template sin documento PUBLISHED | Crear y publicar documento asociado |

---

### El backend arranca pero falla al llamar a Keycloak

El `KC_BACKEND_SECRET` en el `.env` es incorrecto. Obtener el valor actual de Keycloak:

```bash
MASTER=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

CLIENT_UUID=$(curl -s "http://localhost:8180/admin/realms/leydata/clients?clientId=leydata-backend" \
  -H "Authorization: Bearer $MASTER" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

curl -s "http://localhost:8180/admin/realms/leydata/clients/$CLIENT_UUID/client-secret" \
  -H "Authorization: Bearer $MASTER" | python3 -c "import sys,json; print(json.load(sys.stdin)['value'])"
```

Actualizar `KC_BACKEND_SECRET` en el `.env` y reiniciar el backend.

---

### Reiniciar desde cero

```bash
# ⚠️ Borra base de datos y configuración de Keycloak
docker-compose down -v
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis
```

Luego volver desde el paso A.4.

---

## Credenciales por defecto

| Servicio | Usuario | Contraseña | Notas |
|---|---|---|---|
| Keycloak admin | `admin` | `admin` | `realms/master` vía admin-cli |
| LeyData admin | `admin@leydata.cl` | `Admin1234!` | realm `leydata`, client `leydata-frontend` |
| LeyData DPO | `dpo@leydata.cl` | `Test1234!` | realm `leydata` |
| LeyData Jefe dominio | `jefe@test.cl` | `Test1234!` | realm `leydata` |
| CRM externo (pruebas) | `operador@empresa.cl` | `operador123` | realm `empresa-cliente`, client `crm-sistema` |
| PostgreSQL | `admin` | `admin` | host `localhost`, puerto `5433` |
