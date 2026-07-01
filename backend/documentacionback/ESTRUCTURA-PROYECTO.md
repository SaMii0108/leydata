# Estructura del Proyecto — Backend LeyData

**Fecha:** 2026-06-26

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
│   ├── SecurityConfig.java
│   ├── DataSourceConfig.java            ← AbstractRoutingDataSource (write → PgBouncer :5435, read → replica :5434)
│   ├── DataSourceType.java              ← enum WRITE | READ
│   ├── ReadWriteRoutingDataSource.java  ← determineCurrentLookupKey() via TransactionSynchronizationManager
│   └── README.md                        ← explica enrutamiento, LazyConnectionDataSourceProxy, @Transactional(readOnly)
│
├── shared/                              ← componentes sin módulo dueño
│   ├── SecurityContextHelper.java
│   └── EmailService.java
│
├── entity/                              ← entidades JPA compartidas (modelo Keycloak-first)
│   ├── Users.java                       ← caché local mínima: keycloak_id, email, name, active
│   ├── Domains.java
│   ├── PurposeRequests.java
│   ├── Purposes.java
│   ├── PurposeDataCategories.java
│   ├── SystemAuditLog.java
│   ├── PrivacyDocuments.java
│   ├── DocumentPurposes.java
│   ├── Notification.java
│   ├── Templates.java
│   ├── TemplatePurposes.java
│   ├── Agreements.java
│   ├── AgreementsPurposes.java
│   ├── AgreementIntegrityLog.java       ← legacy (referenciada por agreement/)
│   ├── EntityIntegrityLog.java          ← nueva (feature/trazabilidad) — mapeada a entity_integrity_log
│   ├── AgreementMetadata.java
│   ├── DataSubjects.java
│   ├── DataCategories.java
│   ├── DataRetentionPolicies.java
│   └── LegalBasisCatalog.java
│
├── repository/                          ← repositorios legacy (sin módulo dueño aún)
│   ├── AgreementsRepository.java
│   ├── AgreementsPurposesRepository.java
│   ├── AgreementIntegrityLogRepository.java
│   ├── AgreementMetadataRepository.java
│   ├── DataSubjectsRepository.java
│   ├── DataCategoriesRepository.java    ← legacy; el módulo activo es datacategory/
│   ├── DataRetentionPoliciesRepository.java
│   ├── LegalBasisCatalogRepository.java ← legacy; el módulo activo es legalbasis/
│   └── PurposeDataCategoriesRepository.java ← legacy; el módulo activo es purposedatacategory/
│
├── security/                            ← infraestructura de autenticación
│   ├── KeycloakJwtAuthConverter.java
│   ├── KeycloakAdminService.java
│   └── UserStatusFilter.java
│
├── seeder/                              ← inicialización de datos al arrancar
│   └── CatalogSeeder.java               ← catálogos fijos (bases de licitud, categorías de datos)
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
│   │       └── UsersRepository.java
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
│   │       └── DomainsRepository.java
│   └── web/
│       └── DomainController.java
│
├── purpose/                             ← módulo: solicitudes de propósito (workflow JEFE → DPO)
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
├── legalbasis/                          ← módulo: catálogo de bases de licitud (Ley 21.719)
│   ├── application/
│   │   └── dto/
│   │       └── LegalBasisResponse.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       └── LegalBasisRepository.java
│   └── web/
│       └── LegalBasisController.java
│
├── datacategory/                        ← módulo: catálogo de categorías de datos personales
│   ├── domain/
│   │   └── exception/
│   │       └── DataCategoryNotFoundException.java
│   ├── application/
│   │   ├── dto/
│   │   │   ├── DataCategoryRequest.java
│   │   │   └── DataCategoryResponse.java
│   │   └── service/
│   │       └── DataCategoryService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       └── DataCategoryRepository.java
│   └── web/
│       └── DataCategoryController.java
│
├── purposes/                            ← módulo: finalidades de tratamiento de datos
│   ├── domain/
│   │   └── exception/
│   │       ├── PurposeNotFoundException.java
│   │       └── PurposeNotLockedException.java  ← HTTP 409 — lanzada por newVersion() si locked=false
│   ├── application/
│   │   ├── dto/
│   │   │   ├── CreatePurposeRequest.java
│   │   │   ├── UpdatePurposeRequest.java
│   │   │   └── PurposeResponse.java
│   │   └── service/
│   │       └── PurposeService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       └── PurposesRepository.java
│   └── web/
│       └── PurposeController.java
│
├── purposedatacategory/                 ← módulo: vínculo finalidad ↔ categoría de dato
│   ├── domain/
│   │   ├── enums/
│   │   │   └── DataUseType.java
│   │   └── exception/
│   │       ├── PurposeDataCategoryNotFoundException.java
│   │       └── RetentionPolicyLockedException.java
│   ├── application/
│   │   ├── dto/
│   │   │   ├── PurposeDataCategoryRequest.java
│   │   │   ├── PurposeDataCategoryResponse.java
│   │   │   └── DataRetentionPolicyRequest.java
│   │   └── service/
│   │       └── PurposeDataCategoryService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── PurposeDataCategoryRepository.java
│   │       └── RetentionPolicyRepository.java
│   └── web/
│       └── PurposeDataCategoryController.java
│
├── audit/                               ← módulo: log de auditoría + integridad de entidades
│   ├── application/
│   │   ├── dto/
│   │   │   ├── AuditContext.java
│   │   │   ├── AuditLogResponseDto.java
│   │   │   ├── VerifyIntegrityRequest.java      ← request de verificación (entityType, entityId, checkType)
│   │   │   ├── EntityIntegrityLogResponse.java  ← respuesta de verificación individual
│   │   │   └── AgreementTraceResponse.java      ← traza completa: DocumentLink, TemplateLink, PurposeLink, overallIntegrity
│   │   └── service/
│   │       ├── AuditService.java
│   │       ├── IntegrityVerifier.java            ← despacha a recalculateHash() por entidad; escribe entity_integrity_log
│   │       ├── IntegrityScheduler.java           ← @Scheduled(cron="0 0 3 * * *"); cubre AGREEMENT, TEMPLATE, DOCUMENT, PURPOSE
│   │       └── AgreementTraceService.java        ← traza completa de un agreement; drift check de purposes
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── SystemAuditLogRepository.java
│   │       └── EntityIntegrityLogRepository.java ← repositorio de entity_integrity_log
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
│   │   │   ├── UpdateDocumentRequest.java
│   │   │   ├── RejectDocumentRequest.java
│   │   │   ├── PrivacyDocumentResponse.java
│   │   │   └── VerifyResponse.java
│   │   └── service/
│   │       └── PrivacyDocumentService.java
│   ├── infrastructure/
│   │   ├── pdf/
│   │   │   └── PdfGeneratorService.java
│   │   └── persistence/
│   │       ├── PrivacyDocumentsRepository.java
│   │       └── DocumentPurposesRepository.java
│   └── web/
│       └── PrivacyDocumentController.java
│
├── notification/                        ← módulo: notificaciones in-app
│   ├── domain/
│   │   └── enums/
│   │       └── NotificationType.java
│   ├── application/
│   │   ├── dto/
│   │   │   └── NotificationResponse.java
│   │   └── service/
│   │       └── NotificationService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       └── NotificationRepository.java
│   └── web/
│       └── NotificationController.java
│
├── template/                            ← módulo: plantillas de consentimiento (DRAFT → APPROVED → ACTIVE)
│   ├── domain/
│   │   ├── enums/
│   │   │   └── TemplateStatus.java      ← DRAFT, APPROVED, ACTIVE
│   │   └── exception/
│   │       └── TemplateNotFoundException.java
│   ├── application/
│   │   ├── dto/
│   │   │   ├── CreateTemplateRequest.java
│   │   │   ├── TemplateResponse.java
│   │   │   ├── TemplateVerifyResponse.java
│   │   │   ├── TemplateResolutionResponse.java  ← resolución B2B (templateKey+domainId → templateId+documentId)
│   │   │   ├── AddTemplatePurposeRequest.java
│   │   │   ├── UpdateTemplatePurposeRequest.java
│   │   │   └── TemplatePurposeResponse.java
│   │   └── service/
│   │       └── TemplateService.java
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── TemplatesRepository.java
│   │       ├── TemplatePurposesRepository.java
│   │       └── TemplateSpecifications.java
│   └── web/
│       └── TemplateController.java
│
├── agreement/                           ← módulo: acuerdos de consentimiento (ledger SHA-256 encadenado)
│   ├── domain/
│   │   ├── event/
│   │   │   ├── AgreementRevokedEvent.java            ← Spring event publicado al revocar
│   │   │   ├── AgreementIntegrityFailedEvent.java
│   │   │   └── README.md                             ← explica AFTER_COMMIT vs. dentro de @Transactional
│   │   └── exception/
│   │       └── AgreementNotFoundException.java
│   ├── application/
│   │   ├── dto/
│   │   │   ├── CreateAgreementRequest.java
│   │   │   ├── PurposeDecisionRequest.java
│   │   │   ├── AgreementMetadataRequest.java
│   │   │   ├── AgreementResponse.java
│   │   │   ├── AgreementPurposeResponse.java
│   │   │   └── AgreementMetadataResponse.java
│   │   └── service/
│   │       ├── AgreementService.java
│   │       └── AgreementRevocationCacheListener.java ← @TransactionalEventListener(AFTER_COMMIT): invalida Redis
│   ├── infrastructure/
│   │   └── persistence/
│   │       ├── AgreementsRepository.java
│   │       ├── AgreementsPurposesRepository.java
│   │       └── AgreementMetadataRepository.java
│   └── web/
│       └── AgreementController.java
│
├── userdomain/                          ← módulo: vínculo usuario ↔ dominio (Keycloak-first)
│   ├── domain/
│   │   └── UserDomain.java              ← keycloak_id + FK a Domains
│   ├── infrastructure/
│   │   └── persistence/
│   │       └── UserDomainRepository.java
│   └── (sin web — uso interno)
│
└── userstatus/                          ← módulo: bloqueo permanente por keycloak_id
    ├── domain/
    │   └── UserStatus.java              ← keycloak_id + blocked + blockedAt
    └── infrastructure/
        └── persistence/
            └── UserStatusRepository.java
