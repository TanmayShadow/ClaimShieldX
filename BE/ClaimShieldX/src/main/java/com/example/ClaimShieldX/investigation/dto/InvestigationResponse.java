package com.example.ClaimShieldX.investigation.dto;

import com.example.ClaimShieldX.investigation.model.Investigation;
import com.example.ClaimShieldX.investigation.model.InvestigationStageRow;
import com.example.ClaimShieldX.investigation.model.InvestigationStatus;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.model.StageStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The status of an investigation, for polling. Nothing here comes from a document or from an exception: the error is a code from a
 * fixed list with its fixed message, and a stage's summary holds counts and ids. {@code snapshotId} and {@code reportStatus} are
 * reserved for the evidence snapshot and the report, which later modules fill in.
 */
public record InvestigationResponse(
        UUID id,
        UUID claimId,
        long claimVersion,
        InvestigationStatus status,
        int attempt,
        int maxAttempts,
        Instant nextRetryAt,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant finishedAt,
        boolean cancelRequested,
        String note,
        UUID ruleRunId,
        UUID snapshotId,
        UUID reportId,
        String reportStatus,
        List<StageView> stages,
        List<Map<String, Object>> waitingFor,
        ErrorView error
) {
    public record StageView(Stage stage, StageStatus status, int attempts, Instant startedAt, Instant finishedAt, String errorCode, Map<String, Object> summary) {
    }

    public record ErrorView(String code, String message) {
    }

    /** The same status with what the investigation produced: its evidence snapshot, its report, and whether that report is still current. */
    public InvestigationResponse withOutputs(UUID snapshotId, UUID reportId, String reportStatus) {
        return new InvestigationResponse(id, claimId, claimVersion, status, attempt, maxAttempts, nextRetryAt, createdAt, startedAt, updatedAt, finishedAt, cancelRequested, note,
                ruleRunId, snapshotId, reportId, reportStatus, stages, waitingFor, error);
    }

    /** Every stage is always listed, in order; one that has not started is PENDING. */
    public static InvestigationResponse of(Investigation i, List<InvestigationStageRow> rows) {
        Map<Stage, InvestigationStageRow> byStage = rows.stream().collect(Collectors.toMap(InvestigationStageRow::getStage, Function.identity()));
        List<StageView> stages = new ArrayList<>();
        for (Stage stage : Stage.values()) {
            InvestigationStageRow row = byStage.get(stage);
            stages.add(row == null ? new StageView(stage, StageStatus.PENDING, 0, null, null, null, Map.of())
                    : new StageView(stage, row.getStatus(), row.getAttempts(), row.getStartedAt(), row.getFinishedAt(), row.getErrorCode(), row.getSummary()));
        }
        List<Map<String, Object>> waitingFor = null;
        if (i.getStatus() == InvestigationStatus.WAITING_FOR_EVIDENCE) {
            Object blockers = byStage.containsKey(Stage.PREPARING_EVIDENCE) ? byStage.get(Stage.PREPARING_EVIDENCE).getSummary().get("blockers") : null;
            waitingFor = blockers instanceof List<?> list ? list.stream().filter(Map.class::isInstance).map(m -> {
                @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) m;
                return typed;
            }).toList() : List.of();
        }
        return new InvestigationResponse(i.getId(), i.getClaimId(), i.getClaimVersion(), i.getStatus(), i.getAttempt(), i.getMaxAttempts(),
                i.getStatus() == InvestigationStatus.RETRY_WAIT ? i.getNextAttemptAt() : null, i.getCreatedAt(), i.getStartedAt(), i.getUpdatedAt(), i.getFinishedAt(),
                i.isCancelRequested(), i.getNote(), i.getRuleRunId(), null, null, null, stages, waitingFor,
                i.getErrorCode() == null ? null : new ErrorView(i.getErrorCode(), i.getErrorMessage()));
    }
}
