package com.example.ClaimShieldX.history.service;

import com.example.ClaimShieldX.history.config.HistoryProperties;
import com.example.ClaimShieldX.history.csv.*;
import com.example.ClaimShieldX.history.model.*;
import com.example.ClaimShieldX.history.repository.GarageHistoryRepository;
import com.example.ClaimShieldX.history.repository.HistoricalClaimRepository;
import com.example.ClaimShieldX.history.repository.HistoricalImportRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Validates every row of a parsed file and stores the accepted ones together with a result for each row, in ONE transaction:
 * either the rows and the finished import are all there, or none of it is. Imports of one kind for one organization run one
 * at a time (a database advisory lock), so two imports can never both pass the duplicate and overlap checks and then both
 * insert. A bad row never stops the others (partial acceptance), and nothing already stored is ever changed.
 */
@Slf4j
@Component
class HistoryImportProcessor {
    private static final int FLUSH_EVERY = 200;
    private static final int LOOKUP_CHUNK = 500;

    private record Period(LocalDate start, LocalDate end, String sourceRecordId) {
    }

    @PersistenceContext
    private EntityManager entityManager;

    private final HistoricalImportRepository importRepository;
    private final HistoricalClaimRepository claimRepository;
    private final GarageHistoryRepository garageRepository;
    private final JdbcTemplate jdbc;
    private final HistoryProperties properties;

    HistoryImportProcessor(HistoricalImportRepository importRepository, HistoricalClaimRepository claimRepository,
                           GarageHistoryRepository garageRepository, JdbcTemplate jdbc, HistoryProperties properties) {
        this.importRepository = importRepository;
        this.claimRepository = claimRepository;
        this.garageRepository = garageRepository;
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** @param header the file's column names, already trimmed and lower-cased and already checked against the template */
    @Transactional
    HistoricalImport process(UUID importId, UUID organizationId, ImportType type, List<String> header, List<StrictCsvReader.Row> rows) {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", "history-import:" + organizationId + ":" + type);

        int[] counts = type == ImportType.HISTORICAL_CLAIMS
                ? processClaims(importId, organizationId, header, rows)
                : processGarageHistory(importId, organizationId, header, rows);

        HistoricalImport record = importRepository.findById(importId).orElseThrow();
        record.setTotalRows(rows.size());
        record.setAcceptedRows(counts[0]);
        record.setRejectedRows(counts[1]);
        record.setSkippedDuplicateRows(counts[2]);
        record.setStatus(counts[1] > 0 ? ImportStatus.COMPLETED_WITH_ERRORS : ImportStatus.COMPLETED);
        record.setFinishedAt(Instant.now());
        HistoricalImport saved = importRepository.saveAndFlush(record);
        log.info("History import finished: organization={} import={} type={} rows={} accepted={} rejected={} skippedDuplicate={}",
                organizationId, importId, type, rows.size(), counts[0], counts[1], counts[2]);
        return saved;
    }

    // ------------------------------------------------------------ historical claims

    private int[] processClaims(UUID importId, UUID organizationId, List<String> header, List<StrictCsvReader.Row> rows) {
        List<Validated<HistoricalClaimData>> validated = rows.stream()
                .map(row -> RowValidators.historicalClaim(header, row.cells(), properties.dateOnlyZone())).toList();

        Set<String> sourceIds = new HashSet<>();
        Set<String> externalIds = new HashSet<>();
        for (Validated<HistoricalClaimData> v : validated) {
            if (v.accepted()) {
                sourceIds.add(v.data().sourceRecordId());
                externalIds.add(v.data().externalClaimId());
            }
        }
        Map<String, String> knownHashBySource = new HashMap<>();
        for (List<String> chunk : chunks(sourceIds)) {
            claimRepository.findBySourceRecordIds(organizationId, chunk).forEach(c -> knownHashBySource.put(c.getSourceRecordId(), c.getContentHash()));
        }
        Set<String> knownVersions = new HashSet<>();
        for (List<String> chunk : chunks(externalIds)) {
            claimRepository.findByExternalClaimIds(organizationId, chunk).forEach(c -> knownVersions.add(versionKey(c.getExternalClaimId(), c.getClaimVersion())));
        }

        int accepted = 0, rejected = 0, skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            Validated<HistoricalClaimData> v = validated.get(i);
            int number = rows.get(i).number();
            if (!v.accepted()) {
                rejected++;
                storeRow(importId, organizationId, number, RowStatus.REJECTED, v.sourceRecordId(), v.errors());
            } else {
                HistoricalClaimData data = v.data();
                String hash = data.contentHash();
                String known = knownHashBySource.get(data.sourceRecordId());
                if (known != null) {
                    if (known.equals(hash)) {
                        skipped++;
                        storeRow(importId, organizationId, number, RowStatus.SKIPPED_DUPLICATE, data.sourceRecordId(), null);
                    } else {
                        rejected++;
                        storeRow(importId, organizationId, number, RowStatus.REJECTED, data.sourceRecordId(), List.of(new RowError("source_record_id",
                                RowErrorCode.CONFLICTING_DUPLICATE, "a record with this id already exists with different content; records are never overwritten")));
                    }
                } else if (!knownVersions.add(versionKey(data.externalClaimId(), data.claimVersion()))) {
                    rejected++;
                    storeRow(importId, organizationId, number, RowStatus.REJECTED, data.sourceRecordId(), List.of(new RowError("external_claim_id",
                            RowErrorCode.DUPLICATE_CLAIM_VERSION, "this claim version already exists under a different record id")));
                } else {
                    accepted++;
                    knownHashBySource.put(data.sourceRecordId(), hash);
                    entityManager.persist(toEntity(data, hash, importId, organizationId, number));
                    storeRow(importId, organizationId, number, RowStatus.ACCEPTED, data.sourceRecordId(), null);
                }
            }
            flushPeriodically(i);
        }
        return new int[]{accepted, rejected, skipped};
    }

