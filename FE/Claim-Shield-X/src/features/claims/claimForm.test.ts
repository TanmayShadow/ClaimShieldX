import { FIELD_ORDER, emptyClaimForm, firstInvalidField, localDateTimeToIso, localDateToIso, mapServerFieldErrors, validateClaimForm } from './claimForm'
import type { ClaimFormValues } from './claimForm'

const valid: ClaimFormValues = {
  ...emptyClaimForm,
  internalId: 'CLM-DEMO-1001',
  currentStage: 'FINAL_INVOICE_RECEIVED',
  'policy.policyToken': 'POL-TOKEN-1001',
  'garage.garageId': 'GARAGE-DEMO-10',
  'vehicle.vehicleToken': 'VEH-TOKEN-1001',
  'incident.incidentAt': '2026-09-10T10:30',
  'incident.incidentType': 'COLLISION',
  'financial.currency': 'INR',
}

const withValues = (patch: Partial<ClaimFormValues>): ClaimFormValues => ({ ...valid, ...patch })

function errorsFor(patch: Partial<ClaimFormValues>) {
  const result = validateClaimForm(withValues(patch))
  if (result.ok) throw new Error('expected validation to fail')
  return result.errors
}

describe('validateClaimForm: the minimum claim', () => {
  it('accepts the required fields alone and sends nothing optional', () => {
    const result = validateClaimForm(valid)
    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.body).toEqual({
      internalId: 'CLM-DEMO-1001',
      claimVersion: 1,
      currentStage: 'FINAL_INVOICE_RECEIVED',
      policy: { policyToken: 'POL-TOKEN-1001' },
      garage: { garageId: 'GARAGE-DEMO-10' },
      vehicle: { vehicleToken: 'VEH-TOKEN-1001' },
      incident: { incidentAt: new Date('2026-09-10T10:30').toISOString(), incidentType: 'COLLISION' },
      financial: { currency: 'INR' },
    })
  })

  it('sends the full claim when everything is filled in', () => {
    const result = validateClaimForm(
      withValues({
        'policy.policyType': 'COMPREHENSIVE',
        'policy.policyStartDate': '2026-01-01',
        'policy.policyEndDate': '2026-12-31',
        'claimant.claimantToken': 'CLT-TOKEN-1001',
        'vehicle.make': 'DEMO_MAKE',
        'vehicle.model': 'DEMO_MODEL',
        'incident.reportedAt': '2026-09-11T09:00',
        'incident.permittedLocationPrecision': 'CITY',
        'financial.initialEstimateAmount': '45000.50',
        'financial.approvedEstimateAmount': '40000',
        'financial.deductible': '1000',
      }),
    )
    expect(result.ok).toBe(true)
    if (!result.ok) return
    expect(result.body.claimant).toEqual({ claimantToken: 'CLT-TOKEN-1001' })
    expect(result.body.policy).toMatchObject({ policyType: 'COMPREHENSIVE', policyStartDate: new Date('2026-01-01T00:00').toISOString() })
    expect(result.body.financial).toEqual({ currency: 'INR', initialEstimateAmount: 45000.5, approvedEstimateAmount: 40000, deductible: 1000 })
    expect(result.body.incident.reportedAt).toBe(new Date('2026-09-11T09:00').toISOString())
  })

  it('never sends a field the server controls, and always sends version 1', () => {
    const result = validateClaimForm(valid)
    if (!result.ok) throw new Error('invalid')
    const text = JSON.stringify(result.body)
    for (const forbidden of ['organizationId', 'createdBy', 'createdAt', 'updatedAt', '"id"', 'isActive', 'status', 'reviewerId', 'score']) {
      expect(text, forbidden).not.toContain(forbidden)
    }
    expect(result.body.claimVersion).toBe(1)
  })

  it('trims surrounding whitespace, as the server does', () => {
    const result = validateClaimForm(withValues({ internalId: '  CLM-1  ', 'vehicle.vehicleToken': '\tVEH-1 ' }))
    if (!result.ok) throw new Error('invalid')
    expect(result.body.internalId).toBe('CLM-1')
    expect(result.body.vehicle.vehicleToken).toBe('VEH-1')
  })

  it('keeps inner characters of a token exactly, because matching is case-sensitive and exact', () => {
    const result = validateClaimForm(withValues({ 'vehicle.vehicleToken': 'veh Token-A1' }))
    if (!result.ok) throw new Error('invalid')
    expect(result.body.vehicle.vehicleToken).toBe('veh Token-A1')
  })

  it('treats blank optional text as absent rather than as an empty string', () => {
    const result = validateClaimForm(withValues({ 'policy.policyType': '   ', 'claimant.claimantToken': ' ' }))
    if (!result.ok) throw new Error('invalid')
    expect(result.body.policy).toEqual({ policyToken: 'POL-TOKEN-1001' })
    expect(result.body.claimant).toBeUndefined()
  })
})

