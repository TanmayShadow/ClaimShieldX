import type { MarkState } from '@/components/ui/StatusMark'
import { pluralize } from '@/utils/format'

export type Readiness = { level: string; blockerCount: number; warningCount: number }

/**
 * Readiness describes the evidence, never the claim (api-contract 7.5). NOT_READY means the one document the stage cannot do without
 * is missing or failed; it does not say anything about the claim itself.
 */
export function readinessMark(readiness: Readiness): { state: MarkState; label: string; detail: string } {
  const { level, blockerCount, warningCount } = readiness
  const warnings = pluralize(warningCount, 'warning')
  if (level === 'NOT_READY') {
    return { state: 'blocked', label: 'Not ready', detail: `${pluralize(blockerCount, 'required document issue')}${warningCount > 0 ? `, ${warnings}` : ''}` }
  }
  if (level === 'READY_WITH_WARNINGS') return { state: 'attention', label: 'Ready with warnings', detail: warnings }
  if (level === 'READY') return { state: 'done', label: 'Ready', detail: 'No evidence issues' }
  return { state: 'unknown', label: 'Readiness not available', detail: '' }
}

export const readinessMeaning = {
  NOT_READY: 'The document this stage cannot do without is missing or failed, so the checks cannot start. This says nothing about the claim itself.',
  READY_WITH_WARNINGS: 'Checks can run, but some expected documents are missing or need review. Checks that depend on them will say they could not run.',
  READY: 'Every expected document has been received and read.',
} as const
