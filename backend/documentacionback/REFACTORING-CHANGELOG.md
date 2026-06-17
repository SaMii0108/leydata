# Changelog — Refactoring Modular del Backend LeyData

**Fecha:** 2026-06-15

**Compilación:** `./mvnw clean package -DskipTests` → BUILD SUCCESS (87 archivos)

---

## Motivación

El backend tenía todos los archivos de cada módulo en un paquete plano (controller, service, repositorios y DTOs al mismo nivel). Esto generaba:

- `try/catch` manuales en cada método del controller devolviendo `ResponseEntity<?>` con lógica de status codes duplicada
- Servicios que devolvían entidades JPA directamente como respuesta HTTP
- Excepciones genéricas (`IllegalArgumentException`, `IllegalStateException`) sin semántica HTTP correcta
- Helpers de autenticación duplicados exactamente en `UserService`, `DomainService` y `PurposeRequestService`
- El paquete `domain/` colisionaba en nombre con la capa de arquitectura DDD

---

## Arquitectura objetivo aplicada

Cada módulo sigue el mismo patrón que `privacydoc/` (referencia ya existente):

```
<modulo>/
  domain/
    exception/       ← excepciones tipadas con nombre de negocio
    enums/           ← solo si el módulo tiene enums propios
  application/
    dto/             ← Request/Response DTOs, nunca entidades JPA
    service/         ← lógica de negocio @Service @Transactional
  infrastructure/
    persistence/     ← JpaRepository del módulo
  web/
    <Modulo>Controller.java  ← solo HTTP, cero lógica, cero try/catch
```

---

## Estructura anterior vs. nueva

### Antes (paquete plano)

```
user/
  UserController.java        ← ResponseEntity<?> + try/catch en cada método
  UserService.java           ← devuelve Users (entidad), lanza IllegalArgumentException
  UsersRepository.java
  RoleRepository.java
  UsersRoleRepository.java
  CreateUserRequest.java
  UpdateUserByAdminRequest.java
  UserSummaryDto.java        ← sin método from()

domain/                      ← nombre colisiona con capa DDD
  DomainController.java      ← ResponseEntity<?> + try/catch
  DomainService.java         ← devuelve Domains (entidad), sin DomainResponse DTO
  DomainsRepository.java
  UserDomainsRepository.java
  CreateDomainRequest.java

purpose/
  PurposeRequestController.java  ← ResponseEntity<?> + try/catch
  PurposeRequestService.java     ← getAuthenticatedUser() duplicado
  PurposeRequestsRepository.java
  PurposeRequestDto.java
  PurposeRequestSummaryDto.java
  ReviewRequestDto.java

audit/
  AuditController.java       ← ResponseEntity<?>
  AuditService.java
  AuditContext.java
  AuditLogResponseDto.java
  SystemAuditLogRepository.java
```

### Después (capas separadas)

```
shared/
  SecurityContextHelper.java         ← NUEVO: helpers de auth extraídos

user/
  domain/exception/
    UserNotFoundException.java        ← NUEVO → 404
    UserAlreadyExistsException.java   ← NUEVO → 409
  application/dto/
    CreateUserRequest.java            ← movido
    UpdateUserByAdminRequest.java     ← movido
    UserResponse.java                 ← renombrado desde UserSummaryDto + from()
  application/service/
    UserService.java                  ← movido + actualizado
  infrastructure/persistence/
    UsersRepository.java              ← movido
    RoleRepository.java               ← movido
    UsersRoleRepository.java          ← movido
  web/
    UserController.java               ← sin try/catch, sin ResponseEntity<?>

orgdomain/                            ← RENOMBRADO desde domain/
  domain/exception/
    DomainNotFoundException.java      ← NUEVO → 404
  application/dto/
    CreateDomainRequest.java          ← movido
    DomainResponse.java               ← NUEVO con from(Domains)
  application/service/
    DomainService.java                ← movido + actualizado
  infrastructure/persistence/
    DomainsRepository.java            ← movido
    UserDomainsRepository.java        ← movido
  web/
    DomainController.java             ← sin try/catch, retorna DomainResponse

purpose/
  application/dto/
    PurposeRequestDto.java            ← movido
    PurposeRequestSummaryDto.java     ← movido
    ReviewRequestDto.java             ← movido
  application/service/
    PurposeRequestService.java        ← movido + actualizado
  infrastructure/persistence/
    PurposeRequestsRepository.java    ← movido
  web/
    PurposeRequestController.java     ← sin try/catch

audit/
  application/dto/
    AuditContext.java                 ← movido
    AuditLogResponseDto.java          ← movido
  application/service/
    AuditService.java                 ← movido
  infrastructure/persistence/
    SystemAuditLogRepository.java     ← movido
  web/
    AuditController.java              ← movido, sin ResponseEntity<?>
```

