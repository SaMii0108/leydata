package com.leydata.backend.purposedatacategory.domain.exception;

// Se lanza cuando se intenta modificar o eliminar una política de retención
// cuya finalidad ya está asociada a un documento PUBLISHED.
// La solución es crear una nueva versión del documento con la nueva política.
public class RetentionPolicyLockedException extends RuntimeException {
    public RetentionPolicyLockedException(String purposeName) {
        super("La política de retención está bloqueada: la finalidad '" + purposeName +
              "' está asociada a un documento publicado. " +
              "Para cambiar la retención crea una nueva versión del documento " +
              "(POST /api/privacy-documents/{id}/new-version).");
    }
}
