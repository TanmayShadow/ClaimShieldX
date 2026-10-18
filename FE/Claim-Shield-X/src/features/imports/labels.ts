import type { MarkState } from '@/components/ui/StatusMark'
import { humanizeCode } from '@/features/checks/text'

export const IMPORT_TYPES = ['HISTORICAL_CLAIMS', 'GARAGE_HISTORY'] as const
export type ImportType = (typeof IMPORT_TYPES)[number]

export const importTypeLabels: Record<ImportType, string> = {
  HISTORICAL_CLAIMS: 'Earlier claims',
  GARAGE_HISTORY: 'Garage history',
}
export const importTypeLabel = (value: string) => (importTypeLabels as Record<string, string>)[value] ?? humanizeCode(value)

export const importTypeHints: Record<ImportType, string> = {
  HISTORICAL_CLAIMS: 'One row per earlier claim, with pseudonymous tokens for vehicle, policy and claimant. Used to find earlier claims that relate to a new one.',
  GARAGE_HISTORY: 'One row per garage and reporting period, with claim counts and totals. Shown as context only, never as evidence about a garage.',
}

export const templateFileNames: Record<ImportType, string> = {
  HISTORICAL_CLAIMS: 'historical-claims-template-v1.csv',
  GARAGE_HISTORY: 'garage-history-template-v1.csv',
}

export function importStatusMark(status: string): { state: MarkState; label: string } {
  switch (status) {
    case 'COMPLETED':
      return { state: 'done', label: 'Stored' }
    case 'COMPLETED_WITH_ERRORS':
      return { state: 'attention', label: 'Stored, some rows rejected' }
    case 'FAILED':
      return { state: 'failed', label: 'Nothing stored' }
    case 'RECEIVED':
      return { state: 'pending', label: 'Received' }
    case 'PROCESSING':
      return { state: 'active', label: 'Processing' }
    default:
      return { state: 'unknown', label: humanizeCode(status) }
  }
}

export const isImportInFlight = (status: string) => status === 'RECEIVED' || status === 'PROCESSING'

/** Why a whole file was refused, in words. The codes are the backend's (api-contract 7.7). */
const failureText: Record<string, string> = {
  EMPTY_FILE: 'The file is empty, or it has a header but no rows.',
  FILE_TOO_LARGE: 'The file is larger than 5 MB.',
  TOO_MANY_ROWS: 'The file has more than 10,000 rows. Split it into smaller files.',
  INVALID_ENCODING: 'The file is not UTF-8 text. Save it as "CSV (UTF-8)" and try again.',
  INVALID_CSV: 'The quoting in the file is broken, so it cannot be read as CSV.',
  INVALID_HEADER: 'The first row does not match the template. Download the template and use exactly its column names.',
  INTERNAL_ERROR: 'The service had a problem. Nothing was stored, and you can try again.',
}
export const importFailureText = (code: string | null | undefined) => (code && failureText[code]) || 'The file could not be used. Nothing was stored.'

export const ROW_STATUSES = ['ACCEPTED', 'REJECTED', 'SKIPPED_DUPLICATE'] as const
export const rowStatusLabels: Record<(typeof ROW_STATUSES)[number], string> = { ACCEPTED: 'Stored', REJECTED: 'Rejected', SKIPPED_DUPLICATE: 'Already stored' }

export function rowStatusMark(status: string): { state: MarkState; label: string } {
  switch (status) {
    case 'ACCEPTED':
      return { state: 'done', label: 'Stored' }
    case 'REJECTED':
      return { state: 'attention', label: 'Rejected' }
    case 'SKIPPED_DUPLICATE':
      return { state: 'skipped', label: 'Already stored' }
    default:
      return { state: 'unknown', label: humanizeCode(status) }
  }
}

const rowErrorText: Record<string, string> = {
  WRONG_COLUMN_COUNT: 'Wrong number of columns',
  MISSING_REQUIRED: 'Required value missing',
  TOO_LONG: 'Too long',
  INVALID_TOKEN: 'Not a valid token',
  INVALID_NUMBER: 'Not a valid number',
  INVALID_DATE: 'Not a valid date',
  INVALID_VALUE: 'Not an allowed value',
  DATE_ORDER: 'Dates are in the wrong order',
  COUNT_EXCEEDS_CLAIM_COUNT: 'A count is larger than the claim count',
  CONFLICTING_DUPLICATE: 'Same record id as a stored row, with different content',
  DUPLICATE_CLAIM_VERSION: 'This claim version is already stored under another record id',
  OVERLAPPING_PERIOD: 'The period overlaps one already stored for this garage',
}
export const rowErrorLabel = (code: string) => rowErrorText[code] ?? humanizeCode(code)

export const MAX_IMPORT_BYTES = 5 * 1024 * 1024
export const SOURCE_PATTERN = /^[A-Za-z0-9._:-]{1,100}$/
