package com.luxpretty.app.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the Cloudflare R2 backend. Resolved from
 * environment variables ({@code R2_ACCOUNT_ID}, {@code R2_BUCKET},
 * {@code R2_ACCESS_KEY_ID}, {@code R2_SECRET_ACCESS_KEY}) via the
 * {@code application.properties} mapping.
 */
@ConfigurationProperties(prefix = "app.storage.r2")
public record R2Properties(
        String accountId,
        String bucket,
        String accessKeyId,
        String secretAccessKey
) {
    public String endpoint() {
        return "https://" + accountId + ".r2.cloudflarestorage.com";
    }
}
