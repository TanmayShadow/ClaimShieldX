package com.example.ClaimShieldX.documents.controller;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.common.utils.PageParams;
import com.example.ClaimShieldX.documents.dto.DocumentResponse;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.documents.service.DocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/documents")
public class DocumentController {
    private static final Map<String, String> SORTABLE = Map.of(
            "receivedAt", "receivedAt", "documentType", "documentType", "fileName", "fileName", "sizeBytes", "sizeBytes");

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('INVESTIGATOR', 'ORG_ADMIN')")
    public ResponseEntity<DocumentResponse> upload(@AuthenticationPrincipal UserPrincipal principal,
                                                   @PathVariable UUID claimId,
                                                   @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                   @RequestPart("file") MultipartFile file,
                                                   @RequestParam("documentType") DocumentType documentType,
                                                   @RequestParam(value = "externalDocumentId", required = false) String externalDocumentId,
                                                   @RequestParam(value = "supersedesDocumentId", required = false) UUID supersedesDocumentId) {
        DocumentService.UploadResult result = documentService.upload(principal, claimId, file, documentType,
                externalDocumentId, supersedesDocumentId, idempotencyKey);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/claims/" + claimId + "/documents/" + result.document().id()));
        if (result.replayed()) {
            response.header("Idempotent-Replay", "true");
        }
        return response.body(result.document());
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PagedResponse<DocumentResponse> list(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable UUID claimId,
                                                @RequestParam(value = "page", required = false) Integer page,
                                                @RequestParam(value = "size", required = false) Integer size,
                                                @RequestParam(value = "sort", required = false) String sort) {
        return documentService.list(principal, claimId,
                PageParams.resolve(page, size, sort, SORTABLE, "receivedAt", Sort.Direction.DESC));
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("isAuthenticated()")
    public DocumentResponse get(@AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID claimId, @PathVariable UUID documentId) {
        return documentService.get(principal, claimId, documentId);
    }

    /** Streams the original through the backend. There are no signed or public URLs. */
    @GetMapping("/{documentId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal UserPrincipal principal,
                                                       @PathVariable UUID claimId, @PathVariable UUID documentId) {
        DocumentService.DocumentContent content = documentService.openContent(principal, claimId, documentId);
        DocumentResponse metadata = content.metadata();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(metadata.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(new InputStreamResource(content.stream()));
    }
}
