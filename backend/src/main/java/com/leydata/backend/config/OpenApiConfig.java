package com.leydata.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//Configuración de la documentación OpenAPI (Swagger UI).
//Agrega el esquema de seguridad Bearer JWT para que los endpoints puedan
//probarse directamente desde el navegador en http://localhost:8080/swagger-ui.html
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI leydataOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ley Data API")
                        .description("""
                                Sistema de gestión de consentimiento para cumplimiento de la **Ley 21.719** de Protección de Datos Personales en Chile.

                                ## Autenticación
                                El backend usa Keycloak como proveedor de identidad. Para probar los endpoints:
                                1. Obtener un token: `POST http://localhost:8180/realms/leydata/protocol/openid-connect/token`
                                2. Click en **Authorize** (candado) e ingresar el token como: `Bearer <token>`

                                ## Roles disponibles
                                - **ADMIN** — Gestión de usuarios y dominios, consulta de auditoría
                                - **DPO** — Revisión de solicitudes de propósito
                                - **JEFE_DOMINIO** — Creación de solicitudes de propósito para sus dominios
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipo Ley Data")
                                .email("admin@leydata.cl")))

                //Aplica el esquema de seguridad a todos los endpoints por defecto
                .addSecurityItem(new SecurityRequirement().addList("Bearer JWT"))

                .components(new Components()
                        .addSecuritySchemes("Bearer JWT",
                                new SecurityScheme()
                                        .name("Bearer JWT")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("""
                                                Token JWT obtenido de Keycloak.

                                                Para obtenerlo:
                                                POST http://localhost:8180/realms/leydata/protocol/openid-connect/token
                                                Body (x-www-form-urlencoded):
                                                  grant_type=password
                                                  client_id=leydata-frontend
                                                  username=admin@leydata.cl
                                                  password=Admin1234!
                                                """)));
    }
}
