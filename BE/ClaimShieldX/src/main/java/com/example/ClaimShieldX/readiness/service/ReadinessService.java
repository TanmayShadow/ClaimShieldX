package com.example.ClaimShieldX.readiness.service;

import com.example.ClaimShieldX.auth.model.UserPrincipal;
import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.exceptions.ResourceNotFoundException;
import com.example.ClaimShieldX.readiness.dto.DocumentRequirementsResponse;
import com.example.ClaimShieldX.readiness.dto.ReadinessSummary;
import com.example.ClaimShieldX.readiness.model.DocumentRequirement;
import com.example.ClaimShieldX.readiness.repository.DocumentRequirementRepository;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator.Evaluation;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator.Evidence;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator.Rule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Loads a claim's documents and the checklist configuration and hands them to the {@link ChecklistEvaluator}. */
@Service
public class ReadinessService {
    private final ClaimsRepository claimsRepository;
    private final DocumentRepository documentRepository;
    private final DocumentRequirementRepository requirementRepository;

    public ReadinessService(ClaimsRepository claimsRepository, DocumentRepository documentRepository,
                            DocumentRequirementRepository requirementRepository) {
        this.claimsRepository = claimsRepository;
        this.documentRepository = documentRepository;
        this.requirementRepository = requirementRepository;
    }

    /** A claim of another organization looks exactly like a claim that does not exist. */
    @Transactional(readOnly = true)
    public DocumentRequirementsResponse checklist(UserPrincipal principal, UUID claimId) {
        UUID organizationId = principal.getOrganization().getId();
        Claim claim = claimsRepository.findByIdAndOrganizationId(claimId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        Evaluation evaluation = evaluate(claim, rules(), evidenceByClaim(organizationId, List.of(claimId)).getOrDefault(claimId, List.of()));
        return new DocumentRequirementsResponse(claim.getId(), claim.getCurrentStage(), incidentTypeOf(claim),
                evaluation.readiness(), evaluation.items(), evaluation.blockers(), evaluation.warnings());
    }

    /** Readiness for many claims of one organization with two queries in total, however many claims there are. */
    @Transactional(readOnly = true)
    public Map<UUID, ReadinessSummary> summarize(UUID organizationId, Collection<Claim> claims) {
        if (claims.isEmpty()) {
            return Map.of();
        }
        List<Rule> rules = rules();
        Map<UUID, List<Evidence>> evidence = evidenceByClaim(organizationId, claims.stream().map(Claim::getId).toList());
        Map<UUID, ReadinessSummary> summaries = new HashMap<>();
        for (Claim claim : claims) {
            summaries.put(claim.getId(), evaluate(claim, rules, evidence.getOrDefault(claim.getId(), List.of())).summary());
        }
        return summaries;
    }

    private static Evaluation evaluate(Claim claim, List<Rule> rules, List<Evidence> evidence) {
        return ChecklistEvaluator.evaluate(claim.getCurrentStage(), incidentTypeOf(claim), rules, evidence);
    }

    private static IncidentType incidentTypeOf(Claim claim) {
        return claim.getIncident() == null ? null : claim.getIncident().getIncidentType();
    }

    private List<Rule> rules() {
        return requirementRepository.findAll().stream().map(ReadinessService::toRule).toList();
    }

    private static Rule toRule(DocumentRequirement row) {
        return new Rule(row.getStage(), row.getIncidentType(), row.getDocumentType(), row.getRequirement(), row.isGating());
    }

    private Map<UUID, List<Evidence>> evidenceByClaim(UUID organizationId, Collection<UUID> claimIds) {
        return documentRepository.findCurrentEvidence(organizationId, claimIds).stream()
                .collect(Collectors.groupingBy(DocumentRepository.EvidenceRow::getClaimId,
                        Collectors.mapping(row -> new Evidence(row.getId(), row.getDocumentType(), row.getStatus(), row.getReceivedAt()),
                                Collectors.toList())));
    }
}
