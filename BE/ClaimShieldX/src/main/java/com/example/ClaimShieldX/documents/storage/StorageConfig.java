package com.example.ClaimShieldX.documents.storage;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.nio.file.Path;
import java.util.Set;

/**
 * Chooses where original documents live. There is no automatic fallback: asking for the cloud store without
 * configuring it stops the application at startup instead of quietly writing files somewhere else.
 */
@Slf4j
@Configuration
public class StorageConfig {
    private static final Set<String> TYPES = Set.of("local", "gcs");

    @Bean
    InitializingBean storageTypeCheck(@Value("${app.storage.type:local}") String type,
                                      @Value("${app.storage.allow-local-on-cloud-run:false}") boolean allowLocalOnCloudRun,
                                      Environment environment) {
        return () -> {
            if (!TYPES.contains(type)) {
                throw new IllegalStateException("app.storage.type must be 'local' or 'gcs', but was '" + type + "'");
            }
            // Cloud Run sets K_SERVICE (services) or CLOUD_RUN_JOB (jobs). Its disk is in memory and is wiped whenever
            // an instance stops, and a separate worker instance would not see the files at all.
            boolean onCloudRun = environment.getProperty("K_SERVICE") != null || environment.getProperty("CLOUD_RUN_JOB") != null;
            if (type.equals("local") && onCloudRun && !allowLocalOnCloudRun) {
                throw new IllegalStateException("app.storage.type=local on Cloud Run would lose every uploaded document when the "
                        + "instance restarts. Use app.storage.type=gcs with app.storage.gcs.bucket, or set "
                        + "app.storage.allow-local-on-cloud-run=true to accept the data loss (a throwaway demo only).");
            }
            log.info("Document storage type: {}", type);
        };
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    DocumentStorage localDocumentStorage(@Value("${app.storage.local-path}") String path) {
        return new LocalDocumentStorage(Path.of(path));
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "gcs")
    DocumentStorage gcsDocumentStorage(@Value("${app.storage.gcs.bucket:}") String bucket,
                                       @Value("${app.storage.gcs.project-id:}") String projectId) {
        if (bucket.isBlank()) {
            throw new IllegalStateException("app.storage.type=gcs requires app.storage.gcs.bucket to be set "
                    + "(for example the APP_STORAGE_GCS_BUCKET environment variable)");
        }
        StorageOptions.Builder options = StorageOptions.newBuilder();
        if (!projectId.isBlank()) {
            options.setProjectId(projectId);
        }
        Storage storage = options.build().getService();
        return new GcsDocumentStorage(storage, bucket);
    }
}
