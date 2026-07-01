# Guía de Levantamiento y Pruebas del Orquestador

> Para desarrolladores que se incorporan al proyecto o necesitan levantar el stack completo desde cero.

---

## Índice

1. [Entender el stack antes de tocarlo](#1-entender-el-stack)
2. [Archivos de configuración y dónde va cada variable](#2-archivos-de-configuración)
3. [Paso a paso: levantar todo](#3-paso-a-paso)
4. [Verificar que todo funciona](#4-verificación)
5. [Probar los endpoints del Orquestador](#5-probar-endpoints)
6. [Flujo completo de captura B2B (end-to-end)](#6-flujo-end-to-end)
7. [Flujo de ciclo de vida: reconsent, expiración y eliminación](#7-flujo-ciclo-de-vida)
8. [Troubleshooting frecuente](#8-troubleshooting)

---

## 1. Entender el stack

```
Internet / Sistema cliente (CRM, ERP)
         │
         ▼  JWT externo (realm empresa-cliente)
[Orquestador :8081]  ←→  [Redis :6379]
         │
         │  red interna Docker + JWT M2M (realm leydata)
         ▼
[Backend LeyData :8080]  ←→  [Keycloak :8180]
                          ←→  [PostgreSQL :5433 / PgBouncer :5435]
```

**Lo más importante para entender:**

- El **Backend** (puerto 8080) **no se ejecuta en Docker** durante el desarrollo local — corre directo en la JVM desde el IDE o con `./mvnw spring-boot:run`. Solo los servicios de infraestructura (DB, Keycloak, Redis, Orquestador) corren en Docker.
- El **Orquestador** (puerto 8081) **sí corre en Docker**. Se construye con su propio Dockerfile. Para que alcance al backend que corre fuera de Docker, usa `host.docker.internal` (ver sección 2).
- Hay **dos realms de Keycloak** con propósitos distintos:
  - `leydata` → realm interno. Autentica usuarios del backend (DPO, Admin) y emite tokens M2M para el Orquestador.
  - `empresa-cliente` → realm de simulación. Simula el IdP externo del cliente B2B que consume el Orquestador. Solo se usa en desarrollo para tener un JWT "externo" de prueba.

---

## 2. Archivos de configuración

### El `.env` (raíz del proyecto)

```
# Solo estas tres variables son necesarias:
DB_USER=admin
DB_PASS=admin
DB_NAME=leydata_db

# Este se rellena DESPUÉS de ejecutar setup-keycloak.sh:
KC_BACKEND_SECRET=<se genera en el paso 3.3>
```

El `.env` **solo sirve para dos cosas**:
1. Que Docker Compose sepa cómo crear la base de datos.
2. Que el backend (JVM fuera de Docker) sepa con qué secret autenticarse como servicio contra Keycloak.

> **No pongas aquí** el secret del orquestador ni URLs del backend.

---

### El `docker-compose.override.yml`

Se aplica **automáticamente** sobre `docker-compose.yml` cuando corres `docker-compose up`. No hay que mencionarlo explícitamente en el comando.

Contiene las variables de entorno del Orquestador para desarrollo local. La única que cambia según la plataforma es `LEYDATA_BACKEND_URL` — la URL con la que el contenedor del Orquestador alcanza al backend que corre fuera de Docker.

#### ¿Qué valor usar en `LEYDATA_BACKEND_URL`?

| Plataforma | Valor |
|---|---|
| Mac / Windows nativo (Docker Desktop) | `http://host.docker.internal:8080` (ya configurado por defecto) |
| **WSL con Docker Desktop** | `http://<IP-de-WSL>:8080` — ver abajo |
| Linux nativo (Docker Engine) | `http://172.17.0.1:8080` (gateway de `docker0`) |

#### Si estás en WSL (este proyecto usa WSL)

`host.docker.internal` dentro de un contenedor Docker apunta a la IP de Windows (`192.168.65.254`), **no a WSL**. Como el backend corre en WSL, hay que usar la IP de la interfaz `eth0` de WSL.

**El problema:** esa IP **cambia con cada reinicio de WSL**.

**La solución — un comando que lo actualiza automáticamente:**

```bash
# Ejecutar en WSL antes de levantar el Orquestador (o después de reiniciar WSL):
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1) && \
  sed -i "s|LEYDATA_BACKEND_URL:.*|LEYDATA_BACKEND_URL: http://$WSL_IP:8080|" docker-compose.override.yml && \
  echo "LEYDATA_BACKEND_URL actualizada a http://$WSL_IP:8080"
```

Ejecutar desde la raíz del proyecto. Modifica el archivo en el momento y confirma qué IP quedó configurada. Luego correr `docker-compose up -d orchestrator` normalmente.

> Si el Orquestador lanza `Connection refused` al backend, la causa más probable es que WSL reinició y la IP cambió. Volver a correr el comando de arriba y recrear el contenedor:
> ```bash
> docker-compose up -d --force-recreate orchestrator
> ```

---

### El `KC_ORCHESTRATOR_CLIENT_SECRET`

Este secret existe porque el Orquestador necesita autenticarse contra Keycloak para poder llamar al Backend LeyData en nombre propio (flujo M2M `client_credentials`). Keycloak genera ese secret cuando se crea el cliente `leydata-orchestrator`.

**El flujo completo es:**

```
1. Tú creas el cliente leydata-orchestrator en Keycloak (paso 3.3)
        ↓
2. Keycloak genera un secret automáticamente
        ↓
3. Tú copias ese secret
        ↓
4. Docker Compose lo pasa al contenedor del Orquestador como variable de entorno
        ↓
5. El Orquestador lo usa para pedir tokens Bearer a Keycloak antes de cada llamada al Backend
```

**Dónde ponerlo:**

El `docker-compose.override.yml` ya tiene esta línea:
```yaml
KC_ORCHESTRATOR_CLIENT_SECRET: ${KC_ORCHESTRATOR_CLIENT_SECRET:-dev-secret-placeholder}
```

Eso significa: "leer el valor de la variable de entorno del sistema; si no existe, usar `dev-secret-placeholder`". Por eso no hay que editar el archivo — solo exportar la variable antes de correr `docker-compose`:

```bash
# En la terminal donde vas a correr docker-compose:
export KC_ORCHESTRATOR_CLIENT_SECRET=<secret-obtenido-en-paso-3.3>
docker-compose up -d orchestrator
```

> Si cierras la terminal y vuelves a levantar el contenedor, tenés que exportar la variable de nuevo. Para no repetirlo, podés agregarlo al final de tu `.bashrc` / `.zshrc`, o escribirlo directamente en `docker-compose.override.yml` (sin commitear).

---

## 3. Paso a paso

### 3.1 — Levantar infraestructura Docker

```bash
# Desde la raíz del proyecto (donde está docker-compose.yml)
cd /ruta/al/proyecto/leydata

# Copiar el .env de ejemplo y ajustar si es necesario
cp .env.example .env   # o créalo manualmente con los valores de la sección 2

# Levantar toda la infraestructura EXCEPTO el orquestador (el backend no está listo aún)
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis

# Verificar que Keycloak levantó (puede tardar 30-60 segundos la primera vez)
curl -s http://localhost:8180/realms/master | python3 -m json.tool | head -5
```

---

### 3.2 — Configurar el realm `leydata` en Keycloak

```bash
# Ejecutar UNA sola vez (o si borrás el volumen de Keycloak)
bash scripts/setup-keycloak.sh
```

Al finalizar, el script imprime:

```
======================================================
 Keycloak configurado correctamente.
 KC_BACKEND_SECRET: xv1azD1GtBEJpxZOAVovKBpBfrZ063eL
======================================================
```

**Copiar ese valor** y pegarlo en el `.env`:
```
KC_BACKEND_SECRET=xv1azD1GtBEJpxZOAVovKBpBfrZ063eL
```

---

### 3.3 — Crear el cliente del Orquestador en Keycloak

El script anterior no crea el cliente `leydata-orchestrator`. Hay que hacerlo manualmente una sola vez:

```bash
# Obtener token de admin de Keycloak master
MASTER_TOKEN=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Crear cliente leydata-orchestrator (confidential, solo client_credentials)
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

# Obtener el Client ID interno para buscar el secret
CLIENT_UUID=$(curl -s "http://localhost:8180/admin/realms/leydata/clients?clientId=leydata-orchestrator" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

# Obtener el secret generado automáticamente y exportarlo para Docker Compose
export KC_ORCHESTRATOR_CLIENT_SECRET=$(curl -s \
  "http://localhost:8180/admin/realms/leydata/clients/$CLIENT_UUID/client-secret" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['value'])")

echo "Secret del orquestador: $KC_ORCHESTRATOR_CLIENT_SECRET"
echo "↑ Guardalo — lo necesitás cada vez que levantás el contenedor del orquestador."
```

El último comando exporta `KC_ORCHESTRATOR_CLIENT_SECRET` directamente en tu sesión de terminal. **En esa misma terminal** podés correr el `docker-compose up` del paso 3.7 y Docker Compose va a leer la variable automáticamente. Si abrís una terminal nueva, tenés que volver a exportarla (o guardarla en tu `.bashrc`).

---

### 3.4 — Configurar el realm de prueba `empresa-cliente`

```bash
# Crear el realm que simula el IdP del sistema cliente externo
bash scripts/setup-empresa-cliente-realm.sh
```

Al finalizar muestra el comando `curl` para obtener un token de prueba (lo vas a usar en las pruebas).

---

### 3.5 — Agregar el claim `leydata_domain` al cliente `crm-sistema`

El Orquestador extrae el UUID del dominio LeyData del claim `leydata_domain` del JWT entrante. Sin este claim, `POST /consent/capture` devuelve `422`.

```bash
MASTER_TOKEN=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Obtener el UUID interno del cliente crm-sistema
CRM_UUID=$(curl -s "http://localhost:8180/admin/realms/empresa-cliente/clients?clientId=crm-sistema" \
  -H "Authorization: Bearer $MASTER_TOKEN" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

# Agregar protocol mapper que hardcodea el domainId en el token
# Reemplazar <UUID-DOMINIO> con el UUID real del dominio en LeyData
# (GET http://localhost:8080/api/domains/all con token de admin para obtenerlo)
DOMAIN_ID="<UUID-DOMINIO>"

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
echo "Mapper agregado OK"
```

**Cómo obtener el UUID del dominio:**

```bash
# Primero levantar el backend (paso 3.6), luego:
ADMIN_TOKEN=$(curl -s http://localhost:8180/realms/leydata/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=leydata-frontend&username=admin@leydata.cl&password=Admin1234!' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s http://localhost:8080/api/domains/all \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); [print(x['id'], x['name']) for x in d['domains']]"
```

---

### 3.6 — Levantar el Backend LeyData

El backend corre **fuera de Docker**, directo en la JVM. Desde la carpeta `backend/`:

```bash
cd backend

# En WSL / Linux:
DB_USER=admin DB_PASS=admin DB_NAME=leydata_db \
  KC_BACKEND_SECRET=<el-secret-del-paso-3.2> \
  ./mvnw spring-boot:run
```

Esperar hasta ver en consola:
```
Started BackendApplication in X.XXX seconds
```

**Verificar:**
```bash
# Debe responder 401 (no 404 ni connection refused):
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/domains/all
```

---

### 3.7 — Construir y levantar el Orquestador

```bash
cd /ruta/al/proyecto/leydata

# Exportar el secret del orquestador (del paso 3.3)
export KC_ORCHESTRATOR_CLIENT_SECRET=<secret-del-orquestador>

# Si estás en WSL, actualizar la IP del backend en docker-compose.override.yml primero (ver sección 2)

# Construir la imagen y levantar el contenedor
docker-compose up -d --build orchestrator

# Ver los logs del orquestador
docker logs leydata-orchestrator -f
```

Esperar hasta ver:
```
Started OrchestratorApplication in X.XXX seconds
```

---

## 4. Verificación

```bash
# 1. Health del Orquestador
curl -s http://localhost:8081/actuator/health
# Respuesta esperada: {"status":"UP"}

# 2. El backend es alcanzable por el orquestador
docker exec leydata-orchestrator wget -qO- http://<ip-backend>:8080/actuator/health 2>/dev/null || \
  echo "Si falla aquí, revisar LEYDATA_BACKEND_URL en docker-compose.override.yml"

# 3. El Orquestador puede obtener token M2M de Keycloak (ver en logs que no hay errores de 401)
docker logs leydata-orchestrator 2>&1 | grep -i "error\|exception\|401" | tail -10

# 4. El JWKS externo es alcanzable desde el contenedor
docker exec leydata-orchestrator wget -qO- \
  http://keycloak:8080/realms/empresa-cliente/protocol/openid-connect/certs | python3 -m json.tool | head -5
```

---

## 5. Probar endpoints

### Opción A: Bruno (colección incluida en el repo)

1. Abrir Bruno y cargar la carpeta `bruno/` del proyecto.
2. Seleccionar el environment `local`.
3. Editar las variables del environment:
   - `domainId`: UUID de un dominio activo (obtener con `GET /api/domains/all`)
4. Ejecutar en orden:
   - `bruno/Orquestador/01 Obtener Token Sistema Externo.bru` → copiar el `access_token` en la variable secreta `externalToken`.
   - Luego los demás requests de la carpeta `Orquestador/`.

### Opción B: curl

#### Paso 1 — Obtener token del sistema externo

```bash
EXTERNAL_TOKEN=$(curl -s -X POST \
  http://localhost:8180/realms/empresa-cliente/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=crm-sistema&username=operador@empresa.cl&password=operador123' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

echo "Token OK: ${EXTERNAL_TOKEN:0:30}..."
```

> Si falla aquí: verificar que corriste `setup-empresa-cliente-realm.sh`.

#### Paso 2 — Verificar consentimiento (GET /consent/check)

```bash
PURPOSE_ID="<uuid-de-una-finalidad>"  # cualquier UUID de purpose existente

curl -s "http://localhost:8081/consent/check?subjectId=test-user-001&purposeId=$PURPOSE_ID" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
```

Respuesta esperada si no hay consentimiento previo:
```json
{"subjectId": "test-user-001", "purposeId": "...", "status": "PENDING"}
```

#### Paso 3 — Capturar consentimiento (POST /consent/capture)

Prerequisitos antes de poder capturar:
- Debe existir un template con status `ACTIVE` en el dominio del JWT (`leydata_domain`)
- Ese template debe tener un documento de privacidad `PUBLISHED` asociado

```bash
TEMPLATE_KEY="TEST_ONBOARDING"   # el templateKey del template ACTIVE en ese dominio
PURPOSE_ID="<uuid-finalidad-del-template>"

curl -s -X POST http://localhost:8081/consent/capture \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"subjectId\": \"test-user-001\",
    \"templateKey\": \"$TEMPLATE_KEY\",
    \"purposes\": [
      { \"purposeId\": \"$PURPOSE_ID\", \"accepted\": true }
    ]
  }" | python3 -m json.tool
```

Respuesta esperada:
```json
{
  "subjectId": "test-user-001",
  "agreementId": "<uuid>",
  "status": "ALLOWED"
}
```

#### Paso 4 — Verificar que el consentimiento quedó en caché (Redis)

```bash
# La misma consulta de antes ahora debe responder "ALLOWED" desde Redis
curl -s "http://localhost:8081/consent/check?subjectId=test-user-001&purposeId=$PURPOSE_ID" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
```

#### Paso 5 — Obtener textos legales del template (GET /consent/template-content)

```bash
# Obtener los textos legales que se mostrarán al titular antes de firmar
curl -s "http://localhost:8081/consent/template-content?templateKey=MI_TEMPLATE" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool

# Respuesta: { templateId, templateKey, title, content, version, purposes: [...] }
```

#### Paso 6 — Ver estado completo del titular (GET /consent/subject/{subjectId})

```bash
# Portal de preferencias: todas las finalidades del titular en el dominio
curl -s "http://localhost:8081/consent/subject/test-user-001" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool

# Respuesta: lista de agreements con estado (accepted, status) por cada purpose
```

#### Paso 7 — Revocar una finalidad específica (POST /consent/revoke-purpose)

```bash
# Revocación granular: solo una finalidad, las demás siguen activas
# Internamente hace re-consent (nuevo agreement) para preservar el hash SHA-256
curl -s -X POST http://localhost:8081/consent/revoke-purpose \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"subjectId\": \"test-user-001\",
    \"purposeId\": \"$PURPOSE_ID\",
    \"templateKey\": \"MI_TEMPLATE\"
  }" | python3 -m json.tool
```

#### Paso 8 — Revocar todo el consentimiento (POST /consent/revoke)

```bash
AGREEMENT_ID="<agreementId-del-paso-3>"

curl -s -X POST http://localhost:8081/consent/revoke \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"subjectId\": \"test-user-001\",
    \"agreementId\": \"$AGREEMENT_ID\"
  }" | python3 -m json.tool
```

#### Paso 9 — Consultar datos pendientes de eliminación (GET /consent/pending-deletions)

```bash
# Lista finalidades vencidas cuyos datos aún no fueron eliminados
curl -s "http://localhost:8081/consent/pending-deletions" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool

# Respuesta: [ { subjectIdentifier, purposeId, purposeCode, expiredAt, anonymizeAfter }, ... ]
```

#### Paso 10 — Confirmar eliminación de datos (POST /consent/confirm-deletion)

```bash
# El CRM confirma que eliminó los datos — queda registrado en el audit log
curl -s -X POST http://localhost:8081/consent/confirm-deletion \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"subjectId\": \"test-user-001\",
    \"purposeId\": \"$PURPOSE_ID\",
    \"deletedAt\": \"$(date -u +%Y-%m-%dT%H:%M:%S)\"
  }"
# Respuesta: 204 No Content
```

---

## 6. Flujo end-to-end completo

Para hacer una prueba de punta a punta desde cero (template nuevo → consentimiento → revocación), seguir estos pasos. Todos los requests al backend se hacen con token de DPO o ADMIN.

```bash
# Token de DPO (para gestionar templates y documentos)
DPO_TOKEN=$(curl -s http://localhost:8180/realms/leydata/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=leydata-frontend&username=dpo@leydata.cl&password=Test1234!' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

DOMAIN_ID="<uuid-dominio>"
PURPOSE_ID="<uuid-finalidad-existente>"
```

**1. Crear template:**
```bash
TEMPLATE=$(curl -s -X POST http://localhost:8080/api/templates \
  -H "Authorization: Bearer $DPO_TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"domainId\":\"$DOMAIN_ID\",\"templateKey\":\"MI_TEMPLATE\",\"name\":\"Mi Template\",\"description\":\"Test\",\"purposeIds\":[]}")
TEMPLATE_ID=$(echo $TEMPLATE | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
```

**2. Vincular finalidad, aprobar y activar:**
```bash
curl -s -X POST "http://localhost:8080/api/templates/$TEMPLATE_ID/purposes" \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"purposeId\":\"$PURPOSE_ID\",\"visible\":true,\"orderPosition\":1}"

curl -s -X POST "http://localhost:8080/api/templates/$TEMPLATE_ID/approve" -H "Authorization: Bearer $DPO_TOKEN" > /dev/null
curl -s -X POST "http://localhost:8080/api/templates/$TEMPLATE_ID/activate" \
  -H "Authorization: Bearer $DPO_TOKEN" | python3 -c "import sys,json; print('Template status:', json.load(sys.stdin)['status'])"
```

**3. Crear y publicar documento de privacidad:**
```bash
DOC=$(curl -s -X POST http://localhost:8080/api/privacy-documents \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Aviso de privacidad\",\"category\":\"POLITICA_PRIVACIDAD\",\"content\":\"...\",\"templateId\":\"$TEMPLATE_ID\"}")
DOC_ID=$(echo $DOC | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/purposes/$PURPOSE_ID" -H "Authorization: Bearer $DPO_TOKEN" > /dev/null
curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/submit" -H "Authorization: Bearer $DPO_TOKEN" > /dev/null
curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/approve" -H "Authorization: Bearer $DPO_TOKEN" > /dev/null
curl -s -X POST "http://localhost:8080/api/privacy-documents/$DOC_ID/publish" \
  -H "Authorization: Bearer $DPO_TOKEN" | python3 -c "import sys,json; print('Doc status:', json.load(sys.stdin)['status'])"
```

**4. Verificar que resolve ya devuelve el documentId:**
```bash
curl -s "http://localhost:8080/api/templates/resolve?domainId=$DOMAIN_ID&templateKey=MI_TEMPLATE" \
  -H "Authorization: Bearer $DPO_TOKEN" | python3 -m json.tool
# documentId ya no debe ser null
```

**5. Capturar desde el Orquestador:**
```bash
EXTERNAL_TOKEN=$(...)  # ver paso 1 de la sección 5
curl -s -X POST http://localhost:8081/consent/capture \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"subjectId\":\"test-001\",\"templateKey\":\"MI_TEMPLATE\",\"purposes\":[{\"purposeId\":\"$PURPOSE_ID\",\"accepted\":true}]}" \
  | python3 -m json.tool
```

---

## 7. Flujo de ciclo de vida

El ciclo de vida del consentimiento se evalúa **de forma lazy** en cada CHECK cuando hay cache miss en Redis. No hay batch jobs. Los cuatro estados posibles son:

| Estado | Qué significa | Acción esperada del CRM |
|---|---|---|
| `ALLOWED` | Consentimiento activo y vigente | Permitir acceso a los datos |
| `EXPIRED` | Vencido según política de retención | Bloquear acceso, iniciar flujo de eliminación |
| `REQUIRES_RECONSENT` | El DPO activó nueva versión del template con `forceReconsent=true` | Mostrar nuevo formulario al titular antes del próximo acceso |
| `PENDING` | No existe consentimiento registrado | Mostrar formulario inicial de captura |

### Probar el estado REQUIRES_RECONSENT

```bash
# 1. Crear una nueva versión del template con forceReconsent=true
NEW_TEMPLATE=$(curl -s -X POST "http://localhost:8080/api/templates/$TEMPLATE_ID/new-version" \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d '{}')
NEW_TEMPLATE_ID=$(echo $NEW_TEMPLATE | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

# Aprobar y activar la nueva versión con forceReconsent=true
curl -s -X POST "http://localhost:8080/api/templates/$NEW_TEMPLATE_ID/approve" \
  -H "Authorization: Bearer $DPO_TOKEN" > /dev/null

curl -s -X POST "http://localhost:8080/api/templates/$NEW_TEMPLATE_ID/activate" \
  -H "Authorization: Bearer $DPO_TOKEN" -H 'Content-Type: application/json' \
  -d '{"forceReconsent": true}' | python3 -c "import sys,json; d=json.load(sys.stdin); print('version:', d['version'], '| forceReconsent:', d['forceReconsent'])"

# 2. Invalidar la caché del agreement anterior en Redis (TTL caduca sola en 15 min, o forzar)
docker exec leydata-redis redis-cli DEL "consent:test-user-001:$PURPOSE_ID"

# 3. El próximo CHECK debe devolver REQUIRES_RECONSENT
curl -s "http://localhost:8081/consent/check?subjectId=test-user-001&purposeId=$PURPOSE_ID" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
# { "status": "REQUIRES_RECONSENT", ... }
```

### Probar el estado EXPIRED

El estado EXPIRED depende de que la finalidad tenga una política de retención configurada (`DataRetentionPolicies`). La lógica calcula `expiresAt = fechaCaptura + retentionPeriod` en el momento de crear el agreement.

Para probar sin esperar que venza naturalmente, se puede modificar directamente en la BD:

```bash
# Conectar a la base de datos
psql -U admin -d leydata_db -h localhost -p 5433

-- Forzar expiración de un agreement_purpose para pruebas
UPDATE agreements_purposes
  SET expires_at = NOW() - INTERVAL '1 day'
  WHERE agreement_id = '<uuid-agreement>' AND purpose_id = '<uuid-purpose>';
```

Luego:
```bash
# Invalidar caché y verificar
docker exec leydata-redis redis-cli DEL "consent:test-user-001:$PURPOSE_ID"

curl -s "http://localhost:8081/consent/check?subjectId=test-user-001&purposeId=$PURPOSE_ID" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
# { "status": "EXPIRED", ... }

# Consultar datos pendientes de eliminación
curl -s "http://localhost:8081/consent/pending-deletions" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool

# Confirmar eliminación
curl -s -X POST http://localhost:8081/consent/confirm-deletion \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" -H 'Content-Type: application/json' \
  -d "{\"subjectId\":\"test-user-001\",\"purposeId\":\"$PURPOSE_ID\",\"deletedAt\":\"$(date -u +%Y-%m-%dT%H:%M:%S)\"}"
```

---

## 8. Troubleshooting

### Orquestador devuelve `422 Unprocessable Entity` en `/consent/capture`

**Causa A:** El JWT no tiene el claim `leydata_domain`.
```
"El JWT del sistema cliente no incluye el claim 'leydata_domain'..."
```
→ Verificar que corriste el paso 3.5 (protocol mapper en `crm-sistema`). El token debe renovarse después de agregar el mapper (el token viejo no tendrá el claim).

**Causa B:** No existe una versión `ACTIVE` del `templateKey` en el dominio del JWT.
```
"No hay una versión activa para el template X en este dominio"
```
→ Verificar que el template existe, está `ACTIVE`, y que el `domainId` del JWT coincide con el del template.

**Causa C:** El template existe y está activo, pero no tiene un documento `PUBLISHED` asociado.
```
"El template X no tiene un documento publicado asociado"
```
→ Crear y publicar un documento con `templateId` apuntando a ese template (seguir el paso 3 del flujo end-to-end).

---

### El CHECK devuelve `REQUIRES_RECONSENT` inesperadamente

El DPO activó una nueva versión del template con `forceReconsent=true`. El CRM debe mostrar el formulario de consentimiento actualizado al titular antes de permitir el acceso.

```bash
# Ver la versión actual del template activo
curl -s "http://localhost:8080/api/templates/active/MI_TEMPLATE" \
  -H "Authorization: Bearer $DPO_TOKEN" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('version:', d['version'], '| forceReconsent:', d['forceReconsent'])"

# Ver la versión del agreement del titular
curl -s "http://localhost:8080/api/agreements/lifecycle-check?subjectIdentifier=test-user-001&domainId=$DOMAIN_ID&templateKey=MI_TEMPLATE" \
  -H "Authorization: Bearer $DPO_TOKEN" | python3 -m json.tool
# agreementTemplateVersion < currentTemplateVersion → confirma REQUIRES_RECONSENT
```

La solución es que el titular firme el nuevo consentimiento vía `POST /consent/capture`.

---

### El CHECK devuelve `EXPIRED` aunque el titular consintió recientemente

El acuerdo tiene `expiresAt` en el pasado. Esto ocurre cuando la finalidad tiene una política de retención corta en `DataRetentionPolicies`.

```bash
# Ver cuándo vence la finalidad en el acuerdo
psql -U admin -d leydata_db -h localhost -p 5433 \
  -c "SELECT ap.expires_at, p.name, p.code FROM agreements_purposes ap
      JOIN purposes p ON p.id = ap.purpose_id
      WHERE ap.expires_at IS NOT NULL AND ap.expires_at < NOW()
      ORDER BY ap.expires_at DESC LIMIT 5;"
```

Si la retención es demasiado corta para las pruebas, actualizar la política en `data_retention_policies` y crear un nuevo agreement. El cálculo de `expiresAt` ocurre al crear el agreement, no retroactivamente.

---

### `POST /consent/revoke-purpose` devuelve 400

El titular no tiene ningún agreement ACTIVE con el `templateKey` indicado. Verificar con:

```bash
curl -s "http://localhost:8081/consent/subject/test-user-001" \
  -H "Authorization: Bearer $EXTERNAL_TOKEN" | python3 -m json.tool
```

Si la lista está vacía, el titular no tiene consentimiento activo — no hay nada que revocar granularmente.

---

### `Connection refused` al levantar el Orquestador

El Orquestador no puede conectarse al backend. Causa más común en WSL: la IP cambió al reiniciar.

```bash
# 1. ¿El backend está corriendo?
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/domains/all
# Debe responder 401, no "connection refused"

# 2. Ver qué URL tiene configurada el contenedor ahora mismo:
docker exec leydata-orchestrator env | grep LEYDATA_BACKEND_URL

# 3. Si la IP no coincide con la de WSL, actualizarla y recrear el contenedor:
WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1) && \
  sed -i "s|LEYDATA_BACKEND_URL:.*|LEYDATA_BACKEND_URL: http://$WSL_IP:8080|" docker-compose.override.yml && \
  echo "IP actualizada: $WSL_IP" && \
  docker-compose up -d --force-recreate orchestrator
```

---

### El backend responde `500` al crear usuarios

La causa más común es que el `KC_BACKEND_SECRET` en el `.env` es incorrecto o quedó desactualizado.

```bash
# Obtener el secret actual de Keycloak:
MASTER=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

CLIENT_UUID=$(curl -s "http://localhost:8180/admin/realms/leydata/clients?clientId=leydata-backend" \
  -H "Authorization: Bearer $MASTER" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

curl -s "http://localhost:8180/admin/realms/leydata/clients/$CLIENT_UUID/client-secret" \
  -H "Authorization: Bearer $MASTER" | python3 -c "import sys,json; print(json.load(sys.stdin)['value'])"
```

Actualizar `KC_BACKEND_SECRET` en el `.env` con ese valor y reiniciar el backend.

---

### El token de admin `admin@leydata.cl` no funciona

```bash
# Resetear la contraseña via Keycloak admin API:
MASTER=$(curl -s http://localhost:8180/realms/master/protocol/openid-connect/token \
  -d 'grant_type=password&client_id=admin-cli&username=admin&password=admin' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

USER_ID=$(curl -s "http://localhost:8180/admin/realms/leydata/users?search=admin@leydata.cl" \
  -H "Authorization: Bearer $MASTER" | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['id'])")

curl -s -X PUT "http://localhost:8180/admin/realms/leydata/users/$USER_ID/reset-password" \
  -H "Authorization: Bearer $MASTER" -H 'Content-Type: application/json' \
  -d '{"type":"password","temporary":false,"value":"Admin1234!"}'
echo "Contraseña reseteada a Admin1234!"
```

---

### Reiniciar desde cero (borrar todos los datos)

```bash
# ⚠️ Esto borra la base de datos y la configuración de Keycloak
docker-compose down -v

# Volver a levantar desde el paso 3.1
docker-compose up -d db db-replica pgbouncer keycloak-db keycloak redis
```

Después de esto hay que volver a correr `setup-keycloak.sh`, `setup-empresa-cliente-realm.sh`, y recrear el cliente del orquestador (paso 3.3).

---

## Resumen de credenciales por defecto (entorno local)

| Servicio | Usuario | Contraseña | Notas |
|---|---|---|---|
| Keycloak admin | `admin` | `admin` | Solo para `realms/master` |
| LeyData admin | `admin@leydata.cl` | `Admin1234!` | realm `leydata`, client `leydata-frontend` |
| LeyData DPO | `dpo@leydata.cl` | `Test1234!` | realm `leydata`, client `leydata-frontend` |
| LeyData Jefe de dominio | `jefe@test.cl` | `Test1234!` | realm `leydata`, client `leydata-frontend` |
| CRM externo (pruebas) | `operador@empresa.cl` | `operador123` | realm `empresa-cliente`, client `crm-sistema` |
| PostgreSQL | `admin` | `admin` | host `localhost`, puerto `5433` |
