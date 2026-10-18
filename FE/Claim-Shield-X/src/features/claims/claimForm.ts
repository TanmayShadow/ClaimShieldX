import type { FieldError } from '@/apis/errors'
import { CLAIM_STAGES, INCIDENT_TYPES } from './labels'

/**
 * The claim-creation form as data. Every rule below is the backend's own (api-contract 7.3), checked here only so a person
 * hears about a mistake before the round trip; the server remains the authority and its answers are shown too.
 * Field paths are the API's own paths, so a server error maps straight onto a field.
 */
export type FieldPath =
  | 'internalId'
  | 'currentStage'
  | 'policy.policyToken'
  | 'policy.policyType'
  | 'policy.policyStartDate'
  | 'policy.policyEndDate'
  | 'claimant.claimantToken'
  | 'garage.garageId'
  | 'vehicle.vehicleToken'
  | 'vehicle.make'
  | 'vehicle.model'
  | 'incident.incidentAt'
  | 'incident.reportedAt'
  | 'incident.incidentType'
  | 'incident.permittedLocationPrecision'
  | 'financial.currency'
  | 'financial.initialEstimateAmount'
  | 'financial.approvedEstimateAmount'
  | 'financial.deductible'

/** Order on the page, used to focus the first problem. */
export const FIELD_ORDER: FieldPath[] = [
  'internalId',
  'currentStage',
  'policy.policyToken',
  'policy.policyType',
  'policy.policyStartDate',
  'policy.policyEndDate',
  'claimant.claimantToken',
  'garage.garageId',
  'vehicle.vehicleToken',
  'vehicle.make',
  'vehicle.model',
  'incident.incidentAt',
  'incident.reportedAt',
  'incident.incidentType',
  'incident.permittedLocationPrecision',
  'financial.currency',
  'financial.initialEstimateAmount',
  'financial.approvedEstimateAmount',
  'financial.deductible',
]

export type ClaimFormValues = Record<FieldPath, string>

export const emptyClaimForm: ClaimFormValues = Object.fromEntries(FIELD_ORDER.map((f) => [f, ''])) as ClaimFormValues
emptyClaimForm['financial.currency'] = 'INR'

export type FieldErrors = Partial<Record<FieldPath, string>>

export const FIELD_LABELS: Record<FieldPath, string> = {
  internalId: 'Claim reference',
  currentStage: 'Claim stage',
  'policy.policyToken': 'Policy token',
  'policy.policyType': 'Policy type',
  'policy.policyStartDate': 'Policy start date',
  'policy.policyEndDate': 'Policy end date',
  'claimant.claimantToken': 'Claimant token',
  'garage.garageId': 'Garage id',
  'vehicle.vehicleToken': 'Vehicle token',
  'vehicle.make': 'Vehicle make',
  'vehicle.model': 'Vehicle model',
  'incident.incidentAt': 'Incident date and time',
  'incident.reportedAt': 'Reported date and time',
  'incident.incidentType': 'Incident type',
  'incident.permittedLocationPrecision': 'Location precision',
  'financial.currency': 'Currency',
  'financial.initialEstimateAmount': 'Initial estimate',
  'financial.approvedEstimateAmount': 'Approved estimate',
  'financial.deductible': 'Deductible',
}

const MAX_TOKEN = 255
const MAX_SHORT = 100
const AMOUNT = /^\d{1,13}(\.\d{1,2})?$/

/**
 * A date-time typed in the browser's own zone, as the instant the server stores. Returns null when it is not a real moment.
 * JavaScript quietly rolls 30 February over to 2 March and reads years 0 to 99 as 1900 to 1999, so the parts are checked by reading them back.
 */
export function localDateTimeToIso(value: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value)
  if (!match) return null
  const [year, month, day, hour, minute] = match.slice(1).map(Number) as [number, number, number, number, number]
  const date = new Date(year, month - 1, day, hour, minute)
  const exact = date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day && date.getHours() === hour && date.getMinutes() === minute
  return exact ? date.toISOString() : null
}

/** A calendar date as the start of that day in the browser's zone. */
export function localDateToIso(value: string): string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  return localDateTimeToIso(`${value}T00:00`)
}

export type ClaimRequest = {
  internalId: string
  claimVersion: 1
  currentStage: string
  policy: { policyToken: string; policyType?: string; policyStartDate?: string; policyEndDate?: string }
  claimant?: { claimantToken: string }
  garage: { garageId: string }
  vehicle: { vehicleToken: string; make?: string; model?: string }
  incident: { incidentAt: string; reportedAt?: string; incidentType: string; permittedLocationPrecision?: string }
  financial: { currency: string; initialEstimateAmount?: number; approvedEstimateAmount?: number; deductible?: number }
}

const trimmed = (v: string) => v.trim()
const some = (v: string) => (trimmed(v) === '' ? undefined : trimmed(v))

