# Plan de pruebas: Módulo Vínculo Finalidad ↔ Categoría de Datos

## Pruebas unitarias (`PurposeDataCategoryService`, mocks de repositorios)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `listByPurpose()`

| # | Caso | Notas |
|---|------|-------|
| U1 | Devuelve los vínculos de la finalidad, calculando `retentionLocked` según si hay documento `PUBLISHED` | — |

### `getById()`

| # | Caso | Notas |
|---|------|-------|
| U2 | Devuelve el vínculo por ID | — |
| U3 | Lanza `PurposeDataCategoryNotFoundException` si no existe | — |

### `link()`

| # | Caso | Notas |
|---|------|-------|
| U4 | Vincula la categoría a la finalidad y crea la política de retención en la misma operación | La política nunca queda sin definir |
| U5 | Lanza `BusinessValidationException` si la finalidad no existe | — |
| U6 | Lanza `BusinessValidationException` si la finalidad no está aprobada y activa (`approvedBy=null` o `isActive=false`) | — |
| U7 | Lanza `BusinessValidationException` si la categoría de datos no existe | — |
| U8 | Lanza `BusinessValidationException` si la categoría de datos está inactiva | — |
| U9 | Lanza `RetentionPolicyLockedException` si la finalidad está en un documento `PUBLISHED` | Agregar categorías ampliaría el alcance del consentimiento sin que el titular lo haya visto |
| U10 | Lanza `BusinessValidationException` si la categoría ya está vinculada a la finalidad | — |

### `updateRetention()`

| # | Caso | Notas |
|---|------|-------|
| U11 | Actualiza la política de retención existente con los nuevos valores | — |
| U12 | Lanza `RetentionPolicyLockedException` si la finalidad está en documento `PUBLISHED` | Cambiar la retención requiere nueva versión del documento |
| U13 | Lanza `PurposeDataCategoryNotFoundException` si el vínculo no existe | — |
| U14 | Si no existe una política previa, crea una nueva en su lugar (`orElseGet`) | Caso defensivo — normalmente `link()` ya la creó |
| U15 | Si `legalJustification` viene nulo en el request, no sobrescribe el valor existente | Actualización parcial de ese campo específico |

### `unlink()`

| # | Caso | Notas |
|---|------|-------|
| U16 | Desvincula la categoría y elimina también su política de retención asociada (1:1) | — |
| U17 | Si no hay política de retención asociada, no falla (`ifPresent`) | — |
| U18 | Lanza `RetentionPolicyLockedException` si la finalidad está en documento `PUBLISHED` | El documento declara ese dato — desvincular rompería la trazabilidad |
| U19 | Lanza `PurposeDataCategoryNotFoundException` si el vínculo no existe | — |
