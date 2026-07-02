# Plan de pruebas: Módulo Auditoría

> El módulo `audit/` tiene 4 servicios sin relación de herencia entre sí (ver `audit-module.md`). Cada uno tiene su propia sección de pruebas unitarias abajo, en el mismo orden en que se van implementando.

## Pruebas unitarias (`IntegrityScheduler`, mocks de repositorios y `IntegrityVerifier`)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `verifyAllEntities()`

| # | Caso | Notas |
|---|------|-------|
| U1 | Llama a `IntegrityVerifier.verify()` una vez por cada `Agreement`, `Template`, `PrivacyDocument` y `Purpose` existente, con `checkType="SCHEDULED"` y `actorId=null` | Cubre las 4 entidades con hash propio |
| U2 | Si una verificación individual lanza excepción, la captura y continúa con las siguientes (no interrumpe el batch) | El `catch` interno solo loggea el error |
| U3 | Si no hay entidades de ningún tipo, no llama a `verify()` | Caso base / lista vacía |

---

## Pruebas unitarias (`IntegrityVerifier`, mocks de repositorios y de los 4 services de negocio)

### `verify()`

| # | Caso | Notas |
|---|------|-------|
| U4 | `AGREEMENT` con hash almacenado == hash recalculado → `isValid=true`, `errorDetail=null` | Llama a `AgreementService.recalculateHash()` |
| U5 | `AGREEMENT` con hash almacenado != hash recalculado → `isValid=false`, `errorDetail="El hash recalculado no coincide con el almacenado"` | — |
| U6 | `AGREEMENT` inexistente → lanza `AgreementNotFoundException` | — |
| U7 | `TEMPLATE` válido → `isValid=true` | Llama a `TemplateService.recalculateHash()` |
| U8 | `TEMPLATE` inexistente → lanza `TemplateNotFoundException` | — |
| U9 | `DOCUMENT` válido → `isValid=true` | Llama a `PrivacyDocumentService.recalculateHash()` |
| U10 | `DOCUMENT` inexistente → lanza `DocumentNotFoundException` | — |
| U11 | `PURPOSE` válido → `isValid=true` | Llama a `PurposeService.recalculateHash()` |
| U12 | `PURPOSE` inexistente → lanza `PurposeNotFoundException` | — |
| U13 | `entityType` inválido (ej. `"USER"`) → lanza `BusinessValidationException` | Cubre tanto `readStoredHash()` como `recalculate()` |
| U14 | Primera verificación registrada (`integrityLogRepo` vacío) → `previousHashSha256Id = "GENESIS"` | Semilla de la cadena de hash del log |
| U15 | Verificación posterior a otra existente → `previousHashSha256Id` = hash de la última fila de `entity_integrity_log` | Encadenamiento del ledger |

---

## Pruebas unitarias (`AgreementTraceService`, mocks de repositorios y `PrivacyDocumentService`/`TemplateService`)

### `trace()`

| # | Caso | Notas |
|---|------|-------|
| U16 | Documento, template y purpose con hash coincidente → `overallIntegrity="OK"`, todos los eslabones `isValid=true` | Cadena completa sin drift |
| U17 | `agreementId` inexistente → lanza `AgreementNotFoundException` | — |
| U18 | Documento con hash alterado → `DocumentLink.isValid=false`, `overallIntegrity="MISMATCH"` | Recalcula vía `PrivacyDocumentService` |
| U19 | Template con hash alterado → `TemplateLink.isValid=false`, `overallIntegrity="MISMATCH"` | Recalcula vía `TemplateService` |
| U20 | Purpose con drift posterior al consentimiento (`purposeHash` snapshot ≠ hash actual) → `PurposeLink.isValid=false`, `integrityStatus="INTEGRITY_MISMATCH"`, `overallIntegrity="MISMATCH"` | Detecta cambios en la finalidad después de firmado el agreement |
| U21 | Documento inexistente → `DocumentLink.isValid=false`, `overallIntegrity="PARTIAL"` (sin `MISMATCH` en otros eslabones) | Eslabón `UNKNOWN` |
| U22 | Purpose inexistente → `integrityStatus="UNKNOWN"`, `overallIntegrity="PARTIAL"` | — |
| U23 | `purposeHash` nulo en el snapshot → se considera `UNKNOWN` (no se recalcula ni se compara) | Corresponde a `resolveState()` con `storedHash=null` |
| U24 | Si coexisten un eslabón `UNKNOWN` y uno `MISMATCH` en la misma traza, `overallIntegrity="MISMATCH"` (prioridad sobre `PARTIAL`) | Regla de prioridad de `overallIntegrity()` |
| U25 | Agreement con múltiples purposes → la respuesta incluye un `PurposeLink` por cada una | — |

---

## Pruebas unitarias (`AuditService`, mock del repositorio + `ObjectMapper` real)

> El campo `self` (auto-inyección `@Lazy` usada por `tryLog()` para pasar por el proxy AOP) se setea manualmente con `ReflectionTestUtils.setField()` en vez de por `@InjectMocks`, ya que el constructor generado por Lombok solo cubre los campos `final`.

### `log()`

| # | Caso | Notas |
|---|------|-------|
| U26 | Primer registro de la historia (`findTopByOrderByCreatedAtDesc` vacío) → `previousLogHash="GENESIS"` | Semilla del ledger |
| U27 | Con un registro previo → `previousLogHash` = `logHash` del último registro | Encadenamiento |
| U28 | Serializa `oldData`/`newData` a JSON antes de guardar | Usa `ObjectMapper` real, no mock |
| U29 | Si la serialización JSON falla (objeto sin serializador Jackson), usa `"{}"` sin propagar la excepción | Rama `catch` de `toJson()` |
| U30 | Fuera de un contexto de request HTTP (`RequestContextHolder` sin request activo) → `ipAddress="UNKNOWN"`, `userAgent="UNKNOWN"`, `requestId=null` | Caso natural en tests unitarios sin `MockMvc` |
| U31 | Adquiere el lock de la cadena (`acquireAuditChainLock`) antes de leer el último hash | Serialización de escrituras concurrentes |

### `tryLog()`

| # | Caso | Notas |
|---|------|-------|
| U32 | Delega en `log()` a través del proxy `self` y persiste normalmente | — |
| U33 | Si `log()` lanza una excepción, la captura y no la propaga | Usado para auditoría "best-effort" fuera de una transacción activa |

### `verifyChainIntegrity()`

| # | Caso | Notas |
|---|------|-------|
| U34 | Sin registros → `true` | Caso base |
| U35 | Cadena válida (hashes y `previousLogHash` correctos) → `true` | Genera los logs reales vía `log()` para obtener hashes válidos, evitando reimplementar SHA-256 en el test |
| U36 | `previousLogHash` de un registro no coincide con el hash esperado → `false` (cadena rota) | — |
| U37 | Contenido de un registro alterado después de calculado su hash (ej. `actorId`) → el hash recalculado no coincide con el almacenado → `false` | Simula alteración manual fuera de la aplicación |
