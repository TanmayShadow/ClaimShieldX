package com.example.ClaimShieldX.readiness;

import com.example.ClaimShieldX.claims.model.ClaimStage;
import com.example.ClaimShieldX.claims.model.IncidentType;
import com.example.ClaimShieldX.documents.model.DocumentStatus;
import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.readiness.dto.ChecklistItem;
import com.example.ClaimShieldX.readiness.dto.ReadinessIssue;
import com.example.ClaimShieldX.readiness.dto.ReadinessIssue.Code;
import com.example.ClaimShieldX.readiness.model.EvidenceState;
import com.example.ClaimShieldX.readiness.model.InvestigationCheck;
import com.example.ClaimShieldX.readiness.model.Readiness;
import com.example.ClaimShieldX.readiness.model.RequirementLevel;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator.Evaluation;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator.Evidence;
import com.example.ClaimShieldX.readiness.service.ChecklistEvaluator.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.*;

import static com.example.ClaimShieldX.claims.model.ClaimStage.*;
import static com.example.ClaimShieldX.documents.model.DocumentType.*;
import static com.example.ClaimShieldX.readiness.model.RequirementLevel.*;
import static org.assertj.core.api.Assertions.assertThat;

/** The pure rules: which entries are required, what has arrived, which checks cannot run, and the readiness level. */
class ChecklistEvaluatorTests {
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    // A deliberately small table, so each test shows exactly which rows it relies on. The real seed is checked in RequirementSeedTests.
    private static final List<Rule> RULES = List.of(
            rule(FNOL, "ANY", CLAIM_FORM, REQUIRED, true),
            rule(FNOL, "ANY", GARAGE_ESTIMATE, NOT_APPLICABLE, false),
            rule(FNOL, "ANY", FINAL_INVOICE, NOT_APPLICABLE, false),

            rule(ESTIMATE_RECEIVED, "ANY", CLAIM_FORM, REQUIRED, false),
            rule(ESTIMATE_RECEIVED, "ANY", GARAGE_ESTIMATE, REQUIRED, true),
            rule(ESTIMATE_RECEIVED, "ANY", FINAL_INVOICE, NOT_APPLICABLE, false),

            rule(FINAL_INVOICE_RECEIVED, "ANY", CLAIM_FORM, REQUIRED, false),
            rule(FINAL_INVOICE_RECEIVED, "ANY", POLICY_RECORD, REQUIRED, false),
            rule(FINAL_INVOICE_RECEIVED, "ANY", GARAGE_ESTIMATE, REQUIRED, false),
            rule(FINAL_INVOICE_RECEIVED, "ANY", FINAL_INVOICE, REQUIRED, true),
            rule(FINAL_INVOICE_RECEIVED, "ANY", DAMAGE_PHOTOGRAPH, OPTIONAL, false),
            rule(FINAL_INVOICE_RECEIVED, "ANY", JOB_CARD, OPTIONAL, false),
            rule(FINAL_INVOICE_RECEIVED, "ANY", FIR_POLICE_REPORT, OPTIONAL, false),
            rule(FINAL_INVOICE_RECEIVED, "ANY", THEFT_EVIDENCE, NOT_APPLICABLE, false),
            rule(FINAL_INVOICE_RECEIVED, "THEFT", FIR_POLICE_REPORT, REQUIRED, false),
            rule(FINAL_INVOICE_RECEIVED, "THEFT", THEFT_EVIDENCE, REQUIRED, false));

    private static Rule rule(ClaimStage stage, String incident, DocumentType type, RequirementLevel level, boolean gating) {
        return new Rule(stage, incident, type, level, gating);
    }

    private static Evidence doc(DocumentType type, DocumentStatus status) {
        return new Evidence(UUID.randomUUID(), type, status, T0);
    }

    private static Evidence doc(DocumentType type, DocumentStatus status, Instant receivedAt) {
        return new Evidence(UUID.randomUUID(), type, status, receivedAt);
    }

