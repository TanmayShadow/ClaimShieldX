import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { LoginPage } from '@/pages/LoginPage'
import { makeJwt } from '@/test/jwt'
import { server } from '@/test/server'
import { endSession, getSession, resetSessionForTests } from './session'

function Where() {
  const { pathname, search } = useLocation()
  return <output data-testid="where">{pathname + search}</output>
}

function setup(entry: string | { pathname: string; state?: unknown } = '/login') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  )
}

const email = () => screen.getByLabelText('Email address')
const password = () => screen.getByLabelText('Password')
const submit = () => screen.getByRole('button', { name: 'Sign in' })

function loginHandler(handler: Parameters<typeof http.post>[1]) {
  server.use(http.post('*/api/v1/auth/login', handler))
}

beforeEach(() => {
  resetSessionForTests()
  window.localStorage.clear()
  endSession()
})

describe('LoginPage form', () => {
  it('has the right input types, autocomplete hints and labels', () => {
    setup()
    expect(email()).toHaveAttribute('type', 'email')
    expect(email()).toHaveAttribute('autocomplete', 'username')
    expect(email()).toBeRequired()
    expect(password()).toHaveAttribute('type', 'password')
    expect(password()).toHaveAttribute('autocomplete', 'current-password')
    expect(password()).toBeRequired()
    expect(screen.getByRole('heading', { level: 1, name: 'Sign in' })).toBeInTheDocument()
  })

  it('offers only the sign-in the backend supports', () => {
    setup()
    expect(screen.queryByText(/forgot/i)).toBeNull()
    expect(screen.queryByText(/google|sso|single sign|continue with/i)).toBeNull()
    expect(screen.queryByRole('link', { name: /sign up|register|create account/i })).toBeNull()
    expect(screen.getByText(/no password reset or self-service sign-up/i)).toBeInTheDocument()
  })

  it('shows field errors, focuses the first invalid field, and sends nothing', async () => {
    const calls = vi.fn()
    loginHandler(() => {
      calls()
      return HttpResponse.json({ accessToken: makeJwt() })
    })
    setup()
    await userEvent.click(submit())
    expect(screen.getByText('Enter your email address.')).toBeInTheDocument()
    expect(screen.getByText('Enter your password.')).toBeInTheDocument()
    expect(email()).toHaveFocus()
    expect(email()).toBeInvalid()
    expect(email()).toHaveAccessibleDescription('Enter your email address.')
    expect(calls).not.toHaveBeenCalled()
  })

  it('rejects a malformed email and focuses the password when only it is missing', async () => {
    setup()
    await userEvent.type(email(), 'not-an-email')
    await userEvent.click(submit())
    expect(screen.getByText(/Enter a valid email address/)).toBeInTheDocument()
    await userEvent.clear(email())
    await userEvent.type(email(), 'a@b.test')
    await userEvent.click(submit())
    expect(password()).toHaveFocus()
  })

  it('reveals and hides the password with a labelled toggle', async () => {
    setup()
    await userEvent.type(password(), 'hunter2-hunter2')
    const toggle = screen.getByRole('button', { name: 'Show password' })
    await userEvent.click(toggle)
    expect(password()).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Hide password' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Hide password' }))
    expect(password()).toHaveAttribute('type', 'password')
    expect(password()).toHaveValue('hunter2-hunter2')
  })

  it('shows a pending state and ignores a second activation while signing in', async () => {
    let calls = 0
    loginHandler(async () => {
      calls++
      await delay(80)
      return HttpResponse.json({ accessToken: makeJwt() })
    })
    setup()
    await userEvent.type(email(), 'a@b.test')
    await userEvent.type(password(), 'password-123')
    await userEvent.click(submit())
    const busy = screen.getByRole('button', { name: 'Signing in' })
    expect(busy).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(busy)
    await userEvent.keyboard('{Enter}')
    await waitFor(() => expect(screen.getByTestId('where')).toBeInTheDocument())
    expect(calls).toBe(1)
  })
})

