package com.leydata.backend.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para crear un nuevo operador del sistema")
public class CreateUserRequest {

    @Schema(description = "Contraseña inicial — se envía a Keycloak, nunca se almacena localmente",
            example = "Temporal123!")
    private String password;

    @Schema(description = "Email único del usuario — se usa como username en Keycloak",
            example = "maria.garcia@empresa.cl")
    private String email;

    @Schema(description = "Nombre completo (firstName + lastName separados por espacio)",
            example = "María García")
    private String name;

    @Schema(description = "Rol asignado. Válidos: ADMIN, DPO, JEFE_DOMINIO, USER, TITULAR",
            example = "JEFE_DOMINIO")
    private String roleCode;

    @Schema(description = "ID de dominio a asignar, como lista de un solo elemento. Solo aplica si roleCode=JEFE_DOMINIO. " +
            "Un jefe de dominio solo puede tener un dominio asignado. El dominio debe estar activo.",
            example = "[\"550e8400-e29b-41d4-a716-446655440000\"]")
    private List<UUID> domainIds;

    @Schema(hidden = true)
    private String keycloakId;
}
