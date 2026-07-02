# Flujo de prueba manual end-to-end — Módulo Notificaciones

Este documento registra la secuencia de requests de `bruno/Notificaciones/` usada para probar el módulo `Notificaciones` contra el backend local. **Parcialmente bloqueado**: el sistema no generó ninguna notificación real durante esta corrida, porque las acciones que las disparan (`PurposeRequestService`, `PrivacyDocumentService` al publicar un documento derivado de una solicitud) dependen del flujo de Solicitudes, bloqueado por el mismo problema de Keycloak reportado en `usuarios-manual-test-flow.md` / `solicitudes-manual-test-flow.md`.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token de cualquier usuario autenticado (`admin@leydata.cl` / `Admin1234!`).

## 1. Listar Notificaciones

```
GET http://localhost:8080/api/notifications
```

`200 OK` — `[]` (sin notificaciones; ver nota de bloqueo arriba).

## 2. Contador de Notificaciones No Leídas

```
GET http://localhost:8080/api/notifications/unread-count
```

`200 OK` — `{"count": 0}`. Consistente con el listado vacío.

## 3. Marcar Notificación como Leída

```
PATCH http://localhost:8080/api/notifications/{id}/read
```

No se pudo probar el camino feliz (no existe ninguna notificación real). Se probó con un UUID inexistente para validar el manejo de error:

```
PATCH http://localhost:8080/api/notifications/00000000-0000-0000-0000-000000000000/read
→ 404 Not Found
"Notificación no encontrada: 00000000-0000-0000-0000-000000000000"
```

Comportamiento correcto.

## 4. Marcar Todas las Notificaciones como Leídas

```
PATCH http://localhost:8080/api/notifications/read-all
```

`200 OK` (sin body). Nota menor: la doc del `.bru` dice "Retorna 204 No Content"; la respuesta real es `200 OK`. No afecta funcionalidad, solo desactualización de la doc.

## Cómo generar notificaciones reales para completar esta prueba

1. Arreglar `KC_BACKEND_SECRET` (ver `usuarios-manual-test-flow.md`).
2. Asignar dominio a `jefe@test.cl` y completar el flujo de `solicitudes-manual-test-flow.md` (crear solicitud → aprobarla) — genera una finalidad y dispara notificación al `JEFE_DOMINIO` solicitante.
3. Publicar un documento vinculado a una finalidad con `purposeRequestId` no nulo — dispara `PURPOSE_REQUEST_FULFILLED` (visto en el código de `PrivacyDocumentService`, línea ~439).
4. Retomar el paso 3 de este documento con un `id` real.

## Resultado

Los 2 endpoints de lectura (`GET`) funcionan correctamente en estado vacío. El manejo de error 404 en `PATCH .../read` es correcto. El camino feliz de "marcar como leída" y el contenido real de una notificación quedan pendientes de una corrida futura, una vez desbloqueado el flujo de Solicitudes. Se detectó una desactualización menor de doc (204 documentado vs 200 real en `read-all`).
