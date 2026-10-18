import type { HistoryMatch, Hypothesis, ReviewAction, SampleDocument, SampleRule } from './types'

/**
 * One fictional case. Nothing here is read from, or sent to, a server. Identifiers are made up and carry the DEMO or SYN marker.
 * Amounts are chosen so the lines add up exactly: the estimate to 45,000.50 and the invoice to 62,510.00.
 */
export const sampleClaim = {
  internalId: 'CLM-DEMO-1001',
  stage: 'Final invoice received',
  incidentType: 'Collision',
  incidentAt: '10 Sep 2026',
  reportedAt: '11 Sep 2026',
  policyPeriod: '1 Jan 2026 to 31 Dec 2026',
  policyToken: 'POL-TOKEN-1001',
  vehicleToken: 'VEH-TOKEN-1001',
  garageId: 'GARAGE-DEMO-10',
  initialEstimate: 'INR 45,000.50',
}

export const sampleDocuments: SampleDocument[] = [
  {
    id: 'doc-estimate',
    documentType: 'GARAGE_ESTIMATE',
    typeLabel: 'Garage estimate',
    fileName: 'garage-estimate.pdf',
    status: 'VERIFIED',
    statusLabel: 'Verified',
    sheet: {
      title: 'Repair estimate',
      issuer: 'Demo Motors (fictional)',
      meta: [
        { label: 'Garage id', value: 'GARAGE-DEMO-10', anchor: 'garageIdentifier' },
        { label: 'Estimate date', value: '12 Sep 2026', anchor: 'documentDate' },
      ],
      lines: [
        { id: 'e1', description: 'Front bumper replacement', amount: '12,000.00' },
        { id: 'e2', description: 'Paint and refinish', amount: '8,500.50' },
        { id: 'e3', description: 'Headlamp assembly, left', amount: '9,650.00' },
        { id: 'e4', description: 'Labour', amount: '14,850.00' },
      ],
      totals: [{ id: 'grandTotal', label: 'Grand total', amount: '45,000.50' }],
    },
    fields: [
      { name: 'garageIdentifier', state: 'EXTRACTED', value: 'GARAGE-DEMO-10', normalizedValue: 'GARAGE-DEMO-10', anchor: 'garageIdentifier', page: 1, quote: 'Garage id GARAGE-DEMO-10' },
      { name: 'documentDate', state: 'EXTRACTED', value: '12 Sep 2026', normalizedValue: '2026-09-12', anchor: 'documentDate', page: 1, quote: 'Estimate date 12 Sep 2026' },
      { name: 'grandTotal', state: 'EXTRACTED', value: '45,000.50', normalizedValue: '45000.50', anchor: 'grandTotal', page: 1, quote: 'Grand total 45,000.50' },
      { name: 'currency', state: 'EXTRACTED', value: 'INR', normalizedValue: 'INR', page: 1, quote: 'All amounts in INR' },
      { name: 'vehicleRegistration', state: 'UNKNOWN', unknownReason: 'NOT_PRESENT' },
    ],
  },
  {
    id: 'doc-invoice',
    documentType: 'FINAL_INVOICE',
    typeLabel: 'Final invoice',
    fileName: 'final-invoice.pdf',
    status: 'VERIFIED',
    statusLabel: 'Verified',
    sheet: {
      title: 'Tax invoice',
      issuer: 'Demo Motors (fictional)',
      meta: [
        { label: 'Invoice no.', value: 'INV-2026-0457', anchor: 'invoiceNumber' },
        { label: 'Garage id', value: 'GARAGE-DEMO-10', anchor: 'garageIdentifier' },
        { label: 'Invoice date', value: '24 Sep 2026', anchor: 'documentDate' },
      ],
      lines: [
        { id: 'i1', description: 'Front bumper replacement', amount: '12,000.00' },
        { id: 'i2', description: 'Paint and refinish', amount: '8,500.50' },
        { id: 'i3', description: 'Headlamp assembly, left', amount: '9,650.00' },
        { id: 'i4', description: 'Labour', amount: '14,850.00' },
        { id: 'i5', description: 'Radiator support repair', amount: '9,259.50' },
        { id: 'i6', description: 'Coolant hose and refill', amount: '2,350.00' },
        { id: 'i7', description: 'Wheel alignment', amount: '5,900.00' },
      ],
      totals: [{ id: 'grandTotal', label: 'Grand total', amount: '62,510.00' }],
    },
    fields: [
      { name: 'invoiceNumber', state: 'EXTRACTED', value: 'INV-2026-0457', normalizedValue: 'INV-2026-0457', anchor: 'invoiceNumber', page: 1, quote: 'Invoice no. INV-2026-0457' },
      { name: 'garageIdentifier', state: 'EXTRACTED', value: 'GARAGE-DEMO-10', normalizedValue: 'GARAGE-DEMO-10', anchor: 'garageIdentifier', page: 1, quote: 'Garage id GARAGE-DEMO-10' },
      { name: 'documentDate', state: 'EXTRACTED', value: '24 Sep 2026', normalizedValue: '2026-09-24', anchor: 'documentDate', page: 1, quote: 'Invoice date 24 Sep 2026' },
      { name: 'grandTotal', state: 'EXTRACTED', value: '62,510.00', normalizedValue: '62510.00', anchor: 'grandTotal', page: 1, quote: 'Grand total 62,510.00' },
      { name: 'lineItems[0]', state: 'EXTRACTED', value: 'Front bumper replacement 12,000.00', normalizedValue: '12000.00', anchor: 'i1', page: 1, quote: 'Front bumper replacement 12,000.00' },
      { name: 'lineItems[4]', state: 'EXTRACTED', value: 'Radiator support repair 9,259.50', normalizedValue: '9259.50', anchor: 'i5', page: 1, quote: 'Radiator support repair 9,259.50' },
      { name: 'lineItems[5]', state: 'EXTRACTED', value: 'Coolant hose and refill 2,350.00', normalizedValue: '2350.00', anchor: 'i6', page: 1, quote: 'Coolant hose and refill 2,350.00' },
      { name: 'lineItems[6]', state: 'EXTRACTED', value: 'Wheel alignment 5,900.00', normalizedValue: '5900.00', anchor: 'i7', page: 1, quote: 'Wheel alignment 5,900.00' },
      { name: 'incidentDate', state: 'UNKNOWN', unknownReason: 'NOT_PRESENT' },
    ],
  },
  {
    id: 'doc-photo',
    documentType: 'DAMAGE_PHOTOGRAPH',
    typeLabel: 'Damage photograph',
    fileName: 'front-left.jpg',
    status: 'RECEIVED',
    statusLabel: 'Received',
    note: 'Stored, but not read. The system reads only invoices and estimates today, so nothing from this photograph is used by any check.',
    fields: [],
  },
]

