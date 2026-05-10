package com.luxpretty.app.common.storage;

import java.io.InputStream;

/**
 * Pluggable backend for binary blob storage. Two implementations exist:
 * {@code LocalFsStorageBackend} writes to the working directory under
 * {@code uploads/} (used in dev and tests), and {@code R2StorageBackend}
 * writes to Cloudflare R2 (used in prod).
 *
 * <p>Selection is driven by {@code app.storage.backend=local|r2}.
 *
 * <p>Keys are forward-slash separated logical paths such as
 * {@code "cares/12/abc.png"}. Implementations must not interpret leading
 * slashes; the orchestrating service is responsible for building keys.
 */
public interface StorageBackend {

    /**
     * Persist the given bytes under {@code key}. Overwrites if the key
     * already exists.
     */
    void save(String key, byte[] data, String contentType);

    /**
     * Open a stream over the object at {@code key}. Caller is responsible
     * for closing it. Throws {@link StorageNotFoundException} if missing.
     */
    InputStream load(String key);

    /**
     * Delete the object at {@code key}. No-op if it does not exist.
     */
    void delete(String key);

    /**
     * Recursively delete everything under {@code prefix}. Used to wipe
     * a per-entity folder (e.g. when a care is deleted).
     */
    void deleteFolder(String prefix);
}
