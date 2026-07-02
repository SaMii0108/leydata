# LeyData Orchestrator — Guía de Integración

Motor de consentimiento conforme a la **Ley 21.719** de protección de datos personales de Chile.

---

## Arquitectura y Filosofía

### Privacidad por Diseño — La Bóveda Ciega

El Orquestador implementa el principio de **Pseudonimización** como primera línea de defensa.

> **El sistema nunca almacena ni procesa datos personales identificables (PII).**

El identificador del titular (`subjectId`) es tratado como un **string opaco**. Puede ser un UUID generado por el sistema del cliente, un hash de un RUT, o cualquier identificador interno — el Orquestador no lo interpreta ni lo enriquece. Solo lo asocia a decisiones de consentimiento.

```
Sistema cliente → subjectId: "u-8f3a2c"  ✅
Sistema cliente → subjectId: "12.345.678-9" (si el cliente lo envía así, se trata como string) ✅
Sistema cliente → nombre: "Juan Pérez"  ❌ — el Orquestador no acepta ni guarda esto
```

Esta arquitectura garantiza que, ante una brecha de seguridad, el Orquestador no expone ningún dato personal — solo pseudónimos sin valor fuera del contexto del cliente.

---

## Tecnología Base

| Componente | Rol |
|---|---|
| **Spring WebFlux + Netty** | Motor reactivo no bloqueante — soporta miles de conexiones concurrentes sin aumentar threads |
| **Spring Security WebFlux** | Validación de JWT inbound (sistema cliente) y emisión de token outbound (M2M hacia LeyData) |
| **Redis** | Caché de estado de consentimiento — respuestas en milisegundos sin tocar la base de datos |

El Orquestador es el **único servicio expuesto al exterior**. El backend LeyData corre en red interna y no tiene puertos accesibles desde fuera.

```
Internet
    │
    ▼
[Orquestador :8081]  ←→  [Redis]
    │
    │  red interna Docker
    ▼
[LeyData :8080]  ←→  [Keycloak]  ←→  [PostgreSQL]
```

---

## Flujo de Seguridad — Doble Personalidad

El Orquestador opera con dos motores de autenticación completamente independientes que nunca se mezclan.

### Inbound — El cliente nos habla

Los sistemas externos (CRM, ERP, aplicación del cliente) deben enviar un **JWT firmado por su propio sistema de identidad** en cada request.

```
1. El sistema del cliente autentica a su usuario con su IdP propio
2. Obtiene un JWT firmado por ese IdP
3. Envía ese JWT en el header: Authorization: Bearer <token>
4. El Orquestador valida la firma contra el JWKS del IdP del cliente
   (configurado en EXTERNAL_JWKS_URI)
5. Si el token es válido → procesa la petición
6. Si el token es inválido o expiró → 401 Unauthorized
```

**El JWKS del cliente** es una URL pública que expone las claves públicas de su IdP. El Orquestador la consulta automáticamente para verificar firmas criptográficas sin compartir secretos.

**Claim obligatorio `leydata_domain`:** el JWT del sistema cliente debe incluir un claim `leydata_domain` con el UUID del dominio LeyData al que ese sistema está autorizado. LeyData lo configura al dar de alta la integración usando un _protocol mapper_ en Keycloak (tipo `oidc-hardcoded-claim-mapper`). El claim viene firmado en el token — el sistema cliente no lo declara en el body. `POST /consent/capture` lo usa para resolver a qué template/documento corresponde un `templateKey` dentro de ese dominio. Si el claim falta, el endpoint responde `500`.

### Outbound — El Orquestador habla con LeyData

Cuando el Orquestador necesita consultar o escribir en LeyData (por cache miss, capture o revoke), obtiene automáticamente un token de servicio de Keycloak usando el flujo `client_credentials`.

```
1. El Orquestador detecta que necesita llamar a LeyData
2. Consulta su caché de tokens — si tiene uno vigente, lo reutiliza
3. Si no tiene token → POST a Keycloak /token con client_id + client_secret
4. Keycloak devuelve un Bearer token con rol SYSTEM
5. El Orquestador inyecta ese token automáticamente en el WebClient
6. LeyData recibe la petición con un token válido y la procesa
```

Este flujo ocurre en segundo plano. El sistema cliente nunca ve ni tiene acceso al token interno de Keycloak.

---

## Variables de Entorno

### Requeridas en producción

