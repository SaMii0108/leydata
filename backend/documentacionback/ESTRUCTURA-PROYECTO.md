# Estructura del Proyecto — Backend LeyData

**Fecha:** 2026-06-16

Guía de referencia rápida sobre cómo está organizado el código fuente del backend y qué hace cada carpeta.

---

## Árbol de directorios

```
backend/src/main/java/com/leydata/backend/
│
├── BackendApplication.java              ← punto de entrada Spring Boot
│
├── config/                              ← configuración transversal
│   ├── GlobalExceptionHandler.java
│   ├── OpenApiConfig.java
│   └── SecurityConfig.java
│
├── shared/                              ← componentes sin módulo dueño
│   ├── SecurityContextHelper.java
│   └── EmailService.java
│
├── entity/                              ← 21 entidades JPA compartidas
│   ├── Users.java
│   ├── Domains.java
│   ├── Role.java
│   ├── UsersRole.java
│   ├── UserDomains.java
│   ├── PurposeRequests.java
│   ├── Purposes.java
│   ├── SystemAuditLog.java
│   ├── PrivacyDocuments.java
│   ├── DocumentPurposes.java
│   ├── Notification.java
│   ├── Templates.java
│   ├── TemplatePurposes.java
│   ├── Agreements.java
│   ├── AgreementsPurposes.java
│   ├── AgreementIntegrityLog.java
│   ├── AgreementMetadata.java
│   ├── DataSubjects.java
│   ├── DataCategories.java
│   ├── DataRetentionPolicies.java
│   ├── LegalBasisCatalog.java
│   └── PurposeDataCategories.java
│
├── repository/                          ← repositorios compartidos (legacy)
│   ├── AgreementsRepository.java
│   ├── AgreementsPurposesRepository.java
│   ├── AgreementIntegrityLogRepository.java
│   ├── AgreementMetadataRepository.java
│   ├── DataSubjectsRepository.java
│   ├── DataCategoriesRepository.java
│   ├── DataRetentionPoliciesRepository.java
│   ├── DocumentPurposesRepository.java
│   ├── LegalBasisCatalogRepository.java
│   ├── PrivacyDocumentsRepository.java
│   ├── PurposeDataCategoriesRepository.java
│   ├── PurposesRepository.java
│   ├── TemplatePurposesRepository.java
│   └── TemplatesRepository.java
│
├── security/                            ← infraestructura de autenticación
│   ├── KeycloakJwtAuthConverter.java
│   ├── KeycloakAdminService.java
│   └── UserStatusFilter.java
│
├── seeder/                              ← inicialización de datos base
│   └── DataSeeder.java
│
├── user/                                ← módulo: gestión de usuarios
│   ├── domain/
│   │   └── exception/
│   │       ├── UserNotFoundException.java
│   │       └── UserAlreadyExistsException.java
│   ├── application/
│   │   ├── dto/
│   │   │   ├── CreateUserRequest.java
│   │   │   ├── UpdateUserByAdminRequest.java
│   │   │   └── UserResponse.java
│   │   └── service/
│   │       └── UserService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── UsersRepository.java
│   │       ├── RoleRepository.java
│   │       └── UsersRoleRepository.java
│   └── web/
│       └── UserController.java
│
├── orgdomain/                           ← módulo: dominios organizacionales
│   ├── domain/
│   │   └── exception/
│   │       └── DomainNotFoundException.java
│   ├── application/
│   │   ├── dto/
│   │   │   ├── CreateDomainRequest.java
│   │   │   └── DomainResponse.java
│   │   └── service/
│   │       └── DomainService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── DomainsRepository.java
│   │       └── UserDomainsRepository.java
│   └── web/
│       └── DomainController.java
│
├── purpose/                             ← módulo: solicitudes de propósito
│   ├── application/
│   │   ├── dto/
│   │   │   ├── PurposeRequestDto.java
│   │   │   ├── PurposeRequestSummaryDto.java
│   │   │   └── ReviewRequestDto.java
│   │   └── service/
│   │       └── PurposeRequestService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       └── PurposeRequestsRepository.java
│   └── web/
│       └── PurposeRequestController.java
│
├── audit/                               ← módulo: log de auditoría inmutable
│   ├── application/
│   │   ├── dto/
│   │   │   ├── AuditContext.java
│   │   │   └── AuditLogResponseDto.java
│   │   └── service/
│   │       └── AuditService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       └── SystemAuditLogRepository.java
│   └── web/
│       └── AuditController.java
│
├── privacydoc/                          ← módulo: documentos de privacidad
│   ├── domain/
│   │   ├── enums/
│   │   │   ├── DocumentCategory.java
│   │   │   └── DocumentStatus.java
│   │   └── exception/
│   │       ├── BusinessValidationException.java
│   │       ├── DocumentNotFoundException.java
│   │       └── InvalidTransitionException.java
│   ├── application/
│   │   ├── dto/
│   │   │   ├── CreateDocumentRequest.java
│   │   │   ├── PrivacyDocumentResponse.java
│   │   │   ├── RejectDocumentRequest.java
│   │   │   ├── UpdateDocumentRequest.java
│   │   │   └── VerifyResponse.java
│   │   └── service/
│   │       └── PrivacyDocumentService.java
│   ├── infrastructure/
│   │   ├── pdf/
│   │   │   └── PdfGeneratorService.java
│   │   └── persistence/
│   │       ├── DocumentPurposesRepository.java
│   │       └── PrivacyDocumentsRepository.java
│   └── web/
│       └── PrivacyDocumentController.java
│
└── notification/                        ← módulo: notificaciones in-app
    ├── domain/
    │   └── enums/
    │       └── NotificationType.java
    ├── application/
    │   ├── dto/
    │   │   └── NotificationResponse.java
    │   └── service/
    │       └── NotificationService.java
    └── web/
        └── NotificationController.java
```

