package com.leydata.backend.purposes.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Schema(description = "Datos para crear una nueva finalidad de tratamiento de datos")
public class CreatePurposeRequest {

    @NotBlank(message = "El código es obligatorio")
    @Schema(description = "Código único. Se normaliza a MAYÚSCULAS automáticamente.",
            example = "MARKETING_NEWSLETTER")
    private String code;

    @NotBlank(message = "El nombre es obligatorio")
    @Schema(description = "Nombre operacional de la finalidad",
            example = "Envío de ofertas y promociones por email")
    private String name;

    @NotBlank(message = "La descripción es obligatoria")
    @Schema(description = "Descripción detallada del tratamiento",
            example = "Uso del correo electrónico y teléfono para enviar descuentos, boletines y material publicitario.")
    private String description;

    @Schema(description = "Descripción corta para mostrar al titular",
            example = "Envío de newsletters")
    private String shortDescription;

    @NotNull(message = "El campo 'required' es obligatorio")
    @Schema(description = "true = el titular no puede rechazar esta finalidad (ej: facturación obligatoria). " +
            "false = el titular puede optar por no aceptarla.",
            example = "false")
    private Boolean required;

    @NotNull(message = "El campo 'revocable' es obligatorio")
    @Schema(description = "true = el titular puede retirar su consentimiento después de otorgarlo.",
            example = "true")
    private Boolean revocable;

    @Schema(description = "Orden de presentación al titular (menor número = aparece primero)",
            example = "3")
    private Integer presentationOrder;

    @NotNull(message = "La base de licitud es obligatoria")
    @Schema(description = "UUID de la base de licitud del catálogo (/api/legal-basis). " +
            "Determina si requiere consentimiento activo del titular.",
            example = "550e8400-e29b-41d4-a716-446655440001")
    private UUID legalBasisId;

    @NotNull(message = "El dominio es obligatorio")
    @Schema(description = "UUID del dominio organizacional al que pertenece esta finalidad",
            example = "550e8400-e29b-41d4-a716-446655440002")
    private UUID domainId;

    @Schema(description = "UUID de la solicitud de propósito aprobada que originó esta finalidad. " +
            "Opcional pero recomendado para trazabilidad.",
            example = "550e8400-e29b-41d4-a716-446655440003",
            nullable = true)
    private UUID purposeRequestId;

    @Schema(description = "Texto exacto que el titular lee y acepta al otorgar consentimiento. " +
            "Puede completarse después. Ej: 'Al aceptar, autorizo a [Org] a usar mi email para ofertas.'",
            example = "Al aceptar, autorizo a [Organización] a utilizar mi correo electrónico para enviar ofertas y descuentos durante 365 días.",
            nullable = true)
    private String consentStatement;
}