---

## Cambios por tipo

### 1. `shared/SecurityContextHelper`

**Nuevo componente** que reemplaza la triplicación de helpers de autenticación:

| Método                    | Uso                                                                  | Lanzado por                    |
| ------------------------- | -------------------------------------------------------------------- | ------------------------------ |
| `getAuthenticatedUser()`  | Retorna el `Users` local por email del JWT                           | `PurposeRequestService`        |
| `getAuthenticatedAdmin()` | Verifica `ROLE_ADMIN` y retorna el `Users` local                     | `UserService`, `DomainService` |
| `getAuthenticatedDpo()`   | Verifica `ROLE_DPO` o `ROLE_ADMIN` y retorna el `Users` local        | `PurposeRequestService`        |
| `getActorRole()`          | Extrae el rol de negocio del JWT (filtra roles técnicos de Keycloak) | todos los servicios            |

### 2. Controllers: eliminación de `try/catch` y `ResponseEntity<?>`

**Antes:**

```java
@PostMapping
public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
    try {
        Users created = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(...));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (RuntimeException e) {
        return ResponseEntity.status(500).body(Map.of("error", ...));
    }
}
```

**Después:**

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Map<String, Object> createUser(@RequestBody CreateUserRequest request) {
    UserResponse created = userService.createUser(request);
    return Map.of("status", "success", "message", "...", "userId", created.getId());
}
```

El `GlobalExceptionHandler` (ya existente en `config/`) captura todas las excepciones automáticamente. No se duplica lógica de status codes en los controllers.

### 3. Servicios: tipado de excepciones y DTOs de respuesta

| Módulo                             | Antes                                          | Después                                  |
| ---------------------------------- | ---------------------------------------------- | ---------------------------------------- |
| `UserService.createUser()`         | devuelve `Users` (entidad)                     | devuelve `UserResponse`                  |
| `UserService.getUserById()`        | lanza `IllegalArgumentException` → 400         | lanza `UserNotFoundException` → 404      |
| `UserService.createUser()`         | lanza `IllegalArgumentException` por email dup | lanza `UserAlreadyExistsException` → 409 |
| `DomainService.createDomain()`     | devuelve `Domains` (entidad)                   | devuelve `DomainResponse`                |
| `DomainService.getAllDomains()`    | devuelve `List<Domains>` (entidades)           | devuelve `List<DomainResponse>`          |
| `DomainService.deactivateDomain()` | lanza `IllegalArgumentException` → 400         | lanza `DomainNotFoundException` → 404    |

### 4. Nuevas excepciones tipadas

| Excepción                    | Paquete                      | HTTP Status | Cuándo se lanza              |
| ---------------------------- | ---------------------------- | ----------- | ---------------------------- |
| `UserNotFoundException`      | `user.domain.exception`      | 404         | Usuario no encontrado por ID |
| `UserAlreadyExistsException` | `user.domain.exception`      | 409         | Email ya registrado en BD    |
| `DomainNotFoundException`    | `orgdomain.domain.exception` | 404         | Dominio no encontrado por ID |

### 5. `GlobalExceptionHandler` — handlers agregados

```java
// Módulo user/
@ExceptionHandler(UserNotFoundException.class)      → 404 NOT_FOUND
@ExceptionHandler(UserAlreadyExistsException.class) → 409 CONFLICT

