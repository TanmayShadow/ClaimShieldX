import { z } from 'zod'

const text = z.string().nullish()

const matchSchema = z.object({
  externalClaimId: z.string(),
  claimVersion: z.number().nullish(),
  versionsMatched: z.number().nullish(),
  relation: z.string(),
  strength: z.string(),
  matchedFields: z.array(z.string()).nullish(),
  timing: text,
  daysBetween: z.number().nullish(),
  claimType: text,
  claimStatus: text,
  garageId: text,
  damageAreaCodes: z.array(z.string()).nullish(),
  incidentAt: text,
  ambiguities: z.array(z.string()).nullish(),
  explanation: z.string(),
})
export type HistoryMatch = z.infer<typeof matchSchema>

export const historyMatchesSchema = z.object({
  claimId: z.string(),
  status: z.string(),
  dataAvailability: z
    .object({
      historicalClaimRows: z.number().nullish(),
      garageHistoryRows: z.number().nullish(),
      latestClaimsImportFailed: z.boolean().nullish(),
    })
    .nullish(),
  totalPriorClaims: z.number().nullish(),
  truncated: z.boolean().nullish(),
  matches: z.array(matchSchema),
  garage: z.object({ garageId: text, importedHistoricalClaims: z.number().nullish(), caution: text }).nullish(),
  notes: z.array(z.string()).nullish(),
})
export type HistoryMatches = z.infer<typeof historyMatchesSchema>
