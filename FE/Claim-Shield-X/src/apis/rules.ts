import { historyMatchesSchema } from '@/features/history/schemas'
import { ruleRunSchema } from '@/features/checks/schemas'
import { apiRequest } from './client'
import { isApiError } from './errors'

const claimPath = (claimId: string) => `/claims/${encodeURIComponent(claimId)}`

/** The newest rule run, or null while none exists (the server answers 404 until the first run). */
export async function getLatestRuleRun(claimId: string, signal?: AbortSignal) {
  try {
    return await apiRequest(`${claimPath(claimId)}/rule-runs/latest`, { schema: ruleRunSchema, signal })
  } catch (error) {
    if (isApiError(error) && error.status === 404) return null
    throw error
  }
}

/** Safe to repeat: identical inputs return the stored run (reusedExistingRun true) instead of storing another. */
export const runRules = (claimId: string) => apiRequest(`${claimPath(claimId)}/rule-runs`, { method: 'POST', schema: ruleRunSchema })

export const getHistoryMatches = (claimId: string, signal?: AbortSignal) => apiRequest(`${claimPath(claimId)}/history-matches`, { schema: historyMatchesSchema, signal })

