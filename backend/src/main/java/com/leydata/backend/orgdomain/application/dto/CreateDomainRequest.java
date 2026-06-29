package com.leydata.backend.orgdomain.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos para crear un dominio organizacional")
public class CreateDomainRequest {

    @Schema(description = "Código único e inmutable del dominio — se usa como identificador en logs de auditoría",
            example = "mkt")
    private String code;

    @Schema(description = "Nombre descriptivo del área",
            example = "Marketing Digital")
    private String name;

    @Schema(description = "Descripción del área y su función",
            example = "Área de marketing digital y gestión de campañas publicitarias")
    private String description;

    @Schema(description = "UUID del usuario con rol JEFE_DOMINIO a asignar. Opcional — se puede asignar después.",
            example = "550e8400-e29b-41d4-a716-446655440000",
            nullable = true)
    private UUID jefeId;
}
