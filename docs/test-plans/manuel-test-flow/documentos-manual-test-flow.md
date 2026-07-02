# Flujo de prueba manual end-to-end — Módulo Documentos (Privacy Documents)

Este documento registra la secuencia de requests de `bruno/Documentos/` usada para probar el módulo `Documentos de Privacidad` contra el backend local. Se encontró **1 bug funcional confirmado** (archive inalcanzable) y **2 desajustes de la colección Bruno** con el contrato real de la API.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token con rol **DPO** específicamente (`dpo@leydata.cl` / `Test1234!` — `crear`, `editar`, `submit`, `approve`, `reject`, `publish`, `archive`, `new-version` exigen DPO, no alcanza con ADMIN).
- Template ACTIVE (`96cbdd42-76f5-4d61-b40c-08037df8277b`, de `templates-manual-test-flow.md`).
- Finalidades aprobadas (`c3139a3c-a25c-4256-b192-7d08c4e4dc0f`, `f6f16a09-324e-4fb3-801c-1a03bea607d5`).

## 1. Listar Documentos

```
GET http://localhost:8080/api/privacy-documents
```

`200 OK`.

## 2. Crear Documento (DRAFT)

```
POST http://localhost:8080/api/privacy-documents
Content-Type: application/json

{
  "name": "Politica de prueba - flujo Documentos",
  "category": "MARKETING_DIRECTO",
  "content": "Este documento describe las condiciones de tratamiento de datos para el flujo manual de prueba de Documentos.",
  "templateId": "96cbdd42-76f5-4d61-b40c-08037df8277b"
}
```

`201 Created`, `status: DRAFT`. Nota: `templateId` no aparece en el body de ejemplo del `.bru`, pero el DTO (`CreateDocumentRequest.java:32`) lo acepta como opcional y es **requerido antes de enviar a revisión** — se incluyó desde la creación para evitar un paso extra.

## 3. Obtener Documento por ID

```
GET http://localhost:8080/api/privacy-documents/{id}
```

`200 OK`.

## 4. Editar Documento (DRAFT)

```
PATCH http://localhost:8080/api/privacy-documents/{id}
{ "name": "...", "content": "..." }
```

`200 OK`.

## 5. Vincular Finalidad al Documento

```
POST http://localhost:8080/api/privacy-documents/{id}/purposes/{purposeId}
```

`204 No Content`.

## 6. Enviar a Revisión

```
POST http://localhost:8080/api/privacy-documents/{id}/submit
```

`200 OK`, `status: DRAFT → IN_REVIEW`.

## 7. Aprobar Documento

```
POST http://localhost:8080/api/privacy-documents/{id}/approve
```

`200 OK`, `status: IN_REVIEW → APPROVED`.

## 9. Publicar Documento

```
POST http://localhost:8080/api/privacy-documents/{id}/publish
```

`200 OK`, `status: APPROVED → PUBLISHED`, genera PDF y `hashSha256`.

## 13. Descargar PDF

```
GET http://localhost:8080/api/privacy-documents/{id}/pdf
```

`200 OK`, `Content-Type: application/pdf`.

## 14. Verificar Integridad SHA-256

```
GET http://localhost:8080/api/privacy-documents/{id}/verify
```

`200 OK`, `hashMatch: true`. Igual que en Templates, el campo real es `hashMatch` (+ `message`), no `valid` como dice la doc del `.bru`.

## 15. Documento Activo por Categoría — bug de colección

```
GET http://localhost:8080/api/privacy-documents/active-by-category?category=MARKETING_DIRECTO
```

`400 Bad Request` — `"Valor inválido para el parámetro 'id': active-by-category"`. La ruta real (`PrivacyDocumentController.java:171`) es `GET /api/privacy-documents/active?category=...`, no `/active-by-category`. Spring interpreta `active-by-category` como el `{id}` de `GET /{id}` y falla al parsearlo como UUID.

Con la ruta correcta:
```
GET http://localhost:8080/api/privacy-documents/active?category=MARKETING_DIRECTO
```
`200 OK`. Además, los valores de categoría documentados en el `.bru` (`POLITICA_PRIVACIDAD, AVISO_PRIVACIDAD, CONTRATO_TRATAMIENTO, EVALUACION_IMPACTO, ACUERDO_TRANSFERENCIA, OTRO`) no coinciden con el enum real (`POLITICA_PRIVACIDAD, AVISO_COOKIES, DATOS_SENSIBLES, MARKETING_DIRECTO, MENORES_EDAD, TRANSFERENCIA_TERCEROS`, igual que en `02 Crear Documento`). El archivo `.bru` #15 está desactualizado en URL y en valores.

## 12. Listar Familia

```
GET http://localhost:8080/api/privacy-documents/family/{documentFamilyId}
```

`200 OK`.

## 11. Nueva Versión del Documento

```
POST http://localhost:8080/api/privacy-documents/{id}/new-version
```

`201 Created` — nuevo `DRAFT`, `version: 2`, mismo `documentFamilyId`. No hereda las finalidades vinculadas del padre (`purposeIds: []`), igual que el comportamiento visto en Templates.

## 10. Archivar Documento — BUG CONFIRMADO (inalcanzable vía API)

