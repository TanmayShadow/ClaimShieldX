import type { RuleResult, RuleRun } from '@/features/checks/schemas'

export const RUN_ID = 'bbbbbbbb-0000-4000-8000-000000000001'
export const DOC_ID = 'dddddddd-0000-4000-8000-000000000001'

export function result(patch: Partial<RuleResult> = {}): RuleResult {
  return { id: `r-${patch.ruleId ?? 'X'}`, ruleId: 'DATE_CONSISTENCY', ruleVersion: '1', label: 'Date consistency', outcome: 'PASS', severity: null, reliability: 'RELIABLE', reliabilityReasons: [], explanation: '3 date comparisons are consistent.', values: {}, evidence: [], considerations: [], executedAt: '2026-10-18T09:12:44Z', ...patch }
}

export const flagged = result({
  id: 'r-variance',
  ruleId: 'ESTIMATE_INVOICE_VARIANCE',
  label: 'Estimate-to-invoice variance',
  outcome: 'FLAG',
  severity: 'REVIEW',
  explanation: 'The final invoice total of INR 62510.00 is 38.9% above the estimate of INR 45000.50.',
  values: { invoiceTotal: '62510.00', primaryBasis: 'GARAGE_ESTIMATE_DOCUMENT', comparisons: [{ basis: 'CLAIM_INITIAL_ESTIMATE', differencePercent: '38.90' }], reviewed: true, note: null },
  evidence: [
    { kind: 'DOCUMENT_FIELD', documentId: DOC_ID, extractionId: 'e1', field: 'grandTotal', page: 1, quote: 'Grand total 62,510.00 <b>bold</b>' },
    { kind: 'CLAIM_FIELD', field: 'financial.initialEstimateAmount' },
  ],
  considerations: ['Supplementary damage or repairs found after the estimate was made.'],
})

export function ruleRun(patch: Partial<RuleRun> = {}): RuleRun {
  const results = patch.results ?? [
    result({ id: 'r-na', ruleId: 'INVOICE_ARITHMETIC', label: 'Invoice arithmetic', outcome: 'NOT_APPLICABLE', explanation: 'Not expected at this stage.' }),
    result({ id: 'r-pass' }),
    result({ id: 'r-inc', ruleId: 'REQUIRED_EVIDENCE', label: 'Required evidence', outcome: 'INCOMPLETE', explanation: '2 expected documents are missing.', values: { missing: ['CLAIM_FORM:DOCUMENT_MISSING'] } }),
    flagged,
  ]
  const count = (o: string) => results.filter((r) => r.outcome === o).length
  return {
    id: RUN_ID,
    claimId: 'cc31d592-eab9-4245-be1f-680cd53f55f2',
    ruleSetVersion: '1',
    createdAt: '2026-10-18T09:12:44Z',
    counts: { pass: count('PASS'), flag: count('FLAG'), incomplete: count('INCOMPLETE'), notApplicable: count('NOT_APPLICABLE'), error: count('ERROR') },
    inputFingerprint: 'abc',
    reusedExistingRun: false,
    ...patch,
    results,
  }
}
