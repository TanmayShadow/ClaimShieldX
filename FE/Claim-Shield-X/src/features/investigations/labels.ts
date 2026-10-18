import type { MarkState } from '@/components/ui/StatusMark'

export const STAGE_ORDER = ['PREPARING_EVIDENCE', 'EXTRACTING', 'RUNNING_RULES', 'BUILDING_HYPOTHESES', 'VALIDATING_FINDINGS', 'GENERATING_REPORT'] as const
export type StageId = (typeof STAGE_ORDER)[number]

export const stageLabels: Record<StageId, string> = {
  PREPARING_EVIDENCE: 'Evidence prepared',
  EXTRACTING: 'Documents read',
  RUNNING_RULES: 'Checks run',
  BUILDING_HYPOTHESES: 'Explanations',
  VALIDATING_FINDINGS: 'Evidence validated',
  GENERATING_REPORT: 'Report assembled',
}

/** Plain-language purpose of each stage, shown in the inspector. */
export const stagePurpose: Record<StageId, string> = {
  PREPARING_EVIDENCE: 'Looks at what the claim holds against what its stage expects. If the one document the stage cannot do without is missing, the investigation waits for it.',
  EXTRACTING: 'Reads the invoices and estimates that have not been read yet. A document already read is never read again.',
  RUNNING_RULES: 'Runs the deterministic checks on figures, dates and repeats, and looks up earlier claims in the supplied history.',
  BUILDING_HYPOTHESES: 'Asks a model for up to four possible explanations of what the checks flagged, then for the legitimate explanations and counter-evidence. It is not asked at all when nothing was flagged.',
  VALIDATING_FINDINGS: 'Checks in code that every citation points at real evidence, has an independent review judge each explanation, and publishes only what holds up.',
  GENERATING_REPORT: 'Computes the review-priority score from the rule results and writes the report.',
}

export function stageLabel(stage: string): string {
  return (stageLabels as Record<string, string>)[stage] ?? stage
}

export const STAGE_STATUSES = ['PENDING', 'RUNNING', 'OK', 'DEGRADED', 'FAILED', 'SKIPPED'] as const

/**
 * A stage's state in words and shape. SKIPPED with reason NOT_AVAILABLE_YET means the backend has not built that stage:
 * it must never read as "checked and skipped", so it has its own wording.
 */
export function stageMark(status: string, reason?: string): { state: MarkState; label: string } {
  switch (status) {
    case 'PENDING':
      return { state: 'pending', label: 'Waiting its turn' }
    case 'RUNNING':
      return { state: 'active', label: 'Running now' }
    case 'OK':
      return { state: 'done', label: 'Finished' }
    case 'DEGRADED':
      return { state: 'attention', label: 'Finished, partly' }
    case 'FAILED':
      return { state: 'failed', label: 'Failed' }
    case 'SKIPPED':
      if (reason === 'NOT_AVAILABLE_YET') return { state: 'unknown', label: 'Not available yet' }
      if (reason === 'NO_CONCERNS_TO_ANALYZE') return { state: 'skipped', label: 'Skipped: nothing flagged' }
      return { state: 'skipped', label: 'Nothing to do' }
    default:
      return { state: 'unknown', label: status }
  }
}

/**
 * Stages the backend has not built. All six are built now (B10 to B12 shipped), so this is empty. If a future stage is added that the
 * backend leaves PENDING until it exists, list it here so it never reads as "waiting its turn".
 */
export const UNBUILT_STAGES: readonly string[] = []

/** A stage's mark in the context of the whole investigation. */
export function stageDisplay(stageId: string, stage: { status: string; summary?: Record<string, unknown> | null } | undefined, investigationStatus: string): { state: MarkState; label: string } {
  const status = stage?.status ?? 'PENDING'
  const reason = typeof stage?.summary?.reason === 'string' ? stage.summary.reason : undefined
  if (stageId === 'PREPARING_EVIDENCE' && investigationStatus === 'WAITING_FOR_EVIDENCE' && (status === 'PENDING' || status === 'RUNNING')) {
    return { state: 'pending', label: 'Waiting for a document' }
  }
  if (UNBUILT_STAGES.includes(stageId) && status === 'PENDING') return { state: 'unknown', label: 'Not available yet' }
  return stageMark(status, reason)
}

export const isUnbuilt = (stageId: string, stage: { status: string; summary?: Record<string, unknown> | null } | undefined) =>
  UNBUILT_STAGES.includes(stageId) && (stage?.status === 'PENDING' || (stage?.status === 'SKIPPED' && stage.summary?.reason === 'NOT_AVAILABLE_YET'))

export const TERMINAL_STATUSES = ['COMPLETED', 'FAILED', 'CANCELLED'] as const
export const WAITING_STATUSES = ['WAITING_FOR_EVIDENCE', 'RETRY_WAIT'] as const

export const isTerminal = (status: string) => (TERMINAL_STATUSES as readonly string[]).includes(status)
export const isWaiting = (status: string) => (WAITING_STATUSES as readonly string[]).includes(status)
export const isActive = (status: string) => !isTerminal(status)

export function investigationStatusMark(status: string, cancelRequested?: boolean | null): { state: MarkState; label: string } {
  if (cancelRequested && !isTerminal(status)) return { state: 'attention', label: 'Stopping at the next safe point' }
  switch (status) {
    case 'QUEUED':
      return { state: 'pending', label: 'Queued' }
    case 'WAITING_FOR_EVIDENCE':
      return { state: 'pending', label: 'Waiting for a document' }
    case 'RETRY_WAIT':
      return { state: 'attention', label: 'Waiting to try again' }
    case 'COMPLETED':
      return { state: 'done', label: 'Finished' }
    case 'FAILED':
      return { state: 'failed', label: 'Failed' }
    case 'CANCELLED':
      return { state: 'skipped', label: 'Cancelled' }
    default:
      return isActive(status) ? { state: 'active', label: `Running: ${stageLabel(status).toLowerCase()}` } : { state: 'unknown', label: status }
  }
}

/** Why an investigation failed or is waiting to retry, in words. Codes are the backend's (api-contract 7.8). Never server text. */
const errorText: Record<string, string> = {
  EXTRACTION_FAILED: 'A document could not be read.',
  MODEL_UNAVAILABLE: 'The reading service was unavailable.',
  MODEL_QUOTA_EXHAUSTED: 'The reading service reached its usage limit.',
  INVALID_MODEL_OUTPUT: 'The reading service gave an answer that could not be used.',
  STORAGE_UNAVAILABLE: 'The stored files could not be reached.',
  TIMEOUT: 'The investigation was abandoned after its workers stopped too many times.',
  INTERNAL_ERROR: 'The service had a problem.',
  REPORT_FAILED: 'The report could not be written.',
}

export function investigationErrorMessage(code: string | null | undefined): string {
  return (code && errorText[code]) || 'The investigation could not finish.'
}

export const extractionOutcomeLabels: Record<string, string> = {
  READ: 'Read now',
  ALREADY_READ: 'Already read',
  NEEDS_REVIEW: 'Read, needs review',
  FAILED_FOR_GOOD: 'Could not be read',
  FAILED_LATER_MAY_PASS: 'Could not be read this time',
}
