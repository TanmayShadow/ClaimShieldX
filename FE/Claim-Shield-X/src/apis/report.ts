import { actionListSchema, actionSchema, auditPageSchema, evidenceSchema, graphSchema, reportSchema } from '@/features/report/schemas'
import { apiRequest } from './client'
import { isApiError } from './errors'

const invPath = (id: string) => `/investigations/${encodeURIComponent(id)}`

/** The claim's newest report, or null while none exists (404 until an investigation has finished). */
export async function getLatestReport(claimId: string, signal?: AbortSignal) {
  try {
    return await apiRequest(`/claims/${encodeURIComponent(claimId)}/reports/latest`, { schema: reportSchema, signal })
  } catch (error) {
    if (isApiError(error) && error.status === 404) return null
    throw error
  }
}

export const getEvidence = (investigationId: string, signal?: AbortSignal) => apiRequest(`${invPath(investigationId)}/evidence`, { schema: evidenceSchema, signal })
export const getGraph = (investigationId: string, signal?: AbortSignal) => apiRequest(`${invPath(investigationId)}/graph`, { schema: graphSchema, signal })
export const listActions = (investigationId: string, signal?: AbortSignal) => apiRequest(`${invPath(investigationId)}/actions`, { schema: actionListSchema, signal })
export const getAudit = (investigationId: string, page: number, signal?: AbortSignal) => apiRequest(`${invPath(investigationId)}/audit`, { query: { page, size: 20 }, schema: auditPageSchema, signal })

/** A decision is permanent. The key makes a repeat of the same click safe after a dropped connection. */
export const recordAction = (investigationId: string, idempotencyKey: string, body: { action: string; rationale: string; reportVersion: number }) =>
  apiRequest(`${invPath(investigationId)}/actions`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body, schema: actionSchema })
