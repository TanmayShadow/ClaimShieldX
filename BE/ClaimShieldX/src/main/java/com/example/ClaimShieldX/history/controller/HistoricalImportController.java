package com.example.ClaimShieldX.history.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.common.utils.PageParams;
import com.example.ClaimShieldX.exceptions.RequestValidationException;
import com.example.ClaimShieldX.history.csv.CsvTemplates;
import com.example.ClaimShieldX.history.dto.ImportResponse;
import com.example.ClaimShieldX.history.dto.ImportRowResponse;
import com.example.ClaimShieldX.history.model.ImportType;
import com.example.ClaimShieldX.history.model.RowStatus;
import com.example.ClaimShieldX.history.service.HistoryImportQueryService;
import com.example.ClaimShieldX.history.service.HistoryImportService;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/** Importing historical claims and garage history. Only an organization administrator may load or read imports. */
@RestController
@RequestMapping("/api/v1/historical-imports")
@PreAuthorize("hasRole('ORG_ADMIN')")
public class HistoricalImportController {
    private static final Map<String, String> IMPORT_SORT = Map.of("createdAt", "createdAt");
    private static final Map<String, String> ROW_SORT = Map.of("rowNumber", "rowNumber");

    private final HistoryImportService importService;
    private final HistoryImportQueryService queryService;

    public HistoricalImportController(HistoryImportService importService, HistoryImportQueryService queryService) {
        this.importService = importService;
        this.queryService = queryService;
    }

    /** Answers 202 and finishes first (see api-contract 7.7): clients read the import to learn how it went. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResponse> upload(@AuthenticationPrincipal UserPrincipal principal,
                                                 @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @RequestPart("file") MultipartFile file,
                                                 @RequestParam("importType") ImportType importType,
                                                 @RequestParam("templateVersion") String templateVersion,
                                                 @RequestParam(value = "source", required = false) String source) {
        HistoryImportService.Result result = importService.importFile(principal, file, importType, templateVersion, source, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/historical-imports/" + result.importRecord().id()));
        if (result.replayed()) {
            response.header("Idempotent-Replay", "true");
        }
        return response.body(result.importRecord());
    }

    @GetMapping
    public PagedResponse<ImportResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                              @RequestParam(value = "page", required = false) Integer page,
                                              @RequestParam(value = "size", required = false) Integer size,
                                              @RequestParam(value = "sort", required = false) String sort) {
        return queryService.list(principal, PageParams.resolve(page, size, sort, IMPORT_SORT, "createdAt", Sort.Direction.DESC));
    }

    @GetMapping("/{importId}")
    public ImportResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID importId) {
        return queryService.get(principal, importId);
    }

    @GetMapping("/{importId}/rows")
    public PagedResponse<ImportRowResponse> rows(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID importId,
                                                 @RequestParam(value = "status", required = false) RowStatus status,
                                                 @RequestParam(value = "page", required = false) Integer page,
                                                 @RequestParam(value = "size", required = false) Integer size,
                                                 @RequestParam(value = "sort", required = false) String sort) {
        return queryService.rows(principal, importId, status, PageParams.resolve(page, size, sort, ROW_SORT, "rowNumber", Sort.Direction.ASC));
    }

    /** The versioned template as a ready-to-fill CSV: the header row and one made-up example row. */
    @GetMapping("/templates/{importType}")
    public ResponseEntity<byte[]> template(@PathVariable ImportType importType, @RequestParam(value = "version", defaultValue = CsvTemplates.VERSION_1) String version) {
        String csv = CsvTemplates.find(importType, version)
                .orElseThrow(() -> RequestValidationException.of("version", "is not supported; supported versions: " + String.join(", ", CsvTemplates.supportedVersions())))
                .asCsv();
        String name = importType.name().toLowerCase().replace('_', '-') + "-template-v" + version + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
