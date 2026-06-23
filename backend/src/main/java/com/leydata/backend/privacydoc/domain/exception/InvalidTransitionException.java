package com.leydata.backend.privacydoc.domain.exception;

import com.leydata.backend.privacydoc.domain.enums.DocumentStatus;

public class InvalidTransitionException extends RuntimeException {
    public InvalidTransitionException(DocumentStatus current, DocumentStatus target) {
        super("Transición inválida: " + current + " → " + target);
    }
}
