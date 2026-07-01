# Flujo de prueba manual end-to-end — Módulo Solicitudes de Finalidad

Este documento registra la secuencia de requests de `bruno/Solicitudes/` usada para probar el módulo `Solicitudes de Finalidad` (Purpose Requests) contra el backend local. **Parcialmente bloqueado**: los pasos que requieren un `JEFE_DOMINIO` con dominio asignado no se pudieron ejecutar (mismo bug raíz que `usuarios-manual-test-flow.md`).

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token DPO/ADMIN para los endpoints de revisión (`admin@leydata.cl` / `Admin1234!`).
- Token JEFE_DOMINIO para crear solicitudes (`jefe@test.cl` / `Test1234!`).

## 2. Mis Solicitudes (JEFE_DOMINIO)

```
GET http://localhost:8080/api/purpose-requests/my
Authorization: Bearer {token jefe}
```

`200 OK` — `{"status":"success","requests":[]}` (sin solicitudes previas).

## 1. Crear Solicitud de Finalidad — BLOQUEADO

```
POST http://localhost:8080/api/purpose-requests
Content-Type: application/json

{
  "domainId": "4b9ee1f7-d0d7-4dd6-aade-e01e41f21523",
  "title": "Newsletter de ofertas - flujo prueba",
  "justification": "Envío de comunicaciones comerciales a clientes suscritos",
  "requestedData": "Email, nombre, historial de compras"
}
```

Respuesta real:
```
400 Bad Request
"No puedes crear solicitudes para un dominio que no te pertenece"
```

Comportamiento correcto de la regla de negocio (un `JEFE_DOMINIO` solo puede solicitar para dominios que tiene asignados) — el bloqueo es que **no hay forma de asignarle un dominio a `jefe@test.cl`** en este momento. Se verificó directamente en la base de datos:

```sql
SELECT keycloak_id, domain_id FROM user_domains;
-- 0 rows
```

La asignación de dominios a un `JEFE_DOMINIO` se hace vía `PUT /api/users/{keycloakId}` (`domainIds`), que depende de `KeycloakAdminService` — el mismo endpoint bloqueado en `usuarios-manual-test-flow.md` por `KC_BACKEND_SECRET` inválido. Este módulo hereda ese bloqueo.

## 3. Solicitudes Pendientes (DPO/ADMIN)

```
GET http://localhost:8080/api/purpose-requests/pending
Authorization: Bearer {token admin}
```

`200 OK` — `{"status":"success","requests":[]}`.

## 4. Todas las Solicitudes (DPO/ADMIN)

```
GET http://localhost:8080/api/purpose-requests
Authorization: Bearer {token admin}
```

`200 OK` — `{"status":"success","requests":[]}`.

## 5. Revisar Solicitud — pendiente (depende del paso 1)

```
PATCH http://localhost:8080/api/purpose-requests/{id}/review
{ "status": "APPROVED", "reviewNotes": "Finalidad válida bajo Art. 12 Ley 21.719" }
```

No se pudo probar: no existe ninguna solicitud `PENDING` para revisar, ya que el paso 1 está bloqueado.

## Cómo desbloquear

1. Arreglar `KC_BACKEND_SECRET` en el backend (ver `usuarios-manual-test-flow.md`).
2. Asignar un dominio a `jefe@test.cl` vía `PUT /api/users/{keycloakId}` con `roleCodes: ["JEFE_DOMINIO"]` y `domainIds: ["<uuid-dominio>"]`.
3. Retomar desde el paso 1 de este documento.

## Resultado

3 de 5 endpoints (los de solo lectura) funcionan correctamente. Los 2 restantes (crear y revisar solicitud) están bloqueados por el mismo problema de configuración de Keycloak reportado en `usuarios-manual-test-flow.md`, no por un bug propio de este módulo.
