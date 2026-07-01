# Flujo de prueba manual end-to-end — Módulo Retención (Finalidad ↔ Categoría de Datos)

Este documento registra la secuencia de requests de `bruno/Retencion/` usada para probar el vínculo Finalidad↔Categoría y su política de retención contra el backend local.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token con rol DPO o ADMIN (`admin@leydata.cl` / `Admin1234!`).
- Una finalidad activa y **no bloqueada** (`locked: false`) — se usó `f6f16a09-324e-4fb3-801c-1a03bea607d5` (`TEST_FLOW_TEMPLATES_C`, de `templates-manual-test-flow.md`, nunca quedó vinculada a un documento publicado).
- Una categoría de datos activa creada para esta corrida: `RETENCION_TEST_FLOW` (`6d075671-7bfa-4bd4-877c-9d51fd4c89ce`).

## 1. Listar Vínculos Finalidad-Categoría

```
GET http://localhost:8080/api/purposes/{purposeId}/data-categories
```

`200 OK` — `[]` (sin vínculos previos).

## 2. Vincular Categoría + Definir Retención

```
POST http://localhost:8080/api/purposes/{purposeId}/data-categories
Content-Type: application/json

{
  "dataCategoryId": "6d075671-7bfa-4bd4-877c-9d51fd4c89ce",
  "required": true,
  "dataUses": ["STORAGE", "PROCESSING"],
  "retention": {
    "retentionPeriod": 5,
    "retentionUnit": "YEARS",
    "legalJustification": "Art. 17 Ley 21.719",
    "anonymizeAfter": true
  }
}
```

`201 Created`. Guardar `id` (del vínculo).

**Bug encontrado (mismo patrón que en Finalidades):** la respuesta trae `dataCategoryCode`, `dataCategoryName` e `isSensitive` en `null`, a pesar de que `dataCategoryId` es válido. Confirmado con un `GET` inmediato al mismo recurso: ahí sí vienen completos (`"dataCategoryCode":"RETENCION_TEST_FLOW"`, `"dataCategoryName":"Categoria para test de Retencion"`, `"isSensitive":false`). Es la segunda vez que se observa este patrón de mapeo incompleto en el `POST` de creación vs. el `GET` de lectura (ver también `finalidades-manual-test-flow.md`, bug #1) — sugiere una causa común, probablemente en cómo se construye el DTO de respuesta justo después de un `save()` sin recargar las relaciones.

## 3. Actualizar Política de Retención

```
PUT http://localhost:8080/api/purposes/{purposeId}/data-categories/{linkId}/retention
Content-Type: application/json

{
  "retentionPeriod": 3,
  "retentionUnit": "YEARS",
  "legalJustification": "Actualizacion normativa 2026",
  "anonymizeAfter": false
}
```

`200 OK`. A diferencia del `POST`, esta respuesta **sí** trae `dataCategoryCode`/`dataCategoryName`/`isSensitive` completos — el bug es específico del path de creación, no de actualización.

## 4. Desvincular Categoría

```
DELETE http://localhost:8080/api/purposes/{purposeId}/data-categories/{linkId}
```

`204 No Content`.

## Resultado

Los 4 endpoints funcionan según lo documentado. Se confirmó el mismo bug de mapeo de respuesta visto en Finalidades, esta vez en `POST /api/purposes/{id}/data-categories`.

## IDs usados en la corrida de referencia (01/07/2026)

| Recurso | ID |
|---|---|
| Categoría de datos (`RETENCION_TEST_FLOW`) | `6d075671-7bfa-4bd4-877c-9d51fd4c89ce` |
| Vínculo Finalidad↔Categoría | `7aade4d1-fd47-4982-b7eb-141611494db6` |

## Bugs encontrados durante esta corrida

1. **`POST /api/purposes/{id}/data-categories` no completa `dataCategoryCode`, `dataCategoryName`, `isSensitive` en la respuesta**, mientras que `GET` y `PUT .../retention` sí los completan para el mismo registro. Mismo patrón que el bug #1 de `finalidades-manual-test-flow.md` — revisar si comparten la misma causa raíz (falta de `fetch`/join al mapear la entidad recién guardada a su DTO de respuesta).
