package com.leydata.backend.config;

import com.leydata.backend.security.exception.UserBlockedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejador global de excepciones para la aplicación.
 * 
 * Intercepta todas las excepciones en los controladores y devuelve respuestas
 * JSON
 * estandarizadas sin revelar detalles internos del servidor (stacktraces, etc.)
 * 
 * Cada tipo de excepción se mapea a un código HTTP y mensaje usuario-friendly.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Estructura estándar de respuesta de error
     */
    private Map<String, Object> buildErrorResponse(String status, String message,
            HttpStatus httpStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status);
        body.put("code", httpStatus.value());
        body.put("message", message);
        return body;
    }

    // EXCEPCIONES DE AUTENTICACIÓN

    /**
     * Usuario intenta acceder pero su cuenta está bloqueada o desactivada
     */
    @ExceptionHandler(UserBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleUserBlockedException(
            UserBlockedException ex,
            WebRequest request) {

        log.warn("Acceso denegado por usuario bloqueado/desactivado: {}", ex.getMessage());

        Map<String, Object> body = buildErrorResponse(
                "FORBIDDEN",
                ex.getMessage(),
                HttpStatus.FORBIDDEN);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(body);
    }

    /**
     * Usuario no encontrado (credenciales inválidas, token con usuario eliminado,
     * etc.)
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUsernameNotFoundException(
            UsernameNotFoundException ex,
            WebRequest request) {

        log.warn("Usuario no encontrado: {}", ex.getMessage());

        Map<String, Object> body = buildErrorResponse(
                "UNAUTHORIZED",
                "Credenciales inválidas o usuario no encontrado",
                HttpStatus.UNAUTHORIZED);

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(body);
    }

    /**
     * Error de autenticación genérico (token expirado, inválido, etc.)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(
            AuthenticationException ex,
            WebRequest request) {

        log.warn("Error de autenticación: {}", ex.getClass().getSimpleName());

        Map<String, Object> body = buildErrorResponse(
                "UNAUTHORIZED",
                "Acceso no autorizado. Por favor, inicia sesión nuevamente",
                HttpStatus.UNAUTHORIZED);

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(body);
    }

    // EXCEPCIONES DE AUTORIZACIÓN

    /**
     * Usuario autenticado intenta acceder a recurso sin permisos suficientes
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request) {

        log.warn("Acceso denegado: {}", ex.getMessage());

        Map<String, Object> body = buildErrorResponse(
                "FORBIDDEN",
                "No tienes permiso para acceder a este recurso",
                HttpStatus.FORBIDDEN);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(body);
    }

    /**
     * Seguridad genérica (ej: SecurityException lanzada en validaciones)
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(
            SecurityException ex,
            WebRequest request) {

        log.warn("Excepción de seguridad: {}", ex.getMessage());

        Map<String, Object> body = buildErrorResponse(
                "FORBIDDEN",
                ex.getMessage(),
                HttpStatus.FORBIDDEN);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(body);
    }

    // EXCEPCIONES DE LÓGICA DE NEGOCIO

    /**
     * Operación no permitida por regla de negocio (ej: usuario ya bloqueado)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(
            IllegalStateException ex,
            WebRequest request) {

        log.warn("Estado inválido: {}", ex.getMessage());

        Map<String, Object> body = buildErrorResponse(
                "CONFLICT",
                ex.getMessage(),
                HttpStatus.CONFLICT);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(body);
    }

    /**
     * Argumentos inválidos (datos incorrectos, usuario no encontrado, etc.)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        log.warn("Argumento inválido: {}", ex.getMessage());

        Map<String, Object> body = buildErrorResponse(
                "BAD_REQUEST",
                ex.getMessage(),
                HttpStatus.BAD_REQUEST);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(body);
    }

    /**
     * Validación fallida (datos de entrada incompletos o inválidos)
     */
    @ExceptionHandler(IllegalAccessException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalAccessException(
            IllegalAccessException ex,
            WebRequest request) {

        log.warn("Acceso ilegal: {}", ex.getMessage());

        Map<String, Object> body = buildErrorResponse(
                "FORBIDDEN",
                "Acceso no permitido a este recurso",
                HttpStatus.FORBIDDEN);

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(body);
    }

    // EXCEPCIONES GENÉRICAS

    /**
     * NullPointerException o cualquier otra excepción no controlada
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(
            NullPointerException ex,
            WebRequest request) {

        log.error("NullPointerException no esperada:", ex);

        Map<String, Object> body = buildErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Se produjo un error interno. Por favor, intenta de nuevo más tarde",
                HttpStatus.INTERNAL_SERVER_ERROR);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }

    /**
     * Excepción genérica (fallback para excepciones no contempladas)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex,
            WebRequest request) {

        log.error("Excepción no manejada: ", ex);

        Map<String, Object> body = buildErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Error interno del servidor. Por favor, intenta de nuevo más tarde",
                HttpStatus.INTERNAL_SERVER_ERROR);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }

    /**
     * RuntimeException (cualquier excepción de tiempo de ejecución no capturada)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex,
            WebRequest request) {

        log.error("RuntimeException: ", ex);

        // Algunos RuntimeException pueden contener información útil al usuario
        String userMessage = ex.getMessage() != null ? ex.getMessage()
                : "Error al procesar la solicitud";

        Map<String, Object> body = buildErrorResponse(
                "INTERNAL_SERVER_ERROR",
                userMessage,
                HttpStatus.INTERNAL_SERVER_ERROR);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body);
    }
}
