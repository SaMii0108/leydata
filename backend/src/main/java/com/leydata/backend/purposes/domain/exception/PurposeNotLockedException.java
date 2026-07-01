package com.leydata.backend.purposes.domain.exception;

// Se lanza cuando se intenta crear una nueva versión de una finalidad que no está
// bloqueada (no está vinculada a ningún template con agreements). En ese caso
// corresponde editar la finalidad in-place en vez de versionarla.
public class PurposeNotLockedException extends RuntimeException {
    public PurposeNotLockedException(String purposeName) {
        super("La finalidad '" + purposeName + "' no está bloqueada. " +
              "Edítala in-place en vez de crear una nueva versión.");
    }
}
