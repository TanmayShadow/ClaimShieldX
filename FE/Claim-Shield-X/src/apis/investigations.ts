import { apiRequest } from './client'
import { investigationPageSchema, investigationSchema } from '@/features/investigations/schemas'

const claimPath = (claimId: string) => `/claims/${encodeURIComponent(claimId)}`
const path = (id: string) => `/investigations/${encodeURIComponent(id)}`

export const listInvestigations = (claimId: string, signal?: AbortSignal) =>
  apiRequest(`${claimPath(claimId)}/investigations`, { query: { size: 20, sort: 'createdAt,desc' }, schema: investigationPageSchema, signal })

export const getInvestigation = (id: string, signal?: AbortSignal) => apiRequest(path(id), { schema: investigationSchema, signal })

/** Queues a run. The server answers 202 only after it is durably stored. The key makes a repeat of the same click safe. */
export const startInvestigation = (claimId: string, idempotencyKey: string, note?: string) =>
  apiRequest(`${claimPath(claimId)}/investigations`, {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: note ? { note } : undefined,
    schema: investigationSchema,
  })

export const retryInvestigation = (id: string, idempotencyKey: string) =>
  apiRequest(`${path(id)}/retry`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, schema: investigationSchema })

export const cancelInvestigation = (id: string, idempotencyKey: string) =>
  apiRequest(`${path(id)}/cancel`, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, schema: investigationSchema })
