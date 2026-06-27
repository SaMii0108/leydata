package com.leydata.backend.template.domain.exception;

import java.util.UUID;

public class TemplateNotFoundException extends RuntimeException {
    public TemplateNotFoundException(UUID id) {
        super("Template no encontrado: " + id);
    }
}
