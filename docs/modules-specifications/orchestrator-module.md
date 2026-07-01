# Módulo: Orquestador — Vista Interna para Desarrolladores

> Para la guía de integración externa (cómo conectar un sistema cliente), ver [`orchestrator/INTEGRATION.md`](../orchestrator/INTEGRATION.md).  
> Este documento describe la implementación interna del Orquestador para quien trabaja en el código.

---

## Stack técnico

| Componente | Uso |
|---|---|
| **Spring WebFlux + Netty** | Runtime reactivo no bloqueante — sin Tomcat |
| **Spring Cloud Gateway** | Proxy transparente para rutas `/api/**` hacia el backend |
| **Spring Security WebFlux** | Validación JWT reactiva (`NimbusReactiveJwtDecoder`) |
| **Spring Data Redis Reactive** | `ReactiveStringRedisTemplate` para caché de consentimiento |
| **Spring WebClient** | Llamadas HTTP reactivas al backend LeyData |
| **OAuth2 Client (client_credentials)** | Token M2M hacia Keycloak realm `leydata` |

Todo el código es reactivo (`Mono<T>`, `Flux<T>`). No hay bloques síncronos ni `block()`.

---

## Estructura de paquetes

```
orchestrator/src/main/java/com/leydata/orchestrator/
├── OrchestratorApplication.java
├── config/
│   ├── SecurityConfig.java           ← SecurityWebFilterChain reactivo, JWKS externo
│   ├── WebClientConfig.java          ← WebClient con OAuth2 M2M automático
│   ├── ConsentCacheProperties.java   ← @ConfigurationProperties("leydata.consent")
│   └── OAuth2ClientConfig.java       ← ReactiveClientRegistrationRepository
└── consent/
    ├── ConsentController.java
    ├── ConsentService.java
    └── dto/
        ├── ConsentCheckResponse.java
        ├── CaptureConsentRequest.java
        ├── RevokeConsentRequest.java
        ├── RevokePurposeRequest.java
        ├── ConsentStatusResponse.java
        ├── AgreementBackendResponse.java
        ├── AgreementPurposeBackendResponse.java
        ├── TemplateResolutionResponse.java        ← respuesta de GET /api/templates/resolve
        ├── TemplateContentResponse.java           ← textos legales + lista de purposes
        ├── SubjectSummaryBackendResponse.java     ← estado portal titular por finalidad
        ├── LifecycleCheckBackendResponse.java     ← estado ciclo de vida del consentimiento
        ├── PendingDeletionBackendItem.java        ← datos pendientes de eliminación
        └── OrchestratorConfirmDeletionRequest.java
```

---

## Flujo de autenticación doble

### Inbound (sistema externo → Orquestador)
`SecurityConfig` configura `NimbusReactiveJwtDecoder` apuntando a `EXTERNAL_JWKS_URI`. El Orquestador valida la firma del JWT del cliente externo pero **no requiere que el issuer sea Keycloak**. Cualquier IdP que exponga un JWKS es compatible.

**Claim `leydata_domain`:** cada client M2M de sistema cliente (CRM, ERP) debe tener configurado en su IdP un protocol mapper que incluya el claim `leydata_domain` con el UUID del dominio LeyData al que ese sistema está autorizado a consentir/consultar. `ConsentController.extractDomainId()` lo lee del JWT validado y lo usa para resolver templates contra ese dominio — un sistema cliente nunca puede declarar su propio dominio en el body del request, solo lo que el IdP le firmó. **Esto es configuración operativa de Keycloak (o el IdP del cliente), no código** — si falta, el request falla con un 500 explícito antes de tocar el backend.

### Outbound (Orquestador → Backend)
`WebClientConfig` configura el `WebClient` con `ServerOAuth2AuthorizedClientExchangeFilterFunction`. Al hacer cada llamada al backend, el filtro obtiene automáticamente un token `client_credentials` de Keycloak (o reutiliza el que tiene en caché si aún es válido). El backend recibe un Bearer token con `client_id = leydata-orchestrator`.

**Importante:** el backend nunca ve el JWT del sistema cliente — solo ve la identidad de servicio del Orquestador. Por eso `leydata_domain` no se puede leer del lado del backend; tiene que resolverse en el Orquestador (que sí valida el JWT externo) y viajar como parámetro explícito (`domainId`) en las llamadas al backend, nunca como claim relay.

