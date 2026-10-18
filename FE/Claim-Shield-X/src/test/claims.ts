import type { ClaimSummary } from '@/features/claims/schemas'

let n = 0

export function summary(patch: Partial<ClaimSummary> & { level?: string; blockers?: number; warnings?: number } = {}): ClaimSummary {
  n++
  const { level = 'NOT_READY', blockers = 1, warnings = 4, ...rest } = patch
  return {
    id: `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`,
    internalId: `CLM-TEST-${String(n).padStart(4, '0')}`,
    currentStage: 'FINAL_INVOICE_RECEIVED',
    createdAt: '2026-10-17T22:40:00.810245Z',
    readiness: { level, blockerCount: blockers, warningCount: warnings },
    ...rest,
  }
}

export function page(items: ClaimSummary[], extra: Partial<{ page: number; size: number; totalElements: number; totalPages: number }> = {}) {
  const size = extra.size ?? 20
  const totalElements = extra.totalElements ?? items.length
  return { items, page: extra.page ?? 0, size, totalElements, totalPages: extra.totalPages ?? Math.ceil(totalElements / size) }
}

export const detail = {
  id: '11111111-2222-4333-8444-555555555555',
  internalId: 'CLM-DEMO-1001',
  claimVersion: 1,
  currentStage: 'FINAL_INVOICE_RECEIVED',
  policy: { policyToken: 'POL-TOKEN-1001', policyType: 'COMPREHENSIVE', policyStartDate: '2025-12-31T18:30:00Z', policyEndDate: '2026-12-30T18:30:00Z' },
  claimant: { claimantToken: 'CLT-TOKEN-1001' },
  garage: { garageId: 'GARAGE-DEMO-10' },
  vehicle: { vehicleToken: 'VEH-TOKEN-1001', make: 'DEMO_MAKE', model: 'DEMO_MODEL' },
  incident: { incidentAt: '2026-09-10T05:00:00Z', reportedAt: '2026-09-11T03:30:00Z', incidentType: 'COLLISION', permittedLocationPrecision: 'CITY' },
  financial: { initialEstimateAmount: 45000.5, approvedEstimateAmount: null, currency: 'INR', deductible: 1000 },
  createdAt: '2026-10-17T13:41:11.717576Z',
  updatedAt: '2026-10-17T13:41:11.717603Z',
}