---

## Qué hace cada capa

### `web/`

La capa HTTP. Los controllers reciben la request, delegan al servicio, y devuelven la respuesta. No tienen lógica de negocio ni bloques `try/catch`. El código HTTP de éxito se declara con `@ResponseStatus`; los errores los gestiona `GlobalExceptionHandler` automáticamente.

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public Map<String, Object> createUser(@RequestBody CreateUserRequest request) {
    UserResponse created = userService.createUser(request);
    return Map.of("status", "success", "userId", created.getId());
}
```

### `application/service/`

La lógica de negocio. Aquí viven las validaciones, las reglas de la Ley 21.719, y la orquestación de repositorios. Anotado con `@Service` y `@Transactional`. Trabaja con DTOs, nunca expone entidades JPA al exterior.

### `application/dto/`

Data Transfer Objects: clases simples de Java (sin anotaciones JPA) que representan lo que entra (`CreateUserRequest`, `ReviewRequestDto`, …) y lo que sale (`UserResponse`, `DomainResponse`, …) de cada operación. Desacoplan la API HTTP del modelo relacional interno.

Los DTOs de respuesta tienen un método estático `from(Entidad e)` que encapsula la conversión:

```java
public static UserResponse from(Users user) {
    return new UserResponse(
        user.getId(), user.getEmail(), user.getName(),
        user.getActive(), user.getBlocked(), ...
    );
}
```

### `infrastructure/persistence/`

Los `JpaRepository` de Spring Data. Solo declaran los métodos de acceso a datos; Spring genera la implementación en tiempo de compilación. Cada módulo tiene sus propios repositorios, aislados de los de los demás módulos.

### `domain/exception/`

Excepciones tipadas con semántica de negocio: `UserNotFoundException`, `DomainNotFoundException`, `InvalidTransitionException`, etc. No llevan código HTTP en sí mismas — eso lo decide `GlobalExceptionHandler`. Esto permite que un mismo error semántico se pueda mapear a distintos códigos HTTP según el contexto, sin modificar el servicio.

### `domain/enums/`

Enums con significado de negocio. Por ejemplo:

- `DocumentStatus`: `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED` (con `REJECTED` como re-entrada desde `IN_REVIEW`)
- `DocumentCategory`: `POLITICA_PRIVACIDAD`, `AVISO_COOKIES`, `DATOS_SENSIBLES`, `MARKETING_DIRECTO`, `MENORES_EDAD`, `TRANSFERENCIA_TERCEROS`

---

## Qué hace cada paquete raíz

### `config/`

Configuración transversal de Spring que no pertenece a ningún módulo de negocio.

| Archivo | Qué hace |
|---|---|
| `SecurityConfig.java` | Define qué rutas son públicas, cuáles requieren ADMIN, DPO, etc. Configura el backend como OAuth2 Resource Server de Keycloak. |
| `GlobalExceptionHandler.java` | `@ControllerAdvice` que captura todas las excepciones tipadas y las convierte al código HTTP correcto. Centraliza en un solo lugar toda la lógica de error. |
| `OpenApiConfig.java` | Configura Swagger UI en `/swagger-ui.html`. |

### `shared/`

Componentes sin módulo dueño que son usados por varios módulos. Evita que la lógica se duplique.

| Archivo | Qué hace |
|---|---|
| `SecurityContextHelper.java` | Extrae el usuario autenticado del JWT de Keycloak. Antes existía copiado en `UserService`, `DomainService` y `PurposeRequestService`. Ahora vive aquí una sola vez. |
| `EmailService.java` | Servicio de envío de emails HTML por SMTP. Solo se crea como bean si `MAIL_ENABLED=true` (`@ConditionalOnProperty`). Los servicios que lo usan lo inyectan como `Optional<EmailService>` para que el backend arranque normalmente cuando el email no está configurado. Envía de forma asíncrona (`@Async`) para no bloquear la respuesta HTTP. |

Métodos disponibles:

```
getAuthenticatedUser()  → busca en BD por email del claim "sub"
getAuthenticatedAdmin() → verifica ROLE_ADMIN y devuelve el Users local
getAuthenticatedDpo()   → verifica ROLE_DPO o ROLE_ADMIN y devuelve el Users local
getActorRole()          → extrae el rol de negocio del JWT (filtra roles técnicos de Keycloak)
```

### `entity/`

Las 21 entidades JPA que mapean el modelo relacional de PostgreSQL a objetos Java. Son compartidas por todos los módulos. **No se exponen directamente como respuesta HTTP** — siempre se convierten a DTOs antes de salir del servicio.

### `repository/`

Repositorios compartidos que aún no tienen un módulo dueño claro: `Agreements`, `DataSubjects`, `Templates`, `LegalBasisCatalog`, etc. Son usados principalmente por `privacydoc/` y en el futuro por módulos de consentimiento.

### `security/`

Infraestructura de autenticación y autorización que trabaja con Spring Security.

| Archivo | Qué hace |
|---|---|
| `KeycloakJwtAuthConverter.java` | Convierte los roles del claim `realm_access.roles` del JWT en `GrantedAuthority` de Spring Security (añade el prefijo `ROLE_`). |
| `KeycloakAdminService.java` | Llama a la API de administración de Keycloak para crear, leer y modificar usuarios cuando el admin usa `POST /api/users`. |
| `UserStatusFilter.java` | Filtro que corre **después** de validar el JWT. Consulta la BD local y rechaza con 403 si el usuario está bloqueado o desactivado, aunque su token de Keycloak sea válido. |

### `seeder/`

`DataSeeder` implementa `CommandLineRunner`: al arrancar el backend, crea los roles base (`ADMIN`, `DPO`, `JEFE_DOMINIO`) y el usuario `admin@leydata.cl` en la BD local si no existen. Solo toca la base de datos local — Keycloak se configura por separado con el script `scripts/setup-keycloak.sh`.

---

## Módulos de negocio

| Módulo | Paquete | Endpoints | Acceso |
|---|---|---|---|
| Usuarios | `user/` | `/api/users/**` | ADMIN |
| Dominios organizacionales | `orgdomain/` | `/api/domains/**` | ADMIN |
| Solicitudes de propósito | `purpose/` | `/api/purpose-requests/**` | JEFE_DOMINIO / DPO |
| Auditoría | `audit/` | `/api/audit/**` | ADMIN |
| Documentos de privacidad | `privacydoc/` | `/api/privacy-documents/**` | DPO (escritura) · cualquier autenticado (lectura) |
| Notificaciones in-app | `notification/` | `/api/notifications/**` | Cualquier autenticado (cada uno ve solo las suyas) |

> El paquete del módulo de dominios es `orgdomain` (no `domain`) para evitar colisión de nombres con la capa de arquitectura `domain/` dentro de cada módulo.

---

## Convenciones del proyecto

- Los controllers no tienen `try/catch` — las excepciones se propagan hasta `GlobalExceptionHandler`
- Los servicios devuelven DTOs, nunca entidades JPA
- Los DTOs de Request llevan el sufijo `Request`; los de respuesta llevan el sufijo `Response` o `Dto`
- Las excepciones tipadas viven en `<modulo>/domain/exception/` y extienden `RuntimeException`
- Los repositorios de cada módulo viven en `<modulo>/infrastructure/persistence/`
- `SecurityContextHelper` es el único lugar donde se extrae el usuario del contexto de seguridad
