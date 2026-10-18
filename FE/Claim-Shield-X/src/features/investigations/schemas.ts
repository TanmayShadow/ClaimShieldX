import { z } from 'zod'
import { pageSchema } from '@/features/claims/schemas'

const text = z.string().nullish()

const stageSchema = z.object({
  stage: z.string(),
  status: z.string(),
  attempts: z.number().int().nullish(),
  startedAt: text,
  finishedAt: text,
  errorCode: text,
  // Counts and ids only, by contract: never anything read from a document. Its inner shape is read defensively where shown.
  summary: z.record(z.string(), z.unknown()).nullish(),
})
export type StageDto = z.infer<typeof stageSchema>

export const investigationSchema = z.object({
  id: z.string(),
  claimId: z.string(),
  claimVersion: z.number().int(),
  status: z.string(),
  attempt: z.number().int().nullish(),
  maxAttempts: z.number().int().nullish(),
  nextRetryAt: text,
  createdAt: z.string(),
  startedAt: text,
  updatedAt: text,
  finishedAt: text,
  cancelRequested: z.boolean().nullish(),
  note: text,
  ruleRunId: text,
  snapshotId: text,
  reportId: text,
  reportStatus: text,
  stages: z.array(stageSchema),
  waitingFor: z.array(z.object({ documentType: z.string(), code: z.string() })).nullish(),
  error: z.object({ code: z.string(), message: z.string().nullish() }).nullish(),
})
export type Investigation = z.infer<typeof investigationSchema>

export const investigationPageSchema = pageSchema(investigationSchema)

// What the stage summaries are known to carry (api-contract 7.8), read tolerantly: anything else is simply not shown.
export const preparingSummarySchema = z.object({
  readiness: z.string().optional(),
  blockers: z.union([z.number(), z.array(z.unknown())]).optional(),
  warnings: z.union([z.number(), z.array(z.unknown())]).optional(),
  documents: z.record(z.string(), z.number()).optional(),
})

export const extractingSummarySchema = z.object({
  read: z.number().optional(),
  alreadyRead: z.number().optional(),
  needsReview: z.number().optional(),
  failed: z.number().optional(),
  willRetry: z.number().optional(),
  busy: z.number().optional(),
  documents: z
    .array(z.object({ documentId: z.string(), documentType: z.string(), outcome: z.string(), code: z.string().nullish() }))
    .optional(),
})

// The rule counts arrive flat beside the run id, exactly as the backend writes them.
export const rulesSummarySchema = z.object({
  ruleRunId: z.string().nullish(),
  reusedExistingRun: z.boolean().optional(),
  pass: z.number().optional(),
  flag: z.number().optional(),
  incomplete: z.number().optional(),
  notApplicable: z.number().optional(),
  error: z.number().optional(),
})

// The three model-assisted stages. Counts, codes and ids only, by contract.
export const hypothesesSummarySchema = z.looseObject({
  hypotheses: z.number().nullish(),
  h1Rejected: z.number().nullish(),
  h1: z.string().nullish(),
  h2: z.string().nullish(),
  h3: z.string().nullish(),
  reason: z.string().nullish(),
})
export const validatingSummarySchema = z.looseObject({ findings: z.number().nullish(), published: z.number().nullish(), rejectedHypotheses: z.number().nullish(), h3: z.string().nullish() })
export const reportSummarySchema = z.looseObject({ band: z.string().nullish(), score: z.number().nullish(), completeness: z.string().nullish(), recommendedAction: z.string().nullish(), reportId: z.string().nullish() })