    private static String versionKey(String externalClaimId, int version) {
        return externalClaimId + '\u0000' + version;
    }

    private static HistoricalClaim toEntity(HistoricalClaimData d, String hash, UUID importId, UUID organizationId, int rowNumber) {
        HistoricalClaim claim = new HistoricalClaim();
        claim.setOrganizationId(organizationId);
        claim.setImportId(importId);
        claim.setRowNumber(rowNumber);
        claim.setSourceRecordId(d.sourceRecordId());
        claim.setExternalClaimId(d.externalClaimId());
        claim.setClaimVersion(d.claimVersion());
        claim.setVehicleToken(d.vehicleToken());
        claim.setPolicyToken(d.policyToken());
        claim.setClaimantToken(d.claimantToken());
        claim.setGarageId(d.garageId());
        claim.setIncidentAt(d.incidentAt());
        claim.setIncidentAtPrecision(d.incidentAtPrecision());
        claim.setReportedAt(d.reportedAt());
        claim.setClaimType(d.claimType());
        claim.setClaimStatus(d.claimStatus());
        claim.setOutcomeStatus(d.outcomeStatus());
        claim.setClaimedAmount(d.claimedAmount());
        claim.setApprovedAmount(d.approvedAmount());
        claim.setCurrency(d.currency());
        claim.setRepairSummary(d.repairSummary());
        claim.setDamageAreaCodes(d.damageAreaCodes());
        claim.setRepairCompletedAt(d.repairCompletedAt());
        claim.setContentHash(hash);
        return claim;
    }

    // ------------------------------------------------------------ garage history

