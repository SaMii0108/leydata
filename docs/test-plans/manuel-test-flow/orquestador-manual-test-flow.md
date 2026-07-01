# Flujo de prueba manual end-to-end — Módulo Orquestador (B2B)

Este documento registra la secuencia de requests de `bruno/Orquestador/` usada para probar el Orquestador (`localhost:8081`) contra el backend LeyData local. **Bloqueado en el último paso**: se completó todo el setup de infraestructura necesario, pero el Orquestador no puede autenticarse contra el backend LeyData por el mismo tipo de problema de configuración de Keycloak reportado en `usuarios-manual-test-flow.md`.

## Prerrequisitos y setup realizado en esta corrida

El realm `empresa-cliente` (IdP simulado del sistema cliente B2B) **no existía** al empezar. Se ejecutó el setup completo:

1. **Realm, cliente y usuario** — como indica `scripts/setup-empresa-cliente-realm.sh` (el script no pudo correr tal cual por falta de `python3` real en el entorno; se replicaron sus mismos pasos vía `curl`+`jq` contra la Admin API de Keycloak):
   - Realm `empresa-cliente` creado.
   - Cliente `crm-sistema` (public, `directAccessGrantsEnabled`).
   - Usuario `operador@empresa.cl` / `operador123`.

2. **Bug encontrado en el script de setup:** el usuario creado sin `firstName`/`lastName` no puede loguearse — Keycloak devuelve `invalid_grant: "Account is not fully set up"` (el User Profile declarativo del realm exige esos atributos). El script no los setea. Se corrigió manualmente:
   ```
   PUT /admin/realms/empresa-cliente/users/{id}
   { "firstName": "Operador", "lastName": "Empresa Cliente", "emailVerified": true }
   ```
   Después de esto, `POST /realms/empresa-cliente/protocol/openid-connect/token` con `grant_type=password` funciona correctamente.

3. **Claim `leydata_domain` faltante:** el token obtenido no traía el claim `leydata_domain` que el Orquestador exige (`ConsentController.java:101-109`, lanza `IllegalStateException` si falta). El script de setup tampoco lo configura. Se agregó manualmente un protocol mapper hardcodeado en el cliente `crm-sistema`:
   ```
   POST /admin/realms/empresa-cliente/clients/{clientUuid}/protocol-mappers/models
   { "protocolMapper": "oidc-hardcoded-claim-mapper", "config": { "claim.name": "leydata_domain", "claim.value": "4b9ee1f7-d0d7-4dd6-aade-e01e41f21523", ... } }
   ```
   Verificado: el token ahora incluye `"leydata_domain": "4b9ee1f7-d0d7-4dd6-aade-e01e41f21523"`.

4. **`EXTERNAL_JWKS_URI`** ya estaba configurado correctamente en `docker-compose.override.yml` (`http://keycloak:8080/realms/empresa-cliente/protocol/openid-connect/certs`), apuntando al mismo Keycloak — no fue necesario tocarlo ni reiniciar el contenedor para que la validación de firma del JWT funcionara.

## 1. Obtener Token Sistema Externo

```
POST http://localhost:8180/realms/empresa-cliente/protocol/openid-connect/token
grant_type=password&client_id=crm-sistema&username=operador@empresa.cl&password=operador123
```

`200 OK` (después del fix de `firstName`/`lastName` del punto 2). Token válido con claim `leydata_domain` presente.

## 2. Verificar Consentimiento — BLOQUEADO

```
GET http://localhost:8081/consent/check?subjectId=test-titular-orq&purposeId=c3139a3c-a25c-4256-b192-7d08c4e4dc0f
Authorization: Bearer {externalToken}
```

La autenticación del request **entrante** al Orquestador funciona (no da 401/403 — el JWT externo con `leydata_domain` se valida correctamente). Pero responde:

```
500 Internal Server Error
{"path":"/consent/check","status":500,"error":"Internal Server Error"}
```

### Causa raíz confirmada (logs del contenedor `leydata-orchestrator`)

```
Caused by: org.springframework.security.oauth2.core.OAuth2AuthorizationException: [invalid_client] Invalid client or Invalid client credentials
	at ... OAuth2AccessTokenResponseBodyExtractor ...
Error observed at: Body from POST http://keycloak:8080/realms/leydata/protocol/openid-connect/token
```

