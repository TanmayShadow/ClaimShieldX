import { delay, http, HttpResponse } from 'msw'
import { z } from 'zod'
import { endSession, getSession, resetSessionForTests } from '@/features/auth/session'
import { server } from '@/test/server'
import { makeJwt } from '@/test/jwt'
import { apiRequest, buildUrl } from './client'
import { ApiError } from './errors'

const expired = { code: 'TOKEN_EXPIRED', status: 401, error: 'Unauthorized', message: 'Access token has expired' }
const claimSchema = z.object({ id: z.string(), internalId: z.string() })

async function signedIn(token = makeJwt()) {
  // Sign in through the real session code so the bridge holds a real token.
  server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: token, refreshToken: 'ignored' })))
  const { signIn } = await import('@/features/auth/session')
  await signIn('a@b.test', 'secret-password')
  return token
}

beforeEach(() => {
  resetSessionForTests()
  window.localStorage.clear()
})

describe('buildUrl', () => {
  it('keeps the base relative and drops empty query values', () => {
    expect(buildUrl('/claims', { page: 0, size: 20, stage: undefined, q: '', active: false })).toBe('/api/v1/claims?page=0&size=20&active=false')
  })

  it('encodes values', () => {
    expect(buildUrl('/claims', { q: 'a&b=c d' })).toBe('/api/v1/claims?q=a%26b%3Dc+d')
  })
})

describe('apiRequest', () => {
  it('sends the access token and validates the body', async () => {
    const token = await signedIn()
    let seen: string | null = null
    server.use(
      http.get('*/api/v1/claims/1', ({ request }) => {
        seen = request.headers.get('authorization')
        return HttpResponse.json({ id: '1', internalId: 'CLM-1', extra: 'dropped' })
      }),
    )
    const claim = await apiRequest('/claims/1', { schema: claimSchema })
    expect(seen).toBe(`Bearer ${token}`)
    expect(claim).toEqual({ id: '1', internalId: 'CLM-1' })
  })

  it('sends JSON bodies as JSON and form data without forcing a content type', async () => {
    await signedIn()
    const seen: Array<string | null> = []
    server.use(
      http.post('*/api/v1/json', async ({ request }) => {
        seen.push(request.headers.get('content-type'))
        return HttpResponse.json(await request.json())
      }),
      http.post('*/api/v1/upload', ({ request }) => {
        seen.push(request.headers.get('content-type'))
        return new HttpResponse(null, { status: 204 })
      }),
    )
    expect(await apiRequest('/json', { method: 'POST', body: { a: 1 } })).toEqual({ a: 1 })
    const form = new FormData()
    form.append('documentType', 'FINAL_INVOICE')
    expect(await apiRequest('/upload', { method: 'POST', body: form })).toBeUndefined()
    expect(seen[0]).toBe('application/json')
    expect(seen[1]).toMatch(/^multipart\/form-data; boundary=/)
  })

  it('does not send a token on the auth endpoints', async () => {
    await signedIn()
    let auth: string | null = 'unset'
    server.use(
      http.post('*/api/v1/auth/x', ({ request }) => {
        auth = request.headers.get('authorization')
        return HttpResponse.json({})
      }),
    )
    await apiRequest('/auth/x', { method: 'POST', auth: false })
    expect(auth).toBeNull()
  })

  it('turns an error body into an ApiError with the contract code', async () => {
    await signedIn()
    server.use(
      http.post('*/api/v1/claims', () =>
        HttpResponse.json({ status: 409, error: 'Conflict', code: 'DUPLICATE_CLAIM', message: 'A claim with the same internal id already exists' }, { status: 409 }),
      ),
    )
    const error = await apiRequest('/claims', { method: 'POST', body: {} }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 409, code: 'DUPLICATE_CLAIM' })
  })

  it('reports a body that does not match the schema as MALFORMED_RESPONSE and logs no values', async () => {
    await signedIn()
    server.use(http.get('*/api/v1/claims/1', () => HttpResponse.json({ id: 7, internalId: 'SECRET-CLAIM-DATA' })))
    const log = vi.spyOn(console, 'error').mockImplementation(() => {})
    const error = await apiRequest('/claims/1', { schema: claimSchema }).catch((e: unknown) => e)
    expect(error).toMatchObject({ code: 'MALFORMED_RESPONSE' })
    expect(JSON.stringify(log.mock.calls)).not.toContain('SECRET-CLAIM-DATA')
  })

  it('treats an unreadable success body as malformed when a schema is required', async () => {
    await signedIn()
    server.use(http.get('*/api/v1/claims/1', () => new HttpResponse('<html>proxy page</html>', { status: 200 })))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    await expect(apiRequest('/claims/1', { schema: claimSchema })).rejects.toMatchObject({ code: 'MALFORMED_RESPONSE' })
  })

  it('reports an unreachable server as NETWORK_ERROR', async () => {
    await signedIn()
    server.use(http.get('*/api/v1/claims', () => HttpResponse.error()))
    await expect(apiRequest('/claims')).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 })
  })

  it('reports a slow server as REQUEST_TIMEOUT', async () => {
    await signedIn()
    server.use(
      http.get('*/api/v1/slow', async () => {
        await delay(300)
        return HttpResponse.json({})
      }),
    )
    await expect(apiRequest('/slow', { timeoutMs: 30 })).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' })
  })

  it('lets the caller cancel, and says so with an AbortError rather than an API error', async () => {
    await signedIn()
    server.use(
      http.get('*/api/v1/slow', async () => {
        await delay(300)
        return HttpResponse.json({})
      }),
    )
    const controller = new AbortController()
    const pending = apiRequest('/slow', { signal: controller.signal }).catch((e: unknown) => e)
    controller.abort()
    const error = await pending
    expect(error).not.toBeInstanceOf(ApiError)
    expect((error as Error).name).toBe('AbortError')
  })
})

