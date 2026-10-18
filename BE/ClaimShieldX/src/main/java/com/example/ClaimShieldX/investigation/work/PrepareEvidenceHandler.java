package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.documents.repository.DocumentRepository;
import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.readiness.model.Readiness;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator.Evaluation;
import com.example.ClaimShieldX.readiness.service.ReadinessService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether the investigation can start: the one document the claim's stage cannot do without must be there and usable
 * (the checklist's NOT_READY, defined in B4). If it is not, the investigation waits for evidence instead of failing, and starts when a
 * document arrives. Any other gap is a warning that makes some rules INCOMPLETE later; it does not block.
 */
@Component
public class PrepareEvidenceHandler implements StageHandler {
    private final ClaimsRepository claimsRepository;
    private final ReadinessService readinessService;
    private final DocumentRepository documentRepository;

    public PrepareEvidenceHandler(ClaimsRepository claimsRepository, ReadinessService readinessService, DocumentRepository documentRepository) {
        this.claimsRepository = claimsRepository;
        this.readinessService = readinessService;
        this.documentRepository = documentRepository;
    }

    @Override
    public Stage stage() {
        return Stage.PREPARING_EVIDENCE;
    }

    @Override
    public StageOutcome execute(StageContext context) {
        Claim claim = claimsRepository.findByIdAndOrganizationId(context.claimId(), context.organizationId()).orElse(null);
        if (claim == null) {
            return new StageOutcome.Permanent(InvestigationErrorCode.INTERNAL_ERROR, Map.of("reason", "CLAIM_NOT_FOUND"));
        }
        Evaluation evaluation = readinessService.evaluateFor(context.organizationId(), claim);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("claimVersion", claim.getClaimVersion());
        summary.put("readiness", evaluation.readiness().name());
        List<Map<String, Object>> blockers = new ArrayList<>();
        evaluation.blockers().forEach(b -> blockers.add(Map.of("documentType", String.valueOf(b.documentType()), "code", b.code().name())));
        summary.put("blockers", blockers);
        summary.put("warnings", evaluation.warnings().size());

        Map<DocumentType, Integer> counts = new EnumMap<>(DocumentType.class);
        documentRepository.findByOrganizationIdAndClaimIdOrderByReceivedAtAscIdAsc(context.organizationId(), context.claimId()).stream()
                .filter(d -> !d.isSuperseded()).forEach(d -> counts.merge(d.getDocumentType(), 1, Integer::sum));
        Map<String, Integer> documents = new LinkedHashMap<>();
        counts.forEach((type, n) -> documents.put(type.name(), n));
        summary.put("documents", documents);

        if (evaluation.readiness() == Readiness.NOT_READY) {
            return new StageOutcome.Waiting(summary);
        }
        return StageOutcome.ok(summary);
    }
}
