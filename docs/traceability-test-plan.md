# Plan de pruebas: Módulo Trazabilidad (`ENTITY_INTEGRITY_LOG`, versionado de `PURPOSES`, `/trace`)

| # | Caso | Precondición | Pasos | Resultado esperado |
|---|------|--------------|-------|---------------------|
| 1 | Verificar integridad de un AGREEMENT válido | Agreement sin alteraciones | POST `/api/audit/integrity/verify` `{entityType: AGREEMENT, entityId, checkType: MANUAL}` | 200, `isValid=true`, queda registrado en `ENTITY_INTEGRITY_LOG` |
| 2 | Verificar integridad de un AGREEMENT alterado | Agreement con datos modificados manualmente en BD | POST `/api/audit/integrity/verify` | 200, `isValid=false`, `errorDetail` poblado |
| 3 | Verificar integridad de TEMPLATE / DOCUMENT / PURPOSE | Entidad existente de cada tipo | POST `/api/audit/integrity/verify` con `entityType` correspondiente | 200, recalcula con la fórmula de hash propia de cada tipo |
| 4 | Verificar integridad con `entityType` inválido | — | POST `/api/audit/integrity/verify` `{entityType: "X"}` | 400/422, error de validación |
| 5 | Verificar integridad de entidad inexistente | `entityId` no existe | POST `/api/audit/integrity/verify` | 404 |
| 6 | Historial de integridad de una entidad | Entidad con varias verificaciones | GET `/api/audit/integrity/log?entityType=&entityId=` | 200, lista ordenada desc por fecha |
| 7 | Listar verificaciones fallidas (todas) | Hay registros con `isValid=false` | GET `/api/audit/integrity/failed` | 200, solo los fallidos |
| 8 | Listar verificaciones fallidas filtradas por tipo | Hay fallidos de varios tipos | GET `/api/audit/integrity/failed?entityType=PURPOSE` | 200, solo fallidos de ese tipo |
| 9 | Job `SCHEDULED` recorre las 4 entidades | Existen agreements/templates/documents/purposes | Disparo del cron (3:00 AM) | Se crea un registro `ENTITY_INTEGRITY_LOG` por cada entidad, `checkType=SCHEDULED` |
| 10 | `ENTITY_INTEGRITY_LOG` es de solo inserción | Existe al menos un registro | Intentar `UPDATE`/`DELETE` directo en BD | Rechazado por el trigger PostgreSQL |
| 11 | Crear purpose (versión 1) | No existe `code` previo | POST `/api/purposes` | 201, `version=1`, `status=ACTIVE`, `purposeFamilyId` = id propio |
| 12 | Editar purpose no bloqueada | Purpose sin documento PUBLISHED ni template con agreements | PUT `/api/purposes/{id}` | 200, edición in-place, hash recalculado |
| 13 | Editar purpose bloqueada por documento PUBLISHED | Purpose vinculada a documento PUBLISHED | PUT `/api/purposes/{id}` | 400/422, bloqueada |
| 14 | Editar purpose bloqueada por template+agreement | Purpose vinculada a template con ≥1 agreement | PUT `/api/purposes/{id}` | 400/422, bloqueada (regla nueva) |
| 15 | Nueva versión de purpose bloqueada | Purpose bloqueada (documento o template+agreement) | POST `/api/purposes/{id}/new-version` | 201, nueva fila `version+1`, `status=ACTIVE`; la anterior pasa a `SUPERSEDED` |
| 16 | Nueva versión de purpose NO bloqueada | Purpose editable in-place | POST `/api/purposes/{id}/new-version` | 409, corresponde editar in-place |
| 17 | Historial de versiones de una familia | Familia con N versiones | GET `/api/purposes/family/{purposeFamilyId}` | 200, lista ordenada desc por versión |
| 18 | Versión activa de una familia | Familia con una versión `ACTIVE` | GET `/api/purposes/active/{purposeFamilyId}` | 200, devuelve la `ACTIVE` |
| 19 | Versión activa sin ninguna activa | Familia sin status `ACTIVE` (caso inconsistente) | GET `/api/purposes/active/{purposeFamilyId}` | 422, no hay versión activa |
| 20 | Trace de un agreement íntegro | Agreement con documento/template/purposes sin alterar | GET `/api/audit/trace/agreement/{id}` | 200, `overallIntegrity=OK`, todos los eslabones `isValid=true` |
| 21 | Trace con template alterado | `Templates.hash_sha256` no coincide con el contenido actual | GET `/api/audit/trace/agreement/{id}` | 200, `template.isValid=false`, `overallIntegrity=MISMATCH` |
| 22 | Trace con documento sin hash calculable | `PrivacyDocuments.hash_sha256` es `null` (nunca publicado) | GET `/api/audit/trace/agreement/{id}` | 200, `document.isValid=false`, `overallIntegrity=PARTIAL` |
| 23 | Trace con purpose eliminada | La purpose referenciada por `AgreementsPurposes.purposeId` ya no existe | GET `/api/audit/trace/agreement/{id}` | 200, `integrityStatus=UNKNOWN` en esa purpose, `overallIntegrity=PARTIAL` |
| 24 | Trace de agreement inexistente | `agreementId` no existe | GET `/api/audit/trace/agreement/{id}` | 404 |
| 25 | Trace no escribe ningún log | Cualquier escenario | GET `/api/audit/trace/agreement/{id}` | No se crea ningún registro nuevo en `ENTITY_INTEGRITY_LOG` ni `SYSTEM_AUDIT_LOG` (regla 13) |

