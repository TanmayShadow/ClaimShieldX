import { z } from 'zod'
import { pageSchema } from '@/features/claims/schemas'

const text = z.string().nullish()
const refs = z.array(z.string()).nullish()

export const findingSchema = z.object({
  id: z.string(),
  ordinal: z.number().nullish(),
  source: z.string(),
  category: z.string(),
  title: z.string(),
  summary: z.string(),
  type: z.string(),
  status: z.string(),
  validationState: text,
  supportingEvidenceRefs: refs,
  counterEvidenceRefs: refs,
  counterEvidence: z.array(z.string()).nullish(),
  missingChecks: z.array(z.string()).nullish(),
  recommendation: text,
  rejectionReason: text,
})
export type Finding = z.infer<typeof findingSchema>

export const actionSchema = z.object({
  id: z.string(),
  createdAt: z.string(),
  action: z.string(),
  rationale: z.string(),
  createdBy: text,
  reportVersion: z.number(),
  reportWasStale: z.boolean().nullish(),
  recommendedAction: text,
  score: z.number().nullish(),
})
export type ReviewAction = z.infer<typeof actionSchema>
export const actionListSchema = z.array(actionSchema)

export const reportSchema = z.object({
  reportId: z.string(),
  reportVersion: z.number(),
  investigationId: z.string(),
  claimId: z.string(),
  snapshotId: text,
  createdAt: z.string(),
  reportStatus: z.string(),
  completeness: z.string(),
  unavailableSections: z.array(z.string()).nullish(),
  summary: z.string(),
  reviewPriority: z.object({
    score: z.number(),
    scale: text,
    band: z.string(),
    recommendedAction: z.string(),
    scoreVersion: text,
    components: z.array(z.object({ ruleId: z.string(), severity: text, points: z.number() })).nullish(),
  }),
  findings: z.array(findingSchema),
  evidenceCoverage: z.object({ itemCount: z.number(), citedByFindings: z.number().nullish(), byType: z.record(z.string(), z.number()).nullish() }).nullish(),
  latestAction: actionSchema.nullish(),
  notice: text,
})
export type Report = z.infer<typeof reportSchema>

export const auditPageSchema = pageSchema(z.object({ id: z.string(), createdAt: z.string(), eventType: z.string(), actorUserId: text, detail: z.record(z.string(), z.unknown()).nullish() }))

export const evidenceSchema = z.object({
  snapshotId: z.string(),
  investigationId: z.string(),
  createdAt: text,
  itemCount: z.number(),
  items: z.array(z.object({ ref: z.string(), type: z.string(), state: z.string(), label: z.string(), data: z.record(z.string(), z.unknown()).nullish() })),
})
export type Evidence = z.infer<typeof evidenceSchema>

export const graphSchema = z.object({
  snapshotId: text,
  nodes: z.array(z.object({ id: z.string(), type: z.string(), label: z.string() })),
  edges: z.array(z.object({ ref: z.string(), source: z.string(), target: z.string(), relationship: z.string(), method: text, fields: refs, evidenceRefs: refs, uncertainty: text })),
})
export type Graph = z.infer<typeof graphSchema>
