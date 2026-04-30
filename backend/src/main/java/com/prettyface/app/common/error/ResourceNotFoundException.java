package com.prettyface.app.common.error;

/**
 * Thrown by services when a referenced resource (entity, file…) cannot be
 * found. Maps to HTTP 404 in {@link GlobalExceptionHandler}.
 *
 * <p>Use this — rather than {@link IllegalArgumentException} — for
 * "the requested thing doesn't exist" scenarios. Plain
 * {@link IllegalArgumentException} is reserved for invalid input
 * (validation failures) and maps to HTTP 400.</p>
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
