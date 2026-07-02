# Flujo de prueba manual end-to-end — Módulo Templates

Este documento registra la secuencia de requests de `bruno/Templates/` usada para probar el módulo `Templates` contra el backend local.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token con rol DPO o ADMIN (`admin@leydata.cl` / `Admin1234!`).
- Un dominio activo (`4b9ee1f7-d0d7-4dd6-aade-e01e41f21523`, de `dominios-manual-test-flow.md`).
- Dos finalidades activas/aprobadas creadas para esta corrida: `TEST_FLOW_TEMPLATES_A` (`c3139a3c-a25c-4256-b192-7d08c4e4dc0f`) y `TEST_FLOW_TEMPLATES_C` (`f6f16a09-324e-4fb3-801c-1a03bea607d5`).

## 1. Listar Templates

```
GET http://localhost:8080/api/templates
```

`200 OK`.

## 2. Crear Template (DRAFT)

```
POST http://localhost:8080/api/templates
Content-Type: application/json

{
  "domainId": "4b9ee1f7-d0d7-4dd6-aade-e01e41f21523",
  "templateKey": "test-flow-templates",
  "name": "Template flujo de prueba",
  "title": "Gestion de tus datos - prueba",
  "description": "Template creado para el flujo manual de test de Templates",
  "version": 1,
  "changeReason": "Version inicial"
}
```

`201 Created`, `status: DRAFT`. `templateKey` se normaliza a mayúsculas (`TEST-FLOW-TEMPLATES`). Guardar `id`.

## 3. Obtener Template por ID

```
GET http://localhost:8080/api/templates/{id}
```

`200 OK`.

## 10. Vincular Purpose al Template (x2)

```
POST http://localhost:8080/api/templates/{id}/purposes
{ "purposeId": "c3139a3c-a25c-4256-b192-7d08c4e4dc0f", "orderPosition": 1, "isVisible": true }

POST http://localhost:8080/api/templates/{id}/purposes
{ "purposeId": "f6f16a09-324e-4fb3-801c-1a03bea607d5", "orderPosition": 2, "isVisible": true }
```

Ambas `204 No Content`.

## 11. Listar Purposes del Template

```
GET http://localhost:8080/api/templates/{id}/purposes
```

`200 OK` — devuelve las 2 purposes vinculadas, ordenadas por `orderPosition`.

## 12. Actualizar Purpose en Template

```
PATCH http://localhost:8080/api/templates/{id}/purposes/{purposeIdA}
{ "orderPosition": 1, "isVisible": true }
```

`200 OK`.

## 13. Desvincular Purpose del Template

```
DELETE http://localhost:8080/api/templates/{id}/purposes/{purposeIdC}
```

`204 No Content`. Verificado con un `GET /purposes` posterior: queda solo la purpose A vinculada.

## 7. Aprobar Template

```
POST http://localhost:8080/api/templates/{id}/approve
```

`200 OK`, `status: DRAFT → APPROVED` (con 1 purpose visible, cumple el prerrequisito).

## 8. Activar Template

```
POST http://localhost:8080/api/templates/{id}/activate
Content-Type: application/json

{ "forceReconsent": false }
```

`200 OK`, `status: APPROVED → ACTIVE`, `isActive: true`, `activationDate` sellada.

## 9. Verificar Integridad SHA-256

```
GET http://localhost:8080/api/templates/{id}/verify
```

`200 OK`:
```json
{
  "computedHash": "8981c2004da924c188a22057e31fab212609f3732bbf4e5744e36f401bd2ca91",
  "storedHash":   "8981c2004da924c188a22057e31fab212609f3732bbf4e5744e36f401bd2ca91",
  "hashMatch": true,
  "message": "Integridad verificada: el contenido coincide con el hash registrado",
  "templateId": "96cbdd42-76f5-4d61-b40c-08037df8277b",
  "version": 1
}
```

Nota menor: la doc de `09 Verificar Integridad SHA-256.bru` describe el campo de resultado como `valid`; el campo real en la respuesta es `hashMatch` (más `message`). Solo desactualización de la documentación, no es un bug funcional.

## 4. Nueva Versión de Template

```
POST http://localhost:8080/api/templates/{id}/new-version
```

`201 Created` — nuevo template `version: 2`, `status: DRAFT`, mismo `templateKey`. La versión 1 no se modifica (sigue `ACTIVE`).

## 5. Historial de Versiones (por templateKey)

```
GET http://localhost:8080/api/templates/family/TEST-FLOW-TEMPLATES?domainId={domainId}
```

`200 OK` — devuelve ambas versiones (2 DRAFT, 1 ACTIVE).

## 6. Versión Activa (por templateKey)

```
GET http://localhost:8080/api/templates/active/TEST-FLOW-TEMPLATES?domainId={domainId}
```

`200 OK` — devuelve la versión 1 (ACTIVE), correcto.

## 14. Resolver Template (B2B)

```
GET http://localhost:8080/api/templates/resolve?domainId={domainId}&templateKey=TEST-FLOW-TEMPLATES
```

`200 OK`:
```json
{ "documentId": null, "domainId": "4b9ee1f7-...", "templateId": "96cbdd42-...", "templateKey": "TEST-FLOW-TEMPLATES", "version": 1 }
```

`documentId: null` correcto — todavía no hay un documento de privacidad publicado para este template (se cubre en `documentos-manual-test-flow.md`).

## Resultado

Los 14 endpoints se comportan según lo documentado. Solo se detectó una desactualización menor de nombre de campo en la doc de `verify` (`valid` vs `hashMatch`), sin impacto funcional.

## IDs usados en la corrida de referencia (01/07/2026)

| Recurso | ID |
|---|---|
| Finalidad A (vinculada, queda en el template) | `c3139a3c-a25c-4256-b192-7d08c4e4dc0f` |
| Finalidad C (vinculada y luego desvinculada) | `f6f16a09-324e-4fb3-801c-1a03bea607d5` |
| Template v1 (ACTIVE) | `96cbdd42-76f5-4d61-b40c-08037df8277b` |
| Template v2 (DRAFT) | `0748298d-28b0-48b7-980d-b090a49fa75c` |
| templateKey | `TEST-FLOW-TEMPLATES` |
