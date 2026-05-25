package com.luxpretty.app.common.error;

import com.luxpretty.app.feature.app.FeatureDisabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String,Object>> typeMismatch(MethodArgumentTypeMismatchException ex) {
        String param = ex.getName();
        String value = ex.getValue() == null ? "null" : ex.getValue().toString();
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid value for parameter '" + param + "': " + value));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String,Object>> notReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Malformed request body"));
    }

    /**
     * Maps to 400 BAD_REQUEST. Use {@link IllegalArgumentException} for
     * invalid input / validation failures. For "resource not found", throw
     * {@link ResourceNotFoundException} which maps to 404 below.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> badArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String,Object>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String,Object>> badState(IllegalStateException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String,Object>> badCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null || ex.getReason().isBlank()
                ? ex.getMessage()
                : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> badRequest(MethodArgumentNotValidException ex) {
        var msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField()+": "+f.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(Map.of("errors", msg));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String,Object>> conflict(org.springframework.dao.DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "A record with this value already exists"));
    }

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<Map<String, Object>> featureDisabled(FeatureDisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "FEATURE_DISABLED",
                "featureKey", ex.featureKey.name(),
                "minimumTier", ex.minimumTier.name()
        ));
    }

    @ExceptionHandler(BookingLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> bookingLimitExceeded(BookingLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "code", ex.getCode(),
                        "message", ex.getMessage(),
                        "limit", ex.getLimit(),
                        "currentCount", ex.getCurrentCount()
                ));
    }
}
