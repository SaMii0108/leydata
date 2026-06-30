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
        ├── ConsentStatusResponse.java
        ├── AgreementBackendResponse.java
        └── AgreementPurposeBackendResponse.java
```

---

## Flujo de autenticación doble

### Inbound (sistema externo → Orquestador)
`SecurityConfig` configura `NimbusReactiveJwtDecoder` apuntando a `EXTERNAL_JWKS_URI`. El Orquestador valida la firma del JWT del cliente externo pero **no requiere que el issuer sea Keycloak**. Cualquier IdP que exponga un JWKS es compatible.

### Outbound (Orquestador → Backend)
`WebClientConfig` configura el `WebClient` con `ServerOAuth2AuthorizedClientExchangeFilterFunction`. Al hacer cada llamada al backend, el filtro obtiene automáticamente un token `client_credentials` de Keycloak (o reutiliza el que tiene en caché si aún es válido). El backend recibe un Bearer token con `client_id = leydata-orchestrator`.

---

## ConsentService — Lógica de caché

### `check(subjectId, purposeId)`
1. Busca `consent:{subjectId}:{purposeId}` en Redis
2. Si existe → deserializa `ConsentCheckResponse` y retorna (cache hit, ~1ms)
3. Si no → llama `GET /api/agreements/active?dataSubjectId={s}&templateId={p}` al backend
4. Construye `ConsentCheckResponse` con `status: ALLOWED|REVOKED|DENIED|PENDING`
5. Escribe en Redis con TTL `cacheTtlSeconds` (default: 300s)
6. Si el backend responde 404 → retorna `status: PENDING` (sin escribir en Redis)

### `capture(request, realIp)`
1. Llama `POST /api/agreements` al backend con `subjectIdentifier` (no UUID)
2. El backend hace `findOrCreate` del `DataSubject` por el identificador opaco
3. Por cada purpose aceptado, escribe `status: ALLOWED` en Redis (pre-warm)
4. Retorna `{ subjectId, agreementId, status: ALLOWED }`

### `revoke(request, realIp)`
1. Llama `PATCH /api/agreements/{id}/revoke` al backend
2. El backend persiste en Postgres y publica `AgreementRevokedEvent`
3. El `AgreementRevocationCacheListener` elimina las keys de Redis (AFTER_COMMIT)
4. El Orquestador además escribe `status: REVOKED` por cada purpose del acuerdo
5. Retorna `{ subjectId, agreementId, status: REVOKED }`

> **Nota:** la doble escritura (listener backend + Orquestador) es intencional: garantiza consistencia si el Orquestador falla tras llamar al backend pero antes de actualizar Redis.

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
  "validUntil": null
}
```

TTL default: 300 segundos. Configurable con `LEYDATA_CONSENT_CACHE_TTL_SECONDS`.

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
| `LEYDATA_CONSENT_CACHE_TTL_SECONDS` | ❌ | `300` | TTL de keys de consentimiento |

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
