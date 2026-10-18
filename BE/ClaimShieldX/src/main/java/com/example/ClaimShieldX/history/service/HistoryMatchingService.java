package com.example.ClaimShieldX.history.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.exceptions.ResourceNotFoundException;
import com.example.ClaimShieldX.history.config.HistoryProperties;
import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse;
import com.example.ClaimShieldX.history.dto.HistoryMatchesResponse.*;
import com.example.ClaimShieldX.history.dto.MatchStatus;
import com.example.ClaimShieldX.history.model.GarageHistoryRecord;
import com.example.ClaimShieldX.history.model.HistoricalClaim;
import com.example.ClaimShieldX.history.model.HistoricalImport;
import com.example.ClaimShieldX.history.model.ImportStatus;
import com.example.ClaimShieldX.history.model.ImportType;
import com.example.ClaimShieldX.history.repository.GarageHistoryRepository;
import com.example.ClaimShieldX.history.repository.HistoricalClaimRepository;
import com.example.ClaimShieldX.history.repository.HistoricalImportRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Looks up the earlier claims that relate to a claim. Everything is read through the caller's organization. Which of the four
 * statuses is returned depends on whether there is history to match against and whether the last attempt to load it failed.
 */
@Service
public class HistoryMatchingService {
    private static final int GARAGE_RECORDS = 12;
    static final String GARAGE_CAUTION = "A garage's claim volume, or sharing a garage with other claims, is context only and never evidence of wrongdoing. "
            + "The figures are the insurer's own and are defined by the insurer.";

    private final ClaimsRepository claimsRepository;
    private final HistoricalClaimRepository claimRepository;
    private final GarageHistoryRepository garageRepository;
    private final HistoricalImportRepository importRepository;
    private final HistoryProperties properties;