    private int[] processGarageHistory(UUID importId, UUID organizationId, List<String> header, List<StrictCsvReader.Row> rows) {
        List<Validated<GarageHistoryData>> validated = rows.stream()
                .map(row -> RowValidators.garageHistory(header, row.cells(), properties.dateOnlyZone())).toList();

        Set<String> sourceIds = new HashSet<>();
        Set<String> garageIds = new HashSet<>();
        for (Validated<GarageHistoryData> v : validated) {
            if (v.accepted()) {
                sourceIds.add(v.data().sourceRecordId());
                garageIds.add(v.data().garageId());
            }
        }
        Map<String, String> knownHashBySource = new HashMap<>();
        for (List<String> chunk : chunks(sourceIds)) {
            garageRepository.findBySourceRecordIds(organizationId, chunk).forEach(g -> knownHashBySource.put(g.getSourceRecordId(), g.getContentHash()));
        }
        Map<String, List<Period>> periodsByGarage = new HashMap<>();
        for (List<String> chunk : chunks(garageIds)) {
            garageRepository.findByGarageIds(organizationId, chunk).forEach(g -> periodsByGarage
                    .computeIfAbsent(g.getGarageId(), k -> new ArrayList<>())
                    .add(new Period(g.getReportingPeriodStart(), g.getReportingPeriodEnd(), g.getSourceRecordId())));
        }

        int accepted = 0, rejected = 0, skipped = 0;
        for (int i = 0; i < rows.size(); i++) {
            Validated<GarageHistoryData> v = validated.get(i);
            int number = rows.get(i).number();
            if (!v.accepted()) {
                rejected++;
                storeRow(importId, organizationId, number, RowStatus.REJECTED, v.sourceRecordId(), v.errors());
            } else {
                GarageHistoryData data = v.data();
                String hash = data.contentHash();
                String known = knownHashBySource.get(data.sourceRecordId());
                if (known != null) {
                    if (known.equals(hash)) {
                        skipped++;
                        storeRow(importId, organizationId, number, RowStatus.SKIPPED_DUPLICATE, data.sourceRecordId(), null);
                    } else {
                        rejected++;
                        storeRow(importId, organizationId, number, RowStatus.REJECTED, data.sourceRecordId(), List.of(new RowError("source_record_id",
                                RowErrorCode.CONFLICTING_DUPLICATE, "a record with this id already exists with different content; records are never overwritten")));
                    }
                } else if (overlaps(periodsByGarage.get(data.garageId()), data)) {
                    rejected++;
                    storeRow(importId, organizationId, number, RowStatus.REJECTED, data.sourceRecordId(), List.of(new RowError("reporting_period_start",
                            RowErrorCode.OVERLAPPING_PERIOD, "the period overlaps another record of the same garage, which would count the same claims twice")));
                } else {
                    accepted++;
                    knownHashBySource.put(data.sourceRecordId(), hash);
                    periodsByGarage.computeIfAbsent(data.garageId(), k -> new ArrayList<>()).add(new Period(data.periodStart(), data.periodEnd(), data.sourceRecordId()));
                    entityManager.persist(toEntity(data, hash, importId, organizationId, number));
                    storeRow(importId, organizationId, number, RowStatus.ACCEPTED, data.sourceRecordId(), null);
                }
            }
            flushPeriodically(i);
        }
        return new int[]{accepted, rejected, skipped};
    }

    private static boolean overlaps(List<Period> existing, GarageHistoryData data) {
        if (existing == null) {
            return false;
        }
        return existing.stream().anyMatch(p -> !p.sourceRecordId().equals(data.sourceRecordId())
                && !data.periodEnd().isBefore(p.start()) && !data.periodStart().isAfter(p.end()));
    }

    private static GarageHistoryRecord toEntity(GarageHistoryData d, String hash, UUID importId, UUID organizationId, int rowNumber) {
        GarageHistoryRecord record = new GarageHistoryRecord();
        record.setOrganizationId(organizationId);
        record.setImportId(importId);
        record.setRowNumber(rowNumber);
        record.setSourceRecordId(d.sourceRecordId());
        record.setGarageId(d.garageId());
        record.setReportingPeriodStart(d.periodStart());
        record.setReportingPeriodEnd(d.periodEnd());
        record.setClaimCount(d.claimCount());
        record.setClaimedTotal(d.claimedTotal());
        record.setApprovedTotal(d.approvedTotal());
        record.setCurrency(d.currency());
        record.setEstimateInvoiceVarianceCount(d.estimateInvoiceVarianceCount());
        record.setDisputedOrReopenedCount(d.disputedOrReopenedCount());
        record.setConfirmedOutcomeCount(d.confirmedOutcomeCount());
        record.setContentHash(hash);
        return record;
    }

    // ------------------------------------------------------------ shared

    private void storeRow(UUID importId, UUID organizationId, int number, RowStatus status, String sourceRecordId, List<RowError> errors) {
        HistoricalImportRow row = new HistoricalImportRow();
        row.setOrganizationId(organizationId);
        row.setImportId(importId);
        row.setRowNumber(number);
        row.setStatus(status);
        row.setSourceRecordId(sourceRecordId);
        row.setErrors(errors);
        entityManager.persist(row);
    }

    private void flushPeriodically(int index) {
        if ((index + 1) % FLUSH_EVERY == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }

    private static List<List<String>> chunks(Collection<String> values) {
        List<String> all = new ArrayList<>(values);
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < all.size(); i += LOOKUP_CHUNK) {
            chunks.add(all.subList(i, Math.min(all.size(), i + LOOKUP_CHUNK)));
        }
        return chunks;
    }
}
