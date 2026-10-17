package com.example.ClaimShieldX.documents.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The storage choice is explicit and fails fast: it must never fall back to another store on its own. */
class StorageConfigTests {
    @TempDir Path folder;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(StorageConfig.class);
    }

    @Test
    void localIsTheDefaultAndUsesTheConfiguredFolder() {
        runner().withPropertyValues("app.storage.local-path=" + folder.resolve("docs")).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DocumentStorage.class)).isInstanceOf(LocalDocumentStorage.class);
        });
    }

    @Test
    void anUnknownStorageTypeStopsStartup() {
        runner().withPropertyValues("app.storage.type=gsc", "app.storage.local-path=" + folder).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("must be 'local' or 'gcs'");
        });
    }

    @Test
    void cloudStorageWithoutABucketStopsStartupInsteadOfFallingBackToLocalDisk() {
        runner().withPropertyValues("app.storage.type=gcs", "app.storage.local-path=" + folder).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.storage.gcs.bucket");
        });
    }

    @Test
    void localStorageOnCloudRunStopsStartupBecauseItsDiskIsWipedOnRestart() {
        runner().withPropertyValues("K_SERVICE=claimshield-api", "app.storage.local-path=" + folder).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("would lose every uploaded document");
        });
    }

    @Test
    void localStorageInACloudRunJobIsRefusedToo() {
        runner().withPropertyValues("CLOUD_RUN_JOB=claimshield-worker", "app.storage.local-path=" + folder).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("would lose every uploaded document");
        });
    }

    @Test
    void theCloudRunGuardCanBeOverriddenOnPurposeForAThrowawayDemo() {
        runner().withPropertyValues("K_SERVICE=claimshield-api", "app.storage.allow-local-on-cloud-run=true", "app.storage.local-path=" + folder)
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void onCloudRunTheCloudStoreIsNotBlamedForTheLocalGuard() {
        runner().withPropertyValues("K_SERVICE=claimshield-api", "app.storage.type=gcs", "app.storage.local-path=" + folder).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.storage.gcs.bucket");
            java.io.StringWriter trace = new java.io.StringWriter();
            context.getStartupFailure().printStackTrace(new java.io.PrintWriter(trace));
            assertThat(trace.toString()).doesNotContain("would lose every uploaded document");
        });
    }

    @Test
    void aBlankBucketIsTreatedAsMissing() {
        runner().withPropertyValues("app.storage.type=gcs", "app.storage.gcs.bucket=   ", "app.storage.local-path=" + folder).run(context ->
                assertThat(context).hasFailed());
    }
}
