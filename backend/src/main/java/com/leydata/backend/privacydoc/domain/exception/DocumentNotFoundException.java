package com.leydata.backend.privacydoc.domain.exception;

import java.util.UUID;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(UUID id) {
        super("Documento de privacidad no encontrado: " + id);
    }
}
