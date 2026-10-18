import { render, screen, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { makeJwt } from '@/test/jwt'
import { server } from '@/test/server'
import { RequireAuth } from './RequireAuth'
import { endSession, resetSessionForTests, signIn, signOut, useSession } from './session'

function Login() {
  const { state } = useLocation()
  return <output data-testid="login">{JSON.stringify(state)}</output>
}

function Page() {
  const session = useSession()
  return (
    <>
      <h1>Workspace</h1>
      <p>
        Signed in as {session.user?.email} · {session.user?.organizationName}
      </p>
    </>
  )
}

function setup(entry = '/app') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/app" element={<RequireAuth />}>
          <Route index element={<Page />} />
          <Route path="claims" element={<p>claims page</p>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  resetSessionForTests()
  window.localStorage.clear()
})

describe('RequireAuth', () => {
  it('shows a loading state while it asks the server whether a session exists', async () => {
    let release: () => void = () => {}
    const gate = new Promise<void>((r) => (release = r))
    server.use(
      http.post('*/api/v1/auth/refresh', async () => {
        await gate
        return HttpResponse.json({ accessToken: makeJwt() })
      }),
    )
    setup()
    expect(screen.getByRole('status')).toHaveTextContent('Checking your session')
    expect(screen.queryByRole('heading', { name: 'Workspace' })).toBeNull()
    release()
    expect(await screen.findByRole('heading', { level: 1, name: 'Workspace' })).toBeInTheDocument()
  })

  it('restores a session from the refresh cookie on a direct visit, without the sign-in hint', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: makeJwt({ sub: 'back@x.test', organizationName: 'Org A' }) })))
    setup()
    expect(await screen.findByText(/Signed in as back@x.test · Org A/)).toBeInTheDocument()
  })

  it('sends a visitor with no session to sign in and remembers where they were going', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ code: 'REFRESH_TOKEN_INVALID', status: 401, message: 'no' }, { status: 401 })))
    setup('/app')
    const login = await screen.findByTestId('login')
    expect(JSON.parse(login.textContent!)).toEqual({ from: '/app' })
  })

  it('renders nothing of the protected page for a visitor', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ code: 'REFRESH_TOKEN_INVALID', status: 401, message: 'no' }, { status: 401 })))
    setup()
    await screen.findByTestId('login')
    expect(screen.queryByText(/Signed in as/)).toBeNull()
  })

  it('lets a signed-in person through without asking again', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: makeJwt() })))
    await signIn('a@b.test', 'password-123')
    setup()
    expect(await screen.findByRole('heading', { level: 1, name: 'Workspace' })).toBeInTheDocument()
  })

  it('sends someone to sign in the moment their session ends, and keeps the page they were on', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: makeJwt() })))
    await signIn('a@b.test', 'password-123')
    setup()
    await screen.findByRole('heading', { level: 1, name: 'Workspace' })
    endSession('expired')
    await waitFor(() => expect(screen.getByTestId('login')).toBeInTheDocument())
  })

  it('sends someone to sign in after they sign out', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: makeJwt() })),
      http.post('*/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
    )
    await signIn('a@b.test', 'password-123')
    setup()
    await screen.findByRole('heading', { level: 1, name: 'Workspace' })
    await signOut()
    await waitFor(() => expect(screen.getByTestId('login')).toBeInTheDocument())
    expect(screen.queryByRole('heading', { name: 'Workspace' })).toBeNull()
  })
})
