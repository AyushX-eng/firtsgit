package com.firstgit.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler.
 * 
 * ⚠️ CRITICAL: NEVER leaks stack traces, internal paths, server versions, or framework details.
 * All errors return opaque, generic messages suitable for production.
 * Detailed logging happens server-side only.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_ERROR = "An unexpected error occurred. Please try again.";

    private static final String JSON_ERROR = "error";
    private static final String JSON_TIMESTAMP = "timestamp";

    /**
     * Catch-all for unhandled exceptions — returns HTTP 500 with opaque message.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e) {
        log.error("Unhandled exception: ", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR);
    }

    /**
     * Validation errors (@Valid on @RequestBody) — returns HTTP 400 with field-level details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        String fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .collect(Collectors.joining(", "));
        log.warn("Validation failed for fields: {}", fieldErrors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request parameters.");
    }

    /**
     * Binding validation errors for multipart/form-data requests.
     * Spring uses BindException for @Valid on @ModelAttribute in multipart requests.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(BindException e) {
        String fieldErrors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getField)
                .collect(Collectors.joining(", "));
        log.warn("Binding validation failed for fields: {}", fieldErrors);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid request parameters.");
    }

    /**
     * Missing required parameters/parts.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, Object>> handleMissingParam(Exception e) {
        log.warn("Missing required parameter: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "A required field is missing.");
    }

    /**
     * File upload exceeds configured maximum size.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large. Maximum allowed is 100MB.");
    }

    /**
     * Access denied (insufficient permissions).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        log.warn("Access denied: {}", e.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
    }

    /**
     * Standardized error response builder.
     * Returns a JSON object with only 'error' and 'timestamp' fields.
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put(JSON_ERROR, message);
        body.put(JSON_TIMESTAMP, Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
