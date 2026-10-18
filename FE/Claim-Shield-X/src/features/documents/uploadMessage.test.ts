import { ApiError, networkError } from '@/apis/errors'
import { describeUploadFailure } from './uploadMessage'

const api = (status: number, code: string, extra: Partial<ConstructorParameters<typeof ApiError>[0]> = {}) => new ApiError({ status, code, message: 'server text', ...extra })

describe('describeUploadFailure', () => {
  it('names the existing document for a duplicate', () => {
    const f = describeUploadFailure(api(409, 'DUPLICATE_DOCUMENT', { details: { existingDocumentId: 'abc-123' } }))
    expect(f.title).toBe('This exact file is already stored.')
    expect(f.existingDocumentId).toBe('abc-123')
    expect(f.retryable).toBe(false)
  })

  it('says a dropped connection is safe to repeat, because of the idempotency key', () => {
    const f = describeUploadFailure(networkError())
    expect(f.retryable).toBe(true)
    expect(f.message).toMatch(/may or may not have reached the server/)
    expect(f.message).toMatch(/cannot store the file twice/)
  })

  it('does not offer a retry for a file the server will never accept', () => {
    for (const [status, code] of [[415, 'UNSUPPORTED_MEDIA_TYPE'], [413, 'PAYLOAD_TOO_LARGE'], [400, 'EMPTY_FILE']] as const) {
      expect(describeUploadFailure(api(status, code)).retryable, code).toBe(false)
    }
  })

  it('offers a retry for a busy or unavailable service', () => {
    expect(describeUploadFailure(api(503, 'SERVICE_UNAVAILABLE')).retryable).toBe(true)
    expect(describeUploadFailure(api(409, 'REQUEST_IN_PROGRESS')).retryable).toBe(true)
  })

  it('shows the server’s field messages for a validation failure, which are safe by contract', () => {
    const f = describeUploadFailure(api(400, 'VALIDATION_FAILED', { fieldErrors: [{ field: 'supersedesDocumentId', message: 'document not found' }] }))
    expect(f.message).toBe('supersedesDocumentId: document not found')
  })

  it('never repeats server text for a fault', () => {
    expect(JSON.stringify(describeUploadFailure(api(500, 'INTERNAL_ERROR')))).not.toContain('server text')
    expect(JSON.stringify(describeUploadFailure(new Error('storage path /var/x')))).not.toContain('/var/x')
  })
})