---

## Pruebas unitarias

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `IntegrityVerifier.verify()` — [`IntegrityVerifierTest`](../backend/src/test/java/com/leydata/backend/audit/application/service/IntegrityVerifierTest.java)

| # | Caso |
|---|------|
| U1 | `AGREEMENT`: `isValid=true` cuando el hash recalculado (`AgreementService.recalculateHash`) coincide con el almacenado |
| U2 | `AGREEMENT`: `isValid=false` + `errorDetail` poblado cuando no coincide |
| U3 | `AGREEMENT`: lanza `AgreementNotFoundException` si no existe |
| U4 | `TEMPLATE`: `isValid=true` cuando coincide (`TemplateService.recalculateHash`) |
| U5 | `TEMPLATE`: lanza `TemplateNotFoundException` si no existe |
| U6 | `DOCUMENT`: `isValid=false` cuando no coincide (`PrivacyDocumentService.recalculateHash`) |
| U7 | `DOCUMENT`: lanza `DocumentNotFoundException` si no existe |
| U8 | `PURPOSE`: `isValid=true` cuando coincide (`PurposeService.recalculateHash`) |
| U9 | `PURPOSE`: lanza `PurposeNotFoundException` si no existe |
| U10 | Lanza `BusinessValidationException` si `entityType` no es uno de los 4 soportados |
| U11 | El log creado encadena `previousHashSha256Id` contra el último `EntityIntegrityLog.hashSha256` |
| U12 | Usa `"GENESIS"` como `previousHashSha256Id` cuando no hay logs previos |

### `IntegrityScheduler.verifyAllEntities()` — [`IntegritySchedulerTest`](../backend/src/test/java/com/leydata/backend/audit/application/service/IntegritySchedulerTest.java)

| # | Caso |
|---|------|
| U13 | Recorre `AGREEMENT`, `TEMPLATE`, `DOCUMENT` y `PURPOSE`, llamando a `IntegrityVerifier.verify(..., "SCHEDULED", null)` por cada entidad encontrada |
| U14 | Si `verify()` lanza una excepción para una entidad, la loguea y continúa con el resto (no aborta el batch) |
| U15 | No falla si no hay entidades de ningún tipo |

### `PurposeService` (versionado e integridad) — [`PurposeServiceTest`](../backend/src/test/java/com/leydata/backend/purposes/application/service/PurposeServiceTest.java)