Es el **mismo tipo de bug que bloqueó `usuarios-manual-test-flow.md`**, pero en el lado del Orquestador: para llamar de vuelta a la API de LeyData (`POST /api/agreements`, `GET /api/templates/resolve`, etc.) con credenciales M2M, el Orquestador pide un token `client_credentials` al cliente `leydata-orchestrator` en el realm `leydata` (`application.yml`: `client-id: ${KC_ORCHESTRATOR_CLIENT_ID:leydata-orchestrator}`, `client-secret: ${KC_ORCHESTRATOR_CLIENT_SECRET}`, sin default). Keycloak rechaza esas credenciales con `invalid_client` — el secret configurado en el contenedor no coincide con el del cliente `leydata-orchestrator` en Keycloak.

No se intentó leer ni regenerar el secret del contenedor: imprimir variables de entorno con `docker exec ... env` fue bloqueado por el clasificador de seguridad de la sesión al detectar que expondría un credential (`KC_BACKEND_SECRET`/`KC_ORCHESTRATOR_CLIENT_SECRET`) directamente en la transcripción. Requiere que el usuario regenere el secret del cliente `leydata-orchestrator` en Keycloak y actualice `KC_ORCHESTRATOR_CLIENT_SECRET` en el entorno del contenedor `leydata-orchestrator`, igual que con `KC_BACKEND_SECRET` para el backend.

## 3–9. Resto de endpoints — no probados

Todos dependen de la misma llamada M2M interna del Orquestador hacia LeyData (`Capturar Consentimiento`, `Revocar Consentimiento`, `Contenido Template`, `Estado del Titular`, `Revocar Finalidad`, `Eliminaciones Pendientes`, `Confirmar Eliminación`), así que heredan el mismo bloqueo.

## Cómo desbloquear

1. En Keycloak, regenerar/verificar el `client-secret` del cliente `leydata-orchestrator` en el realm `leydata`.
2. Actualizar `KC_ORCHESTRATOR_CLIENT_SECRET` en el entorno del contenedor `leydata-orchestrator` (docker-compose / `.env`) y reiniciarlo.
3. De paso, arreglar `KC_BACKEND_SECRET` del backend (bloqueo de `usuarios-manual-test-flow.md`) — son credenciales distintas pero la causa (secrets desactualizados tras recrear el realm/clientes) es probablemente la misma.
4. Retomar desde el paso 2 de este documento.

## Resultado

El setup de infraestructura B2B (realm, cliente, usuario, claim `leydata_domain`) quedó completo y funcional — reutilizable para la próxima corrida. El flujo de negocio en sí (9 endpoints) está bloqueado por credenciales M2M inválidas del lado del Orquestador hacia LeyData, la misma clase de problema que bloqueó Usuarios y Solicitudes.

## Bugs encontrados durante esta corrida

1. **`scripts/setup-empresa-cliente-realm.sh` crea un usuario sin `firstName`/`lastName`**, lo que hace que el login por `password` grant falle con `invalid_grant: "Account is not fully set up"` en versiones de Keycloak con User Profile declarativo. Agregar esos campos al payload de creación del usuario en el script.
2. **`scripts/setup-empresa-cliente-realm.sh` no configura el protocol mapper `leydata_domain`** que el Orquestador exige (`ConsentController.java:101`). Sin este paso manual, ningún endpoint de `/consent/*` es alcanzable — el script debería crearlo automáticamente (con un valor de dominio de ejemplo o como parámetro).
3. **Credenciales M2M del Orquestador hacia LeyData inválidas** (`KC_ORCHESTRATOR_CLIENT_SECRET` desactualizado) — bloquea los 9 endpoints del módulo. Mismo patrón que el bug de `KC_BACKEND_SECRET` en `usuarios-manual-test-flow.md`.

## IDs / recursos creados en la corrida de referencia (01/07/2026)

| Recurso | Valor |
|---|---|
| Realm | `empresa-cliente` |
| Cliente | `crm-sistema` (id interno `dd02dc8c-add4-4373-8413-9f886ee5714c`) |
| Usuario de prueba | `operador@empresa.cl` / `operador123` |
| Claim `leydata_domain` configurado | `4b9ee1f7-d0d7-4dd6-aade-e01e41f21523` (dominio de prueba de `dominios-manual-test-flow.md`) |
