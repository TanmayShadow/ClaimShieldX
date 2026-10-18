import { makeJwt } from '@/test/jwt'
import { getSession, resetSessionForTests } from '@/features/auth/session'
import { authBridge, registerAuthBridge } from './authBridge'
import { ApiError } from './errors'
import { isUploadAbort, uploadDocument } from './upload'

const doc = {
  id: 'd1',
  claimId: 'c1',
  documentType: 'FINAL_INVOICE',
  fileName: 'invoice.pdf',
  contentType: 'application/pdf',
  sizeBytes: 5,
  sha256: 'a'.repeat(64),
  status: 'RECEIVED',
  externalDocumentId: null,
  supersedesDocumentId: null,
  superseded: false,
  receivedAt: '2026-10-18T05:42:33Z',
}

class FakeXhr {
  method = ''
  url = ''
  headers: Record<string, string> = {}
  body: FormData | null = null
  status = 0
  responseText = ''
  timeout = 0
  responseHeaders: Record<string, string> = {}
  aborted = false
  upload = { onprogress: null as ((e: ProgressEvent) => void) | null } as unknown as XMLHttpRequestUpload
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  ontimeout: (() => void) | null = null
  open(method: string, url: string) {
    this.method = method
    this.url = url
  }
  setRequestHeader(k: string, v: string) {
    this.headers[k] = v
  }
  getResponseHeader(k: string) {
    return this.responseHeaders[k] ?? null
  }
  send(body: FormData) {
    this.body = body
  }
  abort() {
    this.aborted = true
    this.onabort?.()
  }
  // helpers used by the tests
  progress(loaded: number, total: number) {
    ;(this.upload as unknown as { onprogress: (e: unknown) => void }).onprogress({ lengthComputable: true, loaded, total })
  }
  reply(status: number, body: unknown, headers: Record<string, string> = {}) {
    this.status = status
    this.responseText = typeof body === 'string' ? body : JSON.stringify(body)
    this.responseHeaders = headers
    this.onload?.()
  }
}

function setup(token: string | null = 'tok-1', refresh: () => Promise<string> = () => Promise.reject(new Error('no refresh'))) {
  const requests: FakeXhr[] = []
  const lost = vi.fn()
  registerAuthBridge({ getToken: () => token, refresh, sessionLost: lost })
  const file = new File(['hello'], 'invoice.pdf', { type: 'application/pdf' })
  const progress = vi.fn()
  const handle = uploadDocument(
    { claimId: 'c1', file, documentType: 'FINAL_INVOICE', externalDocumentId: 'INS-77', supersedesDocumentId: 'old-1', idempotencyKey: 'upload-key-123456' },
    progress,
    () => {
      const x = new FakeXhr()
      requests.push(x)
      return x as never
    },
  )
  return { requests, progress, handle, lost, file }
}

afterEach(() => {
  resetSessionForTests()
})

describe('uploadDocument: the request', () => {
  it('posts the file and fields as multipart to the claim, with the token and idempotency key, and no forced content type', () => {
    const { requests, file } = setup()
    const x = requests[0]!
    expect(x.method).toBe('POST')
    expect(x.url).toBe('/api/v1/claims/c1/documents')
    expect(x.headers).toMatchObject({ Authorization: 'Bearer tok-1', 'Idempotency-Key': 'upload-key-123456', Accept: 'application/json' })
    expect(x.headers['Content-Type']).toBeUndefined()
    expect(x.body!.get('file')).toBe(file)
    expect(x.body!.get('documentType')).toBe('FINAL_INVOICE')
    expect(x.body!.get('externalDocumentId')).toBe('INS-77')
    expect(x.body!.get('supersedesDocumentId')).toBe('old-1')
  })

  it('omits optional parts that were not given', () => {
    const requests: FakeXhr[] = []
    registerAuthBridge({ getToken: () => 't', refresh: () => Promise.reject(new Error('x')), sessionLost: () => {} })
    uploadDocument({ claimId: 'c1', file: new File(['x'], 'a.pdf'), documentType: 'OTHER', idempotencyKey: 'upload-key-123456' }, () => {}, () => {
      const x = new FakeXhr()
      requests.push(x)
      return x as never
    })
    expect([...requests[0]!.body!.keys()].sort()).toEqual(['documentType', 'file'])
  })

  it('escapes the claim id in the address', () => {
    const requests: FakeXhr[] = []
    registerAuthBridge({ getToken: () => 't', refresh: () => Promise.reject(new Error('x')), sessionLost: () => {} })
    uploadDocument({ claimId: 'a/b?c', file: new File(['x'], 'a.pdf'), documentType: 'OTHER', idempotencyKey: 'upload-key-123456' }, () => {}, () => {
      const x = new FakeXhr()
      requests.push(x)
      return x as never
    })
    expect(requests[0]!.url).toBe('/api/v1/claims/a%2Fb%3Fc/documents')
  })
})

