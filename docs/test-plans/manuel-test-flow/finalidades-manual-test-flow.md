# Flujo de prueba manual end-to-end — Módulo Finalidades

Este documento registra la secuencia de requests de `bruno/Finalidades/` usada para probar el módulo `Finalidades` (Purposes) contra el backend local.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token con rol DPO o ADMIN (`admin@leydata.cl` / `Admin1234!`). Se renovó el token durante esta corrida (dura 5 minutos).
- Un dominio activo (se reutilizó el creado en `dominios-manual-test-flow.md`: `4b9ee1f7-d0d7-4dd6-aade-e01e41f21523`).
- Un `legalBasisId` del catálogo (se usó `CONSENTIMIENTO`: `826c67f5-3e42-4f7c-8aa6-2465eab95b55`).

## 1. Listar Finalidades

```
GET http://localhost:8080/api/purposes
Authorization: Bearer {token}
```

`200 OK`.

## 4. Crear Finalidad

```
POST http://localhost:8080/api/purposes
Content-Type: application/json

{
  "code": "TEST_FLOW_FINALIDADES",
  "name": "Finalidad de prueba flujo manual",
  "description": "Finalidad creada para el flujo manual de test de Finalidades.",
  "shortDescription": "Prueba Finalidades",
  "consentStatement": "Acepto el uso de mis datos para esta finalidad de prueba.",
  "required": false,
  "revocable": true,
  "presentationOrder": 1,
  "legalBasisId": "826c67f5-3e42-4f7c-8aa6-2465eab95b55",
  "domainId": "4b9ee1f7-d0d7-4dd6-aade-e01e41f21523",
  "purposeRequestId": null
}
```

`201 Created`. Guardar `id` (= `purposeFamilyId` de la versión 1).

**Bug encontrado:** la respuesta de este endpoint trae `legalBasisCode`, `legalBasisName` y `domainName` en `null`, a pesar de que `legalBasisId`/`domainId` sí son válidos y se guardaron correctamente (confirmado en el paso 2). El mapeo a `PurposeResponse` en el path de creación no hace el join/lookup que sí hace el path de lectura.

## 2. Obtener Finalidad por ID

```
GET http://localhost:8080/api/purposes/{id}
```

`200 OK`. A diferencia de la respuesta del `POST`, acá `legalBasisCode: "CONSENTIMIENTO"`, `legalBasisName: "Consentimiento del titular"` y `domainName: "Dominio Flujo de Prueba"` sí vienen completos.

## 3. Listar Finalidades por Dominio

```
GET http://localhost:8080/api/purposes/domain/{domainId}
```

`200 OK`. Devuelve la finalidad creada, con los mismos datos completos que el paso 2.

## 5. Editar Finalidad

```
PUT http://localhost:8080/api/purposes/{id}
Content-Type: application/json

{
  "name": "Finalidad de prueba flujo manual (editada)",
  "description": "Descripcion actualizada",
  "consentStatement": "Texto actualizado del consentimiento.",
  "required": false,
  "revocable": true,
  "presentationOrder": 1
}
```

`200 OK`. `name`/`description`/`consentStatement`/`hashSha256`/`updatedAt` cambian; `code` y `domainId` (no editables) se mantienen.

## 7. Nueva Versión de Finalidad

```
POST http://localhost:8080/api/purposes/{id}/new-version
Content-Type: application/json

{
  "name": "Finalidad de prueba flujo manual (v2)",
  "description": "Descripcion v2",
  "consentStatement": "Texto v2",
  "required": false,
  "revocable": true,
  "presentationOrder": 1
}
```

Sobre una finalidad `locked=false`, responde `409 CONFLICT` con `code: PURPOSE_NOT_LOCKED` — comportamiento correcto según lo documentado.

**Bug en la colección Bruno:** `bruno/Finalidades/07 Nueva Version.bru` declara `body: none`. El endpoint real exige `@RequestBody @Valid UpdatePurposeRequest` (`PurposeController.java:144-146`); sin body, Spring devuelve `400 Bad Request` genérico ("Cuerpo de la solicitud inválido o con formato incorrecto") en vez del `409 PURPOSE_NOT_LOCKED` esperado, lo que puede confundir a quien pruebe siguiendo la colección tal cual está. Se verificó reenviando la misma request con un body válido: ahí sí devuelve el `409` documentado.

## 8. Historial de Versiones (familia)

```
GET http://localhost:8080/api/purposes/family/{purposeFamilyId}
```

`200 OK`. Devuelve un array con la única versión existente (`version: 1`, `status: ACTIVE`).

## 9. Versión Activa de Familia

```
GET http://localhost:8080/api/purposes/active/{purposeFamilyId}
```

`200 OK`. Devuelve la misma versión 1 (única activa).

## 6. Desactivar Finalidad

```
DELETE http://localhost:8080/api/purposes/{id}
```

`200 OK`. Soft delete: `"isActive": false` en la respuesta, sin eliminar el registro.

## Resultado

8 de 9 endpoints se comportan según lo documentado. Se encontraron 2 problemas (ver detalle arriba): uno de mapeo de datos en la respuesta de creación, y un desajuste entre la colección Bruno y el contrato real del endpoint `new-version`.

## IDs usados en la corrida de referencia (01/07/2026)

| Recurso | ID |
|---|---|
| Finalidad de prueba (`TEST_FLOW_FINALIDADES`, = purposeFamilyId) | `efac1f88-2a3a-496c-a3a7-16668e4f1986` |

## Bugs encontrados durante esta corrida

1. **`POST /api/purposes` no completa `legalBasisCode`, `legalBasisName`, `domainName` en la respuesta**, mientras que `GET /api/purposes/{id}` sí los completa para el mismo registro. Revisar el mapeo en `PurposeService`/`PurposeResponse` del path de creación.
2. **`bruno/Finalidades/07 Nueva Version.bru` está desactualizado**: declara `body: none` pero `POST /api/purposes/{id}/new-version` requiere un `UpdatePurposeRequest` válido en el body (`PurposeController.java:144`). Sin body devuelve `400` genérico en vez del `409 PURPOSE_NOT_LOCKED` documentado.
