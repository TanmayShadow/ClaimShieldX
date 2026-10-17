package com.example.ClaimShieldX.history.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.exceptions.ResourceNotFoundException;
import com.example.ClaimShieldX.history.dto.ImportResponse;
import com.example.ClaimShieldX.history.dto.ImportRowResponse;
import com.example.ClaimShieldX.history.model.RowStatus;
import com.example.ClaimShieldX.history.repository.HistoricalImportRepository;
import com.example.ClaimShieldX.history.repository.HistoricalImportRowRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Reads imports and their rows, always through the caller's organization. */
@Service
public class HistoryImportQueryService {
    private final HistoricalImportRepository importRepository;
    private final HistoricalImportRowRepository rowRepository;

    public HistoryImportQueryService(HistoricalImportRepository importRepository, HistoricalImportRowRepository rowRepository) {
        this.importRepository = importRepository;
        this.rowRepository = rowRepository;
    }

    @Transactional(readOnly = true)
    public ImportResponse get(UserPrincipal principal, UUID importId) {
        return ImportResponse.of(importRepository.findByIdAndOrganizationId(importId, principal.getOrganization().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Import not found")));
    }

    @Transactional(readOnly = true)
    public PagedResponse<ImportResponse> list(UserPrincipal principal, Pageable pageable) {
        return PagedResponse.of(importRepository.findByOrganizationId(principal.getOrganization().getId(), pageable), ImportResponse::of);
    }

    /** @param status optional filter, for example REJECTED to see only the rows that need fixing */
    @Transactional(readOnly = true)
    public PagedResponse<ImportRowResponse> rows(UserPrincipal principal, UUID importId, RowStatus status, Pageable pageable) {
        UUID organizationId = principal.getOrganization().getId();
        if (importRepository.findByIdAndOrganizationId(importId, organizationId).isEmpty()) {
            throw new ResourceNotFoundException("Import not found");
        }
        return PagedResponse.of(status == null
                ? rowRepository.findByImportIdAndOrganizationId(importId, organizationId, pageable)
                : rowRepository.findByImportIdAndOrganizationIdAndStatus(importId, organizationId, status, pageable), ImportRowResponse::of);
    }
}
