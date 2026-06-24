package com.leydata.backend.privacydoc.application.dto;

import lombok.Data;
import java.util.UUID;

/** Solo aplica en estado DRAFT. Todos los campos son opcionales (PATCH semántico). */
@Data
public class UpdateDocumentRequest {
    private String name;
    private String content;
    private UUID templateId;
}
