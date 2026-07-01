# Módulos: Categorías de Datos y Retención (`datacategory/` + `purposedatacategory/`)

---

## Módulo `datacategory/` — Catálogo de Categorías de Datos

**Endpoints base:** `/api/data-categories/**`  
**Escritura:** DPO · ADMIN  
**Lectura:** DPO · ADMIN · JEFE_DOMINIO

### Qué es una Categoría de Datos

Clasifica qué tipo de dato personal se trata (ej: SALUD, FINANCIERO, IDENTIFICACION). La Ley 21.719 distingue entre datos **sensibles** (requieren consentimiento explícito reforzado) y **no sensibles**.

### Categorías del sistema (`isSystem = true`)

Sembradas por `CatalogSeeder` al iniciar — no se pueden eliminar:

| Código | Tipo |
|---|---|
| `SALUD` | Sensible |
| `BIOMETRICO` | Sensible |
| `GENETICO` | Sensible |
| `VIDA_SEXUAL` | Sensible |
| `RELIGION` | Sensible |
| `POLITICO` | Sensible |
| `SINDICAL` | Sensible |
| `RACIAL` | Sensible |
| `IDENTIFICACION` | No sensible |
| `CONTACTO` | No sensible |
| `FINANCIERO` | No sensible |
| `LABORAL` | No sensible |
| `UBICACION` | No sensible |
| `ACADEMICO` | No sensible |
| `COMPORTAMIENTO` | No sensible |

Las categorías con `isSystem = true` no pueden modificarse ni eliminarse — son el catálogo legal base.

### Endpoints

```
POST   /api/data-categories           — Crear categoría personalizada
GET    /api/data-categories           — Listar todas (system + personalizadas)
GET    /api/data-categories/{id}      — Obtener por ID
PUT    /api/data-categories/{id}      — Actualizar (solo categorías no-system)
DELETE /api/data-categories/{id}      — Eliminar (solo categorías no-system)
```

---

## Módulo `purposedatacategory/` — Vínculo Finalidad ↔ Categoría + Retención

**Endpoints base:** `/api/purposes/{id}/data-categories/**`  
**Escritura:** DPO · ADMIN  
**Lectura:** DPO · ADMIN · JEFE_DOMINIO

### Qué es el vínculo Finalidad ↔ Categoría

Declara explícitamente qué categorías de datos trata cada finalidad y para qué uso. Esto es el registro de **inventario de tratamiento** exigido por la Ley 21.719.

Ejemplo: la finalidad "Facturación" trata `IDENTIFICACION` (para emitir boleta) y `FINANCIERO` (para cobrar). Cada par tiene su propio `DataUseType` y política de retención.

### `DataUseType` — Tipo de uso del dato

| Valor | Descripción |
|---|---|
| `COLLECTION` | Recolección inicial del dato |
| `PROCESSING` | Procesamiento (análisis, transformación) |
| `STORAGE` | Almacenamiento |
| `SHARING` | Compartir con terceros |
| `DELETION` | Proceso de eliminación |

### Política de Retención (`DataRetentionPolicies`)

Cada vínculo `purpose ↔ data-category` puede tener una política de retención que define cuánto tiempo se conserva el dato. Una vez definida y aprobada, la política queda **bloqueada** (`RetentionPolicyLockedException`) — no se puede modificar sin intervención del DPO.

```json
// POST /api/purposes/{purposeId}/data-categories/{categoryId}/retention
{
  "retentionDays": 365,
  "legalJustification": "Art. 17 Ley 21.719 — conservación mínima exigida"
}
```

### Endpoints

```
POST   /api/purposes/{id}/data-categories                     — Vincular categoría a finalidad
GET    /api/purposes/{id}/data-categories                     — Listar categorías de una finalidad
DELETE /api/purposes/{id}/data-categories/{categoryId}        — Desvincular
POST   /api/purposes/{id}/data-categories/{categoryId}/retention — Definir política de retención
GET    /api/purposes/{id}/data-categories/{categoryId}/retention — Obtener política
```

---

## Archivos clave

| Archivo | Rol |
|---|---|
| `datacategory/web/DataCategoryController.java` | CRUD de categorías |
| `datacategory/application/service/DataCategoryService.java` | Lógica + protección isSystem |
| `purposedatacategory/web/PurposeDataCategoryController.java` | Endpoints de vínculo y retención |
| `purposedatacategory/application/service/PurposeDataCategoryService.java` | Lógica + bloqueo de política |
| `purposedatacategory/domain/enums/DataUseType.java` | Enum de tipos de uso |
| `purposedatacategory/domain/exception/RetentionPolicyLockedException.java` | Error cuando se intenta modificar política bloqueada |
