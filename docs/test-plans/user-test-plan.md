# Plan de pruebas: Módulo Usuarios

## Pruebas unitarias (`UserService`, mocks de repositorios y `KeycloakAdminService`)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service). `StringRedisTemplate` se mockea junto con su `ValueOperations` (`redisTemplate.opsForValue()`).

### `createUser()`

| # | Caso | Notas |
|---|------|-------|
| U1 | Crea un usuario `JEFE_DOMINIO` con un dominio activo asignado | Crea primero en Keycloak, luego el vínculo en `user_domains` |
| U2 | Crea un usuario sin dominios (rol `USER`) — no llama a `UserDomainRepository.save` | — |
| U3 | Lanza `IllegalArgumentException` si la contraseña es nula o en blanco | Falla antes de tocar Keycloak |
| U4 | Lanza `IllegalArgumentException` si se envían `domainIds` para un rol distinto de `JEFE_DOMINIO` | — |
| U5 | Lanza `IllegalArgumentException` si se envía más de un dominio | — |
| U6 | Si falla la asignación del dominio después de crear en Keycloak (ej. dominio inexistente), compensa eliminando el usuario recién creado en Keycloak y propaga `RuntimeException` | Patrón de compensación manual — no hay transacción distribuida entre Keycloak y la BD local |

### `updateUserByAdmin()`

| # | Caso | Notas |
|---|------|-------|
| U7 | Actualiza nombre y email vía `KeycloakAdminService.updateUserProfile()` | — |
| U8 | Resetea la contraseña si viene en el request | `temporary=true` — el usuario debe cambiarla en el próximo login |
| U9 | Si el usuario pierde el rol `JEFE_DOMINIO` en la actualización, limpia sus dominios asignados (`deleteByKeycloakId`) | — |
| U10 | Lanza `IllegalStateException` si el usuario está bloqueado permanentemente | No se puede modificar un usuario bloqueado |
| U11 | `domainIds` vacío limpia los dominios asignados | Delegado en `assignUserDomains()` |
| U12 | Lanza `IllegalArgumentException` si se intenta asignar un dominio a un usuario sin rol `JEFE_DOMINIO` | — |

### `deactivateUser()`

| # | Caso | Notas |
|---|------|-------|
| U13 | Desactiva correctamente vía `KeycloakAdminService.disableUser()` | — |
| U14 | Lanza `IllegalStateException` si el actor intenta desactivarse a sí mismo | — |
| U15 | Lanza `IllegalStateException` si el objetivo tiene rol `ADMIN` | Un admin no puede desactivar a otro admin |
| U16 | Lanza `IllegalStateException` si el objetivo está bloqueado permanentemente | — |
| U17 | Lanza `IllegalStateException` si el usuario ya está desactivado | — |

### `reactivateUser()`

| # | Caso | Notas |
|---|------|-------|
| U18 | Reactiva correctamente vía `KeycloakAdminService.enableUser()` | — |
| U19 | Lanza `IllegalStateException` si el objetivo está bloqueado permanentemente | — |
| U20 | Lanza `IllegalStateException` si el usuario ya está activo | — |

### `blockUser()`

| # | Caso | Notas |
|---|------|-------|
| U21 | Bloquea correctamente: deshabilita en Keycloak, guarda `UserStatus` con `blockedAt`, y escribe `user:{keycloakId}:blocked=true` en Redis (sin TTL) | — |
| U22 | Lanza `IllegalStateException` si el actor intenta bloquearse a sí mismo | — |
| U23 | Lanza `IllegalStateException` si el objetivo tiene rol `ADMIN` | — |
| U24 | Lanza `IllegalStateException` si el usuario ya está bloqueado | — |

### `getAllUsers()`

| # | Caso | Notas |
|---|------|-------|
| U25 | Filtra por `status=blocked` combinando datos de Keycloak con `UserStatus` local | — |
| U26 | Filtra por `role` sobre la lista de roles de cada usuario | — |

### `getUserByKeycloakId()`

| # | Caso | Notas |
|---|------|-------|
| U27 | Devuelve el usuario ensamblado con roles (Keycloak) y dominios (BD local) | — |
