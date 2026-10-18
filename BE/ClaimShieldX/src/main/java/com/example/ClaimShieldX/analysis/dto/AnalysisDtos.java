package com.example.ClaimShieldX.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The shapes of what an investigation produced: its evidence, its findings, its report and what people did with it. */
public final class AnalysisDtos {
    private AnalysisDtos() {
    }

    public record ItemView(String ref, String type, String state, String label, Map<String, Object> data) {
    }

    public record EvidenceResponse(UUID snapshotId, UUID investigationId, UUID claimId, long claimVersion, UUID ruleRunId, String ruleSetVersion, Instant createdAt,
                                   int itemCount, List<ItemView> items) {
    }

    public record Node(String id, String type, String label) {
    }

    public record Edge(String ref, String source, String target, String relationship, String method, List<String> fields, List<String> evidenceRefs, String uncertainty) {
    }

    public record GraphResponse(UUID snapshotId, List<Node> nodes, List<Edge> edges) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FindingView(UUID id, int ordinal, String source, String category, String title, String summary, String type, String status, String validationState,
                              String rejectionReason, List<String> supportingEvidenceRefs, List<String> counterEvidenceRefs, List<String> counterEvidence,
                              List<String> missingChecks, String recommendation) {
    }

    public record ReviewPriority(int score, String scale, String band, String recommendedAction, String scoreVersion, String ruleVersion, List<Object> components) {
    }

    public record ReviewActionView(UUID id, Instant createdAt, String action, String rationale, UUID createdBy, int reportVersion, boolean reportWasStale,
                                   String recommendedAction, int score) {
    }

    public record ReportResponse(UUID reportId, int reportVersion, UUID investigationId, UUID claimId, UUID snapshotId, Instant createdAt, String reportStatus, String completeness,
                                 List<String> unavailableSections, String summary, ReviewPriority reviewPriority, List<FindingView> findings,
                                 Map<String, Object> evidenceCoverage, ReviewActionView latestAction, String notice) {
    }

    public record AuditEventView(UUID id, Instant createdAt, String eventType, UUID actorUserId, Map<String, Object> detail) {
    }

    public record ActionRequest(
            @NotNull(message = "Action is required") ActionType action,
            @NotBlank(message = "A rationale is required") @Size(min = 10, max = 2000, message = "Rationale must be 10 to 2000 characters") String rationale,
            @NotNull(message = "The report version the decision is based on is required") Integer reportVersion) {
    }

    /** What a person may decide after reading a report. None of them settles or rejects a claim. */
    public enum ActionType {
        REQUEST_MORE_EVIDENCE,
        ESCALATE_FOR_FURTHER_INVESTIGATION,
        NO_FURTHER_CHECKS_NEEDED,
        DEFER_REVIEW
    }
}
