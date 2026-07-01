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
@Schema(description = "Datos para editar un usuario existente. Todos los campos son opcionales.")
public class UpdateUserByAdminRequest {

    @Schema(description = "Nuevo nombre completo del usuario", example = "Pedro López Martínez")
    private String name;

    @Schema(description = "Nuevo email del usuario", example = "pedro@empresa.cl")
    private String email;

    @Schema(description = "Nueva contraseña. Al establecerla, el usuario deberá cambiarla en su próximo login.", example = "NuevaPass123!")
    private String password;

    @Schema(description = "Lista completa de nuevos roles (reemplaza los actuales). " +
            "Si no incluye JEFE_DOMINIO, los dominios del usuario se limpian automáticamente.",
            example = "[\"JEFE_DOMINIO\"]")
    private List<String> roleCodes;

    @Schema(description = "Dominio asignado, como lista de un solo elemento (reemplaza el actual). " +
            "Lista vacía limpia el dominio. Solo válido para usuarios con rol JEFE_DOMINIO — " +
            "un jefe de dominio solo puede tener un dominio asignado a la vez.",
            example = "[\"550e8400-e29b-41d4-a716-446655440000\"]")
    private List<UUID> domainIds;
}
