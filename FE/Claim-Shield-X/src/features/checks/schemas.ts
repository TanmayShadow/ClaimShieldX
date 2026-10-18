import { z } from 'zod'

const text = z.string().nullish()

export const evidenceRefSchema = z.looseObject({
  kind: z.string(),
  documentId: text,
  documentType: text,
  field: text,
  page: z.number().nullish(),
  quote: text,
  note: text,
})
export type EvidenceRef = z.infer<typeof evidenceRefSchema>

export const ruleResultSchema = z.object({
  id: z.string(),
  ruleId: z.string(),
  ruleVersion: text,
  label: z.string(),
  outcome: z.string(),
  severity: text,
  reliability: text,
  reliabilityReasons: z.array(z.string()).nullish(),
  explanation: z.string(),
  // The numbers, dates and ids the rule used. Shown generically as text; its shape differs per rule.
  values: z.record(z.string(), z.unknown()).nullish(),
  evidence: z.array(evidenceRefSchema).nullish(),
  considerations: z.array(z.string()).nullish(),
  executedAt: text,
})
export type RuleResult = z.infer<typeof ruleResultSchema>

export const ruleRunSchema = z.object({
  id: z.string(),
  claimId: z.string(),
  ruleSetVersion: z.string(),
  createdAt: z.string(),
  counts: z.object({ pass: z.number(), flag: z.number(), incomplete: z.number(), notApplicable: z.number(), error: z.number() }),
  inputFingerprint: text,
  reusedExistingRun: z.boolean().nullish(),
  results: z.array(ruleResultSchema),
})
export type RuleRun = z.infer<typeof ruleRunSchema>