export const sampleRules: SampleRule[] = [
  {
    ruleId: 'REQUIRED_EVIDENCE',
    label: 'Required evidence',
    outcome: 'INCOMPLETE',
    reliability: 'RELIABLE',
    explanation: 'Two documents expected at this stage were not received: the claim form and the policy record. This says what could not be checked. It says nothing against the claim.',
    values: [
      { label: 'Stage', value: 'Final invoice received' },
      { label: 'Missing', value: 'Claim form, policy record' },
    ],
    evidence: [{ kind: 'MISSING', label: 'Claim form and policy record: not received' }],
  },
  {
    ruleId: 'INVOICE_ARITHMETIC',
    label: 'Invoice arithmetic',
    outcome: 'PASS',
    reliability: 'RELIABLE',
    explanation: 'The seven invoice lines add up to the printed grand total of INR 62,510.00. Recalculated in code, not by the model.',
    values: [
      { label: 'Sum of lines', value: '62,510.00' },
      { label: 'Printed grand total', value: '62,510.00' },
      { label: 'Difference', value: '0.00' },
      { label: 'Allowance', value: '1.00' },
    ],
    evidence: [{ kind: 'DOCUMENT_FIELD', documentId: 'doc-invoice', field: 'grandTotal' }],
  },
  {
    ruleId: 'ESTIMATE_INVOICE_VARIANCE',
    label: 'Estimate-to-invoice variance',
    outcome: 'FLAG',
    severity: 'REVIEW',
    reliability: 'RELIABLE',
    explanation:
      'The final invoice total of INR 62,510.00 is INR 17,509.50 (38.91%) above the garage estimate of INR 45,000.50. The check flags an increase of more than 10% and at least INR 1,000.00.',
    values: [
      { label: 'Invoice total', value: '62,510.00' },
      { label: 'Baseline used', value: 'Garage estimate document' },
      { label: 'Baseline amount', value: '45,000.50' },
      { label: 'Increase', value: '17,509.50 (38.91%)' },
      { label: 'Thresholds', value: 'more than 10% and at least 1,000.00' },
    ],
    evidence: [
      { kind: 'DOCUMENT_FIELD', documentId: 'doc-invoice', field: 'grandTotal' },
      { kind: 'DOCUMENT_FIELD', documentId: 'doc-estimate', field: 'grandTotal' },
      { kind: 'CLAIM_FIELD', field: 'financial.initialEstimateAmount', label: 'Initial estimate on the claim', value: 'INR 45,000.50' },
    ],
    considerations: [
      'Extra damage found after the estimate was made, or a revised estimate.',
      'A supplementary estimate or job card that was not uploaded.',
      'A scope change agreed with the insurer.',
    ],
  },
  {
    ruleId: 'DATE_CONSISTENCY',
    label: 'Date consistency',
    outcome: 'PASS',
    reliability: 'RELIABLE',
    explanation: 'The claim was reported one day after the incident, the incident falls inside the policy period, and the estimate and invoice are dated after the incident in the right order.',
    values: [
      { label: 'Incident', value: '10 Sep 2026' },
      { label: 'Reported', value: '11 Sep 2026' },
      { label: 'Estimate', value: '12 Sep 2026' },
      { label: 'Invoice', value: '24 Sep 2026' },
    ],
    evidence: [
      { kind: 'CLAIM_FIELD', field: 'incident.incidentAt', label: 'Incident date on the claim', value: '10 Sep 2026' },
      { kind: 'DOCUMENT_FIELD', documentId: 'doc-invoice', field: 'documentDate' },
      { kind: 'DOCUMENT_FIELD', documentId: 'doc-estimate', field: 'documentDate' },
    ],
  },
  {
    ruleId: 'DOCUMENT_FILE_DUPLICATE',
    label: 'Identical files elsewhere',
    outcome: 'PASS',
    reliability: 'RELIABLE',
    explanation: 'No file with identical bytes was found on another claim in the organization.',
    values: [{ label: 'Files compared', value: '3' }, { label: 'Identical elsewhere', value: '0' }],
    evidence: [],
  },
  {
    ruleId: 'INVOICE_IDENTITY_DUPLICATE',
    label: 'Repeated invoice number',
    outcome: 'PASS',
    reliability: 'RELIABLE',
    explanation: 'No other final invoice with the number INV-2026-0457 from the same issuer was found.',
    values: [{ label: 'Invoice number', value: 'INV-2026-0457' }, { label: 'Other invoices with it', value: '0' }],
    evidence: [{ kind: 'DOCUMENT_FIELD', documentId: 'doc-invoice', field: 'invoiceNumber' }],
  },
  {
    ruleId: 'REPEATED_LINE_ITEMS',
    label: 'Repeated line items',
    outcome: 'PASS',
    reliability: 'RELIABLE',
    explanation: 'None of the seven invoice lines repeats an earlier line, exactly or nearly.',
    values: [{ label: 'Lines compared', value: '7' }, { label: 'Repeats', value: '0' }],
    evidence: [],
  },
  {
    ruleId: 'HISTORICAL_RELATIONSHIP',
    label: 'Relationship to earlier claims',
    outcome: 'FLAG',
    severity: 'REVIEW',
    reliability: 'RELIABLE',
    explanation:
      'One earlier claim in the imported history has the same vehicle token and is dated 45 days before this incident. This records a relationship to look at. It is not a finding.',
    values: [
      { label: 'Earlier claim', value: 'CLM-SYN-REPEAT-B' },
      { label: 'Shared', value: 'Vehicle token (exact)' },
      { label: 'Time between', value: '45 days earlier' },
      { label: 'Outcome of earlier claim', value: 'Not recorded' },
    ],
    evidence: [{ kind: 'HISTORICAL_RECORD', sourceRecordId: 'HC-0231' }],
    considerations: [
      'A vehicle can have more than one genuine claim, including a second impact.',
      'The earlier damage may have been repaired in full before this incident.',
      'The history is only what was imported. It is not a complete record of the vehicle.',
    ],
  },
]

