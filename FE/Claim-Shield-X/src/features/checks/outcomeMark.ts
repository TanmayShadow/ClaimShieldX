import { ruleOutcomeMark } from '@/features/rules/outcome'
import type { RuleOutcome, RuleSeverity } from '@/features/rules/outcome'
import type { RuleResult } from './schemas'

export const KNOWN: RuleOutcome[] = ['PASS', 'FLAG', 'INCOMPLETE', 'NOT_APPLICABLE', 'ERROR']

export function outcomeMark(result: Pick<RuleResult, 'outcome' | 'severity'>) {
  if (!KNOWN.includes(result.outcome as RuleOutcome)) return { state: 'unknown' as const, label: 'Not recognised' }
  return ruleOutcomeMark(result.outcome as RuleOutcome, (result.severity as RuleSeverity | null | undefined) ?? undefined)
}

