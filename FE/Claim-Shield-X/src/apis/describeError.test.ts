import { describeApiError } from './describeError'
import { ApiError, malformedResponse, networkError, timeoutError } from './errors'

const api = (status: number, code: string, message = 'server text') => new ApiError({ status, code, message })

describe('describeApiError', () => {
  it.each([
    [networkError(), 'The server could not be reached.', true],
    [timeoutError(), 'The server took too long to answer.', true],
    [malformedResponse(200), /could not read/, true],
    [api(403, 'FORBIDDEN'), 'You do not have permission to do this.', false],
    [api(404, 'NOT_FOUND'), 'We could not find this.', false],
    [api(429, 'RATE_LIMITED'), 'Too many requests.', true],
    [api(503, 'SERVICE_UNAVAILABLE'), /unavailable/, true],
    [api(413, 'PAYLOAD_TOO_LARGE'), 'That file is too large.', false],
    [api(415, 'UNSUPPORTED_MEDIA_TYPE'), 'That file type is not accepted.', false],
    [api(400, 'VALIDATION_FAILED'), 'Some details need fixing.', false],
    [api(500, 'INTERNAL_ERROR'), 'The service had a problem.', true],
    [api(502, 'SOMETHING_NEW'), 'The service had a problem.', true],
    [api(409, 'DUPLICATE_CLAIM'), 'That did not work.', false],
    [api(400, 'EMPTY_FILE'), 'That file is empty.', false],
    [api(409, 'DUPLICATE_DOCUMENT'), 'This exact file is already stored.', false],
    [api(409, 'INVESTIGATION_ALREADY_ACTIVE'), /already under way/, false],
    [api(429, 'INVESTIGATION_QUOTA_EXCEEDED'), /daily limit of investigations/, false],
    [api(409, 'DOCUMENT_LIMIT_REACHED'), /most documents/, false],
    [api(409, 'INVALID_STATE_TRANSITION'), /not possible in the document/, false],
    [api(409, 'REQUEST_IN_PROGRESS'), 'This is already being worked on.', true],
    [api(422, 'IDEMPOTENCY_KEY_REUSED'), 'That upload could not be repeated.', false],
    [api(422, 'EXTRACTION_NOT_SUPPORTED'), 'This kind of document is not read.', false],
  ])('describes %#', (error, title, retryable) => {
    const d = describeApiError(error)
    expect(d.title).toMatch(title)
    expect(d.retryable).toBe(retryable)
  })

  it('names the thing that was not found', () => {
    expect(describeApiError(api(404, 'NOT_FOUND'), 'that claim').title).toBe('We could not find that claim.')
  })

  it('never repeats server text, even if the server leaked a stack trace or SQL', () => {
    for (const code of ['INTERNAL_ERROR', 'FORBIDDEN', 'NOT_FOUND', 'VALIDATION_FAILED', 'WHATEVER']) {
      const d = describeApiError(api(500, code, 'org.postgresql.util.PSQLException: select * from claims at com.example.Secret.java:42'))
      expect(JSON.stringify(d)).not.toMatch(/PSQL|select \*|com\.example|\.java/)
    }
  })

  it('says a missing record and a foreign one look alike on purpose, never confirming either', () => {
    expect(describeApiError(api(404, 'NOT_FOUND')).message).toMatch(/may not exist, or it may belong to another organization/)
  })

  it('handles things that are not API errors', () => {
    expect(describeApiError(new Error('boom: secret detail')).message).not.toContain('secret')
    expect(describeApiError('x').retryable).toBe(true)
    expect(describeApiError(undefined).title).toBe('Something went wrong.')
  })

  it('exposes only the stable code, for support', () => {
    expect(describeApiError(api(500, 'INTERNAL_ERROR')).code).toBe('INTERNAL_ERROR')
    expect(describeApiError(new Error('x')).code).toBeUndefined()
  })
})
