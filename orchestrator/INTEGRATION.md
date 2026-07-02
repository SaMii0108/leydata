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
| **Spring Cloud Gateway** | Proxy transparente hacia el backend LeyData para rutas `/api/**` |
| **Redis** | Caché de estado de consentimiento — respuestas en milisegundos sin tocar la base de datos |
| **Spring Security WebFlux** | Validación de JWT inbound (sistema cliente) y emisión de token outbound (M2M hacia LeyData) |

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

**Cómo funciona:**

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

**Claim obligatorio `leydata_domain`:** el JWT del sistema cliente debe incluir un claim `leydata_domain` con el UUID del dominio LeyData al que ese sistema está autorizado (ej. el dominio "Comercial" si el CRM es de ventas). LeyData lo configura al dar de alta la integración — el sistema cliente no lo elige ni lo declara en el body de sus requests, viene firmado en el token. `POST /consent/capture` lo usa para resolver a qué template/documento corresponde un `templateKey` dentro de ese dominio.

### Outbound — El Orquestador habla con LeyData

Cuando el Orquestador necesita consultar o escribir en LeyData (por cache miss, capture o revoke), obtiene automáticamente un token de servicio de Keycloak usando el flujo `client_credentials`.

**Cómo funciona:**

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
| `EXTERNAL_JWKS_URI` | URL del JWKS del IdP del sistema cliente. El Orquestador valida todos los JWT inbound contra este endpoint. | `https://idp.empresa-cliente.cl/realms/produccion/protocol/openid-connect/certs` |
| `KC_ORCHESTRATOR_CLIENT_SECRET` | Secret del cliente `leydata-orchestrator` en Keycloak realm `leydata`. Usado para el flujo M2M outbound. | `s3cr3t-generado-en-keycloak` |
| `LEYDATA_BACKEND_URL` | URL interna del backend LeyData. En producción apunta al nombre del contenedor. | `http://backend:8080` |
| `KC_TOKEN_URI` | URL del endpoint de token de Keycloak para el flujo M2M. | `http://keycloak:8080/realms/leydata/protocol/openid-connect/token` |
| `REDIS_HOST` | Host de Redis. | `redis` |

### Opcionales

| Variable | Default | Descripción |
|---|---|---|
| `REDIS_PORT` | `6379` | Puerto de Redis |
| `KC_ORCHESTRATOR_CLIENT_ID` | `leydata-orchestrator` | Client ID del Orquestador en Keycloak |

> **⚠️ `LEYDATA_CONSENT_CACHE_TTL_SECONDS` y `LEYDATA_CONSENT_CACHE_SOFT_TTL_SECONDS` no son configurables actualmente.** `application.yml` hardcodea `cache-ttl-seconds: 300` y `cache-soft-ttl-seconds: 240` sin leer ninguna variable de entorno — el TTL real es siempre 300s. Además, el "refresco anticipado" (soft TTL) descrito abajo **no está implementado**: la propiedad se lee pero `ConsentService` nunca la usa. Es un bug de código pendiente de resolución, no una opción de configuración disponible hoy.

### Entorno de desarrollo local

Para desarrollo local, el archivo `docker-compose.override.yml` ya configura estas variables apuntando a los servicios locales. El backend puede correr desde el IDE — el Orquestador lo alcanza via `host.docker.internal`.

```bash
# Una sola vez: crear el realm de prueba que simula el IdP externo
bash scripts/setup-empresa-cliente-realm.sh

# Levantar el Orquestador
KC_ORCHESTRATOR_CLIENT_SECRET=<secret> docker-compose up -d orchestrator
```

---

## Contratos de API

### Autenticación

Todos los endpoints requieren:
```
Authorization: Bearer <JWT firmado por el IdP externo configurado en EXTERNAL_JWKS_URI>
```

---

### `GET /consent/check`

