# Plan de pruebas: Módulo Notificaciones

## Pruebas unitarias (`NotificationService`, mocks de repositorios)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `create()`

| # | Caso | Regla |
|---|------|-------|
| U1 | Crea la notificación con `read=false` y los datos recibidos (`recipientId`, `type`, `title`, `message`, `referenceId`) | — |

### `getMyNotifications()`

| # | Caso | Regla |
|---|------|-------|
| U2 | Devuelve las notificaciones del usuario autenticado (extraído del JWT vía `SecurityContextHelper`), ordenadas por fecha descendente | 1 |
| U3 | Devuelve lista vacía si el usuario no tiene notificaciones | — |

### `getUnreadCount()`

| # | Caso | Regla |
|---|------|-------|
| U4 | Devuelve el conteo de no leídas del usuario autenticado | 1 |

### `markAsRead()`

| # | Caso | Regla |
|---|------|-------|
| U5 | Marca la notificación como leída y devuelve el `NotificationResponse` actualizado | — |
| U6 | Lanza `NoSuchElementException` si la notificación no existe | — |
| U7 | Lanza `IllegalArgumentException` si la notificación no pertenece al usuario autenticado (no la marca, no llama a `save`) | 1 |

### `markAllAsRead()`

| # | Caso | Regla |
|---|------|-------|
| U8 | Delega en el repositorio pasando el `keycloakId` del usuario autenticado | 1 |
