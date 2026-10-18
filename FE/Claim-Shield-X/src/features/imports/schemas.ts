import { z } from 'zod'
import { pageSchema } from '@/features/claims/schemas'

const text = z.string().nullish()

export const importSchema = z.object({
  id: z.string(),
  importType: z.string(),
  templateVersion: text,
  source: text,
  status: z.string(),
  failureCode: text,
  // The backend guarantees this never holds text from the file: it names missing template columns and counts the unknown ones.
  failureDetail: text,
  sha256: text,
  sizeBytes: z.number().nullish(),
  counts: z.object({ totalRows: z.number(), accepted: z.number(), rejected: z.number(), skippedDuplicate: z.number() }),
  createdAt: z.string(),
  startedAt: text,
  finishedAt: text,
})
export type HistoricalImport = z.infer<typeof importSchema>
export const importPageSchema = pageSchema(importSchema)

export const rowSchema = z.object({
  rowNumber: z.number(),
  status: z.string(),
  sourceRecordId: text,
  errors: z.array(z.object({ field: text, code: z.string(), message: text })).nullish(),
})
export type ImportRow = z.infer<typeof rowSchema>
export const rowPageSchema = pageSchema(rowSchema)