describe('expired access token', () => {
  it('renews once and repeats the request with the new token', async () => {
    await signedIn(makeJwt({ sub: 'old@x.test' }))
    const fresh = makeJwt({ sub: 'new@x.test' })
    const calls: string[] = []
    server.use(
      http.get('*/api/v1/claims/1', ({ request }) => {
        const auth = request.headers.get('authorization')!
        calls.push(auth)
        return auth.endsWith(fresh) ? HttpResponse.json({ id: '1', internalId: 'CLM-1' }) : HttpResponse.json(expired, { status: 401 })
      }),
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: fresh, refreshToken: 'rotated' })),
    )
    await expect(apiRequest('/claims/1', { schema: claimSchema })).resolves.toEqual({ id: '1', internalId: 'CLM-1' })
    expect(calls).toHaveLength(2)
    expect(getSession().user?.email).toBe('new@x.test')
  })

  it('shares one renewal between simultaneous requests, because the refresh token works only once', async () => {
    await signedIn(makeJwt({ sub: 'old@x.test' }))
    const fresh = makeJwt({ sub: 'new@x.test' })
    let refreshes = 0
    server.use(
      http.get('*/api/v1/claims/:id', ({ request, params }) =>
        request.headers.get('authorization')!.endsWith(fresh) ? HttpResponse.json({ id: String(params.id), internalId: 'C' }) : HttpResponse.json(expired, { status: 401 }),
      ),
      http.post('*/api/v1/auth/refresh', async () => {
        refreshes++
        await delay(30)
        return HttpResponse.json({ accessToken: fresh })
      }),
    )
    const results = await Promise.all(['1', '2', '3', '4'].map((id) => apiRequest(`/claims/${id}`, { schema: claimSchema })))
    expect(results.map((r) => r.id)).toEqual(['1', '2', '3', '4'])
    expect(refreshes).toBe(1)
  })

  it('ends the session and reports the original error when renewal fails', async () => {
    await signedIn()
    let requests = 0
    server.use(
      http.get('*/api/v1/claims/1', () => {
        requests++
        return HttpResponse.json(expired, { status: 401 })
      }),
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ code: 'REFRESH_TOKEN_EXPIRED', status: 401, message: 'Please log in again' }, { status: 401 })),
    )
    await expect(apiRequest('/claims/1')).rejects.toMatchObject({ code: 'TOKEN_EXPIRED' })
    expect(requests).toBe(1)
    expect(getSession()).toMatchObject({ status: 'anonymous', user: null, reason: 'expired' })
  })

  it('does not loop when the renewed token is refused too', async () => {
    await signedIn()
    let requests = 0
    server.use(
      http.get('*/api/v1/claims/1', () => {
        requests++
        return HttpResponse.json(expired, { status: 401 })
      }),
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: makeJwt() })),
    )
    await expect(apiRequest('/claims/1')).rejects.toBeInstanceOf(ApiError)
    expect(requests).toBe(2)
    expect(getSession().status).toBe('anonymous')
  })

  it('does not try to renew a token that is invalid, only end the session', async () => {
    await signedIn()
    let refreshes = 0
    server.use(
      http.get('*/api/v1/claims/1', () => HttpResponse.json({ code: 'TOKEN_INVALID', status: 401, message: 'bad token' }, { status: 401 })),
      http.post('*/api/v1/auth/refresh', () => {
        refreshes++
        return HttpResponse.json({ accessToken: makeJwt() })
      }),
    )
    await expect(apiRequest('/claims/1')).rejects.toMatchObject({ code: 'TOKEN_INVALID' })
    expect(refreshes).toBe(0)
    expect(getSession().status).toBe('anonymous')
  })

  it.each([
    [400, 'VALIDATION_FAILED'],
    [403, 'FORBIDDEN'],
    [404, 'NOT_FOUND'],
    [409, 'DUPLICATE_CLAIM'],
    [413, 'PAYLOAD_TOO_LARGE'],
    [422, 'IDEMPOTENCY_KEY_REUSED'],
    [429, 'RATE_LIMITED'],
    [500, 'INTERNAL_ERROR'],
    [503, 'SERVICE_UNAVAILABLE'],
  ])('never renews or repeats a request for a %i', async (status, code) => {
    await signedIn()
    let requests = 0
    let refreshes = 0
    server.use(
      http.post('*/api/v1/claims', () => {
        requests++
        return HttpResponse.json({ status, code, message: 'no' }, { status })
      }),
      http.post('*/api/v1/auth/refresh', () => {
        refreshes++
        return HttpResponse.json({ accessToken: makeJwt() })
      }),
    )
    await expect(apiRequest('/claims', { method: 'POST', body: {} })).rejects.toMatchObject({ status, code })
    expect(requests).toBe(1)
    expect(refreshes).toBe(0)
    expect(getSession().status).toBe('authenticated')
  })

  it('tries one renewal when a protected call finds no token at all', async () => {
    resetSessionForTests()
    const fresh = makeJwt({ sub: 'restored@x.test' })
    server.use(
      http.get('*/api/v1/claims/1', ({ request }) =>
        request.headers.get('authorization') === `Bearer ${fresh}`
          ? HttpResponse.json({ id: '1', internalId: 'C' })
          : HttpResponse.json({ code: 'UNAUTHENTICATED', status: 401, message: 'Authentication is required' }, { status: 401 }),
      ),
      http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: fresh })),
    )
    await expect(apiRequest('/claims/1', { schema: claimSchema })).resolves.toEqual({ id: '1', internalId: 'C' })
    endSession()
  })
})
