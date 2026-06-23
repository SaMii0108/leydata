package com.leydata.backend.privacydoc.application.dto;

import com.leydata.backend.privacydoc.domain.enums.DocumentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateDocumentRequest {

    @NotNull(message = "La categoría es obligatoria")
    private DocumentCategory category;

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    /** Texto legal. Puede enviarse vacío y completarse antes de enviar a revisión. */
    private String content;

    /** UUID de la Template visual. Requerido antes de enviar a revisión. */
    private UUID templateId;
}