Consulta el estado de consentimiento de un titular para un propósito específico. Responde desde Redis en milisegundos. Solo consulta LeyData si el estado no está en caché.

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
  "status": "ALLOWED"
}
```

**Estados posibles:**

| Status | Significado | Acción recomendada |
|---|---|---|
| `ALLOWED` | El titular dio su consentimiento y está vigente | Procesar el dato |
| `DENIED` | El consentimiento expiró | No procesar el dato |
| `REVOKED` | El titular revocó explícitamente | No procesar el dato, notificar si aplica |
| `PENDING` | No hay historial de consentimiento | Mostrar la pantalla de consentimiento al titular |

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

**Response `201 Created`:**
```json
{
  "subjectId": "u-8f3a2c",
  "agreementId": "d4e5f6a7-b8c9-0123-def4-567890abcdef",
  "status": "ALLOWED"
}
```

**Notas:**
- `templateKey` es el identificador de **negocio** acordado al integrarse (ej. `ONBOARDING_CLIENTE`), no un UUID interno de LeyData. El sistema cliente nunca necesita conocer `templateId` ni `documentId` directamente.
- El Orquestador resuelve `templateKey` contra el **dominio** que el JWT del sistema cliente autoriza (claim `leydata_domain`, configurado por LeyData al dar de alta la integración) y obtiene el `documentId` del documento de privacidad `PUBLISHED` vigente de ese template. Si el template no tiene versión `ACTIVE` en ese dominio, o no tiene un documento publicado, la petición falla antes de llegar a LeyData.
- `documentId` puede enviarse opcionalmente como override explícito si la integración ya lo conoce — no es necesario en el flujo estándar.
- Si el titular ya tenía un consentimiento `ACTIVE` para el mismo template, se revoca automáticamente y se crea uno nuevo (reconsent)
- La IP real del dispositivo del titular queda sellada en el hash SHA-256 del registro — no puede ser alterada posteriormente

---

### `POST /consent/revoke`

Revoca un acuerdo de consentimiento existente. La revocación es inmediata: Redis se actualiza antes de responder al cliente, garantizando que cualquier consulta posterior devuelva `REVOKED` al instante.

**Request:**
```
POST /consent/revoke
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "subjectId": "u-8f3a2c",
  "agreementId": "d4e5f6a7-b8c9-0123-def4-567890abcdef"
}
```

**Response `200 OK`:**
```json
{
  "subjectId": "u-8f3a2c",
  "agreementId": "d4e5f6a7-b8c9-0123-def4-567890abcdef",
  "status": "REVOKED"
}
```

**Garantía de consistencia:** el Orquestador sigue el patrón LeyData-primero. Primero persiste la revocación en el registro inmutable de LeyData y, solo si recibe confirmación, actualiza Redis. Si LeyData falla, el cliente recibe un error y puede reintentar — el estado nunca queda inconsistente.

---

### `GET /consent/template-content`

Obtiene los datos del template activo (incluyendo el `documentId` del documento de privacidad publicado) para un `templateKey`, resuelto contra el dominio del JWT.

**Request:**
```
GET /consent/template-content?templateKey=ONBOARDING_CLIENTE
Authorization: Bearer <token>
```

**Response `200 OK`:**
```json
{
  "templateId": "uuid",
  "domainId": "uuid",
  "templateKey": "ONBOARDING_CLIENTE",
  "version": 2,
  "name": null,
  "title": null,
  "description": null,
  "documentId": "uuid",
  "purposes": [
    {
      "purposeId": "uuid",
      "purposeCode": "MARKETING",
      "purposeName": "Envío de comunicaciones comerciales",
      "purposeDescription": "...",
      "purposeShortDescription": "...",
      "required": true,
      "revocable": true,
      "legalBasisCode": "ART_12_CONSENTIMIENTO"
    }
  ]
}
```

**⚠️ Importante:** `name`, `title` y `description` siempre vienen `null` — es un bug de código pendiente (el servicio los construye como `null` explícito). Este endpoint **no** trae el texto legal a mostrar al titular pese a su propósito declarado. Hoy solo es útil para obtener `documentId` y la lista de `purposes`.

---

### `GET /consent/subject/{subjectId}`

Devuelve el estado de todas las finalidades consentidas por un titular en el dominio del sistema cliente. Pensado para un portal de preferencias.

**Request:**
```
GET /consent/subject/u-8f3a2c
Authorization: Bearer <token>
```

**Response `200 OK`:**
```json
[
  {
    "subjectIdentifier": "u-8f3a2c",
    "domainId": "uuid",
    "agreementId": "uuid",
    "templateId": "uuid",
    "templateKey": "ONBOARDING_CLIENTE",
    "templateVersion": 2,
    "documentId": "uuid",
    "purposes": [
      {
        "purposeId": "uuid", "purposeCode": "MARKETING", "purposeName": "Marketing",
        "accepted": true, "status": "ACTIVE",
        "expiresAt": "2026-12-31T23:59:59", "acceptedAt": "2026-01-15T10:00:00",
        "required": false, "revocable": true
      }
    ]
  }
]
```

---

### `POST /consent/revoke-purpose`

Revoca una finalidad individual sin afectar las demás del mismo agreement. Internamente hace re-consentimiento (nuevo agreement) en vez de modificar el existente, para no romper el hash SHA-256 de integridad.

**Request:**
```
POST /consent/revoke-purpose
Authorization: Bearer <token>
Content-Type: application/json
```
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
  "subjectId": "u-8f3a2c",
  "agreementId": "uuid-del-nuevo-agreement",
  "status": "REVOKED"
}
```

