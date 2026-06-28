package com.leydata.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String KC_TOKEN_URL =
            "http://localhost:8180/realms/leydata/protocol/openid-connect/token";

    @Bean
    public OpenAPI leydataOpenAPI() {

        SecurityScheme oauth2 = new SecurityScheme()
                .name("oauth2")
                .type(SecurityScheme.Type.OAUTH2)
                .description("""
                        **Keycloak OAuth 2.0 — Password Credentials (ROPC)**

                        Configuración en Postman / Bruno:
                        - Grant type: **Password Credentials**
                        - Access Token URL: `http://localhost:8180/realms/leydata/protocol/openid-connect/token`
                        - Client ID: `leydata-frontend`
                        - Client Secret: *(vacío — cliente público)*
                        - Username / Password: credenciales del operador
                        - Scope: `openid profile email`
                        """)
                .flows(new OAuthFlows()
                        .password(new OAuthFlow()
                                .tokenUrl(KC_TOKEN_URL)
                                .refreshUrl(KC_TOKEN_URL)
                                .scopes(new Scopes()
                                        .addString("openid", "Token de identidad")
                                        .addString("profile", "Nombre del usuario")
                                        .addString("email", "Email del usuario"))));

        SecurityScheme bearerJwt = new SecurityScheme()
                .name("bearerAuth")
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        JWT emitido por Keycloak realm `leydata`. Pegar en Swagger UI: `Bearer <token>`

                        Obtener token con curl:
                        ```
                        curl -s -X POST \\
                          http://localhost:8180/realms/leydata/protocol/openid-connect/token \\
                          -H 'Content-Type: application/x-www-form-urlencoded' \\
                          -d 'grant_type=password&client_id=leydata-frontend&username=<email>&password=<password>'
                        ```
                        """);

        return new OpenAPI()
                .addServersItem(new Server()
                        .url("http://localhost:8080")
                        .description("Backend local — Spring Boot :8080"))
                .info(new Info()
                        .title("Ley Data API")
                        .version("3.0.0 — Keycloak-first")
                        .description("""
                                Sistema de gestión de consentimiento para cumplimiento de la **Ley 21.719**
                                de protección de datos personales de Chile.

                                ---

                                ## Modelo de identidad — Keycloak-first

                                **Keycloak es la única fuente de verdad** para usuarios, contraseñas, roles y estado habilitado/deshabilitado.
                                La BD local (`users`) es una caché mínima que solo almacena `keycloak_id`, `email`, `name`, `active` y `created_at`.
                                No existe tabla de roles en la BD local.

                                | Dato | Dónde vive |
                                |---|---|
                                | Email, nombre, contraseña, roles | **Keycloak** (realm `leydata`) |
                                | UUID local, `active`, `created_at` | **BD local** (`users`) |
                                | Dominios asignados | **BD local** (`user_domains`) |
                                | Bloqueo permanente | **BD local** (`user_status`) + Keycloak `enabled=false` |

                                ---

                                ## Autenticación

                                El backend es un **OAuth2 Resource Server** de Keycloak 26 (realm `leydata`).
                                Cada request necesita un JWT en el header `Authorization: Bearer <token>`.

                                El token se obtiene **directamente de Keycloak** — el backend no tiene endpoint de login propio:

                                ```
                                POST http://localhost:8180/realms/leydata/protocol/openid-connect/token
                                Content-Type: application/x-www-form-urlencoded

                                grant_type=password
                                client_id=leydata-frontend
                                username=<email_del_operador>
                                password=<contraseña>
                                ```

                                ### Bruno / Postman

                                **Usar la colección Bruno del repositorio** (`bruno/`) — ya tiene el environment `local`
                                configurado con `baseUrl=http://localhost:8080` y los endpoints de Keycloak incluidos.
                                Completar las variables `email` y `password` en el environment antes de usarla.

                                Para importar esta spec directamente:
                                1. Importar desde `http://localhost:8080/v3/api-docs`
                                2. Crear environment con variable `baseUrl = http://localhost:8080`
                                3. Seleccionar el environment antes de hacer requests

                                ---

                                ## Roles del sistema

                                Los roles viven en Keycloak (realm `leydata`) y llegan al backend en el claim `realm_access.roles` del JWT.

                                | Rol | Qué puede hacer |
                                |-----|-----------------|
                                | `ADMIN` | CRUD de usuarios y dominios, consulta de auditoría |
                                | `DPO` | Revisión de solicitudes, gestión de finalidades y documentos |
                                | `JEFE_DOMINIO` | Crear solicitudes de propósito para sus dominios asignados |
                                | `USER` | Acceso de lectura general |
                                | `TITULAR` | Portal de consentimiento |

                                El `UserStatusFilter` verifica además que el usuario no esté bloqueado o inactivo en la BD local.
                                Un usuario puede tener token válido de Keycloak y recibir 403 si está bloqueado en LeyData.

                                ---

                                ## Endpoints de usuarios

                                El `{userId}` en todos los endpoints es el **keycloak_id** (`sub` del JWT) — no un UUID local.

                                | Método | URL | Descripción |
                                |---|---|---|
                                | `GET` | `/api/users` | Listar usuarios directo desde Keycloak |
                                | `GET` | `/api/users?search=juan` | Busca por nombre/email en Keycloak |
                                | `GET` | `/api/users?status=active` | Solo activos y no bloqueados |
                                | `GET` | `/api/users?status=inactive` | Desactivados (no bloqueados) |
                                | `GET` | `/api/users?status=blocked` | Bloqueados permanentemente |
                                | `GET` | `/api/users?role=DPO` | Filtra por rol exacto |
                                | `GET` | `/api/users/{keycloakId}` | Obtener usuario por keycloak_id |
                                | `POST` | `/api/users` | Crear usuario en Keycloak |
                                | `PUT` | `/api/users/{keycloakId}` | Editar nombre/rol/dominios |
                                | `POST` | `/api/users/{keycloakId}/block` | Bloquear permanentemente |
                                | `POST` | `/api/users/{keycloakId}/deactivate` | Desactivar (reversible) |
                                | `POST` | `/api/users/{keycloakId}/reactivate` | Reactivar |

                                La respuesta de todos los endpoints de usuario incluye `keycloakId` (String) en lugar de `id` (UUID).

                                ---

                                ## Flujo de estados de usuario

                                ```
                                Activo (enabled=true en Keycloak)
                                  ↓ desactivar          ↑ reactivar
                                Inactivo (enabled=false en Keycloak) — reversible
                                  ↓ bloquear
                                Bloqueado (disabled en KC + fila en user_status) — irreversible desde API
                                ```

                                ---

                                ## Flujo completo de gestión de consentimiento

                                1. `ADMIN` crea usuarios y dominios
                                2. `JEFE_DOMINIO` crea una solicitud de propósito (`/api/purpose-requests`)
                                3. `DPO` aprueba la solicitud → se crea la Finalidad automáticamente
                                4. `DPO` vincula categorías de datos a la Finalidad con plazos de retención
                                5. `DPO` crea un **Template** de consentimiento (`/api/templates`), lo vincula a sus Finalidades, lo aprueba y activa → SHA-256 sellado
                                6. `DPO` crea un Documento de Privacidad, vincula la Finalidad, lo publica → PDF con SHA-256
                                7. `JEFE_DOMINIO` recibe notificación de publicación

                                ---

                                ## Templates de consentimiento

                                | Método | URL | Descripción |
                                |---|---|---|
                                | `POST` | `/api/templates` | Crear template en DRAFT |
                                | `POST` | `/api/templates/{id}/new-version` | Nueva versión (mismo TEMPLATE_KEY) |
                                | `GET` | `/api/templates` | Listar con filtros opcionales |
                                | `GET` | `/api/templates/{id}` | Obtener por ID |
                                | `GET` | `/api/templates/family/{templateKey}` | Historial de versiones |
                                | `GET` | `/api/templates/active/{templateKey}` | Versión activa del TEMPLATE_KEY |
                                | `GET` | `/api/templates/{id}/verify` | Verificar integridad SHA-256 |
                                | `POST` | `/api/templates/{id}/approve` | Aprobar template (requiere ≥1 purpose visible) |
                                | `POST` | `/api/templates/{id}/activate` | Activar → desactiva versión anterior |
                                | `POST` | `/api/templates/{id}/purposes` | Vincular purpose al template (solo DRAFT) |
                                | `DELETE` | `/api/templates/{id}/purposes/{purposeId}` | Desvincular purpose (solo DRAFT) |
                                | `GET` | `/api/templates/{id}/purposes` | Listar purposes del template |
                                | `PATCH` | `/api/templates/{id}/purposes/{purposeId}` | Actualizar orden/visibilidad |

                                **Estados del template:** `DRAFT → APPROVED → ACTIVE`

                                Solo puede haber **una versión activa** por `TEMPLATE_KEY`. Activar una versión nueva desactiva automáticamente la anterior. El hash SHA-256 se calcula sobre el contenido del template y sus purposes en el momento de la activación.
                                """)
                        .contact(new Contact()
                                .name("Equipo Ley Data")
                                .email("admin@leydata.cl")))

                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth")
                        .addList("oauth2"))

                .components(new Components()
                        .addSecuritySchemes("bearerAuth", bearerJwt)
                        .addSecuritySchemes("oauth2", oauth2));
    }
}
