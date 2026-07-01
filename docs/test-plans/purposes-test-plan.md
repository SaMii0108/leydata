# Plan de pruebas: Módulo Finalidades (catálogo y versionado)

## Pruebas unitarias (`PurposeService`, mocks de repositorios)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `create()`

| # | Caso | Notas |
|---|------|-------|
| U1 | Crea la finalidad en versión 1, `status=ACTIVE`, `purposeFamilyId` = su propio ID, con hash calculado | — |
| U2 | Lanza `BusinessValidationException` si el `code` ya existe | — |
| U3 | Lanza `BusinessValidationException` si la base de licitud no existe | — |
| U4 | Lanza `BusinessValidationException` si el dominio no existe | — |
| U5 | Lanza `BusinessValidationException` si el dominio está desactivado | — |

### `listAll()`

| # | Caso | Notas |
|---|------|-------|
| U6 | `ADMIN`/`DPO` ven todas las finalidades activas | — |
| U7 | `JEFE_DOMINIO` ve solo las finalidades de sus dominios asignados | Filtra por `user_domains` del actor |

### `getById()`

| # | Caso | Notas |
|---|------|-------|
| U8 | Devuelve la finalidad con `locked` calculado | — |
| U9 | Lanza `PurposeNotFoundException` si no existe | — |
| U10 | `JEFE_DOMINIO` sin acceso al dominio de la finalidad → `AccessDeniedException` | — |
| U11 | `JEFE_DOMINIO` con acceso al dominio → devuelve la finalidad | — |

### `listByDomain()`

| # | Caso | Notas |
|---|------|-------|
| U12 | `JEFE_DOMINIO` sin acceso al dominio consultado → `AccessDeniedException` | — |
| U13 | `ADMIN`/`DPO` puede consultar cualquier dominio | — |

### `update()`

| # | Caso | Notas |
|---|------|-------|
| U14 | Actualiza los campos enviados y recalcula el hash | — |
| U15 | Lanza `BusinessValidationException` si la finalidad está bloqueada (`locked`) | — |
| U16 | Lanza `BusinessValidationException` si el nuevo `legalBasisId` no existe | — |
| U17 | Campos nulos en el request no sobrescriben los valores existentes | Actualización parcial |

### `deactivate()`

| # | Caso | Notas |
|---|------|-------|
| U18 | Desactiva correctamente una finalidad sin vínculos | — |
| U19 | Lanza `BusinessValidationException` si la finalidad está bloqueada | — |
| U20 | Lanza `BusinessValidationException` si tiene categorías de datos vinculadas | Debe desvincularse primero |

### `newVersion()`

| # | Caso | Notas |
|---|------|-------|
| U21 | Crea la nueva versión (`version+1`), aplica los campos enviados, y marca el origen como `SUPERSEDED` | Solo permitido si el origen está bloqueado (`locked=true`) — condición inversa a `update()`/`deactivate()` |
| U22 | Los campos no enviados en el request se heredan del origen | — |
| U23 | Lanza `PurposeNotLockedException` si el origen NO está bloqueado | Si no está bloqueada, se debe usar `update()` en su lugar |

### `getFamily()`

| # | Caso | Notas |
|---|------|-------|
| U24 | Devuelve el historial de versiones de una familia, ordenado descendente | — |

### `getActiveByFamily()`

| # | Caso | Notas |
|---|------|-------|
| U25 | Devuelve la versión `ACTIVE` de la familia | — |
| U26 | Lanza `BusinessValidationException` si no hay ninguna versión `ACTIVE` | — |

### `recalculateHash()`

| # | Caso | Notas |
|---|------|-------|
| U27 | Devuelve el hash SHA-256 recalculado sobre los campos vigentes en BD, sin persistir | Usado por `IntegrityVerifier` (ver `audit-test-plan.md`) |
