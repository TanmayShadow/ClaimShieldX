package com.example.ClaimShieldX.rules.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.common.PagedResponse;
import com.example.ClaimShieldX.common.utils.PageParams;
import com.example.ClaimShieldX.exceptions.ResourceNotFoundException;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.dto.RuleCatalogueResponse;
import com.example.ClaimShieldX.rules.dto.RuleResultResponse;
import com.example.ClaimShieldX.rules.dto.RuleRunCounts;
import com.example.ClaimShieldX.rules.dto.RuleRunResponse;
import com.example.ClaimShieldX.rules.dto.RuleRunSummary;
import com.example.ClaimShieldX.rules.engine.RuleEngine;
import com.example.ClaimShieldX.rules.engine.RuleInputs;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleResultRow;
import com.example.ClaimShieldX.rules.model.RuleRun;
import com.example.ClaimShieldX.rules.repository.RuleResultRepository;
import com.example.ClaimShieldX.rules.repository.RuleRunRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs the rules for a claim and stores the result. Because the rules are deterministic, a run is identified by a hash of
 * everything they looked at: asking again with identical inputs returns the stored run rather than a duplicate. Everything is
 * read and written through the claim's organization; another organization's claim looks like one that does not exist.
 */
@Service
public class RuleRunService {
    public static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt");

    public record RunOutcome(RuleRunResponse run, boolean created) {
    }

    private final ClaimsRepository claimsRepository;
    private final RuleInputLoader loader;
    private final RuleEngine engine;
    private final RuleProperties properties;
    private final RuleRunRecords records;
    private final RuleRunRepository runRepository;
    private final RuleResultRepository resultRepository;

    public RuleRunService(ClaimsRepository claimsRepository, RuleInputLoader loader, RuleEngine engine, RuleProperties properties,
                          RuleRunRecords records, RuleRunRepository runRepository, RuleResultRepository resultRepository) {
        this.claimsRepository = claimsRepository;
        this.loader = loader;
        this.engine = engine;
        this.properties = properties;
        this.records = records;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
    }

    public RunOutcome run(UserPrincipal principal, UUID claimId) {
        return run(principal.getOrganization().getId(), claimId, principal.getId());
    }

