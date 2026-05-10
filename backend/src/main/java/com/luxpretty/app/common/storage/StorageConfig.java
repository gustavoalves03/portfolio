package com.luxpretty.app.common.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Wires the R2 backend when {@code app.storage.backend=r2}. The local
 * backend is autoconfigured via its own component-level
 * {@code @ConditionalOnProperty}, so this class is only consulted when
 * R2 is selected.
 */
@Configuration
@EnableConfigurationProperties(R2Properties.class)
@ConditionalOnProperty(name = "app.storage.backend", havingValue = "r2")
public class StorageConfig {

    @Bean
    public S3Client r2S3Client(R2Properties props) {
        // R2 ignores the AWS region but the SDK requires a non-null value;
        // "auto" is the placeholder used in Cloudflare's own examples.
        return S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.accessKeyId(), props.secretAccessKey())))
                .build();
    }

    @Bean
    public StorageBackend r2StorageBackend(S3Client r2S3Client, R2Properties props) {
        return new R2StorageBackend(r2S3Client, props.bucket());
    }
}