describe('validateClaimForm: required fields and limits', () => {
  it.each([
    ['internalId', 'Enter the claim reference.'],
    ['currentStage', 'Choose the stage the claim is at.'],
    ['policy.policyToken', 'Enter the policy token.'],
    ['garage.garageId', 'Enter the garage id.'],
    ['vehicle.vehicleToken', 'Enter the vehicle token.'],
    ['incident.incidentAt', 'Enter when the incident happened.'],
    ['incident.incidentType', 'Choose the incident type.'],
  ] as const)('requires %s', (field, message) => {
    expect(errorsFor({ [field]: '' })[field]).toBe(message)
    expect(errorsFor({ [field]: '   ' })[field]).toBe(message)
  })

  it('reports every problem at once, in page order', () => {
    const result = validateClaimForm(emptyClaimForm)
    if (result.ok) throw new Error('expected failure')
    expect(Object.keys(result.errors).sort()).toEqual(
      ['currentStage', 'garage.garageId', 'incident.incidentAt', 'incident.incidentType', 'internalId', 'policy.policyToken', 'vehicle.vehicleToken'].sort(),
    )
    expect(firstInvalidField(result.errors)).toBe('internalId')
  })

  it.each([
    ['internalId', 255],
    ['policy.policyToken', 255],
    ['garage.garageId', 255],
    ['vehicle.vehicleToken', 255],
    ['claimant.claimantToken', 255],
    ['policy.policyType', 100],
    ['vehicle.make', 100],
    ['vehicle.model', 100],
    ['incident.permittedLocationPrecision', 100],
  ] as const)('limits %s to %i characters', (field, max) => {
    expect(validateClaimForm(withValues({ [field]: 'a'.repeat(max) })).ok).toBe(true)
    expect(errorsFor({ [field]: 'a'.repeat(max + 1) })[field]).toBe(`Use at most ${max} characters.`)
  })

  it('accepts only the four stages and three incident types the backend defines', () => {
    for (const stage of ['FNOL', 'ESTIMATE_RECEIVED', 'FINAL_INVOICE_RECEIVED', 'POST_SETTLEMENT']) expect(validateClaimForm(withValues({ currentStage: stage })).ok).toBe(true)
    for (const type of ['COLLISION', 'THEFT', 'OTHER']) expect(validateClaimForm(withValues({ 'incident.incidentType': type })).ok).toBe(true)
    expect(errorsFor({ currentStage: 'fnol' }).currentStage).toBeTruthy()
    expect(errorsFor({ currentStage: 'ARCHIVED' }).currentStage).toBeTruthy()
    expect(errorsFor({ 'incident.incidentType': 'FIRE' })['incident.incidentType']).toBeTruthy()
  })
})

