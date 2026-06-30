# Flujo de prueba manual end-to-end — Módulo Trazabilidad

Este documento registra la secuencia exacta de requests usada para probar `ENTITY_INTEGRITY_LOG`, el versionado de `PURPOSES` y `/trace` contra el backend local, reutilizando los datos creados por `agreements-manual-test-flow.md`. Sirve como guía repetible para volver a probar el módulo después de un `docker-compose down -v` o de cualquier reseteo de datos (en ese caso, primero rehacer el flujo de `agreements-manual-test-flow.md` hasta el paso 12 para tener un Agreement real).

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Keycloak corriendo en `http://localhost:8180`, realm `leydata`.
- Usuarios de prueba: `admin@leydata.cl` / `Admin1234!` (rol ADMIN), `dpo@leydata.cl` / `Test1234!` (rol DPO).
- Haber corrido el flujo de `agreements-manual-test-flow.md` al menos una vez (provee un Agreement, Template, Document y Purpose reales para usar acá).
- Scripts `V5__entity_integrity_log_trigger.sql` y `V6__purposes_versioning_backfill.sql` aplicados manualmente contra la base (ver `docs/traceability-module.md`).

## 1. Obtener tokens

```
POST http://localhost:8180/realms/leydata/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password
client_id=leydata-frontend
username=admin@leydata.cl
password=Admin1234!
```

Repetir con `dpo@leydata.cl` / `Test1234!` para los endpoints de `purposes` (rol DPO). Los endpoints de `/api/audit/*` requieren rol **ADMIN** específicamente (`@PreAuthorize("hasRole('ADMIN')")` a nivel de controller). Token dura 5 minutos.

> El token de **service account** (`grant_type=client_credentials` con `client_id=leydata-backend`) **no sirve** para estos endpoints — solo tiene roles de gestión de Keycloak (`view-realm`, `manage-users`), no `ADMIN`/`DPO` de la app. Da `403` contra cualquier endpoint de negocio.

## 2. Verificar integridad de una entidad bajo demanda (rol ADMIN)

```
POST http://localhost:8080/api/audit/integrity/verify
Content-Type: application/json

{
  "entityType": "AGREEMENT",
  "entityId": "<agreementId del flujo de agreements>",
  "checkType": "MANUAL"
}
```

`entityType`: `AGREEMENT` | `TEMPLATE` | `DOCUMENT` | `PURPOSE`. Repetir cambiando `entityType`/`entityId` por cada tipo. Respuesta `200` con `isValid`, `storedHash`, `recalculatedHash`. Cada llamada agrega una fila a `ENTITY_INTEGRITY_LOG`.

**Casos de error probados:**
- `entityType` inválido (ej. `"COSA_RARA"`) → `422 UNPROCESSABLE_ENTITY`.
- `entityId` inexistente → `404 NOT_FOUND`.

## 3. Historial y fallidas

```
GET http://localhost:8080/api/audit/integrity/log?entityType=AGREEMENT&entityId=<id>
GET http://localhost:8080/api/audit/integrity/failed
GET http://localhost:8080/api/audit/integrity/failed?entityType=PURPOSE
```

## 4. Trazabilidad end-to-end de un agreement (rol ADMIN)

```
GET http://localhost:8080/api/audit/trace/agreement/<agreementId>
```

Reconstruye `AGREEMENT → DOCUMENT → TEMPLATE → PURPOSES` con `isValid` por eslabón y `overallIntegrity` (`OK` | `MISMATCH` | `PARTIAL`). `agreementId` inexistente → `404 NOT_FOUND`.

> En la corrida de referencia, el Agreement usaba una purpose creada **antes** de que `PurposeService` calculara `hash_sha256` — su snapshot (`AGREEMENTS_PURPOSES.purpose_hash`) y `hash_sha256` son `null`. El trace responde correctamente `integrityStatus: "UNKNOWN"` para esa purpose y `overallIntegrity: "PARTIAL"` (regla 14/15) — comportamiento esperado, no es un bug.