describe('LoginPage outcomes', () => {
  async function fill() {
    await userEvent.type(email(), 'inv@org-a.test')
    await userEvent.type(password(), 'password-123')
    await userEvent.click(submit())
  }

  it('signs in and goes to the signed-in area by default', async () => {
    loginHandler(() => HttpResponse.json({ accessToken: makeJwt(), refreshToken: 'ignored' }))
    setup()
    await fill()
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/app'))
    expect(getSession().status).toBe('authenticated')
  })

  it('returns to the page that was asked for', async () => {
    loginHandler(() => HttpResponse.json({ accessToken: makeJwt() }))
    setup({ pathname: '/login', state: { from: '/app/claims?stage=FNOL' } })
    await fill()
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/app/claims?stage=FNOL'))
  })

  it.each(['//evil.example', 'https://evil.example/app', '/\\evil.example', '/login', 'javascript:alert(1)'])('ignores an unsafe return address %s from the query string', async (bad) => {
    loginHandler(() => HttpResponse.json({ accessToken: makeJwt() }))
    setup(`/login?returnTo=${encodeURIComponent(bad)}`)
    await fill()
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent(/^\/app$/))
  })

  it('ignores an unsafe return address passed in navigation state', async () => {
    loginHandler(() => HttpResponse.json({ accessToken: makeJwt() }))
    setup({ pathname: '/login', state: { from: '//evil.example' } })
    await fill()
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent(/^\/app$/))
  })

  it('says only that the email or password is wrong, and clears the password', async () => {
    loginHandler(() => HttpResponse.json({ code: 'INVALID_CREDENTIALS', status: 401, message: 'Invalid email or password' }, { status: 401 }))
    setup()
    await fill()
    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is not correct.')
    expect(password()).toHaveValue('')
    expect(password()).toHaveFocus()
    expect(email()).toHaveValue('inv@org-a.test')
    expect(screen.queryByText(/no account|unknown (user|email)|not registered|exists/i)).toBeNull()
    expect(getSession().status).not.toBe('authenticated')
  })

  it('gives the same message for a wrong password and an unknown email', async () => {
    const body = { code: 'INVALID_CREDENTIALS', status: 401, message: 'Invalid email or password' }
    const messages: string[] = []
    for (const address of ['known@org-a.test', 'nobody@org-a.test']) {
      loginHandler(() => HttpResponse.json(body, { status: 401 }))
      const { unmount } = setup()
      await userEvent.type(email(), address)
      await userEvent.type(password(), 'password-123')
      await userEvent.click(submit())
      messages.push((await screen.findByRole('alert')).textContent!)
      unmount()
    }
    expect(messages[0]).toBe(messages[1])
  })

  it('explains a network failure and keeps what was typed', async () => {
    loginHandler(() => HttpResponse.error())
    setup()
    await fill()
    expect(await screen.findByRole('alert')).toHaveTextContent('The server could not be reached.')
    expect(password()).toHaveValue('password-123')
    expect(submit()).not.toHaveAttribute('aria-busy')
  })

  it('explains a server failure without exposing what the server said', async () => {
    loginHandler(() => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'NullPointerException at com.example.Secret' }, { status: 500 }))
    setup()
    await fill()
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Signing in did not work.')
    expect(alert).not.toHaveTextContent(/NullPointer|com\.example/)
  })

  it('explains rate limiting', async () => {
    loginHandler(() => HttpResponse.json({ code: 'RATE_LIMITED', status: 429, message: 'slow down' }, { status: 429, headers: { 'Retry-After': '30' } }))
    setup()
    await fill()
    expect(await screen.findByRole('alert')).toHaveTextContent(/Too many sign-in attempts.*30 seconds/)
  })

  it('does not put the password anywhere on the page after a failure', async () => {
    loginHandler(() => HttpResponse.json({ code: 'INVALID_CREDENTIALS', status: 401, message: 'no' }, { status: 401 }))
    const { container } = setup()
    await fill()
    await screen.findByRole('alert')
    expect(container.innerHTML).not.toContain('password-123')
  })
})

describe('LoginPage session notices', () => {
  it('says so when a session has ended', () => {
    endSession('expired')
    setup()
    expect(screen.getByText('Your session has ended.')).toBeInTheDocument()
  })

  it('confirms a sign-out', () => {
    endSession('signed-out')
    setup()
    expect(screen.getByText('You have signed out.')).toBeInTheDocument()
  })

  it('sends someone who is already signed in straight on', async () => {
    window.localStorage.setItem('claimshield.session-hint', '1')
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({ accessToken: makeJwt() })))
    resetSessionForTests()
    setup()
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/app'))
  })

  it('makes no request for a visitor who has never signed in', () => {
    setup()
    expect(email()).toBeInTheDocument()
  })
})
