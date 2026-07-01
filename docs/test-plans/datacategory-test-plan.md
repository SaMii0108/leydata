# Plan de pruebas: Módulo Categorías de Datos

## Pruebas unitarias (`DataCategoryService`, mocks de repositorios)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `listAll()`

| # | Caso | Notas |
|---|------|-------|
| U1 | Devuelve solo las categorías activas mapeadas | `isSystem=true` también se lista si está activa |

### `listSensitive()`

| # | Caso | Notas |
|---|------|-------|
| U2 | Devuelve solo las categorías sensibles y activas | — |

### `getById()`

| # | Caso | Notas |
|---|------|-------|
| U3 | Devuelve la categoría por ID | — |
| U4 | Lanza `DataCategoryNotFoundException` si no existe | — |

### `create()`

| # | Caso | Notas |
|---|------|-------|
| U5 | Crea la categoría con `code` normalizado a mayúsculas, `isSystem=false`, `isActive=true` | Categorías creadas por API nunca son `isSystem` |
| U6 | Lanza `BusinessValidationException` si ya existe una categoría con ese `code` (comparación en mayúsculas) | — |

### `update()`

| # | Caso | Notas |
|---|------|-------|
| U7 | Actualiza `code`/`name`/`description`/`isSensitive` con los valores enviados | — |
| U8 | Lanza `BusinessValidationException` si la categoría es del sistema (`isSystem=true`) | Las categorías Ley 21.719 no se editan |
| U9 | Lanza `BusinessValidationException` si el nuevo `code` ya existe en otra categoría | — |
| U10 | Si el `code` enviado es igual al actual (mismo valor en mayúsculas), no valida duplicado ni llama a `existsByCode` | Evita falso positivo de "código duplicado" contra sí misma |
| U11 | Campos nulos en el request no sobrescriben los valores existentes (actualización parcial) | — |

### `deactivate()`

| # | Caso | Notas |
|---|------|-------|
| U12 | Desactiva una categoría sin vínculos activos | — |
| U13 | Lanza `BusinessValidationException` si la categoría es del sistema | — |
| U14 | Lanza `BusinessValidationException` si está vinculada a alguna finalidad (`PurposeDataCategoryRepository.existsByDataCategoryId`) | Debe desvincularse primero de todas las finalidades |
| U15 | Lanza `DataCategoryNotFoundException` si no existe | — |