export const sampleHistory = {
  status: 'MATCHES_FOUND',
  dataAvailability: { historicalClaimRows: 232, garageHistoryRows: 57 },
  totalPriorClaims: 2,
  matches: [
    {
      externalClaimId: 'CLM-SYN-REPEAT-B',
      relation: 'SAME_VEHICLE',
      strength: 'EXACT',
      matchedFields: ['vehicleToken'],
      matchMethod: 'EXACT_TOKEN',
      timing: 'EARLIER',
      daysBetween: 45,
      claimType: 'Collision',
      claimStatus: 'CLOSED_PAID',
      garageId: 'GARAGE-SYN-03',
      damageAreaCodes: ['FRONT_BUMPER'],
      incidentAt: '27 Jul 2026',
      ambiguities: ['OUTCOME_NOT_RECORDED'],
      source: { importId: 'demo-import-1', rowNumber: 231, sourceRecordId: 'HC-0231' },
      explanation:
        'Another claim in the imported history has the same vehicle token and is dated 45 days before this one. The insurer’s status label CLOSED_PAID is shown as supplied and is not interpreted. No outcome is recorded, which means not known, never cleared.',
    },
    {
      externalClaimId: 'CLM-SYN-POLICY-C',
      relation: 'SAME_POLICY',
      strength: 'CONTEXT',
      matchedFields: ['policyToken'],
      matchMethod: 'EXACT_TOKEN',
      timing: 'EARLIER',
      daysBetween: 210,
      claimType: 'Other',
      claimStatus: 'CLOSED',
      garageId: 'GARAGE-SYN-07',
      damageAreaCodes: [],
      incidentAt: '12 Feb 2026',
      ambiguities: ['WEAK_TOKEN_ONLY', 'OUTCOME_NOT_RECORDED'],
      source: { importId: 'demo-import-1', rowNumber: 12, sourceRecordId: 'HC-0012' },
      explanation:
        'Another claim shares this claim’s policy token. A policy can cover several vehicles and a renewal usually changes its token, so this is context only. No outcome is recorded.',
    },
  ] satisfies HistoryMatch[],
  garage: {
    garageId: 'GARAGE-DEMO-10',
    importedHistoricalClaims: 13,
    caution:
      'A garage’s claim volume, or a garage shared between claims, is context only and never evidence of wrongdoing. It is shown so a person can interpret it.',
  },
}