```
POST http://localhost:8080/api/privacy-documents/{id}/archive
```

Sobre el documento original `PUBLISHED` (con 1 finalidad activa vinculada):

```
422 Unprocessable Entity
"No se puede archivar el documento: tiene finalidades activas. Desvinculálas primero."
```

Intento de desvincular la finalidad para cumplir la condición:

```
DELETE http://localhost:8080/api/privacy-documents/{id}/purposes/{purposeId}
→ 422 Unprocessable Entity
"La operación requiere estado DRAFT (actual: PUBLISHED)"
```

**Callejón sin salida confirmado en código:**
- `archive()` (`PrivacyDocumentService.java:447-452`) exige `purposeRepo.existsByDocument_IdAndIsActiveTrue(id) == false`.
- `removePurpose()` (`PrivacyDocumentService.java:222-224`) exige `DocumentStatus.DRAFT`.
- `validateTransition()` (`PrivacyDocumentService.java:664-674`) solo permite `PUBLISHED → ARCHIVED` (nunca `PUBLISHED → DRAFT`).
- `publish()` (línea 355-359) exige **al menos 1 finalidad activa** para poder publicar.

Combinando las 4 reglas: todo documento válidamente publicado tiene ≥1 finalidad activa, no puede volver a `DRAFT` para desvincularlas, y por lo tanto **nunca cumple la condición para archivarse manualmente**. El endpoint `POST /{id}/archive` es efectivamente inalcanzable para cualquier documento publicado por el flujo normal.

El único camino que sí funciona es el archivado **automático**: al publicar una nueva versión del mismo `templateId`, `publish()` (líneas 367-385) archiva la versión anterior directamente con `previous.setStatus(DocumentStatus.ARCHIVED)`, sin pasar por la validación de finalidades activas. Verificado indirectamente: la lógica de esa rama no llama a `existsByDocument_IdAndIsActiveTrue`.

## 8. Rechazar Documento — bug de colección (nombre de campo)

```
POST http://localhost:8080/api/privacy-documents/{id}/reject
Content-Type: application/json

{ "rejectionReason": "..." }
```

`400 Bad Request` — `"reason: El motivo de rechazo es obligatorio"`. El `.bru` usa el campo `rejectionReason`, pero `RejectDocumentRequest.java:10` espera `reason`. Reintentando con el campo correcto:

```
{ "reason": "El documento no cumple con los requisitos del articulo 14 de la Ley 21.719." }
```

`200 OK`, `status: IN_REVIEW → REJECTED`, `rejectionReason` queda seteado correctamente en la respuesta (el campo de **salida** sí se llama `rejectionReason` — solo el de **entrada** es `reason`, lo que explica la confusión en el `.bru`).

## 16. Desvincular Finalidad del Documento (camino feliz, en DRAFT)

```
DELETE http://localhost:8080/api/privacy-documents/{id}/purposes/{purposeId}
```

Sobre el `DRAFT` v2 (creado en el paso 11): `204 No Content`. Funciona correctamente cuando el documento sí está en `DRAFT`.

## Resultado

13 de 16 endpoints se comportan según lo documentado. Se encontraron 3 problemas:

1. **Bug funcional confirmado**: `POST /{id}/archive` es inalcanzable para documentos con finalidades activas, y todo documento publicable tiene finalidades activas por definición — el endpoint manual de archivado no tiene camino de éxito posible vía API pública.
2. **Bug de colección**: `15 Documento Activo por Categoria.bru` tiene la URL (`active-by-category` en vez de `active`) y los valores de categoría desactualizados.
3. **Bug de colección**: `08 Rechazar Documento.bru` usa el campo `rejectionReason` en el body, pero el DTO real espera `reason`.

## IDs usados en la corrida de referencia (01/07/2026)

| Recurso | ID |
|---|---|
| Documento A (v1, PUBLISHED, con finalidad bloqueada para archivar) | `ad3c882e-c314-4a43-b1f2-ebb3e24742d1` |
| Documento A (v2, DRAFT, misma familia) | `04088454-253e-4546-a4cf-59b852d66a4b` |
| Documento B (REJECTED, para probar rechazo) | `78be91ea-5bab-4962-8b69-bf6c44280eaf` |

## Bugs encontrados durante esta corrida

1. **`POST /api/privacy-documents/{id}/archive` inalcanzable en la práctica.** Precondición (`sin finalidades activas`) y la única forma de cumplirla (`DELETE .../purposes/{purposeId}`, que exige `DRAFT`) son mutuamente excluyentes para cualquier documento `PUBLISHED` con finalidades (obligatorias para publicar). Revisar si `archive()` debería permitir archivar con finalidades activas (igual que el auto-archivado interno en `publish()`), o si `removePurpose()` debería permitirse también en `PUBLISHED`.
2. **`bruno/Documentos/15 Documento Activo por Categoria.bru` desactualizado**: URL incorrecta (`/active-by-category` en vez de `/active`) y lista de categorías que no corresponde al enum `DocumentCategory` real.
3. **`bruno/Documentos/08 Rechazar Documento.bru` desactualizado**: el body de ejemplo usa `rejectionReason`, el DTO real (`RejectDocumentRequest`) espera `reason`.