    /** For callers without a signed-in user (the worker, later): {@code requestedBy} may be null. */
    public RunOutcome run(UUID organizationId, UUID claimId, UUID requestedBy) {
        Claim claim = claimsRepository.findByIdAndOrganizationId(claimId, organizationId).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        RuleInputs inputs = loader.load(organizationId, claim);
        Map<String, Object> configuration = properties.snapshot();
        String configurationHash = RuleJson.sha256(RuleJson.canonical(configuration));
        String fingerprint = RuleJson.sha256(RuleJson.canonical(inputs));

        Optional<RuleRun> existing = find(organizationId, claimId, configurationHash, fingerprint);
        if (existing.isPresent()) {
            return new RunOutcome(response(existing.get(), true), false);
        }

        List<RuleResult> results = engine.evaluate(inputs, properties);
        Instant executedAt = Instant.now();
        RuleRun run = new RuleRun();
        run.setOrganizationId(organizationId);
        run.setClaimId(claimId);
        run.setRequestedBy(requestedBy);
        run.setRuleSetVersion(RuleEngine.RULE_SET_VERSION);
        run.setConfig(configuration);
        run.setConfigSha256(configurationHash);
        run.setInputFingerprint(fingerprint);
        run.setInputs(summary(inputs));
        run.setPassCount(count(results, RuleOutcome.PASS));
        run.setFlagCount(count(results, RuleOutcome.FLAG));
        run.setIncompleteCount(count(results, RuleOutcome.INCOMPLETE));
        run.setNotApplicableCount(count(results, RuleOutcome.NOT_APPLICABLE));
        run.setErrorCount(count(results, RuleOutcome.ERROR));
        List<RuleResultRow> rows = results.stream().map(r -> row(run, r, executedAt)).toList();
        try {
            RuleRun stored = records.store(run, rows);
            // Answered from what the database now holds (timestamps to the microsecond), so a later GET of this run is identical.
            return new RunOutcome(response(runRepository.findById(stored.getId()).orElseThrow(), false), true);
        } catch (DataIntegrityViolationException e) {
            // Two requests with identical inputs at the same moment: the database let only the first in.
            Optional<RuleRun> winner = find(organizationId, claimId, configurationHash, fingerprint);
            if (winner.isPresent()) {
                return new RunOutcome(response(winner.get(), true), false);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public RuleRunResponse latest(UserPrincipal principal, UUID claimId) {
        UUID organizationId = requireClaim(principal, claimId);
        RuleRun run = runRepository.findFirstByOrganizationIdAndClaimIdOrderByCreatedAtDescIdDesc(organizationId, claimId)
                .orElseThrow(() -> new ResourceNotFoundException("No rules have been run for this claim yet"));
        return response(run, null);
    }

    @Transactional(readOnly = true)
    public RuleRunResponse get(UserPrincipal principal, UUID claimId, UUID runId) {
        UUID organizationId = requireClaim(principal, claimId);
        RuleRun run = runRepository.findByIdAndOrganizationIdAndClaimId(runId, organizationId, claimId).orElseThrow(() -> new ResourceNotFoundException("Rule run not found"));
        return response(run, null);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RuleRunSummary> list(UserPrincipal principal, UUID claimId, Integer page, Integer size, String sort) {
        UUID organizationId = requireClaim(principal, claimId);
        Pageable pageable = PageParams.resolve(page, size, sort, SORTABLE, "createdAt", Sort.Direction.DESC);
        Page<RuleRun> runs = runRepository.findByOrganizationIdAndClaimId(organizationId, claimId, pageable);
        return PagedResponse.of(runs, run -> new RuleRunSummary(run.getId(), run.getCreatedAt(), run.getRequestedBy(), run.getRuleSetVersion(), counts(run)));
    }

    public RuleCatalogueResponse catalogue() {
        List<RuleCatalogueResponse.RuleInfo> rules = engine.definitions().stream()
                .map(d -> new RuleCatalogueResponse.RuleInfo(d.id(), d.version(), d.label(), d.description(), d.needs(), d.considerations())).toList();
        return new RuleCatalogueResponse(RuleEngine.RULE_SET_VERSION, rules, properties.snapshot());
    }

    private UUID requireClaim(UserPrincipal principal, UUID claimId) {
        UUID organizationId = principal.getOrganization().getId();
        if (!claimsRepository.existsByIdAndOrganizationId(claimId, organizationId)) {
            throw new ResourceNotFoundException("Claim not found");
        }
        return organizationId;
    }

    private Optional<RuleRun> find(UUID organizationId, UUID claimId, String configurationHash, String fingerprint) {
        return runRepository.findByOrganizationIdAndClaimIdAndRuleSetVersionAndConfigSha256AndInputFingerprint(organizationId, claimId, RuleEngine.RULE_SET_VERSION, configurationHash, fingerprint);
    }

    private static int count(List<RuleResult> results, RuleOutcome outcome) {
        return (int) results.stream().filter(r -> r.outcome() == outcome).count();
    }

    private static RuleResultRow row(RuleRun run, RuleResult r, Instant executedAt) {
        RuleResultRow row = new RuleResultRow();
        row.setOrganizationId(run.getOrganizationId());
        row.setClaimId(run.getClaimId());
        row.setRunId(run.getId());
        row.setRuleId(r.ruleId());
        row.setRuleVersion(r.ruleVersion());
        row.setOutcome(r.outcome());
        row.setSeverity(r.severity());
        row.setReliability(r.reliability());
        row.setReliabilityReasons(r.reliabilityReasons());
        row.setExplanation(r.explanation());
        row.setValues(r.values());
        row.setEvidence(r.evidence());
        row.setConsiderations(r.considerations());
        row.setExecutedAt(executedAt);
        return row;
    }

    private RuleRunResponse response(RuleRun run, Boolean reused) {
        return response(run, resultRepository.findByOrganizationIdAndRunId(run.getOrganizationId(), run.getId()), reused);
    }

    private RuleRunResponse response(RuleRun run, List<RuleResultRow> rows, Boolean reused) {
        Map<String, RuleDefinition> definitions = engine.definitions().stream().collect(Collectors.toMap(RuleDefinition::id, Function.identity()));
        List<String> order = engine.definitions().stream().map(RuleDefinition::id).toList();
        List<RuleResultResponse> results = rows.stream()
                .sorted(Comparator.comparingInt((RuleResultRow r) -> order.indexOf(r.getRuleId()) < 0 ? Integer.MAX_VALUE : order.indexOf(r.getRuleId())).thenComparing(RuleResultRow::getRuleId))
                .map(r -> new RuleResultResponse(r.getId(), r.getRuleId(), r.getRuleVersion(), definitions.containsKey(r.getRuleId()) ? definitions.get(r.getRuleId()).label() : r.getRuleId(),
                        r.getOutcome(), r.getSeverity(), r.getReliability(), r.getReliabilityReasons(), r.getExplanation(), r.getValues(), r.getEvidence(), r.getConsiderations(), r.getExecutedAt()))
                .toList();
        return new RuleRunResponse(run.getId(), run.getClaimId(), run.getRuleSetVersion(), run.getCreatedAt(), run.getRequestedBy(), counts(run), run.getConfig(),
                run.getConfigSha256(), run.getInputFingerprint(), run.getInputs(), reused, results);
    }

    private static RuleRunCounts counts(RuleRun run) {
        return new RuleRunCounts(run.getPassCount(), run.getFlagCount(), run.getIncompleteCount(), run.getNotApplicableCount(), run.getErrorCount());
    }

    /** What the run used, as ids and hashes. The content lives in the rows these point at, which never change. */
    static Map<String, Object> summary(RuleInputs inputs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("claimVersion", inputs.claim().claimVersion());
        m.put("stage", inputs.claim().stage() == null ? null : inputs.claim().stage().name());
        m.put("checklistReadiness", inputs.checklist().readiness().name());
        List<Map<String, Object>> documents = new ArrayList<>();
        inputs.documents().forEach(d -> {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("id", d.id().toString());
            doc.put("type", d.type().name());
            doc.put("status", d.status().name());
            doc.put("sha256", d.sha256());
            doc.put("superseded", d.superseded());
            doc.put("extractionId", d.extraction() == null ? null : d.extraction().extractionId().toString());
            documents.add(doc);
        });
        m.put("documents", documents);
        m.put("sameFileDocumentIds", inputs.fileMatches().stream().map(f -> f.otherDocumentId().toString()).distinct().sorted().toList());
        m.put("sameNumberInvoiceDocumentIds", inputs.invoiceCandidates().stream().map(c -> c.documentId().toString()).sorted().toList());
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("status", inputs.history().status());
        history.put("latestClaimsImportId", inputs.history().latestClaimsImportId() == null ? null : inputs.history().latestClaimsImportId().toString());
        history.put("totalPriorClaims", inputs.history().totalPriorClaims());
        m.put("history", history);
        return m;
    }
}