describe('uploadDocument: progress and result', () => {
  it('reports real transfer progress and resolves only when the server answers', async () => {
    const { requests, progress, handle } = setup()
    let settled = false
    void handle.promise.then(() => (settled = true))
    requests[0]!.progress(2, 10)
    requests[0]!.progress(10, 10)
    await Promise.resolve()
    expect(progress.mock.calls).toEqual([[2, 10], [10, 10]])
    expect(settled).toBe(false) // all bytes sent is not the same as uploaded
    requests[0]!.reply(201, doc)
    await expect(handle.promise).resolves.toEqual({ document: expect.objectContaining({ id: 'd1' }), replayed: false })
  })

  it('ignores progress events that have no known total', () => {
    const { requests, progress } = setup()
    ;(requests[0]!.upload as unknown as { onprogress: (e: unknown) => void }).onprogress({ lengthComputable: false, loaded: 5, total: 0 })
    expect(progress).not.toHaveBeenCalled()
  })

  it('marks an answer that replays an earlier identical request', async () => {
    const { requests, handle } = setup()
    requests[0]!.reply(201, doc, { 'Idempotent-Replay': 'true' })
    await expect(handle.promise).resolves.toMatchObject({ replayed: true })
  })

  it('rejects an answer that is not a document, rather than trusting it', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const { requests, handle } = setup()
    requests[0]!.reply(201, { id: 5 })
    await expect(handle.promise).rejects.toMatchObject({ code: 'MALFORMED_RESPONSE' })
  })
})

describe('uploadDocument: failures', () => {
  it.each([
    [409, 'DUPLICATE_DOCUMENT', { existingDocumentId: 'abc' }],
    [409, 'DOCUMENT_LIMIT_REACHED', undefined],
    [413, 'PAYLOAD_TOO_LARGE', undefined],
    [415, 'UNSUPPORTED_MEDIA_TYPE', undefined],
    [400, 'EMPTY_FILE', undefined],
    [422, 'IDEMPOTENCY_KEY_REUSED', undefined],
    [503, 'SERVICE_UNAVAILABLE', undefined],
  ])('turns a %i %s answer into an ApiError, keeping safe details', async (status, code, details) => {
    const { requests, handle, lost } = setup()
    requests[0]!.reply(status, { status, code, message: 'x', ...(details ? { details } : {}) })
    const error = await handle.promise.catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status, code, details: details ?? {} })
    expect(lost).not.toHaveBeenCalled()
  })

  it('copes with an error page that is not JSON', async () => {
    const { requests, handle } = setup()
    requests[0]!.reply(502, '<html>Bad gateway</html>')
    await expect(handle.promise).rejects.toMatchObject({ status: 502, code: 'INTERNAL_ERROR' })
  })

  it('reports a network failure and a timeout as such', async () => {
    const a = setup()
    a.requests[0]!.onerror?.()
    await expect(a.handle.promise).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    const b = setup()
    b.requests[0]!.ontimeout?.()
    await expect(b.handle.promise).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' })
  })

  it('cancels with an AbortError and stops the transfer', async () => {
    const { requests, handle } = setup()
    handle.abort()
    const error = await handle.promise.catch((e: unknown) => e)
    expect(requests[0]!.aborted).toBe(true)
    expect(isUploadAbort(error)).toBe(true)
    expect(error).not.toBeInstanceOf(ApiError)
  })

  it('does not mistake an API error for a cancellation', () => {
    expect(isUploadAbort(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: 'x' }))).toBe(false)
    expect(isUploadAbort(new Error('boom'))).toBe(false)
  })

  it('has a generous timeout for a large file', () => {
    expect(setup().requests[0]!.timeout).toBeGreaterThanOrEqual(120_000)
  })
})

