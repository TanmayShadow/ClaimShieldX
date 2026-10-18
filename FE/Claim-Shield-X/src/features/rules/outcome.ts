import type { MarkState } from '@/components/ui/StatusMark'

export type RuleOutcome = 'PASS' | 'FLAG' | 'INCOMPLETE' | 'NOT_APPLICABLE' | 'ERROR'
export type RuleSeverity = 'REVIEW' | 'INFO'

/**
 * How a deterministic rule result is shown. Wording follows the backend's own definitions (api-contract 7.7b):
 * INCOMPLETE is missing evidence, never a negative finding; a FLAG is something to check, never a verdict.
 */
export function ruleOutcomeMark(outcome: RuleOutcome, severity?: RuleSeverity): { state: MarkState; label: string } {
  switch (outcome) {
    case 'PASS':
      return { state: 'done', label: 'Pass' }
    case 'FLAG':
      return { state: 'attention', label: severity === 'INFO' ? 'Flag · information' : 'Flag · review' }
    case 'INCOMPLETE':
      return { state: 'unknown', label: 'Incomplete' }
    case 'NOT_APPLICABLE':
      return { state: 'skipped', label: 'Not applicable' }
    case 'ERROR':
      return { state: 'failed', label: 'Could not run' }
  }
}

export const ruleOutcomeMeaning: Record<RuleOutcome, string> = {
  PASS: 'Checked and consistent within the configured tolerance.',
  FLAG: 'A deviation beyond tolerance, or a relationship worth recording. Something to check, not a conclusion.',
  INCOMPLETE: 'Evidence the rule needs is missing or unreadable. This is never a negative finding.',
  NOT_APPLICABLE: 'The checklist for this stage and incident type does not expect the document the rule needs.',
  ERROR: 'The rule failed internally. No conclusion is drawn from it; the other rules still ran.',
}