Falla con `400` si la purpose no es revocable, es requerida, o no hay ningún agreement activo del titular con ese `templateKey`/`purposeId`.

---

### `GET /consent/pending-deletions`

Lista las finalidades vencidas cuyos datos asociados aún no han sido eliminados por el sistema cliente, en el dominio del JWT.

**Request:**
```
GET /consent/pending-deletions
Authorization: Bearer <token>
```

**Response `200 OK`:**
```json
[
  {
    "subjectIdentifier": "u-8f3a2c",
    "purposeId": "uuid",
    "purposeCode": "MARKETING",
    "purposeName": "Envío de comunicaciones comerciales",
    "agreementId": "uuid",
    "expiredAt": "2026-01-15T00:00:00",
    "anonymizeAfter": false
  }
]
```

---

### `POST /consent/confirm-deletion`

El sistema cliente confirma que eliminó o anonimizó los datos del titular para una finalidad vencida. Queda registrado en el `system_audit_log` del backend como evidencia de cumplimiento.

**Request:**
```
POST /consent/confirm-deletion
Authorization: Bearer <token>
Content-Type: application/json
```
```json
{
  "subjectId": "u-8f3a2c",
  "purposeId": "550e8400-e29b-41d4-a716-446655440000",
  "deletedAt": "2026-06-30T10:00:00"
}
```

**Response:** `204 No Content`

---

### `GET /actuator/health`

Endpoint de salud. No requiere autenticación.

```json
{ "status": "UP" }
```

---

### Rutas proxy `/api/**`

Todo el tráfico hacia `/api/**` se reenvía de forma transparente al backend LeyData. El Orquestador inyecta automáticamente el token M2M en estas peticiones. Los sistemas cliente pueden usar estas rutas para consultas administrativas que no pasan por la lógica de consentimiento.

---

## Preparación del entorno (Checklist DevOps)

### En Keycloak — realm `leydata` (una sola vez)

- [ ] Crear cliente `leydata-orchestrator`
  - Client authentication: **ON** (confidential)
  - Authentication flow: **Service accounts roles** únicamente
  - Copiar el Client Secret generado → variable `KC_ORCHESTRATOR_CLIENT_SECRET`
- [ ] Asignar rol `SYSTEM` al service account del cliente (o el rol que LeyData exija)

### Por cada sistema cliente nuevo (CRM, ERP) que se integra

- [ ] En el IdP del cliente: agregar protocol mapper que incluya el claim `leydata_domain` en los tokens emitidos para ese client, con el UUID del dominio LeyData correspondiente
- [ ] Confirmar con LeyData el `templateKey` de negocio que ese sistema va a usar en `POST /consent/capture` — debe existir como template `ACTIVE` en ese dominio

### En el servidor de producción

- [ ] Configurar todas las variables de entorno requeridas
- [ ] Verificar que `EXTERNAL_JWKS_URI` es alcanzable desde el contenedor del Orquestador
- [ ] Verificar que el Orquestador puede alcanzar `LEYDATA_BACKEND_URL` en red interna
- [ ] Confirmar que el puerto `8081` es el único expuesto al exterior
- [ ] Confirmar que el puerto `8080` de LeyData **no** tiene mapping externo en `docker-compose.yml`

### Validación post-deploy

```bash
# Health check
curl http://localhost:8081/actuator/health

# Verificar que LeyData no es alcanzable desde fuera
curl http://localhost:8080/actuator/health  # debe fallar o no responder

# Verificar que el JWKS externo es accesible desde el contenedor
docker exec leydata-orchestrator wget -qO- $EXTERNAL_JWKS_URI
```
