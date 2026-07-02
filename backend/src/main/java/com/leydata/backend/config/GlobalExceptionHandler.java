package com.leydata.backend.config;

import com.leydata.backend.agreement.domain.exception.AgreementNotFoundException;
import com.leydata.backend.datacategory.domain.exception.DataCategoryNotFoundException;
import com.leydata.backend.orgdomain.domain.exception.DomainNotFoundException;
import com.leydata.backend.purposedatacategory.domain.exception.PurposeDataCategoryNotFoundException;
import com.leydata.backend.purposedatacategory.domain.exception.RetentionPolicyLockedException;
import com.leydata.backend.privacydoc.domain.exception.BusinessValidationException;
import com.leydata.backend.privacydoc.domain.exception.DocumentNotFoundException;
import com.leydata.backend.privacydoc.domain.exception.InvalidTransitionException;
import com.leydata.backend.purposes.domain.exception.PurposeNotFoundException;
import com.leydata.backend.purposes.domain.exception.PurposeNotLockedException;
import com.leydata.backend.template.domain.exception.TemplateNotFoundException;
import com.leydata.backend.user.domain.exception.UserAlreadyExistsException;
import com.leydata.backend.user.domain.exception.UserNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private Map<String, Object> buildErrorResponse(String status, String message,
            HttpStatus httpStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status);
        body.put("code", httpStatus.value());
        body.put("message", message);
        return body;
    }

    // ── VALIDACIÓN DE CAMPOS (@Valid / @NotBlank / @NotNull) ─────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        Map<String, Object> body = buildErrorResponse("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Valor inválido para el parámetro '" + ex.getName() + "': " + ex.getValue();
        Map<String, Object> body = buildErrorResponse("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        Map<String, Object> body = buildErrorResponse("BAD_REQUEST",
                "Cuerpo de la solicitud inválido o con formato incorrecto", HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "Parámetro requerido faltante: '" + ex.getParameterName() + "' (tipo " + ex.getParameterType() + ")";
        Map<String, Object> body = buildErrorResponse("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchElement(NoSuchElementException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── HTTP ROUTING ─────────────────────────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = "Método " + ex.getMethod() + " no soportado en esta ruta";
        Map<String, Object> body = buildErrorResponse("METHOD_NOT_ALLOWED", message, HttpStatus.METHOD_NOT_ALLOWED);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", "Ruta no encontrada: " + ex.getResourcePath(),
                HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── MÓDULO USER ──────────────────────────────────────────────────────────────

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        Map<String, Object> body = buildErrorResponse("CONFLICT", ex.getMessage(), HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ── MÓDULO ORGDOMAIN ─────────────────────────────────────────────────────────

    @ExceptionHandler(DomainNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDomainNotFound(DomainNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── MÓDULO AGREEMENTS ────────────────────────────────────────────────────────

    @ExceptionHandler(AgreementNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAgreementNotFound(AgreementNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── MÓDULO DATA CATEGORIES ───────────────────────────────────────────────────

    @ExceptionHandler(DataCategoryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDataCategoryNotFound(DataCategoryNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── MÓDULO AGREEMENTS ────────────────────────────────────────────────────────

    @ExceptionHandler(AgreementNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAgreementNotFound(AgreementNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── MÓDULO PURPOSES
    // ───────────────────────────────────────────────────────────

    @ExceptionHandler(PurposeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePurposeNotFound(PurposeNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(PurposeNotLockedException.class)
    public ResponseEntity<Map<String, Object>> handlePurposeNotLocked(PurposeNotLockedException ex) {
        Map<String, Object> body = buildErrorResponse("PURPOSE_NOT_LOCKED", ex.getMessage(), HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ── MÓDULO TEMPLATES ─────────────────────────────────────────────────────────

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTemplateNotFound(TemplateNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    // ── MÓDULO PURPOSE DATA CATEGORIES ──────────────────────────────────────────

    @ExceptionHandler(PurposeDataCategoryNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handlePdcNotFound(PurposeDataCategoryNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(RetentionPolicyLockedException.class)
    public ResponseEntity<Map<String, Object>> handleRetentionLocked(RetentionPolicyLockedException ex) {
        Map<String, Object> body = buildErrorResponse("RETENTION_LOCKED", ex.getMessage(), HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(PurposeNotLockedException.class)
    public ResponseEntity<Map<String, Object>> handlePurposeNotLocked(PurposeNotLockedException ex) {
        Map<String, Object> body = buildErrorResponse("PURPOSE_NOT_LOCKED", ex.getMessage(), HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ── MÓDULO PRIVACY DOCUMENTS ─────────────────────────────────────────────────

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentNotFound(DocumentNotFoundException ex) {
        Map<String, Object> body = buildErrorResponse("NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidTransitionException ex) {
        Map<String, Object> body = buildErrorResponse("CONFLICT", ex.getMessage(), HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(BusinessValidationException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessValidation(BusinessValidationException ex) {
        Map<String, Object> body = buildErrorResponse("UNPROCESSABLE_ENTITY", ex.getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    // ── AUTENTICACIÓN Y AUTORIZACIÓN ─────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Error de autenticación: {}", ex.getClass().getSimpleName());
        Map<String, Object> body = buildErrorResponse("UNAUTHORIZED",
                "Acceso no autorizado. Por favor, inicia sesión nuevamente", HttpStatus.UNAUTHORIZED);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        Map<String, Object> body = buildErrorResponse("FORBIDDEN",
                "No tienes permiso para acceder a este recurso", HttpStatus.FORBIDDEN);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        log.warn("Excepción de seguridad: {}", ex.getMessage());
        Map<String, Object> body = buildErrorResponse("FORBIDDEN", ex.getMessage(), HttpStatus.FORBIDDEN);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    // ── LÓGICA DE NEGOCIO ────────────────────────────────────────────────────────

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(IllegalStateException ex) {
        log.warn("Estado inválido: {}", ex.getMessage());
        Map<String, Object> body = buildErrorResponse("CONFLICT", ex.getMessage(), HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Argumento inválido: {}", ex.getMessage());
        Map<String, Object> body = buildErrorResponse("BAD_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(IllegalAccessException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalAccessException(IllegalAccessException ex) {
        log.warn("Acceso ilegal: {}", ex.getMessage());
        Map<String, Object> body = buildErrorResponse("FORBIDDEN",
                "Acceso no permitido a este recurso", HttpStatus.FORBIDDEN);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getMostSpecificCause().getMessage());
        Map<String, Object> body = buildErrorResponse("CONFLICT",
                "Ya existe un registro con los mismos datos únicos (clave duplicada o restricción violada)",
                HttpStatus.CONFLICT);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    // ── GENÉRICAS ────────────────────────────────────────────────────────────────

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(NullPointerException ex) {
        log.error("NullPointerException no esperada:", ex);
        Map<String, Object> body = buildErrorResponse("INTERNAL_SERVER_ERROR",
                "Se produjo un error interno. Por favor, intenta de nuevo más tarde",
                HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException no controlada: ", ex);
        Map<String, Object> body = buildErrorResponse("INTERNAL_SERVER_ERROR",
                "Error al procesar la solicitud",
                HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Excepción no manejada: ", ex);
        Map<String, Object> body = buildErrorResponse("INTERNAL_SERVER_ERROR",
                "Error interno del servidor. Por favor, intenta de nuevo más tarde",
                HttpStatus.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