// Módulo orgdomain/
@ExceptionHandler(DomainNotFoundException.class)    → 404 NOT_FOUND
```

### 6. `DomainResponse` — DTO nuevo

Antes `DomainController` y `DomainService` exponían directamente la entidad JPA `Domains`. Ahora existe `DomainResponse` con método `from(Domains d)`:

```java
public class DomainResponse {
    UUID id; String code; String name;
    String description; Boolean active; LocalDateTime createdAt;

    public static DomainResponse from(Domains domain) { ... }
}
```

### 7. `UserResponse` — renombrado desde `UserSummaryDto`

Agrega método estático `from(Users user)` que encapsula la conversión:

```java
public class UserResponse {
    UUID id; String email; String name;
    Boolean active; Boolean blocked;
    List<String> roles; List<String> domains;

    public static UserResponse from(Users user) { ... }
}
```

---

## Archivos modificados (no movidos)

| Archivo                                                      | Cambio                                                                       |
| ------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| `config/GlobalExceptionHandler.java`                         | +3 handlers: UserNotFound, UserAlreadyExists, DomainNotFound                 |
| `security/UserStatusFilter.java`                             | import actualizado: `user.infrastructure.persistence.UsersRepository`        |
| `seeder/DataSeeder.java`                                     | imports actualizados: `user.infrastructure.persistence.*`                    |
| `privacydoc/application/service/PrivacyDocumentService.java` | import actualizado: `orgdomain.infrastructure.persistence.DomainsRepository` |

---

## Archivos eliminados (24 total)

Todos los archivos en sus ubicaciones originales planas:
`user/{UserController, UserService, UserSummaryDto, CreateUserRequest, UpdateUserByAdminRequest, UsersRepository, RoleRepository, UsersRoleRepository}.java`  
`domain/{DomainController, DomainService, CreateDomainRequest, DomainsRepository, UserDomainsRepository}.java`  
`purpose/{PurposeRequestController, PurposeRequestService, PurposeRequestDto, PurposeRequestSummaryDto, ReviewRequestDto, PurposeRequestsRepository}.java`  
`audit/{AuditController, AuditService, AuditContext, AuditLogResponseDto, SystemAuditLogRepository}.java`

---

## Lo que NO cambió

| Módulo/Archivo                           | Motivo                                                 |
| ---------------------------------------- | ------------------------------------------------------ |
| `config/SecurityConfig.java`             | Reglas de autorización correctas, sin cambios          |
| `config/OpenApiConfig.java`              | Sin cambios                                            |
| `security/KeycloakJwtAuthConverter.java` | Sin cambios                                            |
| `security/KeycloakAdminService.java`     | Sin cambios                                            |
| `entity/` (21 entidades)                 | Entidades JPA compartidas, sin cambios                 |
| `repository/` (repos legacy compartidos) | Repositorios de AgreementsPurposes, DataSubjects, etc. |
| `seeder/DataSeeder.java`                 | Solo actualización de imports                          |
| `privacydoc/`                            | Ya tenía la arquitectura correcta, es la referencia    |
| `auth/`                                  | Sin cambios                                            |

---

## Notas de implementación

**`DomainService` inyecta `UsersRepository` directamente** (desde `user.infrastructure.persistence`) para validar el jefe de dominio en `createDomain()`. Esto es un trade-off deliberado: la alternativa (inyectar `UserService`) crearía una dependencia circular ya que `UserService` inyecta `DomainsRepository`. Spring detectaría el ciclo en el startup.

**`AuditController` inyecta `UsersRepository` directamente** para resolver email → UUID en los filtros de log. No hay un método de servicio equivalente en `UserService` para este caso.

**`AuditService` mantiene `@Autowired @Lazy AuditService self`** — patrón necesario para que `@Transactional(REQUIRES_NEW)` funcione correctamente con AOP de Spring. Sin self-injection, `this.log()` bypasea el proxy.