export const sampleHypotheses: Hypothesis[] = [
  {
    id: 'H1',
    title: 'The repair was extended after the first estimate',
    stance: 'Explains the higher invoice without any concern about the claim.',
    supports: [
      { text: 'The invoice has three lines the estimate does not: radiator support, coolant hose and wheel alignment.', ref: { kind: 'DOCUMENT_FIELD', documentId: 'doc-invoice', field: 'lineItems[4]' } },
      { text: 'The four lines both documents share have identical amounts, so the original scope was billed as estimated.', ref: { kind: 'DOCUMENT_FIELD', documentId: 'doc-estimate', field: 'grandTotal' } },
      { text: 'The variance check lists extra damage found after dismantling as a common harmless explanation.', ref: { kind: 'RULE_RESULT', ruleId: 'ESTIMATE_INVOICE_VARIANCE' } },
    ],
    weakens: [
      { text: 'No supplementary estimate or job card was received to document the added work.', ref: { kind: 'MISSING', label: 'Supplementary estimate: not received' } },
      { text: 'Nothing in the supplied documents links a coolant hose or wheel alignment to a front-bumper impact.', ref: { kind: 'DOCUMENT_FIELD', documentId: 'doc-invoice', field: 'lineItems[6]' } },
    ],
    unknown: [
      { text: 'Whether a surveyor re-inspected the vehicle after the estimate.', ref: { kind: 'MISSING', label: 'Surveyor report: not received' } },
      { text: 'What the photograph shows. It is stored but not read.', ref: { kind: 'MISSING', label: 'Damage photograph: not read' } },
    ],
  },
  {
    id: 'H2',
    title: 'The earlier front-bumper claim and this one overlap',
    stance: 'A reason to compare the two repairs. It does not say either claim is wrong.',
    supports: [
      { text: 'Another claim has the same vehicle token and is dated 45 days earlier.', ref: { kind: 'HISTORICAL_RECORD', sourceRecordId: 'HC-0231' } },
      { text: 'That claim recorded FRONT_BUMPER damage, and this invoice replaces a front bumper again.', ref: { kind: 'DOCUMENT_FIELD', documentId: 'doc-invoice', field: 'lineItems[0]' } },
      { text: 'The two claims name different garages.', ref: { kind: 'HISTORICAL_RECORD', sourceRecordId: 'HC-0231' } },
    ],
    weakens: [
      { text: 'A second front impact within 45 days is physically possible, and the incident dates differ.', ref: { kind: 'RULE_RESULT', ruleId: 'DATE_CONSISTENCY' } },
      { text: 'The history match rests on a pseudonymous token. It is exact, but it says nothing about why the vehicle appears twice.', ref: { kind: 'RULE_RESULT', ruleId: 'HISTORICAL_RELATIONSHIP' } },
    ],
    unknown: [
      { text: 'Whether the earlier repair was carried out. The earlier claim has no recorded outcome.', ref: { kind: 'HISTORICAL_RECORD', sourceRecordId: 'HC-0231' } },
      { text: 'Whether the earlier and current damage are the same area. Only an area code is recorded for the earlier claim.', ref: { kind: 'MISSING', label: 'Earlier damage photographs: not in the history' } },
    ],
  },
]

export const reviewActions: Array<{ value: ReviewAction; label: string; meaning: string }> = [
  { value: 'REQUEST_MORE_EVIDENCE', label: 'Request more evidence', meaning: 'Ask for the supplementary estimate, job card or photographs, then run the checks again.' },
  { value: 'ESCALATE_FOR_FURTHER_INVESTIGATION', label: 'Escalate for further investigation', meaning: 'Pass the claim to the person or team that follows up in the field.' },
  { value: 'NO_FURTHER_CHECKS_NEEDED', label: 'No further checks needed', meaning: 'Record that the reviewer is satisfied with the checks as they stand.' },
  { value: 'DEFER_REVIEW', label: 'Defer review', meaning: 'Come back to it later, for example when the history is updated.' },
]

export const ruleOutcomeCounts = sampleRules.reduce<Record<string, number>>((acc, r) => {
  acc[r.outcome] = (acc[r.outcome] ?? 0) + 1
  return acc
}, {})