| # | Caso | Regla |
|---|------|-------|
| U16 | `create()`: crea con `version=1`, `status=ACTIVE`, `purposeFamilyId` autorreferenciado al id propio | 7 |
| U17 | `create()`: lanza `BusinessValidationException` si el código ya existe | — |
| U18 | `create()`: lanza `BusinessValidationException` si la base de licitud no existe | — |
| U19 | `create()`: lanza `BusinessValidationException` si el dominio está desactivado | — |
| U20 | `update()`: edita in-place cuando la purpose no está bloqueada | 10 |
| U21 | `update()`: lanza `BusinessValidationException` si está bloqueada por documento `PUBLISHED` | — |
| U22 | `update()`: lanza `BusinessValidationException` si está bloqueada por template con ≥1 agreement | 9 |
| U23 | `update()`: NO bloquea si el template vinculado no tiene agreements | 9 |
| U24 | `deactivate()`: desactiva cuando no tiene categorías de datos activas | — |
| U25 | `deactivate()`: lanza `BusinessValidationException` si tiene categorías de datos activas | — |
| U26 | `newVersion()`: crea nueva versión (`version+1`, `status=ACTIVE`, mismo `purposeFamilyId`) y pasa la anterior a `SUPERSEDED` en la misma operación, cuando está bloqueada | 8,9 |
| U27 | `newVersion()`: lanza `PurposeNotLockedException` si NO está bloqueada | — |
| U28 | `newVersion()`: lanza `PurposeNotFoundException` si la purpose origen no existe | — |
| U29 | `getFamily()`: devuelve las versiones de una familia ordenadas descendente | — |
| U30 | `getActiveByFamily()`: devuelve la versión `ACTIVE` de una familia | 8 |
| U31 | `getActiveByFamily()`: lanza `BusinessValidationException` si no hay ninguna `ACTIVE` | — |
| U32 | `recalculateHash()`: devuelve el mismo hash para el mismo contenido (determinístico) | 2 |
| U33 | `recalculateHash()`: lanza `PurposeNotFoundException` si no existe | — |

### `AgreementService.recalculateHash()` — [`AgreementServiceTest`](../backend/src/test/java/com/leydata/backend/agreement/application/service/AgreementServiceTest.java)

| # | Caso |
|---|------|
| U34 | Devuelve el mismo hash para el mismo contenido (determinístico) |
| U35 | Lanza `AgreementNotFoundException` si no existe |

### `AgreementTraceService.trace()` — [`AgreementTraceServiceTest`](../backend/src/test/java/com/leydata/backend/audit/application/service/AgreementTraceServiceTest.java)

| # | Caso | Regla |
|---|------|-------|
| U36 | Lanza `AgreementNotFoundException` si el agreement no existe | — |
| U37 | `overallIntegrity=OK` cuando documento, template y todas las purposes son válidos | 15 |
| U38 | `overallIntegrity=MISMATCH` cuando el template fue alterado (hash no coincide) | 15 |
| U39 | `overallIntegrity=PARTIAL` cuando el documento no tiene hash calculable (`hash_sha256=null`) | 14,15 |
| U40 | `integrityStatus=UNKNOWN` en una purpose cuando la fila fue eliminada de `PURPOSES` | 14 |
| U41 | No depende de ningún repositorio de logs — ausencia estructural de escritura (regla 13) | 13 |

---

## Cobertura no incluida (fuera de alcance de esta entrega)

- Tests de integración con base de datos real para el trigger de solo-inserción de `entity_integrity_log` (caso #10 de la tabla manual) — requiere un entorno con Postgres, no cubierto por los mocks de Mockito.
- `AuditController` no tiene `AuditControllerTest` (ni los endpoints viejos de integridad de `AgreementController` lo tenían) — se verificó manualmente vía compilación y arranque del backend, no hay test de capa web (`MockMvc`) para los 4 endpoints nuevos.
