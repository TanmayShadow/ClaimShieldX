/** Shapes mirror api-contract.md (rule results 7.7b, history matches 7.7a, extraction 7.6) so the real screens can reuse the components. */

export type SheetLine = { id: string; description: string; amount: string }
export type SheetTotal = { id: string; label: string; amount: string }

export type SampleField = {
  name: string
  state: 'EXTRACTED' | 'UNKNOWN'
  value?: string
  normalizedValue?: string
  unknownReason?: string
  /** What to mark on the document sheet when this field is chosen. */
  anchor?: string
  page?: number
  quote?: string
}

export type SampleDocument = {
  id: string
  documentType: string
  typeLabel: string
  fileName: string
  status: 'VERIFIED' | 'NEEDS_REVIEW' | 'RECEIVED'
  statusLabel: string
  note?: string
  sheet?: {
    title: string
    issuer: string
    meta: Array<{ label: string; value: string; anchor?: string }>
    lines: SheetLine[]
    totals: SheetTotal[]
  }
  fields: SampleField[]
}

export type EvidenceRef =
  | { kind: 'DOCUMENT_FIELD'; documentId: string; field: string }
  | { kind: 'RULE_RESULT'; ruleId: string }
  | { kind: 'HISTORICAL_RECORD'; sourceRecordId: string }
  | { kind: 'CLAIM_FIELD'; field: string; label: string; value: string }
  | { kind: 'MISSING'; label: string }

export type RuleOutcome = 'PASS' | 'FLAG' | 'INCOMPLETE' | 'NOT_APPLICABLE' | 'ERROR'

export type SampleRule = {
  ruleId: string
  label: string
  outcome: RuleOutcome
  severity?: 'REVIEW' | 'INFO'
  reliability: 'RELIABLE' | 'DOUBTFUL'
  explanation: string
  values: Array<{ label: string; value: string }>
  evidence: EvidenceRef[]
  considerations?: string[]
}

export type HistoryMatch = {
  externalClaimId: string
  relation: 'SAME_VEHICLE' | 'SAME_POLICY' | 'SAME_CLAIMANT'
  strength: 'EXACT' | 'CONTEXT'
  matchedFields: string[]
  matchMethod: 'EXACT_TOKEN'
  timing: 'EARLIER' | 'SAME_DAY' | 'LATER'
  daysBetween: number
  claimType: string
  claimStatus: string
  garageId: string
  damageAreaCodes: string[]
  incidentAt: string
  ambiguities: string[]
  source: { importId: string; rowNumber: number; sourceRecordId: string }
  explanation: string
}

export type Hypothesis = {
  id: string
  title: string
  stance: string
  supports: Array<{ text: string; ref: EvidenceRef }>
  weakens: Array<{ text: string; ref: EvidenceRef }>
  unknown: Array<{ text: string; ref: EvidenceRef }>
}

export type ReviewAction = 'REQUEST_MORE_EVIDENCE' | 'ESCALATE_FOR_FURTHER_INVESTIGATION' | 'NO_FURTHER_CHECKS_NEEDED' | 'DEFER_REVIEW'