| Variable | Descripción | Ejemplo |
|---|---|---|
| `EXTERNAL_JWKS_URI` | URL del JWKS del IdP del sistema cliente. El Orquestador valida todos los JWT inbound contra este endpoint. | `https://idp.empresa-cliente.cl/realms/prod/protocol/openid-connect/certs` |
| `KC_ORCHESTRATOR_CLIENT_SECRET` | Secret del cliente `leydata-orchestrator` en Keycloak realm `leydata`. Usado para el flujo M2M outbound. Generado por `setup-keycloak.sh`. | `s3cr3t-generado-en-keycloak` |
| `LEYDATA_BACKEND_URL` | URL interna del backend LeyData. En producción apunta al nombre del contenedor. | `http://backend:8080` |
| `KC_TOKEN_URI` | URL del endpoint de token de Keycloak para el flujo M2M. | `http://keycloak:8080/realms/leydata/protocol/openid-connect/token` |
| `REDIS_HOST` | Host de Redis. | `redis` |

### Opcionales

| Variable | Default | Descripción |
|---|---|---|
| `REDIS_PORT` | `6379` | Puerto de Redis |
| `KC_ORCHESTRATOR_CLIENT_ID` | `leydata-orchestrator` | Client ID del Orquestador en Keycloak |
| `LEYDATA_CONSENT_CACHE_TTL_SECONDS` | `300` | TTL duro del estado en Redis (segundos) |
| `LEYDATA_CONSENT_CACHE_SOFT_TTL_SECONDS` | `240` | TTL suave — inicia refresco anticipado |

### Entorno de desarrollo local

Para desarrollo local, `docker-compose.override.yml` configura estas variables apuntando a los servicios locales.

> **WSL:** `host.docker.internal` en Docker Desktop + WSL2 resuelve a `192.168.65.254` (gateway de Docker Desktop), **no** a la VM de WSL donde corre el backend. Agregar la IP real de WSL al `.env` antes de levantar el orquestador:
> ```bash
> WSL_IP=$(ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d/ -f1)
> echo "LEYDATA_BACKEND_URL=http://$WSL_IP:8080" >> .env
> docker-compose up -d --force-recreate orchestrator
> ```
> La IP cambia con cada reinicio de Windows.

```bash
# 1. Crear realm que simula el IdP externo (una sola vez)
bash scripts/setup-empresa-cliente-realm.sh

# 2. Levantar el orquestador (lee KC_ORCHESTRATOR_CLIENT_SECRET del .env)
docker-compose up -d orchestrator
docker logs leydata-orchestrator -f   # listo cuando aparece "Started OrchestratorApplication"
```

---

## Contratos de API

### Autenticación

Todos los endpoints requieren:
```
Authorization: Bearer <JWT firmado por el IdP externo configurado en EXTERNAL_JWKS_URI>
```

El orquestador **no** valida tokens de Keycloak `leydata` — solo valida tokens del IdP del sistema cliente.

---

### `GET /consent/check`

Consulta el estado de consentimiento de un titular para un propósito específico. Responde desde Redis en milisegundos si hay cache hit. Solo consulta LeyData en cache miss y puebla Redis con el resultado.

**Request:**
```
GET /consent/check?subjectId=u-8f3a2c&purposeId=550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <token>
```

**Response `200 OK`:**
```json
{
  "subjectId": "u-8f3a2c",
  "purposeId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "ALLOWED",
  "legalBasisCode": "CONSENTIMIENTO_EXPLICITO",
  "validUntil": "2027-07-02T03:28:39"
}
```

**Estados posibles:**

| Status | Significado | Acción recomendada |
|---|---|---|
| `ALLOWED` | El titular dio su consentimiento y está vigente | Procesar el dato |
| `DENIED` | El consentimiento expiró | No procesar el dato |
| `REVOKED` | El titular revocó explícitamente | No procesar el dato, notificar si aplica |
| `PENDING` | No hay historial de consentimiento | Mostrar la pantalla de consentimiento al titular |

> **Cache Redis:** la clave es `consent:<subjectId>:<purposeId>`. Se puebla en `/consent/check` (no en `/consent/capture`). El TTL por defecto es 300 segundos. Revocar un consentimiento via API elimina la clave inmediatamente; un cambio directo en la BD **no** invalida el cache.

---

### `POST /consent/capture`

Registra la decisión de consentimiento del titular. El Orquestador captura automáticamente la IP real del titular y la sella en el registro inmutable de LeyData.

**Request:**
```
POST /consent/capture
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "subjectId": "u-8f3a2c",
  "templateKey": "ONBOARDING_CLIENTE",
  "purposes": [
    { "purposeId": "550e8400-e29b-41d4-a716-446655440000", "accepted": true },
    { "purposeId": "661f9511-f30c-52e5-b827-557766551111", "accepted": false }
  ]
}
```

> **Campo `accepted` es boolean, no string.** Mandar `"decision": "ACCEPTED"` guarda el consentimiento con `accepted=false`. El campo es `accepted: true/false`.

