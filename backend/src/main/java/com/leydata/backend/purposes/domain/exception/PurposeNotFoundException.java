package com.leydata.backend.purposes.domain.exception;

import java.util.UUID;

public class PurposeNotFoundException extends RuntimeException {
    public PurposeNotFoundException(UUID id) {
        super("Finalidad no encontrada: " + id);
    }
}
