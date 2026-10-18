import { delay, http, HttpResponse } from 'msw'
import { queryClient } from '@/app/queryClient'
import { makeJwt } from '@/test/jwt'
import { server } from '@/test/server'
import { endSession, ensureSession, getAccessToken, getSession, resetSessionForTests, signIn, signOut } from './session'

const HINT = 'claimshield.session-hint'
const refreshToken = 'REFRESH-TOKEN-VALUE-DO-NOT-STORE'

beforeEach(() => {
  resetSessionForTests()
  window.localStorage.clear()
  window.sessionStorage.clear()
  queryClient.clear()
})

describe('signIn', () => {
  it('opens a session for the person the token names', async () => {
    const token = makeJwt({ sub: 'inv@org-a.test', organizationName: 'Org A' })
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: token, refreshToken })))
    const user = await signIn('inv@org-a.test', 'password-123')
    expect(user).toMatchObject({ email: 'inv@org-a.test', organizationName: 'Org A' })
    expect(getSession()).toMatchObject({ status: 'authenticated' })
    expect(getAccessToken()).toBe(token)
  })

  it('writes neither token to any storage, only a non-secret hint', async () => {
    const token = makeJwt()
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: token, refreshToken })))
    await signIn('a@b.test', 'password-123')
    const written = JSON.stringify(setItem.mock.calls)
    expect(written).not.toContain(token)
    expect(written).not.toContain(refreshToken)
    expect(window.localStorage.getItem(HINT)).toBe('1')
    expect(JSON.stringify({ ...window.localStorage })).not.toContain(token.slice(0, 20))
    expect(JSON.stringify({ ...window.sessionStorage })).not.toContain(token.slice(0, 20))
  })

  it('sends the credentials as JSON and nothing else', async () => {
    let body: unknown
    server.use(
      http.post('*/api/v1/auth/login', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ accessToken: makeJwt() })
      }),
    )
    await signIn('a@b.test', 'password-123')
    expect(body).toEqual({ email: 'a@b.test', password: 'password-123' })
  })

  it.each([
    ['an empty token', { accessToken: '' }],
    ['no token', { refreshToken }],
    ['a token that is not text', { accessToken: 12345 }],
  ])('refuses to sign in on a login answer with %s', async (_label, body) => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json(body)))
    await expect(signIn('a@b.test', 'password-123')).rejects.toMatchObject({ code: 'MALFORMED_RESPONSE' })
    expect(getSession().status).not.toBe('authenticated')
    expect(getAccessToken()).toBeNull()
  })

  it('leaves the session closed and says why when the credentials are wrong', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ code: 'INVALID_CREDENTIALS', status: 401, message: 'Invalid email or password' }, { status: 401 })))
    await expect(signIn('a@b.test', 'wrong')).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' })
    expect(getSession().status).not.toBe('authenticated')
    expect(getAccessToken()).toBeNull()
    expect(window.localStorage.getItem(HINT)).toBeNull()
  })

  it('clears every cached server response, so the next person never sees the last one’s data', async () => {
    queryClient.setQueryData(['claims'], [{ id: 'someone-elses-claim' }])
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: makeJwt() })))
    await signIn('a@b.test', 'password-123')
    expect(queryClient.getQueryData(['claims'])).toBeUndefined()
  })
})

describe('signOut and endSession', () => {
  async function open() {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: makeJwt() })))
    await signIn('a@b.test', 'password-123')
  }

  it('revokes the refresh token on the server, then forgets everything here', async () => {
    await open()
    queryClient.setQueryData(['claims'], ['x'])
    let revoked = 0
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        revoked++
        return new HttpResponse(null, { status: 204 })
      }),
    )
    await signOut()
    expect(revoked).toBe(1)
    expect(getSession()).toMatchObject({ status: 'anonymous', user: null, reason: 'signed-out' })
    expect(getAccessToken()).toBeNull()
    expect(window.localStorage.getItem(HINT)).toBeNull()
    expect(queryClient.getQueryData(['claims'])).toBeUndefined()
  })

  it('still ends the session here when the server cannot be reached', async () => {
    await open()
    server.use(http.post('*/api/v1/auth/logout', () => HttpResponse.error()))
    await signOut()
    expect(getSession().status).toBe('anonymous')
    expect(getAccessToken()).toBeNull()
  })

  it('remembers an expired session so the sign-in page can say so', async () => {
    await open()
    endSession('expired')
    expect(getSession()).toMatchObject({ status: 'anonymous', reason: 'expired' })
  })

  it('does not invent a reason when nobody was signed in', () => {
    endSession()
    expect(getSession().reason).toBeUndefined()
  })
})

describe('ensureSession', () => {
  it('makes no request on a public page when this browser never signed in', async () => {
    const refresh = vi.fn()
    server.use(
      http.post('*/api/v1/auth/refresh', () => {
        refresh()
        return HttpResponse.json({ accessToken: makeJwt() })
      }),
    )
    const state = await ensureSession({ force: false })
    expect(state.status).toBe('anonymous')
    expect(refresh).not.toHaveBeenCalled()
  })

  it('restores a session through the cookie when this browser signed in before', async () => {
    window.localStorage.setItem(HINT, '1')
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: makeJwt({ sub: 'back@x.test' }), refreshToken })))
    const state = await ensureSession({ force: false })
    expect(state).toMatchObject({ status: 'authenticated', user: { email: 'back@x.test' } })
  })

  it('checks even without the hint when a protected page demands it', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: makeJwt() })))
    expect((await ensureSession({ force: true })).status).toBe('authenticated')
  })

  it('becomes anonymous and drops the hint when the refresh token is no longer valid', async () => {
    window.localStorage.setItem(HINT, '1')
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ code: 'REFRESH_TOKEN_INVALID', status: 401, message: 'no' }, { status: 401 })))
    const state = await ensureSession({ force: false })
    expect(state.status).toBe('anonymous')
    expect(window.localStorage.getItem(HINT)).toBeNull()
    expect(state.reason).toBe('expired') // this browser had a session, so the person is told it ended
  })

  it('says nothing about an ended session when this browser never had one', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ code: 'REFRESH_TOKEN_INVALID', status: 401, message: 'no' }, { status: 401 })))
    const state = await ensureSession({ force: true })
    expect(state.status).toBe('anonymous')
    expect(state.reason).toBeUndefined()
  })

  it('shares one refresh between callers, which is what keeps React StrictMode from signing people out', async () => {
    window.localStorage.setItem(HINT, '1')
    let calls = 0
    server.use(
      http.post('*/api/v1/auth/refresh', async () => {
        calls++
        await delay(20)
        return calls === 1 ? HttpResponse.json({ accessToken: makeJwt() }) : HttpResponse.json({ code: 'REFRESH_TOKEN_INVALID', status: 401, message: 'used already' }, { status: 401 })
      }),
    )
    const [a, b, c] = await Promise.all([ensureSession({ force: true }), ensureSession({ force: true }), ensureSession({ force: true })])
    expect(calls).toBe(1)
    expect([a.status, b.status, c.status]).toEqual(['authenticated', 'authenticated', 'authenticated'])
  })

  it('does not ask again once the answer is known', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: makeJwt() })))
    await ensureSession({ force: true })
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ code: 'REFRESH_TOKEN_INVALID', status: 401, message: 'x' }, { status: 401 })))
    expect((await ensureSession({ force: true })).status).toBe('authenticated')
  })
})
