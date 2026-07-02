# Plan de pruebas: Módulo Dominios

## Pruebas unitarias (`DomainService`, mocks de repositorios)

> Stack: JUnit 5 + Mockito + AssertJ, mismo patrón que `TemplateServiceTest`/`AgreementServiceTest` (`@ExtendWith(MockitoExtension.class)`, `@Mock` por repo/colaborador, `@InjectMocks` el service).

### `createDomain()`

| # | Caso | Regla |
|---|------|-------|
| U1 | Crea el dominio sin jefe asignado (`jefeId` nulo) — no llama a `UserDomainRepository.save` | 4 |
| U2 | Lanza `IllegalArgumentException` si el `code` ya existe | 1 |
| U3 | Lanza `IllegalArgumentException` si el `jefeId` no corresponde a un usuario existente | 4 |
| U4 | Lanza `IllegalArgumentException` si el usuario no tiene el rol `JEFE_DOMINIO` en Keycloak | 5 |
| U5 | Con `jefeId` válido y rol correcto, crea la asignación en `UserDomain` con el `keycloakId` del jefe | 6 |

### `getAllDomains()`

| # | Caso | Regla |
|---|------|-------|
| U6 | Devuelve todos los dominios mapeados, incluidos los inactivos | — |

### `deactivateDomain()`

| # | Caso | Regla |
|---|------|-------|
| U7 | Desactiva un dominio activo | — |
| U8 | Lanza `DomainNotFoundException` si el dominio no existe | — |
| U9 | Lanza `IllegalStateException` si el dominio ya estaba inactivo | 7 |

### `reactivateDomain()`

| # | Caso | Regla |
|---|------|-------|
| U10 | Reactiva un dominio inactivo | — |
| U11 | Lanza `DomainNotFoundException` si el dominio no existe | — |
| U12 | Lanza `IllegalStateException` si el dominio ya estaba activo | 7 |
