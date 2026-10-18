/** One error type for everything the API layer can fail with. Callers branch on `code`, never on message text (contract D8). */

export type FieldError = { field: string; message: string }

export type ApiErrorInit = {
  status: number
  code: string
  message: string
  fieldErrors?: FieldError[]
  details?: Record<string, string>
  retryAfterSeconds?: number
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly details: Record<string, string>
  readonly retryAfterSeconds?: number

  constructor(init: ApiErrorInit) {
    super(init.message)
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.fieldErrors = init.fieldErrors ?? []
    this.details = init.details ?? {}
    this.retryAfterSeconds = init.retryAfterSeconds
  }

  /** Worth trying again without the user changing anything. Mutations are never retried automatically regardless. */
  get retryable(): boolean {
    return this.status === 0 || this.status === 429 || this.status === 503 || this.status >= 500
  }
}

export function isApiError(value: unknown): value is ApiError {
  return value instanceof ApiError
}

export const NETWORK_ERROR = 'NETWORK_ERROR'
export const REQUEST_TIMEOUT = 'REQUEST_TIMEOUT'
export const MALFORMED_RESPONSE = 'MALFORMED_RESPONSE'

export function networkError(): ApiError {
  return new ApiError({ status: 0, code: NETWORK_ERROR, message: 'The server could not be reached.' })
}

export function timeoutError(): ApiError {
  return new ApiError({ status: 0, code: REQUEST_TIMEOUT, message: 'The server took too long to answer.' })
}

export function malformedResponse(status: number): ApiError {
  return new ApiError({ status, code: MALFORMED_RESPONSE, message: 'The server sent an answer this page could not understand.' })
}

const text = (value: unknown, max = 300): string | undefined => (typeof value === 'string' && value.trim() !== '' ? value.slice(0, max) : undefined)

/**
 * Builds an ApiError from a response body in the contract's shape (api-contract section 3).
 * Bodies that are not that shape (an HTML error page from a proxy, an empty 403) still give a usable error.
 * Nothing from the body is trusted for display beyond short plain strings.
 */
export function normalizeErrorBody(status: number, body: unknown, retryAfter?: string | null): ApiError {
  const record = body && typeof body === 'object' ? (body as Record<string, unknown>) : {}
  const fieldErrors: FieldError[] = Array.isArray(record.errors)
    ? record.errors.flatMap((item): FieldError[] => {
        const e = item && typeof item === 'object' ? (item as Record<string, unknown>) : {}
        const field = text(e.field, 200)
        const message = text(e.message)
        return field && message ? [{ field, message }] : []
      })
    : []

  const details: Record<string, string> = {}
  if (record.details && typeof record.details === 'object') {
    for (const [key, value] of Object.entries(record.details as Record<string, unknown>)) {
      const v = text(value, 200)
      if (v) details[key] = v
    }
  }

  const seconds = retryAfter && /^\d+$/.test(retryAfter.trim()) ? Number(retryAfter.trim()) : undefined

  return new ApiError({
    status,
    code: text(record.code, 80) ?? fallbackCode(status),
    message: text(record.message) ?? 'The request could not be completed.',
    fieldErrors,
    details,
    retryAfterSeconds: seconds,
  })
}

function fallbackCode(status: number): string {
  if (status === 401) return 'UNAUTHENTICATED'
  if (status === 403) return 'FORBIDDEN'
  if (status === 404) return 'NOT_FOUND'
  if (status === 429) return 'RATE_LIMITED'
  if (status >= 500) return 'INTERNAL_ERROR'
  return 'UNKNOWN_ERROR'
}