describe('validateClaimForm: money', () => {
  it.each(['0', '0.1', '0.10', '1', '45000.50', '45000.5', '999999999999.99', '9999999999999.99', '9999999999999', ' 1000 '])('accepts %j', (text) => {
    expect(validateClaimForm(withValues({ 'financial.deductible': text })).ok).toBe(true)
  })

  it.each(['45000.505', '1.001', '-1', '+1', '1,000', '1,00,000.50', '₹100', 'Rs. 100', '1e3', '.5', '5.', '10000000000000', 'abc', '1 000', '0x10', 'NaN', 'Infinity', '--1'])(
    'rejects %j without rounding it',
    (text) => {
      expect(errorsFor({ 'financial.initialEstimateAmount': text })['financial.initialEstimateAmount']).toMatch(/at most two decimals/)
    },
  )

  it('sends amounts as numbers whose text is exactly what was typed, for the whole range the backend accepts', () => {
    const samples = ['0.10', '0.1', '45000.10', '999999999999.99', '9999999999999.99', '1234567890123.45', '0.01', '100.07', '8.35', '4.35', '1.15']
    for (const text of samples) {
      const result = validateClaimForm(withValues({ 'financial.deductible': text }))
      if (!result.ok) throw new Error(`rejected ${text}`)
      const sent = JSON.stringify(result.body.financial.deductible)
      const normalized = text.includes('.') ? text.replace(/0+$/, '').replace(/\.$/, '') : text
      expect(sent, text).toBe(normalized)
    }
  })

  it('requires a three-letter capital currency code', () => {
    expect(validateClaimForm(withValues({ 'financial.currency': 'USD' })).ok).toBe(true)
    for (const bad of ['', 'inr', 'IN', 'INRR', 'I1R', 'In R']) expect(errorsFor({ 'financial.currency': bad })['financial.currency']).toBeTruthy()
  })
})

describe('validateClaimForm: dates', () => {
  it('turns what the person typed (in their own zone) into the instant the server stores', () => {
    expect(localDateTimeToIso('2026-09-10T10:30')).toBe(new Date('2026-09-10T10:30').toISOString())
    expect(localDateToIso('2026-01-01')).toBe(new Date('2026-01-01T00:00').toISOString())
  })

  it('accepts a real 29 February and refuses an invented one', () => {
    expect(localDateTimeToIso('2028-02-29T12:00')).not.toBeNull()
    expect(localDateTimeToIso('2027-02-29T12:00')).toBeNull()
    expect(localDateToIso('2026-02-30')).toBeNull()
  })

  it('reads 10:30 as Asia/Kolkata in these tests', () => {
    expect(localDateTimeToIso('2026-09-10T10:30')).toBe('2026-09-10T05:00:00.000Z')
  })

  it.each(['', '2026-09-10', '2026-09-10 10:30', '10/09/2026 10:30', '2026-13-10T10:30', '2026-02-30T10:30', '2026-04-31T10:30', '2025-02-29T10:30', '2026-09-10T25:00', '2026-09-10T10:60', '0099-01-01T00:00', 'garbage'])('refuses %j as a date-time', (value) => {
    expect(localDateTimeToIso(value)).toBeNull()
  })

  it('does not reject a report before the incident, or a policy end before its start: the rules module reports those', () => {
    const result = validateClaimForm(
      withValues({ 'incident.reportedAt': '2026-09-01T09:00', 'policy.policyStartDate': '2026-12-31', 'policy.policyEndDate': '2026-01-01' }),
    )
    expect(result.ok).toBe(true)
  })

  it('names a malformed optional date', () => {
    expect(errorsFor({ 'incident.reportedAt': 'yesterday' })['incident.reportedAt']).toBe('Enter a real date and time.')
    expect(errorsFor({ 'policy.policyStartDate': '31/12/2026' })['policy.policyStartDate']).toBe('Enter a real date.')
  })
})

describe('mapServerFieldErrors', () => {
  it('puts each server error on the field it names, keeping the first per field', () => {
    const { fieldErrors, other } = mapServerFieldErrors([
      { field: 'policy.policyToken', message: 'Policy token cannot be blank' },
      { field: 'policy.policyToken', message: 'second message' },
      { field: 'incident.incidentType', message: 'Incident type is required' },
    ])
    expect(fieldErrors).toEqual({ 'policy.policyToken': 'Policy token cannot be blank', 'incident.incidentType': 'Incident type is required' })
    expect(other).toEqual([])
  })

  it('returns errors for fields this form does not have, instead of dropping them', () => {
    expect(mapServerFieldErrors([{ field: 'workflow.status', message: 'not allowed' }]).other).toEqual(['workflow.status: not allowed'])
  })

  it('covers every field path on the form', () => {
    expect(FIELD_ORDER).toHaveLength(19)
    expect(new Set(FIELD_ORDER).size).toBe(19)
  })
})
