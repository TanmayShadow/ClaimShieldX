import type { MarkState } from '@/components/ui/StatusMark'
import { humanizeCode } from '@/features/checks/text'

const label = (map: Record<string, string>, value: string | null | undefined) => (value ? (map[value] ?? humanizeCode(value)) : 'Not recorded')

export const bandLabel = (band: string) => label({ LOW: 'Low', MEDIUM: 'Medium', HIGH: 'High' }, band)

export function bandMark(band: string): { state: MarkState; label: string } {
  return { state: band === 'LOW' ? 'info' : 'attention', label: `${bandLabel(band)} review priority` }
}

export const recommendedActionLabel = (value: string | null | undefined) =>
  label(
    {
      MANUAL_REVIEW: 'Review it by hand',
      REQUEST_MORE_EVIDENCE: 'Ask for more evidence',
      NO_ADDITIONAL_CHECK_INDICATED: 'The tool indicates no further check',
    },
    value,
  )

export const reviewActionLabels: Record<string, string> = {
  REQUEST_MORE_EVIDENCE: 'Request more evidence',
  ESCALATE_FOR_FURTHER_INVESTIGATION: 'Escalate for further investigation',
  NO_FURTHER_CHECKS_NEEDED: 'No further checks needed',
  DEFER_REVIEW: 'Defer the review',
}
export const reviewActionLabel = (value: string) => label(reviewActionLabels, value)

export const completenessLabel = (value: string) => label({ COMPLETE: 'Complete', PARTIAL: 'Partial' }, value)

export const unavailableSectionLabel = (value: string) =>
  label(
    {
      DOCUMENT_READING: 'A document could not be read.',
      SUSPICION_ANALYSIS: 'The step that proposes explanations could not answer.',
      COUNTER_EVIDENCE: 'The step that looks for legitimate explanations could not answer.',
      EVIDENCE_REVIEW: 'The independent review could not answer, so no explanation was published.',
    },
    value,
  )

export type FindingGroup = 'review' | 'noted' | 'gaps' | 'hypotheses' | 'other'

export function findingGroup(f: { type: string; status: string }): FindingGroup {
  if (f.type === 'FACT') return f.status === 'NEEDS_REVIEW' ? 'review' : 'noted'
  if (f.type === 'UNKNOWN') return 'gaps'
  if (f.type === 'HYPOTHESIS') return 'hypotheses'
  return 'other'
}

export const findingGroups: Array<{ id: FindingGroup; title: string; intro: string }> = [
  { id: 'review', title: 'To review', intro: 'Results of the deterministic checks that deserve a look against the documents. Each lists the harmless explanations to rule out first.' },
  { id: 'noted', title: 'Noted', intro: 'Recorded for context. Not material on their own.' },
  { id: 'gaps', title: 'Gaps in the evidence', intro: 'Something could not be checked. This is about the evidence, never a negative finding about the claim.' },
  { id: 'hypotheses', title: 'Hypotheses to verify', intro: 'A model proposed these, a second step looked for legitimate explanations, and an independent review found them supported. Each is something to verify. None is a conclusion.' },
  { id: 'other', title: 'Other', intro: '' },
]

export function findingMark(f: { type: string; status: string }): { state: MarkState; label: string } {
  switch (findingGroup(f)) {
    case 'review':
      return { state: 'attention', label: 'To review' }
    case 'noted':
      return { state: 'info', label: 'Noted' }
    case 'gaps':
      return { state: 'unknown', label: 'Could not be checked' }
    case 'hypotheses':
      return { state: 'info', label: 'Hypothesis to verify' }
    default:
      return { state: 'unknown', label: humanizeCode(f.type) }
  }
}

export const hypothesisReviewLabel = (status: string) =>
  label({ SUPPORTED: 'Independent review: supported by the evidence', PARTIALLY_SUPPORTED: 'Independent review: partly supported by the evidence' }, status)

export const auditEventLabels: Record<string, string> = {
  INVESTIGATION_CREATED: 'Investigation started',
  INVESTIGATION_RETRIED: 'Investigation retried',
  INVESTIGATION_CANCEL_REQUESTED: 'Cancel requested',
  INVESTIGATION_CANCELLED: 'Investigation cancelled',
  INVESTIGATION_COMPLETED: 'Investigation finished',
  INVESTIGATION_FAILED: 'Investigation failed',
  REPORT_GENERATED: 'Report written',
  REVIEW_ACTION_RECORDED: 'Review decision recorded',
}
export const auditEventLabel = (value: string) => label(auditEventLabels, value)

export const evidenceGroupOrder = ['CLAIM_FIELD', 'DOCUMENT', 'EXTRACTED_FIELD', 'RULE_RESULT', 'HISTORICAL_RECORD', 'RELATIONSHIP', 'CHECKLIST_ITEM', 'UNKNOWN']
export const evidenceGroupLabels: Record<string, string> = {
  CLAIM_FIELD: 'Claim record',
  DOCUMENT: 'Documents held',
  EXTRACTED_FIELD: 'Read from documents',
  RULE_RESULT: 'Rule results',
  HISTORICAL_RECORD: 'Earlier claims',
  RELATIONSHIP: 'Relationships',
  CHECKLIST_ITEM: 'Expected documents',
  UNKNOWN: 'Checks that could not run',
}

export function evidenceStateMark(state: string): { state: MarkState; label: string } {
  switch (state) {
    case 'VERIFIED_FACT':
      return { state: 'done', label: 'Recorded by the insurer' }
    case 'EXTRACTED':
      return { state: 'info', label: 'Read by a model, not verified' }
    case 'EXTRACTED_NEEDS_REVIEW':
      return { state: 'attention', label: 'Read by a model, needs review' }
    case 'DETERMINISTIC_RESULT':
      return { state: 'info', label: 'Result of a rule' }
    case 'CONTEXT':
      return { state: 'info', label: 'Context' }
    case 'UNKNOWN':
      return { state: 'unknown', label: 'Not known' }
    default:
      return { state: 'unknown', label: humanizeCode(state) }
  }
}

export const relationshipLabel = (value: string) => label({ SAME_VEHICLE: 'Same vehicle', SAME_POLICY: 'Same policy', SAME_CLAIMANT: 'Same claimant' }, value)
