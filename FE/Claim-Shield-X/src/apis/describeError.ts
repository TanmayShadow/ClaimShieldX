import { isApiError } from './errors'

export type ErrorDescription = {
  title: string
  message: string
  /** Worth offering a "Try again" for this kind of failure. */
  retryable: boolean
  /** A stable code safe to show beside the message, for support. Never server text. */
  code?: string
}

/**
 * Turns any failure into words a person can act on. Server messages are never repeated: only the code decides the wording,
 * so a stack trace, SQL or provider text can never reach the screen even if the backend leaked one.
 */
export function describeApiError(error: unknown, subject = 'this'): ErrorDescription {
  if (!isApiError(error)) {
    return { title: 'Something went wrong.', message: 'The page hit a problem it could not recover from. Reloading usually fixes it.', retryable: true }
  }
  const { code, status } = error
  switch (code) {
    case 'NETWORK_ERROR':
      return { title: 'The server could not be reached.', message: 'Check your connection. Nothing was lost or changed.', retryable: true, code }
    case 'REQUEST_TIMEOUT':
      return { title: 'The server took too long to answer.', message: 'It may still be working. Wait a moment, then try again.', retryable: true, code }
    case 'MALFORMED_RESPONSE':
      return { title: 'The server sent something this page could not read.', message: 'This is a fault, not something you did. Try again, and report it if it keeps happening.', retryable: true, code }
    case 'FORBIDDEN':
      return { title: 'You do not have permission to do this.', message: 'Your account is not allowed to use this part of the workspace. Ask the person who manages your access.', retryable: false, code }
    case 'NOT_FOUND':
      return { title: `We could not find ${subject}.`, message: 'It may not exist, or it may belong to another organization. The two look the same on purpose.', retryable: false, code }
    case 'RATE_LIMITED':
      return { title: 'Too many requests.', message: 'Wait a moment and try again.', retryable: true, code }
    case 'SERVICE_UNAVAILABLE':
      return { title: 'A service this needs is unavailable.', message: 'It is usually brief. Try again in a moment.', retryable: true, code }
    case 'PAYLOAD_TOO_LARGE':
      return { title: 'That file is too large.', message: 'Files can be at most 20 MB.', retryable: false, code }
    case 'UNSUPPORTED_MEDIA_TYPE':
      return { title: 'That file type is not accepted.', message: 'Use a PDF, JPEG or PNG file.', retryable: false, code }
    case 'INVESTIGATION_ALREADY_ACTIVE':
      return { title: 'An investigation is already under way for this claim.', message: 'Only one can run at a time. You can watch it here, or cancel it to start another.', retryable: false, code }
    case 'INVESTIGATION_QUOTA_EXCEEDED':
      return { title: 'Your organization has reached its daily limit of investigations.', message: 'The limit protects the reading service from being used up. Try again later.', retryable: false, code }
    case 'EMPTY_FILE':
      return { title: 'That file is empty.', message: 'Choose a file that has content.', retryable: false, code }
    case 'DUPLICATE_DOCUMENT':
      return { title: 'This exact file is already stored.', message: 'The same file is already on this claim under this document type. Upload a revised file to replace it.', retryable: false, code }
    case 'DOCUMENT_LIMIT_REACHED':
      return { title: 'This claim holds the most documents it can.', message: 'A claim can hold 50 documents.', retryable: false, code }
    case 'INVALID_STATE_TRANSITION':
      return { title: 'That is not possible in the document\u2019s current state.', message: 'It may already have been read, replaced, or read as often as allowed. Reload the list to see where it stands.', retryable: false, code }
    case 'REQUEST_IN_PROGRESS':
      return { title: 'This is already being worked on.', message: 'Wait a moment, then look at the list again.', retryable: true, code }
    case 'IDEMPOTENCY_KEY_REUSED':
      return { title: 'That upload could not be repeated.', message: 'Choose the file again to start a fresh upload.', retryable: false, code }
    case 'REPORT_VERSION_MISMATCH':
      return { title: 'A newer report exists than the one you were reading.', message: 'Reload the page, read the newer report, then record your decision again.', retryable: false, code }
    case 'EXTRACTION_NOT_SUPPORTED':
      return { title: 'This kind of document is not read.', message: 'Only final invoices and garage estimates are read. Other documents are stored for people to open.', retryable: false, code }
    case 'VALIDATION_FAILED':
    case 'MALFORMED_REQUEST':
      return { title: 'Some details need fixing.', message: 'Check the highlighted fields and try again.', retryable: false, code }
  }
  if (status >= 500) {
    return { title: 'The service had a problem.', message: 'This is not caused by anything you did. Try again in a moment.', retryable: true, code }
  }
  return { title: 'That did not work.', message: 'The request could not be completed.', retryable: error.retryable, code }
}