```

---

## Árbol del Orquestador

```
orchestrator/src/main/java/com/leydata/orchestrator/
│
├── OrchestratorApplication.java         ← @EnableConfigurationProperties(ConsentCacheProperties)
│
├── config/
│   ├── SecurityConfig.java              ← ReactiveSecurityFilterChain, NimbusReactiveJwtDecoder (JWKS externo)
│   ├── WebClientConfig.java             ← WebClient con OAuth2 M2M (client_credentials → leydata realm)
│   ├── RedisConfig.java                 ← ReactiveStringRedisTemplate
│   └── ConsentCacheProperties.java      ← @ConfigurationProperties("leydata.consent"): cacheTtlSeconds, cacheSoftTtlSeconds
│
└── consent/
    ├── ConsentController.java            ← GET /consent/check, POST /consent/capture, POST /consent/revoke
    ├── ConsentService.java               ← lógica de caché Redis + proxying a LeyData
    └── dto/
        ├── ConsentCheckResponse.java     ← { subjectId, purposeId, status, legalBasisCode, validUntil }
        ├── CaptureConsentRequest.java
        ├── RevokeConsentRequest.java
        ├── ConsentStatusResponse.java    ← { subjectId, agreementId, status }
        ├── AgreementBackendResponse.java ← DTO de la respuesta de LeyData (id, status, expiration, purposes)
        └── AgreementPurposeBackendResponse.java ← { purposeId, accepted, status, legalBasisCode, expiresAt }