## 5. Versionado de purposes (rol DPO)

### 5.1 Confirmar que la purpose está bloqueada

```
GET http://localhost:8080/api/purposes/<purposeId>
```

`locked: true` si está vinculada a un documento `PUBLISHED` **o** a un template con ≥1 agreement (regla nueva).

### 5.2 Intentar editar in-place una purpose bloqueada (debe fallar)

```
PUT http://localhost:8080/api/purposes/<purposeId>
Content-Type: application/json

{ "name": "Intento de edicion in-place" }
```

`422 UNPROCESSABLE_ENTITY` — "está publicada en un documento activo y no puede modificarse" (o el mensaje correspondiente si el bloqueo es solo por template+agreement).

### 5.3 Crear nueva versión

```
POST http://localhost:8080/api/purposes/<purposeId>/new-version
Content-Type: application/json

{ "name": "Finalidad de prueba para Agreements (v2)" }
```

`201` con la nueva fila: `version` incrementado, `status: "ACTIVE"`, mismo `purposeFamilyId` que la original, `hashSha256` recalculado. Guardar el `id` de la respuesta.

### 5.4 Intentar new-version sobre una purpose NO bloqueada (debe fallar)

```
POST http://localhost:8080/api/purposes/<purposeId-v2>/new-version
Content-Type: application/json

{}
```

`409 CONFLICT` — `"La finalidad '...' no está bloqueada. Edítala in-place en vez de crear una nueva versión."`

### 5.5 Historial y versión activa de la familia

```
GET http://localhost:8080/api/purposes/family/<purposeFamilyId>
GET http://localhost:8080/api/purposes/active/<purposeFamilyId>
```

`family` devuelve todas las versiones (la original ahora con `status: "SUPERSEDED"`, la nueva con `"ACTIVE"`). `active` devuelve solo la `ACTIVE`.

## IDs usados en la corrida de referencia (29/06/2026)

| Recurso | ID |
|---|---|
| Agreement (de `agreements-manual-test-flow.md`) | `ceb9a2f0-d3af-4127-8382-addad499f570` |
| Template | `03a57627-1c83-4bd5-b005-9a9da3d54eb8` |
| Documento | `a425f9a9-8092-4080-adc8-2c2ee47366ce` |
| Purpose v1 (familia, ahora `SUPERSEDED`) | `54eccc94-cf47-43b3-8558-2a6d7237f615` |
| Purpose v2 (`ACTIVE`) | `0fc6b679-1a00-4a3c-bbe0-fbadf2c48d4d` |

## Bugs encontrados y corregidos durante esta corrida

1. **`PURPOSES.code` con `UNIQUE` constraint** bloqueaba `POST /api/purposes/{id}/new-version` con `500 Internal Server Error` (violación de constraint al insertar una segunda fila con el mismo `code`, ya que el versionado requiere que todas las versiones de una familia compartan `code` — igual que `TEMPLATES.template_key` se repite entre versiones). Fix: se quitó `unique = true` de `Purposes.java` y se eliminó el constraint en BD (`backend/src/main/resources/db/migration/V7__purposes_code_not_unique.sql`, a aplicar manualmente igual que V5/V6). La unicidad de `code` para finalidades **nuevas** (familia distinta) se sigue validando en `PurposeService.create()`.
2. **`AgreementNotFoundException` sin `@ExceptionHandler`** en `GlobalExceptionHandler` — cualquier endpoint que la lanzara con un ID inexistente devolvía `500` en vez de `404`. Afectaba tanto a `GET /api/audit/trace/agreement/{id}` (nuevo) como a `GET /api/agreements/{id}` (preexistente, ya estaba roto antes de este módulo). Fix: se agregó el handler en `config/GlobalExceptionHandler.java`, mismo patrón que `TemplateNotFoundException`/`PurposeNotFoundException`/`DocumentNotFoundException`.

Ambos fixes están commiteados junto con el resto del módulo de trazabilidad.