describe('uploadDocument: an expired access token', () => {
  const expired = { status: 401, code: 'TOKEN_EXPIRED', message: 'expired' }

  it('renews once and sends the same file again with the same idempotency key and the new token', async () => {
    const refresh = vi.fn().mockResolvedValue('tok-2')
    const { requests, handle, progress } = setup('tok-1', refresh)
    requests[0]!.reply(401, expired)
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(requests[1]!.headers.Authorization).toBe('Bearer tok-2')
    expect(requests[1]!.headers['Idempotency-Key']).toBe('upload-key-123456')
    expect(requests[1]!.body!.get('file')).toBe(requests[0]!.body!.get('file'))
    expect(progress).toHaveBeenCalledWith(0, 1) // the bar starts over for the repeat
    requests[1]!.reply(201, doc)
    await expect(handle.promise).resolves.toMatchObject({ document: { id: 'd1' } })
  })

  it('ends the session and reports the expiry when renewal fails', async () => {
    const { requests, handle, lost } = setup('tok-1', () => Promise.reject(new Error('no')))
    requests[0]!.reply(401, expired)
    await expect(handle.promise).rejects.toMatchObject({ code: 'TOKEN_EXPIRED' })
    expect(lost).toHaveBeenCalledWith('expired')
    expect(requests).toHaveLength(1)
  })

  it('does not loop when the renewed token is refused too', async () => {
    const { requests, handle, lost } = setup('tok-1', () => Promise.resolve('tok-2'))
    requests[0]!.reply(401, expired)
    await vi.waitFor(() => expect(requests).toHaveLength(2))
    requests[1]!.reply(401, expired)
    await expect(handle.promise).rejects.toBeInstanceOf(ApiError)
    expect(requests).toHaveLength(2)
    expect(lost).toHaveBeenCalledWith('expired')
  })

  it('ends the session for an invalid token without trying to renew it', async () => {
    const refresh = vi.fn()
    const { requests, handle, lost } = setup('tok-1', refresh)
    requests[0]!.reply(401, { status: 401, code: 'TOKEN_INVALID', message: 'bad' })
    await expect(handle.promise).rejects.toMatchObject({ code: 'TOKEN_INVALID' })
    expect(refresh).not.toHaveBeenCalled()
    expect(lost).toHaveBeenCalledWith('invalid')
  })

  it('never renews for any other status', async () => {
    const refresh = vi.fn()
    const { requests, handle } = setup('tok-1', refresh)
    requests[0]!.reply(403, { status: 403, code: 'FORBIDDEN', message: 'no' })
    await expect(handle.promise).rejects.toMatchObject({ code: 'FORBIDDEN' })
    expect(refresh).not.toHaveBeenCalled()
  })
})

describe('registered bridge', () => {
  it('is restored for other suites', () => {
    registerAuthBridge({ getToken: () => null, refresh: () => Promise.reject(new Error('none')), sessionLost: () => {} })
    expect(authBridge().getToken()).toBeNull()
    expect(getSession().status).toBe('unknown')
    expect(makeJwt()).toBeTruthy()
  })
})
