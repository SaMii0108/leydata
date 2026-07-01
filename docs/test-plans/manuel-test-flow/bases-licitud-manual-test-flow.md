# Flujo de prueba manual end-to-end — Módulo Bases de Licitud

Módulo de solo lectura: catálogo de fundamentos legales de la Ley 21.719, sembrado por `CatalogSeeder` al levantar el backend. No tiene endpoints de escritura.

## Prerrequisitos

- Backend corriendo en `http://localhost:8080`.
- Token con rol DPO, ADMIN o JEFE_DOMINIO (se usó `admin@leydata.cl` / `Admin1234!`, igual que en `agreements-manual-test-flow.md` paso 1).

## 1. Listar Bases de Licitud

```
GET http://localhost:8080/api/legal-basis
Authorization: Bearer {token}
```

`200 OK` — devuelve las 6 bases sembradas:

| code | consentRequired | id |
|---|---|---|
| `CONSENTIMIENTO` | true | `826c67f5-3e42-4f7c-8aa6-2465eab95b55` |
| `CONTRATO` | false | `39beaf3a-df40-4c7f-9ffd-797d0dbbf04a` |
| `OBLIGACION_LEGAL` | false | `dbb2f614-83ab-4e1b-88f7-0c15e194021d` |
| `INTERES_VITAL` | false | `fb14bff6-9533-4111-a37e-7c0adf013c7d` |
| `INTERES_PUBLICO` | false | `1ab3505e-971b-4f25-8fc4-ec47fb5b4099` |
| `INTERES_LEGITIMO` | false | `52d4da5f-a3d7-40be-9c54-0331fccb5dae` |

## 2. Bases que Requieren Consentimiento

```
GET http://localhost:8080/api/legal-basis/consent
Authorization: Bearer {token}
```

`200 OK` — devuelve solo `CONSENTIMIENTO` (única con `consentRequired=true`), consistente con el listado completo.

## 3. Obtener Base de Licitud por ID

```
GET http://localhost:8080/api/legal-basis/826c67f5-3e42-4f7c-8aa6-2465eab95b55
Authorization: Bearer {token}
```

`200 OK` — devuelve el detalle de `CONSENTIMIENTO`, coincide con el registro del listado.

## Resultado

Los 3 endpoints responden `200 OK` con datos consistentes entre sí. No se encontraron bugs.

## IDs usados en la corrida de referencia (01/07/2026)

| Recurso | ID |
|---|---|
| Base de licitud (CONSENTIMIENTO) | `826c67f5-3e42-4f7c-8aa6-2465eab95b55` |