    private static Evaluation finalStage(Evidence... documents) {
        return ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.COLLISION, RULES, List.of(documents));
    }

    private static ChecklistItem item(Evaluation evaluation, DocumentType type) {
        return evaluation.items().stream().filter(i -> i.documentType() == type).findFirst().orElseThrow();
    }

    private static List<ReadinessIssue> issuesFor(List<ReadinessIssue> issues, DocumentType type) {
        return issues.stream().filter(i -> i.documentType() == type).toList();
    }

    private static List<Evidence> allVerifiedAtFinalStage() {
        return List.of(doc(CLAIM_FORM, DocumentStatus.VERIFIED), doc(POLICY_RECORD, DocumentStatus.VERIFIED),
                doc(GARAGE_ESTIMATE, DocumentStatus.VERIFIED), doc(FINAL_INVOICE, DocumentStatus.VERIFIED),
                doc(DAMAGE_PHOTOGRAPH, DocumentStatus.VERIFIED));
    }

    // ------------------------------------------------------------ readiness levels

    @Test
    void aClaimWithNoDocumentsIsNotReadyBecauseTheFinalInvoiceIsMissing() {
        Evaluation result = finalStage();

        assertThat(result.readiness()).isEqualTo(Readiness.NOT_READY);
        assertThat(result.blockers()).extracting(ReadinessIssue::documentType).containsExactly(FINAL_INVOICE);
        assertThat(result.blockers().get(0).code()).isEqualTo(Code.DOCUMENT_MISSING);
        assertThat(result.summary().blockerCount()).isEqualTo(1);
    }

    @Test
    void aMissingNonGatingRequiredDocumentOnlyWarnsSoPreliminaryChecksCanProceed() {
        Evaluation result = finalStage(doc(FINAL_INVOICE, DocumentStatus.RECEIVED));

        assertThat(result.readiness()).isEqualTo(Readiness.READY_WITH_WARNINGS);
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).extracting(ReadinessIssue::documentType)
                .containsExactlyInAnyOrder(CLAIM_FORM, POLICY_RECORD, GARAGE_ESTIMATE, DAMAGE_PHOTOGRAPH);
    }

    @Test
    void everythingVerifiedIsReadyWithNoIssues() {
        Evaluation result = finalStage(allVerifiedAtFinalStage().toArray(Evidence[]::new));

        assertThat(result.readiness()).isEqualTo(Readiness.READY);
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.summary().level()).isEqualTo(Readiness.READY);
    }

    @Test
    void anUploadedFileIsNotVerifiedJustBecauseItArrived() {
        List<Evidence> received = new ArrayList<>(allVerifiedAtFinalStage().stream()
                .map(e -> doc(e.documentType(), DocumentStatus.RECEIVED)).toList());

        Evaluation result = finalStage(received.toArray(Evidence[]::new));

        assertThat(item(result, FINAL_INVOICE).evidenceState()).isEqualTo(EvidenceState.RECEIVED);
        assertThat(result.items()).filteredOn(i -> i.requirement() == REQUIRED)
                .extracting(ChecklistItem::evidenceState).containsOnly(EvidenceState.RECEIVED);
    }

    @Test
    void aFailedGatingDocumentBlocks() {
        Evaluation result = finalStage(doc(FINAL_INVOICE, DocumentStatus.FAILED));

        assertThat(result.readiness()).isEqualTo(Readiness.NOT_READY);
        assertThat(result.blockers()).hasSize(1);
        assertThat(result.blockers().get(0).code()).isEqualTo(Code.DOCUMENT_FAILED);
        assertThat(result.blockers().get(0).message()).contains("Final invoice").contains("could not be processed");
    }

    @Test
    void aGatingDocumentThatNeedsReviewOrIsProcessingOnlyWarns() {
        for (DocumentStatus status : List.of(DocumentStatus.NEEDS_REVIEW, DocumentStatus.PROCESSING)) {
            Evaluation result = finalStage(doc(FINAL_INVOICE, status));

            assertThat(result.blockers()).as(status.name()).isEmpty();
            assertThat(issuesFor(result.warnings(), FINAL_INVOICE)).as(status.name()).hasSize(1);
            assertThat(result.readiness()).as(status.name()).isEqualTo(Readiness.READY_WITH_WARNINGS);
        }
    }

    @Test
    void onlyTheStageDefiningDocumentIsMandatoryAndEverythingElseLetsTheUserProceed() {
        for (ClaimStage stage : List.of(FNOL, ESTIMATE_RECEIVED, FINAL_INVOICE_RECEIVED)) {
            Evaluation result = ChecklistEvaluator.evaluate(stage, IncidentType.COLLISION, RULES, List.of());

            assertThat(result.items()).as(stage.name()).filteredOn(ChecklistItem::mandatory).hasSize(1);
            assertThat(result.items()).as(stage.name()).filteredOn(ChecklistItem::mandatory)
                    .allSatisfy(i -> assertThat(i.requirement()).isEqualTo(REQUIRED));
            assertThat(result.blockers()).as(stage.name()).extracting(ReadinessIssue::documentType)
                    .containsExactlyElementsOf(result.items().stream().filter(ChecklistItem::mandatory).map(ChecklistItem::documentType).toList());
        }
        Evaluation final_ = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.COLLISION, RULES, List.of());
        assertThat(item(final_, FINAL_INVOICE).mandatory()).isTrue();
        assertThat(item(final_, GARAGE_ESTIMATE).mandatory()).isFalse();
        assertThat(item(final_, CLAIM_FORM).mandatory()).isFalse();
        assertThat(item(final_, POLICY_RECORD).mandatory()).isFalse();
    }

    @Test
    void theUserCanProceedWithOnlyTheMandatoryDocumentEvenThoughEveryOtherEntryIsMissing() {
        Evaluation result = finalStage(doc(FINAL_INVOICE, DocumentStatus.RECEIVED));

        assertThat(result.readiness()).isNotEqualTo(Readiness.NOT_READY);
        assertThat(result.blockers()).isEmpty();
        assertThat(result.items()).filteredOn(i -> i.documentType() != FINAL_INVOICE && i.requirement() == REQUIRED)
                .extracting(ChecklistItem::evidenceState).containsOnly(EvidenceState.NOT_RECEIVED);
    }

    @Test
    void everyOtherDocumentTogetherDoesNotReplaceTheMandatoryOne() {
        List<Evidence> everythingButTheInvoice = Arrays.stream(DocumentType.values())
                .filter(t -> t != FINAL_INVOICE).map(t -> doc(t, DocumentStatus.VERIFIED)).toList();

        Evaluation result = finalStage(everythingButTheInvoice.toArray(Evidence[]::new));

        assertThat(result.readiness()).isEqualTo(Readiness.NOT_READY);
        assertThat(result.blockers()).extracting(ReadinessIssue::documentType).containsExactly(FINAL_INVOICE);
    }

    // ------------------------------------------------------------ every state against every requirement level

    @ParameterizedTest
    @EnumSource(EvidenceState.class)
    void aRequiredNonGatingEntryProducesTheContractedIssue(EvidenceState state) {
        Evaluation result = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.COLLISION,
                List.of(rule(FINAL_INVOICE_RECEIVED, "ANY", POLICY_RECORD, REQUIRED, false)), documentIn(POLICY_RECORD, state));

        Map<EvidenceState, Code> expected = new EnumMap<>(EvidenceState.class);
        expected.put(EvidenceState.NOT_RECEIVED, Code.DOCUMENT_MISSING);
        expected.put(EvidenceState.FAILED, Code.DOCUMENT_FAILED);
        expected.put(EvidenceState.NEEDS_REVIEW, Code.DOCUMENT_NEEDS_REVIEW);
        expected.put(EvidenceState.PROCESSING, Code.DOCUMENT_PROCESSING);

        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).extracting(ReadinessIssue::code)
                .containsExactlyElementsOf(expected.containsKey(state) ? List.of(expected.get(state)) : List.of());
        assertThat(result.items().get(0).evidenceState()).isEqualTo(state);
    }

    @ParameterizedTest
    @EnumSource(EvidenceState.class)
    void anOptionalEntryOnlyWarnsWhenItFailedOrNeedsReview(EvidenceState state) {
        Evaluation result = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.COLLISION,
                List.of(rule(FINAL_INVOICE_RECEIVED, "ANY", JOB_CARD, OPTIONAL, false)), documentIn(JOB_CARD, state));

        Map<EvidenceState, Code> expected = Map.of(EvidenceState.FAILED, Code.DOCUMENT_FAILED, EvidenceState.NEEDS_REVIEW, Code.DOCUMENT_NEEDS_REVIEW);

        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).extracting(ReadinessIssue::code)
                .containsExactlyElementsOf(expected.containsKey(state) ? List.of(expected.get(state)) : List.of());
    }

    @ParameterizedTest
    @EnumSource(EvidenceState.class)
    void aNotApplicableEntryNeverProducesAnIssue(EvidenceState state) {
        Evaluation result = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.COLLISION,
                List.of(rule(FINAL_INVOICE_RECEIVED, "ANY", JOB_CARD, NOT_APPLICABLE, false)), documentIn(JOB_CARD, state));

        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.readiness()).isEqualTo(Readiness.READY);
        assertThat(result.items().get(0).requirement()).isEqualTo(NOT_APPLICABLE);
    }

    @ParameterizedTest
    @EnumSource(EvidenceState.class)
    void aGatingEntryBlocksOnlyWhenMissingOrFailed(EvidenceState state) {
        Evaluation result = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.COLLISION,
                List.of(rule(FINAL_INVOICE_RECEIVED, "ANY", JOB_CARD, REQUIRED, true)), documentIn(JOB_CARD, state));

        boolean blocks = state == EvidenceState.NOT_RECEIVED || state == EvidenceState.FAILED;
        assertThat(result.blockers()).hasSize(blocks ? 1 : 0);
        assertThat(result.readiness() == Readiness.NOT_READY).isEqualTo(blocks);
    }

    private static List<Evidence> documentIn(DocumentType type, EvidenceState state) {
        return state == EvidenceState.NOT_RECEIVED ? List.of() : List.of(doc(type, DocumentStatus.valueOf(state.name())));
    }

    // ------------------------------------------------------------ evidence state and the documents behind it

    @Test
    void theNewestDocumentDecidesTheStateAndAllCurrentOnesAreListedNewestFirst() {
        Evidence older = doc(FINAL_INVOICE, DocumentStatus.FAILED, T0);
        Evidence newer = doc(FINAL_INVOICE, DocumentStatus.VERIFIED, T0.plusSeconds(60));
        Evidence newest = doc(FINAL_INVOICE, DocumentStatus.NEEDS_REVIEW, T0.plusSeconds(120));

        ChecklistItem invoice = item(finalStage(newer, older, newest), FINAL_INVOICE);

        assertThat(invoice.evidenceState()).isEqualTo(EvidenceState.NEEDS_REVIEW);
        assertThat(invoice.documentIds()).containsExactly(newest.id(), newer.id(), older.id());
    }

    @Test
    void documentsReceivedAtTheSameInstantAreOrderedByIdSoTheResultIsStable() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Evidence a = new Evidence(low, FINAL_INVOICE, DocumentStatus.FAILED, T0);
        Evidence b = new Evidence(high, FINAL_INVOICE, DocumentStatus.VERIFIED, T0);

        ChecklistItem forward = item(finalStage(a, b), FINAL_INVOICE);
        ChecklistItem reversed = item(finalStage(b, a), FINAL_INVOICE);

        assertThat(forward).isEqualTo(reversed);
        assertThat(forward.documentIds()).containsExactly(high, low);
        assertThat(forward.evidenceState()).isEqualTo(EvidenceState.VERIFIED);
    }

    @Test
    void anEntryWithNoDocumentIsNotReceivedWithAnEmptyList() {
        ChecklistItem estimate = item(finalStage(), GARAGE_ESTIMATE);

        assertThat(estimate.evidenceState()).isEqualTo(EvidenceState.NOT_RECEIVED);
        assertThat(estimate.documentIds()).isEmpty();
        assertThat(estimate.requirement()).isEqualTo(REQUIRED);
    }

    @Test
    void aDocumentOfATypeTheStageDoesNotListIsIgnored() {
        Evaluation result = finalStage(doc(OTHER, DocumentStatus.FAILED), doc(PAYMENT_PROOF, DocumentStatus.FAILED));

        assertThat(result.items()).extracting(ChecklistItem::documentType).doesNotContain(OTHER, PAYMENT_PROOF);
        assertThat(result.warnings()).extracting(ReadinessIssue::documentType).doesNotContain(OTHER, PAYMENT_PROOF);
    }

    @Test
    void itemsFollowTheDocumentTypeOrderWhateverTheInputOrder() {
        Evaluation result = finalStage();

        List<DocumentType> types = result.items().stream().map(ChecklistItem::documentType).toList();
        assertThat(types).isSortedAccordingTo(Comparator.comparingInt(DocumentType::ordinal));
    }

    // ------------------------------------------------------------ which checks cannot run

    @Test
    void aMissingInvoiceNamesEveryCheckThatNeedsIt() {
        Evaluation result = finalStage();

        assertThat(item(result, FINAL_INVOICE).blocks()).containsExactlyInAnyOrder(
                InvestigationCheck.INVOICE_ARITHMETIC, InvestigationCheck.ESTIMATE_INVOICE_VARIANCE,
                InvestigationCheck.INVOICE_IDENTITY_DUPLICATE, InvestigationCheck.REPEATED_LINE_ITEMS,
                InvestigationCheck.REPAIR_SCOPE_REVIEW);
        assertThat(result.blockers().get(0).blocks()).isEqualTo(item(result, FINAL_INVOICE).blocks());
        assertThat(result.blockers().get(0).message()).contains("Checks that cannot run without it")
                .contains("Estimate-to-invoice variance check");
    }

    @Test
    void aMissingEstimateOnlyBlocksTheVarianceCheck() {
        Evaluation result = finalStage(doc(FINAL_INVOICE, DocumentStatus.VERIFIED));

        assertThat(item(result, GARAGE_ESTIMATE).blocks()).containsExactly(InvestigationCheck.ESTIMATE_INVOICE_VARIANCE);
        assertThat(item(result, FINAL_INVOICE).blocks()).isEmpty();
    }

    @Test
    void aFailedDocumentBlocksTheSameChecksAsAMissingOne() {
        Evaluation result = finalStage(doc(FINAL_INVOICE, DocumentStatus.VERIFIED), doc(GARAGE_ESTIMATE, DocumentStatus.FAILED));

        assertThat(item(result, GARAGE_ESTIMATE).blocks()).containsExactly(InvestigationCheck.ESTIMATE_INVOICE_VARIANCE);
    }

    @Test
    void aDocumentThatNeedsReviewDoesNotBlockAnyCheck() {
        Evaluation result = finalStage(doc(FINAL_INVOICE, DocumentStatus.VERIFIED), doc(GARAGE_ESTIMATE, DocumentStatus.NEEDS_REVIEW));

        assertThat(item(result, GARAGE_ESTIMATE).blocks()).isEmpty();
    }

    @Test
    void aMissingOptionalDocumentIsReportedOnlyBecauseItBlocksACheck() {
        Evaluation withoutPhotos = finalStage(allVerifiedAtFinalStage().stream().filter(e -> e.documentType() != DAMAGE_PHOTOGRAPH).toArray(Evidence[]::new));

        assertThat(issuesFor(withoutPhotos.warnings(), DAMAGE_PHOTOGRAPH)).hasSize(1);
        assertThat(item(withoutPhotos, DAMAGE_PHOTOGRAPH).blocks()).containsExactly(InvestigationCheck.REPAIR_SCOPE_REVIEW);
        assertThat(issuesFor(withoutPhotos.warnings(), JOB_CARD)).isEmpty();
        assertThat(issuesFor(withoutPhotos.warnings(), FIR_POLICE_REPORT)).isEmpty();
    }

    @Test
    void aCheckThatDoesNotApplyAtTheStageIsNeverReported() {
        Evaluation result = ChecklistEvaluator.evaluate(ESTIMATE_RECEIVED, IncidentType.COLLISION, RULES, List.of());

        // The variance check needs the final invoice, which this stage does not ask for yet.
        assertThat(item(result, GARAGE_ESTIMATE).blocks()).isEmpty();
        assertThat(item(result, FINAL_INVOICE).requirement()).isEqualTo(NOT_APPLICABLE);
        assertThat(item(result, FINAL_INVOICE).blocks()).isEmpty();
    }

    // ------------------------------------------------------------ stage awareness

    @Test
    void theFinalInvoiceIsNeverRequiredBeforeTheFinalStage() {
        for (ClaimStage stage : List.of(FNOL, ESTIMATE_RECEIVED)) {
            Evaluation result = ChecklistEvaluator.evaluate(stage, IncidentType.COLLISION, RULES, List.of());

            assertThat(item(result, FINAL_INVOICE).requirement()).as(stage.name()).isEqualTo(NOT_APPLICABLE);
            assertThat(result.blockers()).as(stage.name()).extracting(ReadinessIssue::documentType).doesNotContain(FINAL_INVOICE);
            assertThat(result.warnings()).as(stage.name()).extracting(ReadinessIssue::documentType).doesNotContain(FINAL_INVOICE);
        }
    }

    @Test
    void eachStageIsBlockedByItsOwnDefiningDocument() {
        assertThat(ChecklistEvaluator.evaluate(FNOL, IncidentType.COLLISION, RULES, List.of()).blockers())
                .extracting(ReadinessIssue::documentType).containsExactly(CLAIM_FORM);
        assertThat(ChecklistEvaluator.evaluate(ESTIMATE_RECEIVED, IncidentType.COLLISION, RULES, List.of()).blockers())
                .extracting(ReadinessIssue::documentType).containsExactly(GARAGE_ESTIMATE);
        assertThat(ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.COLLISION, RULES, List.of()).blockers())
                .extracting(ReadinessIssue::documentType).containsExactly(FINAL_INVOICE);
    }

    @Test
    void aStageWithNoChecklistSaysSoInsteadOfClaimingReadiness() {
        Evaluation result = ChecklistEvaluator.evaluate(POST_SETTLEMENT, IncidentType.COLLISION, RULES, List.of(doc(FINAL_INVOICE, DocumentStatus.VERIFIED)));

        assertThat(result.items()).isEmpty();
        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0).code()).isEqualTo(Code.NO_CHECKLIST_FOR_STAGE);
        assertThat(result.warnings().get(0).documentType()).isNull();
        assertThat(result.warnings().get(0).message()).contains("POST_SETTLEMENT");
        assertThat(result.readiness()).isEqualTo(Readiness.READY_WITH_WARNINGS);
    }

    @Test
    void aMissingStageIsTreatedAsHavingNoChecklist() {
        Evaluation result = ChecklistEvaluator.evaluate(null, IncidentType.COLLISION, RULES, List.of());

        assertThat(result.warnings()).extracting(ReadinessIssue::code).containsExactly(Code.NO_CHECKLIST_FOR_STAGE);
        assertThat(result.readiness()).isEqualTo(Readiness.READY_WITH_WARNINGS);
    }

    // ------------------------------------------------------------ incident type

    @Test
    void aTheftClaimRequiresTheFirAndTheftEvidenceWhileACollisionDoesNot() {
        Evaluation theft = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.THEFT, RULES, List.of());
        Evaluation collision = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.COLLISION, RULES, List.of());

        assertThat(item(theft, FIR_POLICE_REPORT).requirement()).isEqualTo(REQUIRED);
        assertThat(item(theft, THEFT_EVIDENCE).requirement()).isEqualTo(REQUIRED);
        assertThat(issuesFor(theft.warnings(), FIR_POLICE_REPORT)).hasSize(1);
        assertThat(issuesFor(theft.warnings(), THEFT_EVIDENCE)).hasSize(1);

        assertThat(item(collision, FIR_POLICE_REPORT).requirement()).isEqualTo(OPTIONAL);
        assertThat(item(collision, THEFT_EVIDENCE).requirement()).isEqualTo(NOT_APPLICABLE);
        assertThat(issuesFor(collision.warnings(), THEFT_EVIDENCE)).isEmpty();
    }

    @Test
    void anIncidentTypeWithNoSpecificRowsAndAMissingTypeUseTheDefaults() {
        for (IncidentType type : new IncidentType[]{IncidentType.OTHER, null}) {
            Evaluation result = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, type, RULES, List.of());

            assertThat(item(result, FIR_POLICE_REPORT).requirement()).as(String.valueOf(type)).isEqualTo(OPTIONAL);
            assertThat(item(result, THEFT_EVIDENCE).requirement()).as(String.valueOf(type)).isEqualTo(NOT_APPLICABLE);
        }
    }

    @Test
    void aSpecificRowReplacesTheDefaultRowInsteadOfAddingASecondEntry() {
        Evaluation theft = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.THEFT, RULES, List.of());

        assertThat(theft.items()).filteredOn(i -> i.documentType() == FIR_POLICE_REPORT).hasSize(1);
        assertThat(theft.items()).extracting(ChecklistItem::documentType).doesNotHaveDuplicates();
    }

    // ------------------------------------------------------------ determinism and wording

    @Test
    void theSameInputsInAnyOrderGiveTheSameResult() {
        List<Evidence> documents = new ArrayList<>(allVerifiedAtFinalStage());
        documents.add(doc(FIR_POLICE_REPORT, DocumentStatus.NEEDS_REVIEW));
        Evaluation expected = ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.THEFT, RULES, documents);

        for (int seed = 0; seed < 10; seed++) {
            List<Rule> shuffledRules = new ArrayList<>(RULES);
            List<Evidence> shuffledDocuments = new ArrayList<>(documents);
            Collections.shuffle(shuffledRules, new Random(seed));
            Collections.shuffle(shuffledDocuments, new Random(seed));

            assertThat(ChecklistEvaluator.evaluate(FINAL_INVOICE_RECEIVED, IncidentType.THEFT, shuffledRules, shuffledDocuments))
                    .as("seed " + seed).isEqualTo(expected);
        }
    }

    @Test
    void noMessageEverTalksAboutFraudOrAClaimBeingSuspect() {
        String forbidden = "(?i)fraud|suspic|reject|deny|denied|guilty|misrepresent|false|fake";
        for (ClaimStage stage : ClaimStage.values()) {
            for (IncidentType type : IncidentType.values()) {
                for (DocumentStatus status : DocumentStatus.values()) {
                    List<Evidence> documents = Arrays.stream(DocumentType.values()).map(t -> doc(t, status)).toList();
                    for (List<Evidence> input : List.of(List.<Evidence>of(), documents)) {
                        Evaluation result = ChecklistEvaluator.evaluate(stage, type, RULES, input);
                        List<ReadinessIssue> all = new ArrayList<>(result.blockers());
                        all.addAll(result.warnings());
                        assertThat(all).extracting(ReadinessIssue::message).allSatisfy(m -> assertThat(m).isNotBlank().doesNotContainPattern(forbidden));
                    }
                }
            }
        }
    }

    @Test
    void everyDocumentTypeHasAReadableLabelAndEveryCheckNeedsRealDocuments() {
        for (DocumentType type : DocumentType.values()) {
            assertThat(ChecklistEvaluator.label(type)).isNotBlank().doesNotContain("_");
        }
        for (InvestigationCheck check : InvestigationCheck.values()) {
            assertThat(check.label()).isNotBlank();
            assertThat(check.documents()).isNotEmpty().doesNotContain(OTHER).doesNotHaveDuplicates();
        }
    }

    @Test
    void aSummaryCountsBlockersAndWarnings() {
        Evaluation result = finalStage();

        assertThat(result.summary().level()).isEqualTo(Readiness.NOT_READY);
        assertThat(result.summary().blockerCount()).isEqualTo(result.blockers().size());
        assertThat(result.summary().warningCount()).isEqualTo(result.warnings().size());
    }
}
