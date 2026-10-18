import { z } from 'zod'

// Optional fields come back as null when absent, so they are nullish. Enums are plain strings here on purpose:
// a value a newer backend adds must display, not make the whole page fail as malformed.
const optionalText = z.string().nullish()
const optionalAmount = z.number().nullish()

export const readinessSchema = z.object({
  level: z.string(),
  blockerCount: z.number().int().nonnegative(),
  warningCount: z.number().int().nonnegative(),
})

export const claimSummarySchema = z.object({
  id: z.string(),
  internalId: z.string(),
  currentStage: z.string(),
  createdAt: z.string(),
  readiness: readinessSchema,
})
export type ClaimSummary = z.infer<typeof claimSummarySchema>

export function pageSchema<T extends z.ZodType>(item: T) {
  return z.object({
    items: z.array(item),
    page: z.number().int().nonnegative(),
    size: z.number().int().positive(),
    totalElements: z.number().int().nonnegative(),
    totalPages: z.number().int().nonnegative(),
  })
}

export const claimListSchema = pageSchema(claimSummarySchema)
export type ClaimList = z.infer<typeof claimListSchema>

export const claimDetailSchema = z.object({
  id: z.string(),
  internalId: z.string(),
  claimVersion: z.number().int(),
  currentStage: z.string(),
  policy: z.object({
    policyToken: z.string(),
    policyType: optionalText,
    policyStartDate: optionalText,
    policyEndDate: optionalText,
  }),
  claimant: z.object({ claimantToken: optionalText }).nullish(),
  garage: z.object({ garageId: z.string() }),
  vehicle: z.object({ vehicleToken: z.string(), make: optionalText, model: optionalText }),
  incident: z.object({
    incidentAt: z.string(),
    reportedAt: optionalText,
    incidentType: z.string(),
    permittedLocationPrecision: optionalText,
  }),
  financial: z.object({
    initialEstimateAmount: optionalAmount,
    approvedEstimateAmount: optionalAmount,
    currency: z.string(),
    deductible: optionalAmount,
  }),
  createdAt: z.string(),
  updatedAt: z.string(),
})
export type ClaimDetail = z.infer<typeof claimDetailSchema>
