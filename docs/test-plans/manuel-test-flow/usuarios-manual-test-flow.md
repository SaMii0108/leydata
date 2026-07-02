# Flujo de prueba manual end-to-end — Módulo Usuarios

Este documento registra la secuencia de requests de `bruno/Usuarios/` usada para probar el módulo `Usuarios` contra el backend local. **Bloqueado**: no se pudo completar por un bug de configuración del backend corriendo actualmente (ver abajo).

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Keycloak corriendo en `http://localhost:8180`, realm `leydata`.
- Token admin obtenido igual que en `agreements-manual-test-flow.md` (paso 1: `POST /realms/leydata/protocol/openid-connect/token`, `admin@leydata.cl` / `Admin1234!`).

## 1. Listar Usuarios — BLOQUEADO

```
GET http://localhost:8080/api/users
Authorization: Bearer {token}
```

Respuesta real obtenida (2026-07-01):

```
HTTP 500
{"timestamp":"2026-07-01T17:13:51.06","status":"INTERNAL_SERVER_ERROR","code":500,"message":"Error al procesar la solicitud"}
```

### Causa raíz confirmada

`KeycloakAdminService.getAdminToken()` (`backend/src/main/java/com/leydata/backend/security/KeycloakAdminService.java:41`) pide un token `client_credentials` al cliente `leydata-backend` usando `keycloak.admin.client-secret=${KC_BACKEND_SECRET:changeme}` (`application.properties:46`). El proceso backend que está corriendo actualmente no tiene `KC_BACKEND_SECRET` seteada (o tiene un valor desactualizado), así que Keycloak lo rechaza:

```
POST /realms/leydata/protocol/openid-connect/token (grant_type=client_credentials, client_id=leydata-backend, client_secret=changeme)
→ {"error":"unauthorized_client","error_description":"Invalid client or Invalid client credentials"}
```

Esa excepción no está manejada en `KeycloakAdminService`/`UserService`, por lo que sube sin capturar y el `GlobalExceptionHandler` la convierte en `500` genérico. Afecta a **todos** los endpoints de `UserController`, ya que todos dependen del mismo admin token (`listUsers`, `createUser`, `getUser`, `updateUserByAdmin`, `blockUser`, `deactivateUser`, `reactivateUser`).

### Cómo desbloquear

1. Obtener/generar el `client-secret` real del cliente `leydata-backend` en el realm `leydata` (ver `scripts/setup-keycloak.ps1`, sección "Mostrar KC_BACKEND_SECRET").
2. Reiniciar el proceso backend con `KC_BACKEND_SECRET=<secret real>` en el entorno.
3. Retomar este documento desde el paso 2.

## 2. Crear Usuario de prueba — pendiente

```
POST http://localhost:8080/api/users
Content-Type: application/json

{
  "email": "test-usuarios-flow@leydata.cl",
  "name": "Usuario Prueba Flow",
  "roleCode": "DPO",
  "password": "Temporal123!",
  "domainIds": []
}
```

## 3. Obtener Usuario por ID — pendiente

```
GET http://localhost:8080/api/users/{keycloakId}
```

## 4. Editar Usuario — pendiente

```
PUT http://localhost:8080/api/users/{keycloakId}
Content-Type: application/json

{ "name": "Usuario Prueba Editado" }
```

## 5. Desactivar Usuario (reversible) — pendiente

```
POST http://localhost:8080/api/users/{keycloakId}/deactivate
```

## 6. Reactivar Usuario — pendiente

```
POST http://localhost:8080/api/users/{keycloakId}/reactivate
```

## 7. Bloquear Usuario (permanente e irreversible) — pendiente

```
POST http://localhost:8080/api/users/{keycloakId}/block
```

Nota: este paso es irreversible desde la API (requiere intervención manual en Keycloak + borrado en `user_status`). Ejecutar **último**, sobre el usuario de prueba creado en el paso 2, nunca sobre `admin@leydata.cl` o `dpo@leydata.cl`.

## Bugs encontrados durante esta corrida

1. **`GET /api/users` devuelve 500 en vez de propagar un error claro** cuando `KC_BACKEND_SECRET` es incorrecto o falta. `KeycloakAdminService` no captura `HttpClientErrorException` al pedir el admin token ni al llamar a la Admin API de Keycloak, así que cualquier falla de configuración se ve como un 500 opaco en vez de un error accionable (ej. 502/503 con mensaje "Keycloak admin no disponible"). Pendiente de discutir si vale la pena un manejo explícito en `GlobalExceptionHandler` o en `KeycloakAdminService`.
