package com.example.ClaimShieldX.analysis;

import com.example.ClaimShieldX.analysis.dto.AnalysisDtos.*;
import com.example.ClaimShieldX.analysis.findings.FindingStore;
import com.example.ClaimShieldX.analysis.report.ReportService;
import com.example.ClaimShieldX.analysis.review.AuditService;
import com.example.ClaimShieldX.analysis.review.ReviewService;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService;
import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.common.utils.PageParams;
import com.example.ClaimShieldX.exceptions.ResourceNotFoundException;
import com.example.ClaimShieldX.investigation.model.Investigation;
import com.example.ClaimShieldX.investigation.repository.InvestigationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Reads what an investigation produced, always through the caller's organization: another organization's investigation looks like one that does not exist. */
@Service
public class AnalysisQueryService {
    static final String NOTICE = "The review-priority score ranks where a person should look first. It is not a probability, not a finding about the claim and not a decision; "
            + "findings are inconsistencies and hypotheses to verify against the evidence, and the insurer's authorized employee decides.";

    private final InvestigationRepository investigations;
    private final SnapshotService snapshots;
    private final FindingStore findings;
    private final ReportService reports;
    private final ReviewService reviews;
    private final AuditService audit;

    public AnalysisQueryService(InvestigationRepository investigations, SnapshotService snapshots, FindingStore findings, ReportService reports, ReviewService reviews, AuditService audit) {
        this.investigations = investigations;
        this.snapshots = snapshots;
        this.findings = findings;
        this.reports = reports;
        this.reviews = reviews;
        this.audit = audit;
    }

    private Investigation require(UserPrincipal principal, UUID investigationId) {
        return investigations.findByIdAndOrganizationId(investigationId, principal.getOrganization().getId()).orElseThrow(() -> new ResourceNotFoundException("Investigation not found"));
    }

    @Transactional(readOnly = true)
    public EvidenceResponse evidence(UserPrincipal principal, UUID investigationId) {
        Investigation investigation = require(principal, investigationId);
        UUID org = principal.getOrganization().getId();
        var snapshot = snapshots.find(org, investigation.getId()).orElseThrow(() -> new ResourceNotFoundException("The evidence has not been frozen yet"));
        List<ItemView> items = snapshots.items(org, snapshot.id()).stream().map(i -> new ItemView(i.ref(), i.type(), i.state(), i.label(), i.data())).toList();
        return new EvidenceResponse(snapshot.id(), investigationId, snapshot.claimId(), snapshot.claimVersion(), snapshot.ruleRunId(), snapshot.ruleSetVersion(), snapshot.createdAt(), items.size(), items);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public GraphResponse graph(UserPrincipal principal, UUID investigationId) {
        EvidenceResponse evidence = evidence(principal, investigationId);
        Map<String, Node> nodes = new LinkedHashMap<>();
        nodes.put("CLAIM", new Node("CLAIM", "CLAIM", "This claim"));
        List<Edge> edges = new ArrayList<>();
        for (ItemView item : evidence.items()) {
            if (!item.type().equals("RELATIONSHIP")) {
                continue;
            }
            Map<String, Object> d = item.data();
            String target = String.valueOf(d.get("target"));
            nodes.putIfAbsent(target, new Node(target, target.startsWith("HISTORY:") ? "HISTORICAL_CLAIM" : "OTHER_CLAIM", target.substring(target.indexOf(':') + 1)));
            edges.add(new Edge(item.ref(), String.valueOf(d.get("source")), target, String.valueOf(d.get("relationship")), String.valueOf(d.get("method")),
                    (List<String>) d.getOrDefault("fields", List.of()), (List<String>) d.getOrDefault("evidenceRefs", List.of()), (String) d.get("uncertainty")));
        }
        return new GraphResponse(evidence.snapshotId(), List.copyOf(nodes.values()), edges);
    }

    @Transactional(readOnly = true)
    public List<FindingView> findings(UserPrincipal principal, UUID investigationId, boolean includeRejected) {
        require(principal, investigationId);
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORG_ADMIN"));
        return findings.list(principal.getOrganization().getId(), investigationId, !(includeRejected && admin)).stream().map(AnalysisQueryService::view).toList();
    }

    private static FindingView view(FindingStore.Row r) {
        return new FindingView(r.id(), r.ordinal(), r.source(), r.category(), r.title(), r.summary(), r.type(), r.status(), r.validationState(), r.rejectionReason(),
                r.supportingRefs(), r.counterRefs(), r.counterEvidence(), r.missingChecks(), r.recommendation());
    }

    @Transactional(readOnly = true)
    public ReportResponse report(UserPrincipal principal, UUID investigationId) {
        Investigation investigation = require(principal, investigationId);
        UUID org = principal.getOrganization().getId();
        var report = reports.findByInvestigation(org, investigation.getId()).orElseThrow(() -> new ResourceNotFoundException("This investigation has no report yet"));
        return build(org, report);
    }

    @Transactional(readOnly = true)
    public ReportResponse latestReport(UserPrincipal principal, UUID claimId) {
        UUID org = principal.getOrganization().getId();
        var report = reports.latestForClaim(org, claimId).orElseThrow(() -> new ResourceNotFoundException("This claim has no report yet"));
        return build(org, report);
    }

    private ReportResponse build(UUID org, ReportService.Report report) {
        var snapshot = snapshots.find(org, report.investigationId()).orElseThrow();
        var items = snapshots.items(org, snapshot.id());
        List<FindingView> published = findings.list(org, report.investigationId(), true).stream().map(AnalysisQueryService::view).toList();
        Set<String> cited = new LinkedHashSet<>();
        published.forEach(f -> {
            cited.addAll(f.supportingEvidenceRefs());
            cited.addAll(f.counterEvidenceRefs());
        });
        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("itemCount", items.size());
        coverage.put("byType", items.stream().collect(Collectors.groupingBy(i -> i.type(), java.util.TreeMap::new, Collectors.counting())));
        coverage.put("citedByFindings", cited.size());
        boolean stale = reports.isStale(org, report, snapshot.createdAt());
        var latest = reviews.list(org, report.investigationId());
        ReviewActionView latestAction = latest.isEmpty() ? null : latest.get(latest.size() - 1);
        return new ReportResponse(report.id(), report.reportVersion(), report.investigationId(), report.claimId(), report.snapshotId(), report.createdAt(), stale ? "STALE" : "CURRENT",
                report.completeness(), report.unavailableSections(), report.summary(),
                new ReviewPriority(report.score(), "0-100", report.band(), report.recommendedAction(), report.scoreVersion(), report.ruleSetVersion(), report.components()),
                published, coverage, latestAction, NOTICE);
    }

    @Transactional(readOnly = true)
    public PagedResponse<AuditEventView> audit(UserPrincipal principal, UUID investigationId, Integer page, Integer size) {
        require(principal, investigationId);
        UUID org = principal.getOrganization().getId();
        var pageable = PageParams.resolve(page, size, null, Map.of("createdAt", "createdAt"), "createdAt", Sort.Direction.ASC);
        long total = audit.count(org, investigationId);
        List<AuditEventView> events = audit.forInvestigation(org, investigationId, pageable.getPageSize(), (int) pageable.getOffset()).stream()
                .map(e -> new AuditEventView(e.id(), e.createdAt(), e.eventType(), e.actorUserId(), e.detail())).toList();
        return new PagedResponse<>(events, pageable.getPageNumber(), pageable.getPageSize(), total, (int) Math.ceil(total / (double) pageable.getPageSize()));
    }
}