**Response `201 Created`:**
```json
{
  "agreementId": "d4e5f6a7-b8c9-0123-def4-567890abcdef",
  "status": "ALLOWED"
}
```

**Notas:**
- `templateKey` es el identificador de negocio acordado al integrarse, no un UUID. El orquestador lo resuelve contra el dominio del JWT (`leydata_domain`).
- El template debe tener un documento de privacidad `PUBLISHED` asociado. Sin documento publicado, la captura falla.
- Si el titular ya tenía un consentimiento `ACTIVE` para el mismo template, se revoca automáticamente y se crea uno nuevo (reconsent).
- `documentId` puede enviarse como override explícito — no es necesario en el flujo estándar.

---

### `POST /consent/revoke`

Revoca un acuerdo completo. Elimina la clave de Redis inmediatamente.

**Request:**
```json
{
  "subjectId": "u-8f3a2c",
  "agreementId": "d4e5f6a7-b8c9-0123-def4-567890abcdef"
}
```

**Response `200 OK`:**
```json
{
  "agreementId": "d4e5f6a7-b8c9-0123-def4-567890abcdef",
  "status": "REVOKED"
}
```

**Garantía de consistencia:** persiste la revocación en LeyData primero y solo si recibe confirmación actualiza Redis. Si LeyData falla, el cliente recibe error y puede reintentar — el estado nunca queda inconsistente.

---

### `POST /consent/revoke-purpose`

Revoca el consentimiento para una finalidad específica sin revocar el agreement completo. Útil cuando el titular quiere retirar solo un permiso de los varios que otorgó.

**Request:**
```json
{
  "subjectId": "u-8f3a2c",
  "purposeId": "550e8400-e29b-41d4-a716-446655440000",
  "templateKey": "ONBOARDING_CLIENTE"
}
```

**Response `200 OK`:**
```json
{
  "status": "REVOKED"
}
```

---

### `GET /consent/template-content`

Devuelve el contenido y los metadatos del template activo para un `templateKey` en el dominio del JWT. Útil para mostrar el texto del consentimiento al titular antes de capturar.

**Request:**
```
GET /consent/template-content?templateKey=ONBOARDING_CLIENTE
Authorization: Bearer <token>
```

---

### `GET /consent/subject/{subjectId}`

Devuelve el resumen completo de todos los consentimientos de un titular en el dominio del JWT: agreements activos, revocados, expirados y sus finalidades.

**Request:**
```
GET /consent/subject/u-8f3a2c
Authorization: Bearer <token>
```

---

### `GET /consent/pending-deletions`

Lista los titulares del dominio que tienen solicitudes de supresión pendientes (derecho al olvido, art. 16 Ley 21.719). Solo devuelve titulares del dominio autorizado por el JWT.

**Request:**
```
GET /consent/pending-deletions
Authorization: Bearer <token>
```

---

### `POST /consent/confirm-deletion`

Confirma que el sistema cliente ejecutó la supresión de los datos del titular. Cierra el ciclo del derecho al olvido en LeyData.

**Request:**
```json
{
  "subjectId": "u-8f3a2c",
  "purposeId": "550e8400-e29b-41d4-a716-446655440000",
  "deletedAt": "2026-07-02T10:30:00"
}
```

**Response `204 No Content`**

---

### `GET /actuator/health`

Endpoint de salud. No requiere autenticación.

```json
{ "status": "UP" }
```

---

## Integridad y Detección de Manipulación

### Qué pasa si alguien modifica la BD directamente

Cualquier `UPDATE` o `DELETE` directo en las tablas `agreements` o `agreements_purposes` (vía pgAdmin, psql, script SQL) es detectado automáticamente:

```
UPDATE directo en BD
    │
    ▼  (< 1 ms)
Trigger PostgreSQL V8 → db_tamper_log (processed=false)
    │
    ▼  (≤ 30 seg)
TamperDetectionScheduler → system_audit_log
    action=TAMPER_DIRECTO_BD
    actor_role=DIRECT_DB
    actor_id=<usuario postgres>
    │
    ▼  (≤ 1 min)
Grafana alerta "Mutación Directa en BD — Bypass de API (Ley 21.719)"
    severity=critical, compliance=ley21719
```

**Lo que NO hace el sistema automáticamente:** invalidar el cache de Redis. Si un agreement fue modificado directo en la BD, Redis puede devolver un estado obsoleto hasta que expire el TTL (default 300 seg). Para forzar la invalidación:
```bash
docker exec leydata-redis redis-cli del "consent:<subjectId>:<purposeId>"
```

### Por qué importa (Ley 21.719)

