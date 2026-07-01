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
| 13 | Verificar integridad de un agreement | Agreement sin alteraciones | POST `/api/audit/integrity/verify` body `{entityType:"AGREEMENT", entityId:"<id>", checkType:"MANUAL"}` | 200, `isValid:true`, registro en `entity_integrity_log` |
| 14 | Verificar integridad — hash inválido | Agreement con datos alterados manualmente | POST `/api/audit/integrity/verify` | 200, `isValid:false`, `errorDetail` con la discrepancia |
| 15 | Historial de verificaciones de un agreement | Agreement con varias verificaciones | GET `/api/audit/integrity/log?entityType=AGREEMENT&entityId=<id>` | 200, lista ordenada |
| 16 | Eliminar (DELETE) un agreement | Agreement existente | DELETE `/api/agreements/{id}` | 405/no soportado — los agreements no se eliminan (regla 13) |
| 17 | Eliminar un DATA_SUBJECT con agreements asociados | DataSubject con agreements asociados | DELETE del data subject | No se puede eliminar físicamente (regla 13, `ON DELETE RESTRICT`) — se anonimiza el `IDENTIFIER` en su lugar, los agreements permanecen intactos como evidencia legal |
| 18 | Consultar agreement ACTIVE para (dataSubjectId, templateId) | DataSubject con un agreement ACTIVE en ese template | GET `/api/agreements/active?dataSubjectId=X&templateId=Y` | 200, devuelve el agreement ACTIVE |
| 19 | Consultar agreement ACTIVE cuando no hay ninguno | DataSubject sin agreements ACTIVE en ese template | GET `/api/agreements/active?dataSubjectId=X&templateId=Y` | 404 |
| 20 | Reconsentimiento — ya existe un ACTIVE para el mismo (dataSubject, template) | Agreement ACTIVE previo | POST `/api/agreements` con el mismo dataSubjectId/templateId | 201, el agreement anterior pasa a `REVOKED` (y sus AGREEMENTS_PURPOSES heredan REVOKED), el nuevo queda `ACTIVE` con `PREVIOUS_AGREEMENTS_ID` apuntando al cerrado (regla 15, 6.1, 9) |
| 21 | Documento no cubre alguna purpose del request | Template con purpose visible no vinculada al documento vía DOCUMENT_PURPOSES | POST `/api/agreements` | 400, error (regla 16) |
| 22 | Listar verificaciones fallidas de agreements | Hay al menos un registro con `isValid:false` | GET `/api/audit/integrity/failed?entityType=AGREEMENT` | 200, lista solo con los fallidos |

---

## Pruebas unitarias (`AgreementService`, mocks de repositorios)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `create()`

| # | Caso | Regla |
|---|------|-------|
| U1 | Crea agreement correctamente con purposes válidas, calcula hash, devuelve `AgreementResponse` completo | 1,4,6,7,10 |
| U2 | Lanza `BusinessValidationException` si `dataSubjectId` no existe | 12 |
| U3 | Lanza `BusinessValidationException` si `templateId` no existe | 2 |
| U4 | Lanza `BusinessValidationException` si el template no está `ACTIVE` | 2 |
| U5 | Lanza `BusinessValidationException` si `documentId` no existe | — |
| U6 | Lanza `BusinessValidationException` si falta una purpose visible del template en el request | 4 |
| U7 | Lanza `BusinessValidationException` si el request incluye una purpose no visible en el template | 4 |
| U8 | Lanza `BusinessValidationException` si el documento no cubre alguna purpose del request | 16 |
| U9 | Lanza `BusinessValidationException` si una purpose `required=true` viene con `accepted=false` | 5 |
| U10 | El snapshot de `AgreementsPurposes` copia code/name/description/shortDescription/required/revocable/hash/legalBasisCode desde `Purposes` al momento de creación | 6,7 |
| U11 | `templateVersion` del agreement queda igual al `version` actual del template | 3 |
| U12 | Si ya existe un `ACTIVE` para `(dataSubjectId, templateId)`: lo pasa a `REVOKED`, cascadea `REVOKED` a sus `AgreementsPurposes`, y el nuevo agreement queda con `previousAgreementsId` apuntando al cerrado | 15, 6.1, 9 |
| U13 | Si NO existe un `ACTIVE` previo: `previousAgreementsId` queda `null` | 15 |
| U14 | El hash del nuevo agreement encadena (`previousHashSha256`) contra el `hashSha256` del último `Agreements` insertado | 17 |
| U15 | Cada `AgreementsPurposes` encadena su propio `previousHashSha256` contra el último `AgreementsPurposes.hashSha256` insertado | 17 |
| U16 | `AgreementMetadata` se guarda con `ipOrigin`/`userAgent` recibidos como parámetros y los campos del body (`captureChannel`, etc.) | 14 |
| U17 | Si el request no trae bloque `metadata`, no rompe — guarda metadata solo con ip/userAgent | — |

### `getById()`

| # | Caso |
|---|------|
| U18 | Devuelve el agreement con sus purposes y metadata ensamblados |
| U19 | Lanza `AgreementNotFoundException` si no existe |

### `list()`

| # | Caso |
|---|------|
| U20 | Filtra por `dataSubjectId` |
| U21 | Filtra por `templateId` cuando no hay `dataSubjectId` |
| U22 | Filtra por `status` cuando no hay `dataSubjectId` ni `templateId` |
| U23 | Sin filtros, devuelve todos |
| U24 | Combina filtros (ej. `dataSubjectId` + `status`) |

### `getActive()`

| # | Caso |
|---|------|
| U25 | Devuelve el agreement si hay uno `ACTIVE` para `(dataSubjectId, templateId)` |
| U26 | Devuelve `Optional.empty()` si no hay ninguno |

### `recalculateHash()`

| # | Caso | Regla |
|---|------|-------|
| U27 | Devuelve el hash SHA-256 del agreement sin escribir en BD (lectura pura) | 10 |
| U28 | Lanza `AgreementNotFoundException` si el agreement no existe | — |

> **Nota:** la lógica de escribir en `entity_integrity_log` y el scheduler diario migraron a `IntegrityVerifier` y `IntegrityScheduler` en el módulo `audit/`. Ver `docs/audit-module.md` para los tests de esas clases.
