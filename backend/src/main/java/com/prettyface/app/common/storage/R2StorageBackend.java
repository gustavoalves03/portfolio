package com.prettyface.app.common.storage;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.util.List;

/**
 * Cloudflare R2 implementation of {@link StorageBackend}, talking to the
 * S3-compatible API.
 *
 * <p>Wired by {@link StorageConfig} when {@code app.storage.backend=r2}.
 */
public class R2StorageBackend implements StorageBackend {

    private final S3Client client;
    private final String bucket;

    public R2StorageBackend(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public void save(String key, byte[] data, String contentType) {
        client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(normalize(key))
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data));
    }

    @Override
    public InputStream load(String key) {
        try {
            ResponseInputStream<?> body = client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(normalize(key))
                            .build());
            return body;
        } catch (NoSuchKeyException e) {
            throw new StorageNotFoundException(key);
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(normalize(key))
                .build());
    }

    @Override
    public void deleteFolder(String prefix) {
        String norm = normalize(prefix);
        if (!norm.endsWith("/")) {
            norm = norm + "/";
        }
        String continuationToken = null;
        do {
            ListObjectsV2Request.Builder list = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(norm);
            if (continuationToken != null) {
                list.continuationToken(continuationToken);
            }
            ListObjectsV2Response page = client.listObjectsV2(list.build());
            List<ObjectIdentifier> ids = page.contents().stream()
                    .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                    .toList();
            if (!ids.isEmpty()) {
                client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(bucket)
                        .delete(Delete.builder().objects(ids).build())
                        .build());
            }
            continuationToken = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (continuationToken != null);
    }

    /**
     * Strip the legacy {@code uploads/} prefix some callers may still pass.
     * R2 keys are flat — folders are conceptual, expressed as slashes inside
     * the key.
     */
    private String normalize(String key) {
        return key.startsWith("uploads/") ? key.substring("uploads/".length()) : key;
    }
}
