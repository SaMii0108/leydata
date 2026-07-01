# Flujo de prueba manual end-to-end — Módulo Dominios

Este documento registra la secuencia de requests de `bruno/Dominios/` usada para probar el módulo `Dominios` contra el backend local.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token con rol ADMIN (`admin@leydata.cl` / `Admin1234!`, ver `agreements-manual-test-flow.md` paso 1).

## 1. Listar Dominios

```
GET http://localhost:8080/api/domains/all
Authorization: Bearer {token}
```

`200 OK`. En la corrida de referencia ya existía un dominio (`test`, creado durante el flujo de Agreements).

## 2. Crear Dominio

```
POST http://localhost:8080/api/domains
Content-Type: application/json

{
  "code": "test-flow-dominios",
  "name": "Dominio Flujo de Prueba",
  "description": "Dominio creado para el flujo manual de test de Dominios",
  "jefeId": null
}
```

`201 Created`. Guardar `domainId` de la respuesta.

## 3. Desactivar Dominio

```
POST http://localhost:8080/api/domains/{domainId}/deactivate
```

`200 OK`. Se verificó con un `GET /api/domains/all` posterior: el dominio queda con `"active": false`, sin eliminarse ni afectar sus finalidades/solicitudes asociadas (según lo documentado).

## 4. Reactivar Dominio

```
POST http://localhost:8080/api/domains/{domainId}/reactivate
```

`200 OK`. Verificado: `"active": true` de nuevo.

## Resultado

Los 4 endpoints responden correctamente y el ciclo crear → desactivar → reactivar es consistente. No se encontraron bugs.

## IDs usados en la corrida de referencia (01/07/2026)

| Recurso | ID |
|---|---|
| Dominio de prueba | `4b9ee1f7-d0d7-4dd6-aade-e01e41f21523` |