---

## Casos de Uso — Endpoints del Orquestador

Todos los endpoints requieren un JWT válido del sistema cliente externo (CRM/ERP) con el claim `leydata_domain`.

### Caso 0 — Verificar Consentimiento
**`GET /consent/check?subjectId=&purposeId=`**

El CRM consulta si un titular tiene consentimiento activo para una finalidad específica. Es el endpoint de mayor volumen: todos los accesos a datos personales deberían pasar por aquí.

**Flujo:**
1. Busca `consent:{subjectId}:{purposeId}` en Redis
2. Cache hit → retorna `ConsentCheckResponse` inmediatamente (~1ms)
3. Cache miss → llama `GET /api/agreements/lifecycle-check?subjectIdentifier=&domainId=` al backend
4. El backend evalúa el estado del ciclo de vida (evaluación lazy)
5. Escribe resultado en Redis con TTL `cacheTtlSeconds` (default 900s / 15 min)
6. Retorna `ConsentCheckResponse`

**Respuesta:**
```json
{
  "subjectId": "RUT:12345678-9",
  "purposeId": "uuid-del-purpose",
  "status": "ALLOWED",
  "legalBasisCode": "ART_12_CONTRATO",
  "validUntil": "2026-12-31T23:59:59"
}
```

**Estados posibles:**

| Estado | Significado |
|---|---|
| `ALLOWED` | Consentimiento activo y vigente |
| `EXPIRED` | Vencido según política de retención de la finalidad (`expiresAt < now`) |
| `REQUIRES_RECONSENT` | El template tiene una versión nueva con `forceReconsent = true` y el agreement es de una versión anterior |
| `PENDING` | No existe consentimiento registrado para este titular y finalidad |

**Lógica de evaluación (en el backend, evaluación lazy):**
1. Si no existe agreement ACTIVE → `PENDING`
2. Si `agreement.earliestExpiresAt != null AND earliestExpiresAt < now` → `EXPIRED`
3. Si `currentTemplate.version > agreement.templateVersion AND currentTemplate.forceReconsent = true` → `REQUIRES_RECONSENT`
4. Si no → `ALLOWED`

---

### Caso 1 — Obtener Textos Legales del Template
**`GET /consent/template-content?templateKey=`**

El CRM obtiene los textos legales del template activo para mostrárselos al titular antes de firmar el consentimiento. El `domainId` viene del claim del JWT — el CRM no puede acceder a templates de otros dominios.

**Flujo:**
1. Llama `GET /api/templates/active/{templateKey}` al backend con `domainId` del JWT
2. Para cada purpose del template, obtiene la lista vía `GET /api/templates/{id}/purposes`
3. Construye `TemplateContentResponse` con título, contenido y lista de purposes

**Respuesta:**
```json
{
  "templateId": "uuid",
  "templateKey": "ONBOARDING_CLIENTE",
  "title": "Consentimiento de datos personales",
  "content": "Texto legal completo...",
  "version": 2,
  "purposes": [
    {
      "purposeId": "uuid",
      "purposeCode": "MARKETING",
      "purposeName": "Envío de comunicaciones comerciales",
      "legalBasisCode": "ART_12_CONSENTIMIENTO",
      "required": true
    }
  ]
}
```

---

### Caso 2 — Capturar Consentimiento
**`POST /consent/capture`**

El titular firma el consentimiento. El CRM envía las decisiones por finalidad.

**Flujo:**
1. Extrae `domainId` del claim `leydata_domain` del JWT del sistema cliente
2. Resuelve `templateKey → templateId + documentId` vía `GET /api/templates/resolve?domainId=&templateKey=`
3. Si el request trae `documentId` explícito, lo usa como override; si no, usa el resuelto
4. Llama `POST /api/agreements` al backend
5. El backend hace `findOrCreate` de `DataSubject` por `subjectIdentifier` opaco
6. Por cada purpose aceptado, escribe `status: ALLOWED` en Redis (pre-warm de caché)
7. Retorna `{ subjectId, agreementId, status: ALLOWED }`

**Body:**
```json
{
  "subjectIdentifier": "RUT:12345678-9",
  "templateKey": "ONBOARDING_CLIENTE",
  "purposes": [
    { "purposeId": "uuid", "accepted": true },
    { "purposeId": "uuid2", "accepted": false }
  ],
  "captureChannel": "WEB"
}
```

