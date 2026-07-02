# Plan de pruebas: Módulo Documentos de Privacidad

## Pruebas unitarias (`PrivacyDocumentService`, mocks de repositorios y colaboradores)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service). `isPrivilegedUser()` lee `SecurityContextHolder` directamente (no pasa por `SecurityContextHelper`), por lo que los tests de visibilidad pública setean una `Authentication` real en el contexto de seguridad (`ROLE_DPO` vs. `ROLE_TITULAR`) en vez de mockear un colaborador — con `@AfterEach` limpiando el contexto entre tests.

### `create()`

| # | Caso | Notas |
|---|------|-------|
| U1 | Crea el documento en `DRAFT`, versión 1, `documentFamilyId` = su propio ID | — |
| U2 | Lanza `BusinessValidationException` si la template enviada no está activa | — |

### `getById()`

| # | Caso | Notas |
|---|------|-------|
| U3 | Usuario privilegiado (`DPO`/`ADMIN`) ve cualquier documento con el detalle completo (`from()`) | — |
| U4 | Usuario público y documento no `PUBLISHED` → `AccessDeniedException` | — |
| U5 | Usuario público y documento `PUBLISHED` → versión pública (`fromPublic()`) | — |

### `update()`

| # | Caso | Notas |
|---|------|-------|
| U6 | Actualiza los campos enviados mientras está en `DRAFT` | — |
| U7 | Lanza `BusinessValidationException` si no está en `DRAFT` | — |

### `deactivate()`

| # | Caso | Notas |
|---|------|-------|
| U8 | Desactiva si no tiene finalidades activas asociadas | — |
| U9 | Lanza `BusinessValidationException` si tiene finalidades activas | — |

### `addPurpose()`

| # | Caso | Notas |
|---|------|-------|
| U10 | Vincula una nueva finalidad al documento en `DRAFT` | — |
| U11 | Si existía un vínculo soft-deleted, lo reactiva en vez de crear uno nuevo | — |
| U12 | Lanza `BusinessValidationException` si ya está vinculada activamente | — |
| U13 | Lanza `BusinessValidationException` si la finalidad no está aprobada y activa | — |
| U14 | Lanza `BusinessValidationException` si el documento no está en `DRAFT` | — |

### `removePurpose()`

| # | Caso | Notas |
|---|------|-------|
| U15 | Desvincula la finalidad (soft-delete, queda como registro histórico) | — |
| U16 | Lanza `BusinessValidationException` si no hay vínculo activo | — |

### `submit()`

| # | Caso | Notas |
|---|------|-------|
| U17 | `DRAFT` listo (contenido, template y purposes) → `IN_REVIEW` | — |
| U18 | Lanza `BusinessValidationException` si no está en `DRAFT` | — |
| U19 | Lanza `BusinessValidationException` si el contenido está vacío | — |
| U20 | Lanza `BusinessValidationException` si no tiene template asignada | — |
| U21 | Lanza `BusinessValidationException` si no tiene ninguna finalidad activa vinculada | — |

### `resubmit()`

| # | Caso | Notas |
|---|------|-------|
| U22 | `REJECTED` listo → `IN_REVIEW`, limpia `rejectionReason` | — |
| U23 | Lanza `BusinessValidationException` si no está en `REJECTED` | — |

### `approve()`

| # | Caso | Notas |
|---|------|-------|
| U24 | `IN_REVIEW` → `APPROVED`, setea `approvedBy` | — |
| U25 | Lanza `InvalidTransitionException` si la transición no es válida | — |

### `reject()`

| # | Caso | Notas |
|---|------|-------|
| U26 | `IN_REVIEW` → `REJECTED` con motivo | — |
| U27 | Lanza `InvalidTransitionException` si la transición no es válida | — |

### `publish()`

| # | Caso | Notas |
|---|------|-------|
| U28 | `APPROVED` → `PUBLISHED`: genera PDF + hash SHA-256 vía `PdfGeneratorService` | — |
| U29 | Archiva automáticamente la versión `PUBLISHED` anterior del mismo template | A lo sumo un `PUBLISHED` por template |
| U30 | Notifica al solicitante original del `PurposeRequest` que originó la finalidad publicada | Trazabilidad del ticket — `NotificationType.PURPOSE_REQUEST_FULFILLED` |
| U31 | Lanza `BusinessValidationException` si no tiene finalidades activas | — |
| U32 | Lanza `BusinessValidationException` si alguna finalidad vinculada no está aprobada | — |
| U33 | Lanza `InvalidTransitionException` si la transición no es válida | — |

### `archive()`

| # | Caso | Notas |
|---|------|-------|
| U34 | `PUBLISHED` → `ARCHIVED` | — |
| U35 | Lanza `BusinessValidationException` si tiene finalidades activas | — |
| U36 | Lanza `InvalidTransitionException` si la transición no es válida | — |

### `downloadPdf()`

| # | Caso | Notas |
|---|------|-------|
| U37 | Devuelve los bytes del PDF almacenado | — |
| U38 | Lanza `BusinessValidationException` si no tiene PDF generado | — |

### `verify()`

| # | Caso | Notas |
|---|------|-------|
| U39 | Hash coincide → `hashMatch=true` | — |
| U40 | Hash no coincide → `hashMatch=false` con `computedHash` recalculado | Detecta corrupción/alteración del binario |
| U41 | Sin PDF generado → `hashMatch=false` con mensaje explicativo ("no ha sido publicado") | — |

### `recalculateHash()`

| # | Caso | Notas |
|---|------|-------|
| U42 | Sin PDF → devuelve `null` | — |
| U43 | Con PDF → devuelve el hash recalculado sin persistir | Usado por `IntegrityVerifier` (ver `audit-test-plan.md`) |

### `getActive()`

| # | Caso | Notas |
|---|------|-------|
| U44 | Devuelve la versión `PUBLISHED` activa de la categoría | — |
| U45 | Lanza `DocumentNotFoundException` si no hay ninguna publicada | — |

### `newVersion()`

| # | Caso | Notas |
|---|------|-------|
| U46 | Crea una nueva versión `DRAFT` (`version+1`) heredando los campos del origen | — |
| U47 | Lanza `BusinessValidationException` si el origen está `ARCHIVED` | — |
| U48 | Lanza `BusinessValidationException` si ya existe un `DRAFT` activo en la misma familia | — |

### `getByFamily()`

| # | Caso | Notas |
|---|------|-------|
| U49 | Usuario privilegiado ve todas las versiones de la familia | — |
| U50 | Usuario público ve solo las versiones `PUBLISHED`, en formato público | — |