    public HistoryMatchingService(ClaimsRepository claimsRepository, HistoricalClaimRepository claimRepository, GarageHistoryRepository garageRepository,
                                  HistoricalImportRepository importRepository, HistoryProperties properties) {
        this.claimsRepository = claimsRepository;
        this.claimRepository = claimRepository;
        this.garageRepository = garageRepository;
        this.importRepository = importRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public HistoryMatchesResponse matchesFor(UserPrincipal principal, UUID claimId) {
        return matchesFor(principal.getOrganization().getId(), claimId);
    }

    /** The same lookup for a caller that has an organization but no signed-in user (the rules, later the worker). */
    @Transactional(readOnly = true)
    public HistoryMatchesResponse matchesFor(UUID organizationId, UUID claimId) {
        Claim claim = claimsRepository.findByIdAndOrganizationId(claimId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        HistoryMatcher.ClaimKeys keys = new HistoryMatcher.ClaimKeys(claim.getInternalId(),
                claim.getVehicle() == null ? null : claim.getVehicle().getVehicleToken(),
                claim.getPolicy() == null ? null : claim.getPolicy().getPolicyToken(),
                claim.getClaimant() == null ? null : claim.getClaimant().getClaimantToken(),
                claim.getGarage() == null ? null : claim.getGarage().getGarageId(),
                claim.getIncident() == null ? null : claim.getIncident().getIncidentAt());

        long claimRows = claimRepository.countByOrganizationId(organizationId);
        long garageRows = garageRepository.countByOrganizationId(organizationId);
        HistoricalImport latest = importRepository.findFirstByOrganizationIdAndImportTypeOrderByCreatedAtDesc(organizationId, ImportType.HISTORICAL_CLAIMS).orElse(null);
        boolean latestFailed = latest != null && latest.getStatus() == ImportStatus.FAILED;
        DataAvailability availability = new DataAvailability(claimRows, garageRows,
                latest == null ? null : new ImportSummary(latest.getId(), latest.getStatus(), latest.getFinishedAt()), latestFailed);

        List<String> notes = new ArrayList<>();
        notes.add("Each earlier claim is listed once, however many identifiers it shares with this claim, so one relationship is never counted twice.");
        GarageContext garage = garageContext(organizationId, keys.garageId());

        if (claimRows == 0) {
            MatchStatus status = latestFailed ? MatchStatus.HISTORY_IMPORT_FAILED : MatchStatus.NO_HISTORICAL_DATA_AVAILABLE;
            notes.add(latestFailed
                    ? "The latest import of historical claims failed and no historical claims are stored, so the absence of matches says nothing about this claim's history."
                    : "No historical claims have been imported for this organization, so no comparison was possible. This is not the same as finding no match.");
            return new HistoryMatchesResponse(claimId, status, availability, 0, false, 0, List.of(), garage, notes);
        }

        var candidates = new ArrayList<HistoricalClaim>();
        boolean capped = false;
        if (keys.vehicleToken() != null) {
            var rows = claimRepository.findByVehicle(organizationId, keys.vehicleToken(), PageRequest.of(0, properties.maxCandidates()));
            capped |= rows.size() >= properties.maxCandidates();
            candidates.addAll(rows);
        }
        if (keys.policyToken() != null) {
            var rows = claimRepository.findByPolicy(organizationId, keys.policyToken(), PageRequest.of(0, properties.maxCandidates()));
            capped |= rows.size() >= properties.maxCandidates();
            candidates.addAll(rows);
        }
        if (keys.claimantToken() != null) {
            var rows = claimRepository.findByClaimant(organizationId, keys.claimantToken(), PageRequest.of(0, properties.maxCandidates()));
            capped |= rows.size() >= properties.maxCandidates();
            candidates.addAll(rows);
        }

        HistoryMatcher.Matched matched = HistoryMatcher.match(keys, candidates, properties.maxMatches(), properties.dateOnlyZone());
        boolean truncated = matched.truncated() || capped;
        if (capped) {
            notes.add("A very large number of rows share a token with this claim; only the most recent were examined, so the list may be incomplete.");
        }
        if (matched.truncated()) {
            notes.add("Only the " + properties.maxMatches() + " closest earlier claims are listed; " + matched.totalPriorClaims() + " were found.");
        }
        if (matched.excludedAsSameClaim() > 0) {
            notes.add("A history row with this claim's own id was left out: a claim is not compared with itself.");
        }
        if (latestFailed) {
            notes.add("The latest attempt to import historical claims failed; these results come from earlier imports and may be out of date.");
        }
        MatchStatus status = matched.matches().isEmpty() ? MatchStatus.NO_MATCH_FOUND : MatchStatus.MATCHES_FOUND;
        if (status == MatchStatus.NO_MATCH_FOUND) {
            notes.add("History exists for this organization but no earlier claim shares a vehicle, policy or claimant token with this claim.");
        }
        return new HistoryMatchesResponse(claimId, status, availability, matched.totalPriorClaims(), truncated, matched.excludedAsSameClaim(), matched.matches(), garage, notes);
    }

    private GarageContext garageContext(UUID organizationId, String garageId) {
        if (garageId == null) {
            return null;
        }
        List<GarageRecord> records = garageRepository.findByGarage(organizationId, garageId, PageRequest.of(0, GARAGE_RECORDS)).stream().map(this::toRecord).toList();
        return new GarageContext(garageId, claimRepository.countByOrganizationIdAndGarageId(organizationId, garageId), records, GARAGE_CAUTION);
    }

    private GarageRecord toRecord(GarageHistoryRecord g) {
        return new GarageRecord(g.getSourceRecordId(), g.getReportingPeriodStart(), g.getReportingPeriodEnd(), g.getClaimCount(), g.getClaimedTotal(),
                g.getApprovedTotal(), g.getCurrency(), g.getEstimateInvoiceVarianceCount(), g.getDisputedOrReopenedCount(), g.getConfirmedOutcomeCount(),
                new SourceRef(g.getImportId(), g.getRowNumber(), g.getSourceRecordId(), g.getCreatedAt()));
    }
}
