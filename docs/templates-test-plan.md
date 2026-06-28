# Plan de pruebas: Módulo Templates de Consentimiento

| # | Caso | Precondición | Pasos | Resultado esperado |
|---|------|--------------|-------|---------------------|
| 1 | Crear template (versión 1) | No existe `TEMPLATE_KEY` previo | POST `/api/templates` con `templateKey`, título, botones | 201, template creado en estado `DRAFT`, versión 1 |
| 2 | `TEMPLATE_KEY` no UPPERCASE | — | POST `/api/templates` con `templateKey` en minúsculas | 400, error de validación (regla 4) |
| 3 | Crear nueva versión de template existente | Existe template ACTIVE o APPROVED con `TEMPLATE_KEY` X | POST `/api/templates/{id}/new-version` | 201, nueva versión N+1 en `DRAFT`, versión anterior sin cambios de estado todavía |
| 4 | Editar template ACTIVE | Template en estado `ACTIVE` | PATCH/operación de edición sobre ese template | 400/403, error "no se puede editar template activo" (regla 3) |
| 5 | Obtener template por ID | Template existente | GET `/api/templates/{id}` | 200, datos del template |
| 6 | Obtener template por ID inexistente | ID no existe | GET `/api/templates/{id}` | 404, `TemplateNotFoundException` |
| 7 | Listar templates sin filtros | Existen varios templates | GET `/api/templates` | 200, lista completa |
| 8 | Listar templates filtrando por `templateKey` | Existen versiones de varios keys | GET `/api/templates?templateKey=X` | 200, solo versiones del key X |
| 9 | Listar templates filtrando por `status` | Existen templates en distintos estados | GET `/api/templates?status=DRAFT` | 200, solo templates en ese estado |
| 10 | Historial de versiones | `TEMPLATE_KEY` con N versiones | GET `/api/templates/family/{templateKey}` | 200, lista de las N versiones ordenadas |
| 11 | Obtener versión activa | `TEMPLATE_KEY` con una versión ACTIVE | GET `/api/templates/active/{templateKey}` | 200, devuelve la versión ACTIVE |
| 12 | Obtener versión activa sin ninguna activa | `TEMPLATE_KEY` sin versión ACTIVE | GET `/api/templates/active/{templateKey}` | 404 o respuesta vacía (a confirmar comportamiento esperado) |
| 13 | Aprobar template válido | Template en `DRAFT` con ≥1 purpose visible | POST `/api/templates/{id}/approve` | 200, estado `APPROVED`, `APPROVED_BY` y `APPROVED_AT` seteados |
| 14 | Aprobar template sin purpose visible | Template en `DRAFT` sin purposes con `IS_VISIBLE=true` | POST `/api/templates/{id}/approve` | 400, error regla 9 |
| 15 | Aprobar template que no está en `DRAFT` | Template en `APPROVED` o `ACTIVE` | POST `/api/templates/{id}/approve` | 400/409, transición de estado inválida |
| 16 | Activar template válido | Template `APPROVED`, con purpose visible y `APPROVED_BY` | POST `/api/templates/{id}/activate` | 200, estado `ACTIVE`; si existía otra versión ACTIVE del mismo key, queda desactivada en la misma transacción (regla 2) |
| 17 | Activar template sin `APPROVED_BY` | Template `APPROVED` pero sin `APPROVED_BY` (caso inconsistente) | POST `/api/templates/{id}/activate` | 400, error regla 10 |
| 18 | Activar template sin purpose visible | Template `APPROVED` sin purposes visibles | POST `/api/templates/{id}/activate` | 400, error regla 10 |
| 19 | Activar template en estado `DRAFT` | Template `DRAFT` | POST `/api/templates/{id}/activate` | 400/409, transición inválida (debe pasar por `APPROVED`) |
| 20 | Activación automática vía `ACTIVATION_DATE` | Template `APPROVED` con `ACTIVATION_DATE` en el pasado/presente | Job o proceso de activación automática | Template pasa a `ACTIVE` automáticamente |
| 21 | `ACTIVATION_DATE` nulo | Template `APPROVED` sin `ACTIVATION_DATE` | — | Activación permanece manual, no se dispara automáticamente |
| 22 | Vincular purpose aprobada y activa | Template `DRAFT`, purpose en estado aprobado/activo | POST `/api/templates/{id}/purposes` con `orderPosition`, `isVisible` | 201, purpose vinculada |
| 23 | Vincular purpose no aprobada/inactiva | Purpose en `DRAFT` o inactiva | POST `/api/templates/{id}/purposes` | 400, error regla 7 |
| 24 | Vincular purpose con `ORDER_POSITION` duplicado | Ya existe una purpose con esa posición en el template | POST `/api/templates/{id}/purposes` | 400, error regla 8 |
| 25 | Vincular purpose en template no `DRAFT` | Template `APPROVED` o `ACTIVE` | POST `/api/templates/{id}/purposes` | 400/409, solo permitido en `DRAFT` |
| 26 | Desvincular purpose en `DRAFT` | Template `DRAFT` con purpose vinculada | DELETE `/api/templates/{id}/purposes/{purposeId}` | 200/204, purpose eliminada (hard delete) |
| 27 | Desvincular purpose en template no `DRAFT` | Template `APPROVED` o `ACTIVE` | DELETE `/api/templates/{id}/purposes/{purposeId}` | 400/409, error — solo permitido en `DRAFT` (regla 10) |
| 28 | Listar purposes del template | Template con varias purposes vinculadas | GET `/api/templates/{id}/purposes` | 200, lista ordenada por `ORDER_POSITION` |
| 29 | Actualizar `ORDER_POSITION`/`IS_VISIBLE` de purpose | Template `DRAFT`, purpose vinculada | PATCH `/api/templates/{id}/purposes/{purposeId}` | 200, valores actualizados |
| 30 | Actualizar `ORDER_POSITION` a uno ya ocupado | Template `DRAFT` con otra purpose en esa posición | PATCH `/api/templates/{id}/purposes/{purposeId}` con posición duplicada | 400, error regla 8 |
| 31 | Eliminar template referenciado por `PRIVACY_DOCUMENT` activo | Template vinculado a un `PRIVACY_DOCUMENT` activo | Intento de eliminación | 400/409, error regla 5 |
| 32 | Versionado conserva evidencia | Template con versiones anteriores desactivadas y con acuerdos generados | GET historial / consulta de versión vieja | Versión anterior sigue accesible con sus datos y conteo de acuerdos asociados (regla 6) |
