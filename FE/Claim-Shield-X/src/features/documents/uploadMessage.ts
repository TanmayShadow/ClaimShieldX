import { describeApiError } from '@/apis/describeError'
import { isApiError } from '@/apis/errors'

export type UploadFailure = { title: string; message: string; retryable: boolean; existingDocumentId?: string; code?: string }

/** What to tell someone whose upload failed, and what they can do about it. */
export function describeUploadFailure(error: unknown): UploadFailure {
  const base = describeApiError(error, 'that claim')
  if (!isApiError(error)) return base
  const out: UploadFailure = { ...base }
  if (error.code === 'DUPLICATE_DOCUMENT' && error.details.existingDocumentId) out.existingDocumentId = error.details.existingDocumentId
  if (error.code === 'VALIDATION_FAILED' && error.fieldErrors.length > 0) {
    out.message = error.fieldErrors.map((e) => `${e.field}: ${e.message}`).join(' ')
  }
  // A dropped connection leaves it unknown whether the file arrived. The same request is safe to repeat; say so.
  if (error.status === 0) out.message = `${base.message} The upload may or may not have reached the server. Trying again is safe: it cannot store the file twice.`
  if (error.code === 'REQUEST_IN_PROGRESS') out.retryable = true
  return out
}
