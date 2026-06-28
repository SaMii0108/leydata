# Plan de pruebas: Módulo Agreements

| # | Caso | Precondición | Pasos | Resultado esperado |
|---|------|--------------|-------|---------------------|
| 1 | Crear agreement contra template ACTIVE | Template ACTIVE con purposes visibles | POST `/api/agreements` con dataSubjectId, templateId y respuestas por purpose | 201, agreement creado en `ACTIVE`, snapshot en AGREEMENTS_PURPOSES por cada purpose visible |
| 2 | Crear agreement contra template no ACTIVE | Template en `DRAFT`/`APPROVED` | POST `/api/agreements` | 400/409, error (regla 2) |
| 3 | Crear agreement omitiendo una purpose visible | Template con 3 purposes visibles, request con solo 2 | POST `/api/agreements` | 400, error (regla 4) |
| 4 | Crear agreement con purpose duplicada en el detalle | Request con la misma purposeId dos veces | POST `/api/agreements` | 400, viola unicidad (regla 4) |
| 5 | Rechazar una purpose REQUIRED | Template con purpose REQUIRED=true, request con accepted=false para ella | POST `/api/agreements` | 400, viola constraint (regla 5) |
| 6 | Aceptar/rechazar purposes no-required libremente | Template con purposes REQUIRED=false | POST `/api/agreements` con mezcla accepted true/false | 201, snapshot refleja exactamente lo enviado |
| 7 | Snapshot inmutable ante cambio posterior de la purpose | Agreement creado, luego se modifica la purpose original | Consultar el agreement tras el cambio | El snapshot en AGREEMENTS_PURPOSES no cambia (regla 6) |
| 8 | TEMPLATE_VERSION correcto en el snapshot | Template activo en versión N | POST `/api/agreements` | El agreement queda con `TEMPLATE_VERSION = N` (regla 3) |
| 9 | Obtener agreement por ID | Agreement existente | GET `/api/agreements/{id}` | 200, incluye detalle de purposes |
| 10 | Obtener agreement por ID inexistente | ID no existe | GET `/api/agreements/{id}` | 404 |
| 11 | Listar agreements por dataSubjectId | DataSubject con varios agreements | GET `/api/agreements?dataSubjectId=X` | 200, lista del titular |
| 12 | Listar agreements por templateId/status | Agreements en distintos templates/status | GET `/api/agreements?templateId=X&status=ACTIVE` | 200, filtrado correcto |
| 13 | Verificar integridad — hash válido | Agreement sin alteraciones | POST `/api/agreements/{id}/verify-integrity` | 200, `IS_VALID=true`, se crea registro en AGREEMENT_INTEGRITY_LOG |
| 14 | Verificar integridad — hash inválido | Agreement con datos alterados manualmente en BD (simulación) | POST `/api/agreements/{id}/verify-integrity` | 200/409, `IS_VALID=false`, error_detail con la discrepancia |
| 15 | Listar historial de integridad de un agreement | Agreement con varias verificaciones | GET `/api/agreements/{id}/integrity-log` | 200, lista ordenada |
| 16 | Eliminar (DELETE) un agreement | Agreement existente | DELETE `/api/agreements/{id}` | 405/no soportado — los agreements no se eliminan (regla 13) |
| 17 | Cascada al eliminar DATA_SUBJECT | DataSubject con agreements asociados | DELETE del data subject | Sus agreements se eliminan en cascada (regla 13, ON DELETE CASCADE) |
