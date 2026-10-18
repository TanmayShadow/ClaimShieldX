import type { MarkState } from '@/components/ui/StatusMark'

/** Words for the backend's document enums. An unknown value (a newer backend) is shown as sent, never hidden. */

export const DOCUMENT_TYPES = [
  'CLAIM_FORM',
  'POLICY_RECORD',
  'VEHICLE_REGISTRATION',
  'DRIVING_LICENCE',
  'DAMAGE_PHOTOGRAPH',
  'GARAGE_ESTIMATE',
  'JOB_CARD',
  'SURVEYOR_REPORT',
  'FINAL_INVOICE',
  'SUPPLEMENTARY_ESTIMATE',
  'PAYMENT_PROOF',
  'FIR_POLICE_REPORT',
  'THEFT_EVIDENCE',
  'OTHER',
] as const
export type DocumentType = (typeof DOCUMENT_TYPES)[number]

export const documentTypeLabels: Record<DocumentType, string> = {
  CLAIM_FORM: 'Claim form',
  POLICY_RECORD: 'Policy record',
  VEHICLE_REGISTRATION: 'Vehicle registration',
  DRIVING_LICENCE: 'Driving licence',
  DAMAGE_PHOTOGRAPH: 'Damage photograph',
  GARAGE_ESTIMATE: 'Garage estimate',
  JOB_CARD: 'Job card',
  SURVEYOR_REPORT: 'Surveyor report',
  FINAL_INVOICE: 'Final invoice',
  SUPPLEMENTARY_ESTIMATE: 'Supplementary estimate',
  PAYMENT_PROOF: 'Payment proof',
  FIR_POLICE_REPORT: 'FIR or police report',
  THEFT_EVIDENCE: 'Theft evidence',
  OTHER: 'Other document',
}

export function documentTypeLabel(type: string): string {
  return (documentTypeLabels as Record<string, string>)[type] ?? type
}

/** Only these two are read by the model today (api-contract 7.6). Everything else is stored and shown, not read. */
export const EXTRACTABLE_TYPES: readonly string[] = ['FINAL_INVOICE', 'GARAGE_ESTIMATE']
export const isExtractable = (type: string) => EXTRACTABLE_TYPES.includes(type)

/**
 * A stored document's lifecycle. RECEIVED only means the file is stored: for a type the system reads, it has not been read yet;
 * for any other type it never will be, and the label says which.
 */
export function documentStatusMark(status: string, type: string): { state: MarkState; label: string } {
  switch (status) {
    case 'RECEIVED':
      return { state: 'info', label: isExtractable(type) ? 'Uploaded · not read yet' : 'Stored · not read' }
    case 'PROCESSING':
      return { state: 'active', label: 'Being read' }
    case 'VERIFIED':
      return { state: 'done', label: 'Read and consistent' }
    case 'NEEDS_REVIEW':
      return { state: 'attention', label: 'Read · needs review' }
    case 'FAILED':
      return { state: 'failed', label: 'Could not be read' }
    default:
      return { state: 'unknown', label: status }
  }
}

/** The checklist's per-type state. NOT_RECEIVED exists only here: it is an absence, not a status a stored document can have. */
export function evidenceStateMark(state: string): { state: MarkState; label: string } {
  switch (state) {
    case 'NOT_RECEIVED':
      return { state: 'pending', label: 'Not received' }
    case 'RECEIVED':
      return { state: 'info', label: 'Received' }
    case 'PROCESSING':
      return { state: 'active', label: 'Being read' }
    case 'VERIFIED':
      return { state: 'done', label: 'Read and consistent' }
    case 'NEEDS_REVIEW':
      return { state: 'attention', label: 'Needs review' }
    case 'FAILED':
      return { state: 'failed', label: 'Could not be read' }
    default:
      return { state: 'unknown', label: state }
  }
}

export function requirementLabel(requirement: string, mandatory: boolean): string {
  if (mandatory) return 'Needed to proceed'
  if (requirement === 'REQUIRED') return 'Expected'
  if (requirement === 'OPTIONAL') return 'Optional'
  if (requirement === 'NOT_APPLICABLE') return 'Not applicable'
  return requirement
}

const checkLabels: Record<string, string> = {
  REQUIRED_EVIDENCE: 'Required evidence',
  INVOICE_ARITHMETIC: 'Invoice arithmetic',
  ESTIMATE_INVOICE_VARIANCE: 'Estimate-to-invoice variance',
  DATE_CONSISTENCY: 'Date consistency',
  DOCUMENT_FILE_DUPLICATE: 'Identical files elsewhere',
  INVOICE_IDENTITY_DUPLICATE: 'Repeated invoice number',
  REPEATED_LINE_ITEMS: 'Repeated line items',
  HISTORICAL_RELATIONSHIP: 'Relationship to earlier claims',
  REPAIR_SCOPE_REVIEW: 'Repair scope review',
}

export function checkLabel(id: string): string {
  return checkLabels[id] ?? id
}

/** What went wrong reading a document, in words. Codes are the backend's (api-contract 7.6). */
const failureText: Record<string, string> = {
  DOCUMENT_TOO_LARGE: 'The file is too large to read. Upload a smaller copy.',
  UNREADABLE_FILE: 'The file is damaged, password-protected, or has more than 40 pages.',
  STORAGE_UNAVAILABLE: 'The stored file could not be reached just now.',
  STORED_FILE_MISMATCH: 'The stored file no longer matches what was uploaded, so nothing was read.',
  MODEL_REJECTED_INPUT: 'The reading service could not accept this file.',
  MODEL_BLOCKED: 'The reading service declined to read this file.',
  MODEL_NOT_CONFIGURED: 'The reading service is not set up. An operator needs to fix this.',
  MODEL_RATE_LIMITED: 'The reading service is busy.',
  MODEL_UNAVAILABLE: 'The reading service is unavailable.',
  MODEL_OUTPUT_INVALID: 'The reading service gave an answer that could not be used.',
  INTERNAL_ERROR: 'The service had a problem reading this file.',
}

export function failureMessage(code: string | null | undefined): string {
  return (code && failureText[code]) || 'The document could not be read.'
}
