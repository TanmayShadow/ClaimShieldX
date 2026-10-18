import { STAGE_ORDER } from '@/features/investigations/labels'
import type { Investigation } from '@/features/investigations/schemas'

type StagePatch = Partial<Investigation['stages'][number]>

export const T0 = '2026-10-18T09:10:00Z'

export function stage(id: string, status: string, patch: StagePatch = {}): Investigation['stages'][number] {
  return { stage: id, status, attempts: status === 'PENDING' ? 0 : 1, startedAt: status === 'PENDING' ? null : T0, finishedAt: ['OK', 'DEGRADED', 'FAILED'].includes(status) ? '2026-10-18T09:11:00Z' : null, errorCode: null, summary: {}, ...patch }
}

/** An investigation as the contract's example shows it. `done` is how many of the first three real stages have finished. */
export function investigation(patch: Partial<Investigation> & { done?: number } = {}): Investigation {
  const { done = 0, ...rest } = patch
  const real = ['PREPARING_EVIDENCE', 'EXTRACTING', 'RUNNING_RULES']
  const stages = [
    ...real.map((id, i) => (i < done ? stage(id, 'OK') : stage(id, i === done && rest.status && !['COMPLETED', 'QUEUED', 'WAITING_FOR_EVIDENCE', 'CANCELLED'].includes(rest.status) ? 'RUNNING' : 'PENDING'))),
    // The model-assisted stages are real now: all finished once the investigation is, running when it is the current one, otherwise waiting.
    ...['BUILDING_HYPOTHESES', 'VALIDATING_FINDINGS', 'GENERATING_REPORT'].map((id) => stage(id, rest.status === 'COMPLETED' ? 'OK' : rest.status === id ? 'RUNNING' : 'PENDING')),
  ]
  return {
    id: 'aaaaaaaa-0000-4000-8000-000000000001',
    claimId: 'cc31d592-eab9-4245-be1f-680cd53f55f2',
    claimVersion: 1,
    status: 'QUEUED',
    attempt: 1,
    maxAttempts: 3,
    nextRetryAt: null,
    createdAt: T0,
    startedAt: null,
    updatedAt: T0,
    finishedAt: null,
    cancelRequested: false,
    note: null,
    ruleRunId: null,
    snapshotId: null,
    reportId: null,
    reportStatus: null,
    stages,
    waitingFor: null,
    error: null,
    ...rest,
  }
}

export const withStages = (inv: Investigation, byId: Record<string, ReturnType<typeof stage>>): Investigation => ({
  ...inv,
  stages: inv.stages.map((s) => byId[s.stage] ?? s),
})

export const investigationPage = (items: Investigation[]) => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 })

export const allStageIds = STAGE_ORDER
