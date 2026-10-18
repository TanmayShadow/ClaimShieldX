package com.example.ClaimShieldX.history.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.exceptions.RequestValidationException;
import com.example.ClaimShieldX.history.config.HistoryProperties;
import com.example.ClaimShieldX.history.csv.CsvTemplate;
import com.example.ClaimShieldX.history.csv.CsvTemplates;
import com.example.ClaimShieldX.history.csv.ImportFileException;
import com.example.ClaimShieldX.history.csv.StrictCsvReader;
import com.example.ClaimShieldX.history.dto.ImportResponse;
import com.example.ClaimShieldX.history.model.HistoricalImport;
import com.example.ClaimShieldX.history.model.ImportFailureCode;
import com.example.ClaimShieldX.history.model.ImportType;
import com.example.ClaimShieldX.history.repository.HistoricalImportRepository;
import com.example.ClaimShieldX.idempotency.service.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Imports one CSV of historical data. The order matters: cheap request checks first, the Idempotency-Key before anything is
 * stored, a visible import record before the file is read, and the rows stored together with the final status in one
 * transaction. A file that cannot be used at all ends as a FAILED import (a record the client can read), never as a silent
 * nothing; a file with some bad rows keeps the good rows and lists the bad ones.
 */
@Slf4j
@Service
public class HistoryImportService {
    public static final String OPERATION = "POST /historical-imports";
    private static final String RESOURCE_TYPE = "HISTORICAL_IMPORT";
    private static final Pattern SOURCE = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    public record Result(ImportResponse importRecord, boolean replayed) {
    }

    private final HistoryImportRecords records;
    private final HistoryImportProcessor processor;
    private final HistoricalImportRepository importRepository;
    private final IdempotencyService idempotencyService;
    private final HistoryProperties properties;

    public HistoryImportService(HistoryImportRecords records, HistoryImportProcessor processor, HistoricalImportRepository importRepository,
                                IdempotencyService idempotencyService, HistoryProperties properties) {
        this.records = records;
        this.processor = processor;
        this.importRepository = importRepository;
        this.idempotencyService = idempotencyService;
        this.properties = properties;
    }

    public Result importFile(UserPrincipal principal, MultipartFile file, ImportType type, String templateVersion, String source, String idempotencyKey) {
        IdempotencyService.validateKey(idempotencyKey);
        CsvTemplate template = CsvTemplates.find(type, templateVersion == null ? "" : templateVersion.strip())
                .orElseThrow(() -> RequestValidationException.of("templateVersion", "is not supported; supported versions: " + String.join(", ", CsvTemplates.supportedVersions())));
        String sourceLabel = normalizeSource(source);
        byte[] bytes = read(file);
        String sha256 = sha256(bytes);

        UUID organizationId = principal.getOrganization().getId();
        String fingerprint = IdempotencyService.fingerprint(type.name(), template.version(), sourceLabel == null ? "" : sourceLabel, sha256);
        IdempotencyService.Begin begin = idempotencyService.begin(organizationId, OPERATION, idempotencyKey, fingerprint);
        if (begin.mode() == IdempotencyService.Mode.REPLAY) {
            HistoricalImport original = importRepository.findByIdAndOrganizationId(begin.resourceId(), organizationId)
                    .orElseThrow(() -> new IllegalStateException("A completed idempotency key points at a missing import"));
            return new Result(ImportResponse.of(original), true);
        }

        HistoricalImport started = null;
        try {
            started = records.start(organizationId, principal.getId(), type, template.version(), sourceLabel, sha256, bytes.length);
            HistoricalImport finished = run(started, template, bytes, organizationId);
            idempotencyService.complete(begin.keyId(), 202, RESOURCE_TYPE, finished.getId());
            return new Result(ImportResponse.of(finished), false);
        } catch (RuntimeException e) {
            log.error("History import {} failed unexpectedly", started == null ? "(not started)" : started.getId(), e);
            if (started != null) {
                markFailedQuietly(started.getId());
            }
            idempotencyService.release(begin.keyId());
            throw e;
        }
    }

    private HistoricalImport run(HistoricalImport started, CsvTemplate template, byte[] bytes, UUID organizationId) {
        try {
            if (bytes.length > properties.maxFileBytes()) {
                throw new ImportFileException(ImportFailureCode.FILE_TOO_LARGE, "The file is larger than " + properties.maxFileBytes() / (1024 * 1024) + " MiB");
            }
            StrictCsvReader.Parsed parsed = StrictCsvReader.read(bytes, properties.maxRows());
            List<String> header = checkedHeader(parsed.header(), template);
            return processor.process(started.getId(), organizationId, template.type(), header, parsed.rows());
        } catch (ImportFileException e) {
            records.fail(started.getId(), e.code(), e.detail());
            return importRepository.findById(started.getId()).orElseThrow();
        }
    }

    /**
     * The header must name exactly the columns of the template: no empty or repeated name, every required column present, no
     * unknown column. The detail lists only names that come from OUR template; names from the file are never repeated, because
     * a file whose first row is data would otherwise echo that data back.
     */
    static List<String> checkedHeader(List<String> rawHeader, CsvTemplate template) throws ImportFileException {
        List<String> header = rawHeader.stream().map(h -> h.strip().toLowerCase(Locale.ROOT)).toList();
        if (header.stream().anyMatch(String::isEmpty)) {
            throw new ImportFileException(ImportFailureCode.INVALID_HEADER, "A column name in the header row is empty");
        }
        if (header.stream().distinct().count() != header.size()) {
            throw new ImportFileException(ImportFailureCode.INVALID_HEADER, "A column name appears more than once in the header row");
        }
        long unknown = header.stream().filter(h -> !template.columnNames().contains(h)).count();
        List<String> missing = template.requiredColumns().stream().filter(c -> !header.contains(c)).toList();
        if (!missing.isEmpty() || unknown > 0) {
            StringBuilder detail = new StringBuilder();
            if (!missing.isEmpty()) {
                detail.append("Missing required columns: ").append(String.join(", ", missing)).append(". ");
            }
            if (unknown > 0) {
                detail.append(unknown).append(" column(s) are not part of template version ").append(template.version()).append(".");
            }
            throw new ImportFileException(ImportFailureCode.INVALID_HEADER, detail.toString().strip());
        }
        return header;
    }

    private void markFailedQuietly(UUID importId) {
        try {
            records.fail(importId, ImportFailureCode.INTERNAL_ERROR, "The import could not be completed because of an internal error");
        } catch (RuntimeException secondary) {
            log.error("Could not mark history import {} as failed", importId, secondary);
        }
    }

    private static byte[] read(MultipartFile file) {
        if (file == null) {
            throw RequestValidationException.of("file", "is required");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw RequestValidationException.of("file", "could not be read");
        }
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String trimmed = source.strip();
        if (!SOURCE.matcher(trimmed).matches()) {
            throw RequestValidationException.of("source", "must be 1 to 100 letters, digits or . _ : -");
        }
        return trimmed;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
