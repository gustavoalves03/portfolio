package com.luxpretty.app.common.storage;

/**
 * Thrown by {@link StorageBackend#load(String)} when the requested key
 * has no associated object. Callers (e.g. {@code FileController}) map
 * this to HTTP 404.
 */
public class StorageNotFoundException extends RuntimeException {
    public StorageNotFoundException(String key) {
        super("No storage object at key: " + key);
    }
}