---

### Caso 3a — Ver Estado Completo del Titular (Portal de Preferencias)
**`GET /consent/subject/{subjectId}`**

Devuelve el estado actual de todas las finalidades consentidas por un titular en el dominio del sistema cliente. Diseñado para renderizar un portal de preferencias con switches activo/revocado por finalidad.

**Flujo:**
1. Llama `GET /api/agreements/subject-summary?subjectIdentifier=&domainId=` al backend
2. El backend consulta todos los agreements ACTIVE del titular en el dominio
3. Retorna lista de acuerdos con estado por purpose

**Respuesta:**
```json
[
  {
    "agreementId": "uuid",
    "templateKey": "ONBOARDING_CLIENTE",
    "purposes": [
      { "purposeId": "uuid", "purposeCode": "MARKETING", "purposeName": "Marketing", "accepted": true, "status": "ACTIVE" },
      { "purposeId": "uuid2", "purposeCode": "ANALYTICS", "purposeName": "Analítica", "accepted": false, "status": "ACTIVE" }
    ]
  }
]
```

---

### Caso 3b — Revocar Finalidad Específica (Granular)
**`POST /consent/revoke-purpose`**

Revoca una finalidad individual sin afectar las demás del mismo agreement. Usa el mecanismo de re-consent en lugar de modificar el agreement existente para preservar la integridad del hash SHA-256.

**Flujo:**
1. Extrae `domainId` del JWT
2. Llama `GET /api/agreements/subject-summary` para obtener el estado actual de todas las finalidades del titular
3. Construye nuevo body de agreement con la finalidad objetivo en `accepted: false` y las demás sin cambio
4. Llama `POST /api/agreements` — el backend archiva automáticamente el agreement anterior (reconsent)
5. Actualiza Redis: escribe `status: REVOKED` para `consent:{subjectId}:{purposeId}`
6. Retorna `{ subjectId, agreementId, status: REVOKED }`

**Body:**
```json
{
  "subjectId": "RUT:12345678-9",
  "purposeId": "uuid-purpose-a-revocar",
  "templateKey": "ONBOARDING_CLIENTE"
}
```

**Por qué re-consent y no UPDATE:** el hash SHA-256 de cada agreement cubre `AGREEMENTS + AGREEMENTS_PURPOSES + AGREEMENT_METADATA`. Modificar una fila de `agreements_purposes` directamente rompería el hash y la trazabilidad legal exigida por Ley 21.719. El nuevo agreement encadena su hash al anterior, preservando el ledger de integridad auditado.

---

### Caso 4 — Revocar Todo el Consentimiento
**`POST /consent/revoke`**

Revoca el agreement completo (todas las finalidades). Equivale a "retirar todo el consentimiento" del titular para un agreement.

**Body:**
```json
{
  "agreementId": "uuid-del-agreement",
  "subjectIdentifier": "RUT:12345678-9",
  "reason": "TITULAR_SOLICITUD"
}
```

---

### Caso 6 — Consultar Datos Pendientes de Eliminación
**`GET /consent/pending-deletions`**

Lista los registros de finalidades vencidas cuyos datos asociados aún no han sido eliminados por el sistema cliente. Obligación derivada de los plazos de retención definidos en `DataRetentionPolicies` (Ley 21.719, art. 14 y ss.).

**Flujo:**
1. Llama `GET /api/agreements/pending-deletions?domainId=` al backend
2. El backend consulta `agreements_purposes` con `status = ACTIVE` y `expires_at < now` en el dominio
3. Retorna lista de items con `subjectIdentifier`, `purposeId`, `purposeCode`, `expiredAt`, `anonymizeAfter`

**Respuesta:**
```json
[
  {
    "subjectIdentifier": "RUT:12345678-9",
    "purposeId": "uuid",
    "purposeCode": "MARKETING",
    "purposeName": "Envío de comunicaciones comerciales",
    "agreementId": "uuid",
    "expiredAt": "2026-01-15T00:00:00",
    "anonymizeAfter": false
  }
]
```

El campo `anonymizeAfter` indica si la política de retención exige anonimizar en lugar de eliminar.

---

### Caso 7 — Confirmar Eliminación de Datos
**`POST /consent/confirm-deletion`** → 204 No Content