export function validateClaimForm(values: ClaimFormValues): { ok: true; body: ClaimRequest } | { ok: false; errors: FieldErrors } {
  const errors: FieldErrors = {}
  const v = (key: FieldPath) => trimmed(values[key])

  const required = (key: FieldPath, message: string, max: number) => {
    if (v(key) === '') errors[key] = message
    else if (v(key).length > max) errors[key] = `Use at most ${max} characters.`
  }
  const optionalText = (key: FieldPath, max: number) => {
    if (v(key).length > max) errors[key] = `Use at most ${max} characters.`
  }

  required('internalId', 'Enter the claim reference.', MAX_TOKEN)
  if (!(CLAIM_STAGES as readonly string[]).includes(v('currentStage'))) errors.currentStage = 'Choose the stage the claim is at.'
  required('policy.policyToken', 'Enter the policy token.', MAX_TOKEN)
  optionalText('policy.policyType', MAX_SHORT)
  optionalText('claimant.claimantToken', MAX_TOKEN)
  required('garage.garageId', 'Enter the garage id.', MAX_TOKEN)
  required('vehicle.vehicleToken', 'Enter the vehicle token.', MAX_TOKEN)
  optionalText('vehicle.make', MAX_SHORT)
  optionalText('vehicle.model', MAX_SHORT)
  optionalText('incident.permittedLocationPrecision', MAX_SHORT)

  let incidentAt: string | null = null
  if (v('incident.incidentAt') === '') errors['incident.incidentAt'] = 'Enter when the incident happened.'
  else if ((incidentAt = localDateTimeToIso(v('incident.incidentAt'))) === null) errors['incident.incidentAt'] = 'Enter a real date and time.'

  let reportedAt: string | null = null
  if (v('incident.reportedAt') !== '' && (reportedAt = localDateTimeToIso(v('incident.reportedAt'))) === null) errors['incident.reportedAt'] = 'Enter a real date and time.'

  let policyStart: string | null = null
  if (v('policy.policyStartDate') !== '' && (policyStart = localDateToIso(v('policy.policyStartDate'))) === null) errors['policy.policyStartDate'] = 'Enter a real date.'
  let policyEnd: string | null = null
  if (v('policy.policyEndDate') !== '' && (policyEnd = localDateToIso(v('policy.policyEndDate'))) === null) errors['policy.policyEndDate'] = 'Enter a real date.'

  if (!(INCIDENT_TYPES as readonly string[]).includes(v('incident.incidentType'))) errors['incident.incidentType'] = 'Choose the incident type.'
  if (!/^[A-Z]{3}$/.test(v('financial.currency'))) errors['financial.currency'] = 'Use a three-letter currency code in capitals, such as INR.'

  const amounts: Partial<Record<'initialEstimateAmount' | 'approvedEstimateAmount' | 'deductible', number>> = {}
  for (const name of ['initialEstimateAmount', 'approvedEstimateAmount', 'deductible'] as const) {
    const key = `financial.${name}` as const
    const text = v(key)
    if (text === '') continue
    if (!AMOUNT.test(text)) errors[key] = 'Enter digits with at most two decimals, such as 45000.50. No commas, signs or symbols.'
    else amounts[name] = Number(text)
  }

  if (Object.keys(errors).length > 0) return { ok: false, errors }

  // Server-controlled fields (organization, creator, id, timestamps, archive flag) are never sent. The claim version is fixed
  // at 1: claim updates are not part of this release.
  const body: ClaimRequest = {
    internalId: v('internalId'),
    claimVersion: 1,
    currentStage: v('currentStage'),
    policy: {
      policyToken: v('policy.policyToken'),
      ...(some(values['policy.policyType']) ? { policyType: v('policy.policyType') } : {}),
      ...(policyStart ? { policyStartDate: policyStart } : {}),
      ...(policyEnd ? { policyEndDate: policyEnd } : {}),
    },
    ...(some(values['claimant.claimantToken']) ? { claimant: { claimantToken: v('claimant.claimantToken') } } : {}),
    garage: { garageId: v('garage.garageId') },
    vehicle: {
      vehicleToken: v('vehicle.vehicleToken'),
      ...(some(values['vehicle.make']) ? { make: v('vehicle.make') } : {}),
      ...(some(values['vehicle.model']) ? { model: v('vehicle.model') } : {}),
    },
    incident: {
      incidentAt: incidentAt!,
      ...(reportedAt ? { reportedAt } : {}),
      incidentType: v('incident.incidentType'),
      ...(some(values['incident.permittedLocationPrecision']) ? { permittedLocationPrecision: v('incident.permittedLocationPrecision') } : {}),
    },
    financial: { currency: v('financial.currency'), ...amounts },
  }
  return { ok: true, body }
}

const knownPaths = new Set<string>(FIELD_ORDER)

/** Puts the server's field errors on the fields they name. Anything it names that is not on this form is returned separately. */
export function mapServerFieldErrors(errors: FieldError[]): { fieldErrors: FieldErrors; other: string[] } {
  const fieldErrors: FieldErrors = {}
  const other: string[] = []
  for (const e of errors) {
    if (knownPaths.has(e.field)) {
      if (!fieldErrors[e.field as FieldPath]) fieldErrors[e.field as FieldPath] = e.message
    } else {
      other.push(`${e.field}: ${e.message}`)
    }
  }
  return { fieldErrors, other }
}

export function firstInvalidField(errors: FieldErrors): FieldPath | undefined {
  return FIELD_ORDER.find((f) => errors[f])
}
