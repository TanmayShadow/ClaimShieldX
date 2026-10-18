import { apiRequest } from './client'
import { claimDetailSchema, claimListSchema } from '@/features/claims/schemas'
import type { ClaimListParams } from '@/features/claims/listParams'

export function listClaims(params: ClaimListParams, signal?: AbortSignal) {
  return apiRequest('/claims', { query: { page: params.page, size: params.size, sort: params.sort, stage: params.stage }, schema: claimListSchema, signal })
}

export function getClaim(claimId: string, signal?: AbortSignal) {
  return apiRequest(`/claims/${encodeURIComponent(claimId)}`, { schema: claimDetailSchema, signal })
}

export function createClaim(body: unknown) {
  return apiRequest('/claims', { method: 'POST', body, schema: claimDetailSchema })
}
