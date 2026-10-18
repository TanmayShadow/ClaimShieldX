import type { Evidence, Finding, Graph, Report, ReviewAction } from '@/features/report/schemas'

export const INV_ID = 'aaaaaaaa-0000-4000-8000-000000000001'

export function finding(patch: Partial<Finding> = {}): Finding {
  return {
    id: 'f-1',
    ordinal: 1,
    source: 'RULE:HISTORICAL_RELATIONSHIP',
    category: 'HISTORY',
    title: 'Earlier claims relate to this one',
    summary: '2 earlier claims relate to this claim.',
    type: 'FACT',
    status: 'NEEDS_REVIEW',
    validationState: 'VALIDATED',
    supportingEvidenceRefs: ['RULE.HISTORICAL_RELATIONSHIP', 'REL.1'],
    counterEvidenceRefs: [],
    counterEvidence: ['The earlier damage may have been repaired completely.'],
    missingChecks: [],
    recommendation: 'Review the earlier claims.',
    ...patch,
  }
}

export const gap = finding({ id: 'f-2', source: 'RULE:REQUIRED_EVIDENCE', category: 'EVIDENCE_GAP', title: 'Some expected documents are missing', summary: '1 expected document is missing.', type: 'UNKNOWN', status: 'UNKNOWN', supportingEvidenceRefs: ['RULE.REQUIRED_EVIDENCE'], counterEvidence: [], missingChecks: ['POLICY_RECORD:DOCUMENT_MISSING'], recommendation: 'Obtain the missing evidence.' })

export const hypothesis = finding({
  id: 'f-3',
  source: 'AI:H-1',
  category: 'HYPOTHESIS',
  title: 'Recent prior claims on the same vehicle',
  summary: 'The claim shares a vehicle token with two earlier claims <i>quickly</i>.',
  type: 'HYPOTHESIS',
  status: 'SUPPORTED',
  supportingEvidenceRefs: ['RULE.HISTORICAL_RELATIONSHIP', 'HIST.1.151'],
  counterEvidenceRefs: ['RULE.DATE_CONSISTENCY'],
  counterEvidence: ['The vehicle may belong to a fleet.'],
  missingChecks: ['Review damage descriptions from the earlier claims'],
})

export function report(patch: Partial<Report> = {}): Report {
  return {
    reportId: 'rep-1',
    reportVersion: 1,
    investigationId: INV_ID,
    claimId: 'cc31d592-eab9-4245-be1f-680cd53f55f2',
    snapshotId: 'snap-1',
    createdAt: '2026-10-18T09:12:44Z',
    reportStatus: 'CURRENT',
    completeness: 'COMPLETE',
    unavailableSections: [],
    summary: 'Review-priority score 20 of 100 (MEDIUM). 1 item to review, 1 evidence gap, 1 hypothesis to verify.',
    reviewPriority: { score: 20, scale: '0-100', band: 'MEDIUM', recommendedAction: 'MANUAL_REVIEW', scoreVersion: '1', components: [{ ruleId: 'HISTORICAL_RELATIONSHIP', severity: 'REVIEW', points: 20 }] },
    findings: [gap, hypothesis, finding()],
    evidenceCoverage: { itemCount: 20, citedByFindings: 5, byType: { RULE_RESULT: 8 } },
    latestAction: null,
    notice: 'The review-priority score ranks where a person should look first. It is not a probability, not a finding about the claim and not a decision.',
    ...patch,
  }
}

export function action(patch: Partial<ReviewAction> = {}): ReviewAction {
  return { id: 'a-1', createdAt: '2026-10-18T10:00:00Z', action: 'REQUEST_MORE_EVIDENCE', rationale: 'The policy record is missing.\nAsking the insurer.', createdBy: 'u-1', reportVersion: 1, reportWasStale: false, recommendedAction: 'MANUAL_REVIEW', score: 20, ...patch }
}

export const evidence: Evidence = {
  snapshotId: 'snap-1',
  investigationId: INV_ID,
  createdAt: '2026-10-18T09:12:40Z',
  itemCount: 4,
  items: [
    { ref: 'CLAIM.stage', type: 'CLAIM_FIELD', state: 'VERIFIED_FACT', label: 'Claim stage', data: { value: 'FNOL' } },
    { ref: 'FIELD.abc.grandTotal', type: 'EXTRACTED_FIELD', state: 'EXTRACTED_NEEDS_REVIEW', label: 'Grand total', data: { value: '62510.00' } },
    { ref: 'RULE.HISTORICAL_RELATIONSHIP', type: 'RULE_RESULT', state: 'DETERMINISTIC_RESULT', label: 'HISTORICAL_RELATIONSHIP: FLAG', data: { ruleId: 'HISTORICAL_RELATIONSHIP' } },
    { ref: 'GAP.REPEATED_LINE_ITEMS', type: 'UNKNOWN', state: 'UNKNOWN', label: 'REPEATED_LINE_ITEMS could not run', data: {} },
  ],
}

export const graph: Graph = {
  snapshotId: 'snap-1',
  nodes: [
    { id: 'CLAIM', type: 'CLAIM', label: 'This claim' },
    { id: 'HISTORY:CLM-B', type: 'HISTORICAL_CLAIM', label: 'CLM-B' },
  ],
  edges: [{ ref: 'REL.1', source: 'CLAIM', target: 'HISTORY:CLM-B', relationship: 'SAME_VEHICLE', method: 'EXACT_TOKEN', fields: ['vehicleToken'], evidenceRefs: ['RULE.HISTORICAL_RELATIONSHIP'], uncertainty: 'Strength EXACT; no recorded ambiguity' }],
}
