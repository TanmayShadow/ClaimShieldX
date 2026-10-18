import { ApiError, isApiError, malformedResponse, networkError, normalizeErrorBody, timeoutError } from './errors'

describe('normalizeErrorBody', () => {
  it('reads the contract body', () => {
    const e = normalizeErrorBody(409, {
      timestamp: '2026-10-18T00:00:00Z',
      status: 409,
      error: 'Conflict',
      code: 'DUPLICATE_DOCUMENT',
      message: 'Already stored',
      details: { existingDocumentId: 'abc' },
    })
    expect(e).toBeInstanceOf(ApiError)
    expect(e.status).toBe(409)
    expect(e.code).toBe('DUPLICATE_DOCUMENT')
    expect(e.details).toEqual({ existingDocumentId: 'abc' })
  })

  it('keeps field errors for validation failures and drops malformed entries', () => {
    const e = normalizeErrorBody(400, {
      code: 'VALIDATION_FAILED',
      message: 'Request validation failed',
      errors: [{ field: 'policy.policyToken', message: 'Policy token cannot be blank' }, { field: 5 }, null, { field: 'x' }],
    })
    expect(e.fieldErrors).toEqual([{ field: 'policy.policyToken', message: 'Policy token cannot be blank' }])
  })

  it.each([
    [401, 'UNAUTHENTICATED'],
    [403, 'FORBIDDEN'],
    [404, 'NOT_FOUND'],
    [429, 'RATE_LIMITED'],
    [500, 'INTERNAL_ERROR'],
    [418, 'UNKNOWN_ERROR'],
  ])('falls back to a code for an unreadable %i body', (status, code) => {
    expect(normalizeErrorBody(status, '<html>Bad gateway</html>').code).toBe(code)
    expect(normalizeErrorBody(status, null).code).toBe(code)
    expect(normalizeErrorBody(status, undefined).message).toBeTruthy()
  })

  it('never lets a long or non-string message through unbounded', () => {
    expect(normalizeErrorBody(500, { code: 'X', message: 'a'.repeat(5000) }).message.length).toBeLessThanOrEqual(300)
    expect(normalizeErrorBody(500, { code: 7, message: { a: 1 } }).code).toBe('INTERNAL_ERROR')
  })

  it('reads a numeric Retry-After only', () => {
    expect(normalizeErrorBody(429, {}, '30').retryAfterSeconds).toBe(30)
    expect(normalizeErrorBody(429, {}, 'Wed, 21 Oct 2026 07:28:00 GMT').retryAfterSeconds).toBeUndefined()
  })
})

describe('ApiError', () => {
  it('marks transient failures as retryable and client mistakes as not', () => {
    expect(networkError().retryable).toBe(true)
    expect(timeoutError().retryable).toBe(true)
    expect(normalizeErrorBody(503, {}).retryable).toBe(true)
    expect(normalizeErrorBody(429, {}).retryable).toBe(true)
    expect(normalizeErrorBody(400, { code: 'VALIDATION_FAILED' }).retryable).toBe(false)
    expect(normalizeErrorBody(404, {}).retryable).toBe(false)
    expect(malformedResponse(200).retryable).toBe(false)
  })

  it('is recognised by isApiError', () => {
    expect(isApiError(networkError())).toBe(true)
    expect(isApiError(new Error('x'))).toBe(false)
  })
})
