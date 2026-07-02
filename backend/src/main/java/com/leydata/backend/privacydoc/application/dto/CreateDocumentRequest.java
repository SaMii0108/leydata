package com.leydata.backend.privacydoc.application.dto;

import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Datos para crear un nuevo documento de privacidad en estado DRAFT")
public class CreateDocumentRequest {

    @NotNull(message = "La categoría es obligatoria")
    @Schema(description = "Categoría del documento. Define el tipo de política de privacidad.",
            example = "MARKETING",
            allowableValues = {"POLITICA_PRIVACIDAD", "AVISO_COOKIES", "DATOS_SENSIBLES",
                               "MARKETING_DIRECTO", "MENORES_EDAD", "TRANSFERENCIA_TERCEROS"})
    private DocumentCategory category;

    @NotBlank(message = "El nombre es obligatorio")
    @Schema(description = "Nombre descriptivo del documento",
            example = "Política de Privacidad — Marketing Digital 2026")
    private String name;

    @Schema(description = "Contenido legal del documento en texto. Puede enviarse vacío y completarse antes de enviar a revisión.",
            example = "Este documento describe las condiciones bajo las cuales [Organización] trata los datos personales...",
            nullable = true)
    private String content;
}