```

---

## Modelo Keycloak-first

A partir de la rama `feature/keycloak-first-model`, el sistema adoptó un modelo donde **Keycloak es la única fuente de verdad** para identidad, autenticación y roles. La BD local almacena exclusivamente datos de negocio.

| Dato | Dónde vive |
|---|---|
| Email, nombre, contraseña, roles | **Keycloak** |
| UUID local, estado activo, createdAt | **BD local** (`users`) |
| Qué dominios tiene asignados | **BD local** (`user_domains` vía `userdomain/`) |
| Bloqueo permanente (fecha y estado) | **BD local** (`user_status` vía `userstatus/`) |

La entidad `Users` local ya **no tiene** contraseña, roles, `blocked` ni `mustChangePassword` — esos campos migraron a Keycloak o a módulos propios. La tabla `users` es solo una referencia para asociar el `keycloak_id` con datos de negocio (dominios, auditoría).

Las entidades `Role`, `UsersRole` y `UserDomains` (con PK compuesta) fueron **eliminadas** en esta migración.

---

## Qué hace cada capa

### `web/`

La capa HTTP. Los controllers reciben la request, delegan al servicio, y devuelven la respuesta. No tienen lógica de negocio ni bloques `try/catch`. El código HTTP de éxito se declara con `@ResponseStatus`; los errores los gestiona `GlobalExceptionHandler` automáticamente.

### `application/service/`

La lógica de negocio. Aquí viven las validaciones, las reglas de la Ley 21.719, y la orquestación de repositorios. Anotado con `@Service` y `@Transactional`. Trabaja con DTOs, nunca expone entidades JPA al exterior.

### `application/dto/`

Data Transfer Objects: clases simples de Java que representan lo que entra (`CreateUserRequest`, `ReviewRequestDto`, …) y lo que sale (`UserResponse`, `DomainResponse`, …) de cada operación. Desacoplan la API HTTP del modelo relacional interno.

### `infrastructure/persistence/`

Los `JpaRepository` de Spring Data. Solo declaran los métodos de acceso a datos; Spring genera la implementación en tiempo de compilación. Cada módulo tiene sus propios repositorios, aislados de los de los demás módulos.

### `domain/exception/`

Excepciones tipadas con semántica de negocio: `UserNotFoundException`, `DomainNotFoundException`, `InvalidTransitionException`, etc. No llevan código HTTP en sí mismas — eso lo decide `GlobalExceptionHandler`.

### `domain/enums/`

Enums con significado de negocio. Por ejemplo:

- `DocumentStatus`: `DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED`
- `DocumentCategory`: `POLITICA_PRIVACIDAD`, `AVISO_COOKIES`, `DATOS_SENSIBLES`, `MARKETING_DIRECTO`, `MENORES_EDAD`, `TRANSFERENCIA_TERCEROS`

---

## Qué hace cada paquete raíz

### `config/`

| Archivo | Qué hace |
|---|---|
| `SecurityConfig.java` | Define qué rutas son públicas, cuáles requieren ADMIN, DPO, etc. Configura el backend como OAuth2 Resource Server de Keycloak. |
| `GlobalExceptionHandler.java` | `@ControllerAdvice` que captura todas las excepciones tipadas y las convierte al código HTTP correcto. |
| `OpenApiConfig.java` | Configura Swagger UI en `/swagger-ui.html`. |

### `shared/`

| Archivo | Qué hace |
|---|---|
| `SecurityContextHelper.java` | Extrae identidad y rol del JWT de Keycloak. No hace consultas a la BD — toda la información viene del token. |
| `EmailService.java` | Envío de emails HTML por SMTP. Solo se activa si `MAIL_ENABLED=true`. |

Métodos disponibles en `SecurityContextHelper`:

```
getKeycloakId()         → devuelve el claim "sub" del JWT (keycloak_id estable del usuario)
getName()               → devuelve el nombre completo del claim "name"
getEmail()              → devuelve el email del claim "email"
getActorRole()          → extrae el rol de negocio del JWT
requireAdmin()          → lanza ForbiddenException si el token no tiene ROLE_ADMIN
requireDpoOrAdmin()     → lanza ForbiddenException si el token no tiene ROLE_DPO ni ROLE_ADMIN
```

### `entity/`

Entidades JPA que mapean el modelo relacional de PostgreSQL. Son compartidas por todos los módulos. **No se exponen directamente como respuesta HTTP** — siempre se convierten a DTOs antes de salir del servicio.

`Users.java` es una caché local mínima. No tiene roles (viven en Keycloak), no tiene contraseña, no tiene `blocked`. Solo almacena el `keycloak_id` para poder hacer JOINs con datos de negocio.

### `repository/`

Repositorios compartidos sin módulo dueño definido: `Agreements`, `DataSubjects`, etc. **No agregar repositorios nuevos aquí.** Los módulos nuevos siempre deben colocar sus repositorios en `<modulo>/infrastructure/persistence/`.

### `security/`

| Archivo | Qué hace |
|---|---|
| `KeycloakJwtAuthConverter.java` | Convierte los roles del claim `realm_access.roles` del JWT en `GrantedAuthority` de Spring Security. |
| `KeycloakAdminService.java` | Llama a la API de administración de Keycloak para crear, listar, deshabilitar y gestionar usuarios y sus roles. Usado por `UserService`. |
| `UserStatusFilter.java` | Filtro que corre después de validar el JWT. Rechaza con 403 si el usuario está en `user_status` con `blocked=true`, aunque su token de Keycloak sea válido. Es una red de seguridad para cortar sesiones existentes hasta que el token expire. |

`KeycloakAdminService` expone:
- `createUser()` — crea usuario en Keycloak y le asigna rol
- `listUsers()` — lista todos los usuarios del realm con sus roles
- `getUserRoles()` — obtiene los roles de un usuario específico
- `updateUserRoles()` — reemplaza los roles de un usuario en Keycloak
- `disableUser()` — deshabilita un usuario en Keycloak (bloquea login y refresh)
- `enableUser()` — habilita un usuario previamente deshabilitado
- `deleteUser()` — elimina un usuario (solo usado para compensación si falla la BD local)

### `seeder/`

`CommandLineRunner` que se ejecutan al arrancar el backend:

| Archivo | Qué hace |
|---|---|
| `CatalogSeeder.java` | Inserta los registros del catálogo de bases de licitud de la Ley 21.719 si la tabla está vacía. Idempotente — no inserta duplicados. |

En el modelo Keycloak-first no se siembran roles en la BD local (Keycloak los gestiona) ni se crean usuarios administradores localmente (el admin se crea en Keycloak con `scripts/setup-keycloak.sh`).

---

## Módulos de negocio

| Módulo | Paquete | Endpoints | Acceso |
|---|---|---|---|
| Usuarios | `user/` | `/api/users/**` | ADMIN |
| Dominios organizacionales | `orgdomain/` | `/api/domains/**` | ADMIN |
| Solicitudes de propósito | `purpose/` | `/api/purpose-requests/**` | JEFE_DOMINIO (crear) · DPO/ADMIN (revisar) |
| Bases de licitud | `legalbasis/` | `/api/legal-basis/**` | DPO · ADMIN · JEFE_DOMINIO (solo lectura) |
| Categorías de datos | `datacategory/` | `/api/data-categories/**` | DPO · ADMIN (escritura) · JEFE_DOMINIO (lectura) |
| Finalidades | `purposes/` | `/api/purposes/**` — incluye versionado: `POST /{id}/new-version`, `GET /family/{familyId}`, `GET /active/{familyId}` | DPO · ADMIN (escritura) · JEFE_DOMINIO (lectura) |
| Categorías por finalidad | `purposedatacategory/` | `/api/purposes/{id}/data-categories/**` | DPO · ADMIN (escritura) · JEFE_DOMINIO (lectura) |
| Auditoría | `audit/` | `/api/audit/logs/**` · `/api/audit/integrity/**` · `/api/audit/trace/**` | ADMIN |
| Documentos de privacidad | `privacydoc/` | `/api/privacy-documents/**` | DPO (escritura) · cualquier autenticado (lectura) |
| Notificaciones in-app | `notification/` | `/api/notifications/**` | Cualquier autenticado |
| Templates de consentimiento | `template/` | `/api/templates/**` | DPO · ADMIN |
| Agreements (consentimiento) | `agreement/` | `/api/agreements/**` | Cualquier autenticado |
| Vínculo usuario-dominio | `userdomain/` | — (uso interno) | Keycloak-first: vincula `keycloak_id` con dominios |
| Estado de bloqueo | `userstatus/` | — (uso interno) | Keycloak-first: almacena flag `blocked` por `keycloak_id` |

**Módulos del Orquestador** (`orchestrator/` — puerto 8081):

| Módulo | Endpoints | Acceso |
|---|---|---|
| Verificación B2B | `GET /consent/check` | JWT del sistema externo (realm `empresa-cliente`) |
| Captura B2B | `POST /consent/capture` | JWT del sistema externo |
| Revocación B2B | `POST /consent/revoke` | JWT del sistema externo |

---

## Notas sobre `repository/` (paquete legacy)

El paquete `repository/` contiene repositorios que **aún no tienen módulo dueño definido**. **No agregar repositorios nuevos aquí.** Los módulos nuevos siempre deben colocar sus repositorios en `<modulo>/infrastructure/persistence/`.

Los siguientes repositorios en `repository/` tienen duplicado activo en un módulo y son legacy:
- `DataCategoriesRepository` → usar `datacategory/infrastructure/persistence/DataCategoryRepository`
- `LegalBasisCatalogRepository` → usar `legalbasis/infrastructure/persistence/LegalBasisRepository`
- `PurposeDataCategoriesRepository` → usar `purposedatacategory/infrastructure/persistence/PurposeDataCategoryRepository`
- `AgreementsRepository`, `AgreementsPurposesRepository`, `AgreementIntegrityLogRepository`, `AgreementMetadataRepository` → usar los equivalentes en `agreement/infrastructure/persistence/`

`TemplatesRepository` y `TemplatePurposesRepository` **ya no existen en `repository/`** — fueron migrados a `template/infrastructure/persistence/` como parte de la integración del módulo de templates.

---

## Convenciones del proyecto

- Los controllers no tienen `try/catch` — las excepciones se propagan hasta `GlobalExceptionHandler`
- Los servicios devuelven DTOs, nunca entidades JPA
- Los DTOs de Request llevan el sufijo `Request`; los de respuesta llevan el sufijo `Response` o `Dto`
- Las excepciones tipadas viven en `<modulo>/domain/exception/` y extienden `RuntimeException`
- Los repositorios de cada módulo viven en `<modulo>/infrastructure/persistence/` — **no agregar en `repository/`**
- `SecurityContextHelper` es el único lugar donde se extrae el usuario del contexto de seguridad
- Los enums con lógica de negocio viven en `<modulo>/domain/enums/`
- Los módulos nuevos siguen la estructura `domain/ → application/ → infrastructure/ → web/`
- Keycloak es la fuente de verdad para identidad y roles — la BD local solo almacena datos de negocio
