import { z } from 'zod'
import { pageSchema } from '@/features/claims/schemas'

const text = z.string().nullish()

export const documentSchema = z.object({
  id: z.string(),
  claimId: z.string(),
  documentType: z.string(),
  fileName: z.string(),
  contentType: z.string(),
  sizeBytes: z.number().int().nonnegative(),
  sha256: z.string(),
  status: z.string(),
  externalDocumentId: text,
  supersedesDocumentId: text,
  superseded: z.boolean(),
  receivedAt: z.string(),
})
export type DocumentDto = z.infer<typeof documentSchema>

export const documentPageSchema = pageSchema(documentSchema)

const issueSchema = z.object({
  code: z.string(),
  documentType: text,
  requirement: text,
  message: z.string(),
  blocks: z.array(z.string()).default([]),
})
export type RequirementIssue = z.infer<typeof issueSchema>

export const requirementsSchema = z.object({
  claimId: z.string(),
  stage: z.string(),
  incidentType: text,
  readiness: z.string(),
  items: z.array(
    z.object({
      documentType: z.string(),
      requirement: z.string(),
      mandatory: z.boolean(),
      evidenceState: z.string(),
      documentIds: z.array(z.string()),
      blocks: z.array(z.string()),
    }),
  ),
  blockers: z.array(issueSchema),
  warnings: z.array(issueSchema),
})
export type Requirements = z.infer<typeof requirementsSchema>
export type RequirementItem = Requirements['items'][number]

export const extractionStartedSchema = z.object({
  documentId: z.string(),
  documentStatus: z.string(),
  extractionId: z.string(),
  attempt: z.number().int(),
})

const extractionIssue = z.object({ code: z.string(), severity: z.string(), field: text, message: z.string() })

// Field-level values are F9's concern. Here only what the document list needs to say what happened.
export const extractionSchema = z.object({
  documentId: z.string(),
  documentType: z.string(),
  extractionId: z.string(),
  attempt: z.number().int(),
  status: z.string(),
  documentStatus: z.string(),
  schemaVersion: text,
  modelId: text,
  pageCount: z.number().int().nullish(),
  createdAt: z.string(),
  fields: z.array(z.object({ name: z.string(), state: z.string() })).default([]),
  issues: z.array(extractionIssue).default([]),
  failureCode: text,
  retryable: z.boolean().nullish(),
})
export type ExtractionSummary = z.infer<typeof extractionSchema>
