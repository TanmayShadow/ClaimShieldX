package com.example.ClaimShieldX.history.service;

import com.example.ClaimShieldX.history.config.HistoryProperties;
import com.example.ClaimShieldX.history.model.HistoricalImport;
import com.example.ClaimShieldX.history.model.ImportFailureCode;
import com.example.ClaimShieldX.history.model.ImportStatus;
import com.example.ClaimShieldX.history.model.ImportType;
import com.example.ClaimShieldX.history.repository.HistoricalImportRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** The two short transactions that bracket an import, so a crash never leaves an import without a visible record. */
@Component
class HistoryImportRecords {
    private final HistoricalImportRepository repository;
    private final HistoryProperties properties;

    HistoryImportRecords(HistoricalImportRepository repository, HistoryProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    HistoricalImport start(UUID organizationId, UUID uploadedBy, ImportType type, String templateVersion, String source, String sha256, long sizeBytes) {
        // An import left PROCESSING by a process that died stored no rows (they roll back with it); it would otherwise look in progress forever.
        Instant now = Instant.now();
        repository.failStale(organizationId, type, ImportStatus.PROCESSING, ImportStatus.FAILED, ImportFailureCode.INTERNAL_ERROR,
                "The import was interrupted and nothing was stored", now, now.minus(properties.staleProcessingAfter()));
        HistoricalImport record = new HistoricalImport();
        record.setOrganizationId(organizationId);
        record.setUploadedBy(uploadedBy);
        record.setImportType(type);
        record.setTemplateVersion(templateVersion);
        record.setSource(source);
        record.setSha256(sha256);
        record.setSizeBytes(sizeBytes);
        record.setStatus(ImportStatus.PROCESSING);
        record.setStartedAt(now);
        return repository.saveAndFlush(record);
    }

    /** A file-level failure: no row of the file is stored, so the counts stay at zero. */
    @Transactional
    void fail(UUID importId, ImportFailureCode code, String detail) {
        HistoricalImport record = repository.findById(importId).orElseThrow();
        record.setStatus(ImportStatus.FAILED);
        record.setFailureCode(code);
        record.setFailureDetail(detail);
        record.setFinishedAt(Instant.now());
        repository.saveAndFlush(record);
    }
}