El sistema cliente (CRM/ERP) confirma que eliminó o anonimizó los datos del titular para una finalidad vencida. El orquestador registra la confirmación en el `system_audit_log` del backend como evidencia de cumplimiento ante la Agencia de Protección de Datos Personales.

**Flujo:**
1. Llama `POST /api/agreements/confirm-deletion` al backend
2. El backend inserta registro en `system_audit_log` con `entityType = DELETION_CONFIRMATION`
3. Retorna 204 No Content

**Body:**
```json
{
  "subjectId": "RUT:12345678-9",
  "purposeId": "uuid-purpose",
  "deletedAt": "2026-06-30T10:00:00"
}
```

---

## ConsentService — Métodos

| Método | Caso | Descripción |
|---|---|---|
| `check(subjectId, purposeId)` | 0 | Verificación con evaluación lazy + cache Redis |
| `getTemplateContent(domainId, templateKey)` | 1 | Textos legales del template activo |
| `capture(domainId, request, realIp)` | 2 | Captura de consentimiento con pre-warm de caché |
| `getSubjectSummary(domainId, subjectId)` | 3a | Estado completo del titular por finalidad |
| `revokePurpose(domainId, request, realIp)` | 3b | Revocación granular vía re-consent |
| `revoke(request, realIp)` | 4 | Revocación total del agreement |
| `getPendingDeletions(domainId)` | 6 | Datos vencidos pendientes de eliminación |
| `confirmDeletion(domainId, request)` | 7 | Confirmación de eliminación para auditoría |

---

## Clave de Redis

```
consent:{subjectId}:{purposeId}
```

Valor: JSON de `ConsentCheckResponse`:
```json
{
  "subjectId": "RUT:12345678-9",
  "purposeId": "uuid-del-purpose",
  "status": "ALLOWED",
  "legalBasisCode": "ART_12_CONTRATO",
  "validUntil": "2026-12-31T23:59:59"
}
```

TTL default: 900 segundos (15 min). Configurable con `LEYDATA_CONSENT_CACHE_TTL_SECONDS`.

**Estrategia de invalidación:** nunca se eliminan keys — siempre se sobreescribe el valor. Esto evita split-brain si el listener de revocación del backend llega antes que la escritura del Orquestador. El estado más reciente siempre gana.

---

## Variables de entorno

| Variable | Requerida | Default | Descripción |
|---|---|---|---|
| `EXTERNAL_JWKS_URI` | ✅ | — | JWKS del IdP del sistema cliente |
| `KC_ORCHESTRATOR_CLIENT_SECRET` | ✅ | — | Secret del cliente en Keycloak |
| `LEYDATA_BACKEND_URL` | ✅ | — | URL interna del backend |
| `KC_TOKEN_URI` | ✅ | — | Endpoint de token de Keycloak |
| `REDIS_HOST` | ✅ | — | Host de Redis |
| `REDIS_PORT` | ❌ | `6379` | Puerto de Redis |
| `KC_ORCHESTRATOR_CLIENT_ID` | ❌ | `leydata-orchestrator` | Client ID en Keycloak |
| `LEYDATA_CONSENT_CACHE_TTL_SECONDS` | ❌ | `900` | TTL de keys de consentimiento en segundos |

---

## Desarrollo local

El archivo `docker-compose.override.yml` configura todas las variables apuntando a los servicios locales. El backend puede correr desde el IDE — la variable `LEYDATA_BACKEND_URL` debe apuntar a la IP de WSL si se usa Docker Desktop en Windows:

```bash
# Ver IP actual de WSL
wsl -- ip addr show eth0 | grep 'inet '

# Actualizar docker-compose.override.yml si cambió la IP
LEYDATA_BACKEND_URL: http://<nueva-ip>:8080
```

La IP de WSL cambia con cada reinicio. Para producción usar el nombre del contenedor Docker (`http://backend:8080`).

---

## Compatibilidad de versiones

| Componente | Versión |
|---|---|
| Spring Boot | 3.4.6 |
| Spring Cloud | 2024.0.1 |
| Java | 21 |

Spring Cloud 2024.0.1 requiere Spring Boot 3.4.x. **No usar Boot 4.x** — el paquete de Jackson cambió de `com.fasterxml.jackson` a `tools.jackson` y Spring Cloud no es compatible todavía.
