import { claimDetailSchema, claimListSchema } from './schemas'

const detail = {
  id: 'cc31d592-eab9-4245-be1f-680cd53f55f2',
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

describe('claimDetailSchema', () => {
  it('reads the contract example, including a null amount', () => {
    const parsed = claimDetailSchema.parse(detail)
    expect(parsed.financial.approvedEstimateAmount).toBeNull()
    expect(parsed.financial.initialEstimateAmount).toBe(45000.5)
  })

  it('tolerates every optional block and field being absent or null', () => {
    const minimal = {
      ...detail,
      policy: { policyToken: 'P' },
      claimant: null,
      vehicle: { vehicleToken: 'V' },
      incident: { incidentAt: '2026-09-10T05:00:00Z', incidentType: 'OTHER' },
      financial: { currency: 'INR' },
    }
    expect(claimDetailSchema.safeParse(minimal).success).toBe(true)
    const withoutClaimant: Record<string, unknown> = { ...minimal }
    delete withoutClaimant.claimant
    expect(claimDetailSchema.safeParse(withoutClaimant).success).toBe(true)
  })

  it('shows a value a newer backend adds rather than failing the page', () => {
    const parsed = claimDetailSchema.parse({ ...detail, currentStage: 'REOPENED', incident: { ...detail.incident, incidentType: 'FIRE' } })
    expect(parsed.currentStage).toBe('REOPENED')
    expect(parsed.incident.incidentType).toBe('FIRE')
  })

  it('ignores fields it does not know, such as a future latestInvestigation', () => {
    expect(claimDetailSchema.parse({ ...detail, latestInvestigation: { id: 'x' } })).not.toHaveProperty('latestInvestigation')
  })

  it.each([
    ['no id', { ...detail, id: undefined }],
    ['a numeric reference', { ...detail, internalId: 7 }],
    ['no policy', { ...detail, policy: undefined }],
    ['a text amount', { ...detail, financial: { ...detail.financial, deductible: '1000' } }],
    ['no garage', { ...detail, garage: null }],
  ])('rejects %s', (_label, body) => {
    expect(claimDetailSchema.safeParse(body).success).toBe(false)
  })
})

describe('claimListSchema', () => {
  const page = {
    items: [{ id: '1', internalId: 'CLM-1', currentStage: 'FNOL', createdAt: '2026-10-17T22:40:00.810245Z', readiness: { level: 'NOT_READY', blockerCount: 1, warningCount: 4 } }],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  }

  it('reads the contract page', () => {
    expect(claimListSchema.parse(page).items[0]?.readiness).toEqual({ level: 'NOT_READY', blockerCount: 1, warningCount: 4 })
  })

  it('reads an empty page', () => {
    expect(claimListSchema.safeParse({ ...page, items: [], totalElements: 0, totalPages: 0 }).success).toBe(true)
  })

  it.each([
    ['a negative page', { ...page, page: -1 }],
    ['a zero page size', { ...page, size: 0 }],
    ['items that are not a list', { ...page, items: 'x' }],
    ['an item without readiness', { ...page, items: [{ ...page.items[0], readiness: undefined }] }],
    ['a fractional count', { ...page, totalElements: 1.5 }],
  ])('rejects %s', (_label, body) => {
    expect(claimListSchema.safeParse(body).success).toBe(false)
  })
})
