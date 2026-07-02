# Flujo de prueba manual end-to-end — Módulo Categorías de Datos

Este documento registra la secuencia de requests de `bruno/Categorias Datos/` usada para probar el módulo `Categorías de Datos` contra el backend local.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token con rol DPO, ADMIN o JEFE_DOMINIO (`admin@leydata.cl` / `Admin1234!`).

## 1. Listar Categorías

```
GET http://localhost:8080/api/data-categories
Authorization: Bearer {token}
```

`200 OK`. Devuelve el catálogo sembrado por `CatalogSeeder`: 8 categorías sensibles del sistema (`SALUD`, `BIOMETRICO`, `GENETICO`, `VIDA_SEXUAL`, `RELIGION`, `POLITICO`, `SINDICAL`, `RACIAL`, todas `isSystem=true`) más categorías del sistema no sensibles (ej. `IDENTIFICACION`).

## 2. Listar Categorías Sensibles

```
GET http://localhost:8080/api/data-categories/sensitive
Authorization: Bearer {token}
```

`200 OK`. Devuelve exactamente las 8 categorías con `isSensitive=true`, consistente con el listado completo.

## 3. Crear Categoría Custom

```
POST http://localhost:8080/api/data-categories
Content-Type: application/json

{
  "code": "MASCOTA_TEST_FLOW",
  "name": "Datos de mascotas del cliente",
  "isSensitive": false
}
```

`201 Created`. `isSystem: false` en la respuesta. Guardar `id`.

## 4. Editar Categoría

```
PUT http://localhost:8080/api/data-categories/{id}
Content-Type: application/json

{
  "code": "MASCOTA_TEST_FLOW",
  "name": "Datos de mascotas y animales del cliente",
  "isSensitive": false
}
```

`200 OK`. `name` actualizado correctamente.

## 5. Desactivar Categoría

```
DELETE http://localhost:8080/api/data-categories/{id}
```

`200 OK`. Soft delete: la respuesta devuelve el registro con `"isActive": false` (no se elimina físicamente).

## Validación adicional: inmutabilidad de categorías de sistema

Se verificó la regla documentada intentando editar una categoría `isSystem=true` (`SALUD`):

```
PUT http://localhost:8080/api/data-categories/24ff96c9-730a-4f96-a070-629dc81e8378
{ "code": "SALUD", "name": "Intento de editar categoria sistema", "isSensitive": true }
```

`422 Unprocessable Entity` — `"Las categorías del sistema (Ley 21.719) no pueden modificarse."`. Comportamiento correcto según lo documentado en `04 Editar Categoria.bru`.

## Resultado

Los 5 endpoints y la regla de inmutabilidad de categorías de sistema funcionan como se documenta. No se encontraron bugs.

## IDs usados en la corrida de referencia (01/07/2026)

| Recurso | ID |
|---|---|
| Categoría custom (`MASCOTA_TEST_FLOW`) | `1c099200-fc5c-459c-a269-1af3c0849795` |
| Categoría de sistema usada para validar inmutabilidad (`SALUD`) | `24ff96c9-730a-4f96-a070-629dc81e8378` |
