package com.leydata.backend.security.exception;

import org.springframework.security.core.AuthenticationException;

/* Excepción lanzada cuando un usuario intenta acceder pero su cuenta está
 * bloqueada
 * permanentemente o desactivada.
 */
public class UserBlockedException extends AuthenticationException {

    public UserBlockedException(String message) {
        super(message);
    }

    public UserBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