El sistema mantiene una **cadena de hashes SHA-256** en `system_audit_log` — cada entrada firma la anterior. Cualquier intento de borrar o modificar el historial de auditoría rompe la cadena y `verifyChainIntegrity()` lo detecta. La tabla `system_audit_log` tiene un trigger PostgreSQL que impide `UPDATE` y `DELETE`.

---

## Preparación del entorno (Checklist DevOps)

### En Keycloak — realm `leydata` (una sola vez)

El cliente `leydata-orchestrator` se crea automáticamente al ejecutar el script de configuración:

```bash
bash scripts/setup-keycloak.sh   # o .\scripts\setup-keycloak.ps1 en Windows
```

Al finalizar imprime `KC_ORCHESTRATOR_CLIENT_SECRET` — copiarlo al `.env`.

### Configurar el realm empresa-cliente (entorno de prueba)

```bash
# Solo el realm + usuario de prueba:
bash scripts/setup-empresa-cliente-realm.sh

# Con el claim leydata_domain ya configurado (necesario para /consent/capture):
bash scripts/setup-empresa-cliente-realm.sh --domain-id <uuid-del-dominio>

# PowerShell:
.\scripts\setup-empresa-cliente-realm.ps1 -DomainId "<uuid-del-dominio>"
```

Si no se pasa el domain-id, el script imprime las instrucciones para agregarlo después.

### Por cada sistema cliente nuevo (CRM, ERP) en producción

- [ ] En el IdP del cliente: agregar protocol mapper `oidc-hardcoded-claim-mapper` con `claim.name=leydata_domain` y `claim.value=<uuid-dominio-LeyData>`
- [ ] Confirmar con LeyData el `templateKey` que ese sistema va a usar — debe existir como template `ACTIVE` con documento `PUBLISHED` en ese dominio
- [ ] Actualizar `EXTERNAL_JWKS_URI` con la URL del JWKS real del IdP del cliente

### En el servidor de producción

- [ ] Configurar todas las variables de entorno requeridas en el `.env`
- [ ] Verificar que `EXTERNAL_JWKS_URI` es alcanzable desde el contenedor del Orquestador
- [ ] Verificar que el Orquestador alcanza `LEYDATA_BACKEND_URL` en red interna
- [ ] Confirmar que solo el puerto `8081` está expuesto al exterior
- [ ] Confirmar que el puerto `8080` de LeyData **no** tiene mapping externo

### Validación post-deploy

```bash
# Health check del orquestador
curl http://localhost:8081/actuator/health

# Verificar que LeyData no es alcanzable desde fuera
curl http://localhost:8080/actuator/health  # debe fallar o no responder

# Verificar que el JWKS externo es accesible desde el contenedor
docker exec leydata-orchestrator wget -qO- $EXTERNAL_JWKS_URI

# Obtener token del sistema externo y probar /consent/check
TOKEN=$(curl -s -X POST "$EXTERNAL_TOKEN_URL" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials&client_id=$CLIENT_ID&client_secret=$CLIENT_SECRET" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

curl -s "http://localhost:8081/consent/check?subjectId=test&purposeId=<uuid>" \
  -H "Authorization: Bearer $TOKEN"
# Esperado: {"status":"PENDING"} o {"status":"ALLOWED"}

# Verificar detección de tamper (solo en staging/dev)
docker exec leydata-consent-db psql -U admin -d leydata_db \
  -c "SELECT action, actor_role, created_at FROM system_audit_log WHERE action='TAMPER_DIRECTO_BD' LIMIT 3;"
```

---

## Errores comunes

| Error | Causa | Fix |
|---|---|---|
| `401 Unauthorized` | JWT inválido o expirado | Renovar el token del IdP externo |
| `500 "leydata_domain" claim faltante` | Protocol mapper no configurado en Keycloak | Ejecutar `setup-empresa-cliente-realm.sh --domain-id <uuid>` |
| `Connection refused` al backend (en WSL) | `host.docker.internal` resuelve mal en WSL2 | `echo "LEYDATA_BACKEND_URL=http://<wsl-ip>:8080" >> .env` + recrear orquestador |
| `/consent/capture` guarda `accepted=false` | Se mandó `"decision":"ACCEPTED"` en vez de `"accepted":true` | Usar el campo boolean `accepted` |
| Cache devuelve estado obsoleto tras cambio directo en BD | Redis no fue invalidado | `redis-cli del "consent:<subjectId>:<purposeId>"` |
| Grafana alerta `Mutación Directa en BD` | Alguien modificó `agreements` sin pasar por la API | Revisar `SELECT * FROM db_tamper_log ORDER BY detected_at DESC` |
