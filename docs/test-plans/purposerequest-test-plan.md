# Plan de pruebas: Módulo Solicitudes de Finalidad

## Pruebas unitarias (`PurposeRequestService`, mocks de repositorios y colaboradores)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `createPurposeRequest()`

| # | Caso | Notas |
|---|------|-------|
| U1 | Crea la solicitud en `PENDING` con los datos del `JEFE_DOMINIO` autenticado | — |
| U2 | Lanza `IllegalArgumentException` si el dominio no existe | — |
| U3 | Lanza `IllegalStateException` si el dominio está desactivado | — |
| U4 | Lanza `IllegalArgumentException` si el dominio no le pertenece al jefe autenticado (`UserDomainRepository.existsByKeycloakIdAndDomainId=false`) | — |
| U5 | Lanza `IllegalStateException` si ya existe una solicitud `PENDING` con el mismo título para ese dominio y solicitante | — |

### `getMyRequests()`

| # | Caso | Notas |
|---|------|-------|
| U6 | Devuelve solo las solicitudes del `JEFE_DOMINIO` autenticado | — |

### `getPendingRequests()`

| # | Caso | Notas |
|---|------|-------|
| U7 | Requiere `DPO`/`ADMIN` (`securityContextHelper.requireDpoOrAdmin()`) y devuelve solo las `PENDING` | — |

### `getAllRequests()`

| # | Caso | Notas |
|---|------|-------|
| U8 | Requiere `DPO`/`ADMIN` y devuelve todas las solicitudes, independiente del estado | — |

### `reviewRequest()`

| # | Caso | Notas |
|---|------|-------|
| U9 | Aprueba (`status=APPROVED`): actualiza la solicitud, guarda `reviewerId`/`reviewerName`, y notifica al jefe con `NotificationType.PURPOSE_APPROVED` | Ver nota sobre creación de `Purpose` abajo |
| U10 | Rechaza (`status=REJECTED`) con `reviewNotes` obligatorio: actualiza la solicitud y notifica con `NotificationType.PURPOSE_REJECTED` | — |
| U11 | Lanza `IllegalArgumentException` si `status` no es `APPROVED` ni `REJECTED` | — |
| U12 | Lanza `IllegalArgumentException` si rechaza sin `reviewNotes` (nulo o en blanco) | Ley 21.719 exige justificar el rechazo |
| U13 | Lanza `NoSuchElementException` si la solicitud no existe | — |
| U14 | Lanza `IllegalStateException` si la solicitud ya fue revisada anteriormente (`status != PENDING`) | — |

> **Nota (hallazgo, no corregido en esta tarea):** ni `reviewRequest()` ni ninguna otra parte de `PurposeRequestService` crean la `Purpose` automáticamente al aprobar — el servicio no tiene ninguna dependencia hacia `PurposesRepository`/`PurposeService`. Esto contradice la descripción en `PurposeRequestController` ("Si APPROVED: se crea automáticamente la Finalidad") y lo documentado en `purposes-module.md` (regla 10). En la práctica, tras aprobar, el DPO debe crear la `Purpose` manualmente vía `POST /api/purposes`, pudiendo enlazar `purposeRequestId` de forma opcional para trazabilidad. Pendiente de decidir si se corrige el código para automatizarlo o se corrige la documentación para reflejar el flujo manual real.
