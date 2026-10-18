import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { createMemoryRouter, Outlet } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { makeJwt } from '@/test/jwt'
import { server } from '@/test/server'
import { endSession, resetSessionForTests, signIn } from '@/features/auth/session'
import { AppShell } from './AppShell'
import { RouteError } from './RouteError'
import type { NavEntry } from './nav'

const entries: NavEntry[] = [
  { label: 'Overview', to: '/app', icon: 'overview', end: true },
  { label: 'Claims', to: '/app/claims', icon: 'folder' },
]

function Boom(): never {
  throw new Error('SECRET internal detail: select * from claims')
}

function setup(path = '/app') {
  const router = createMemoryRouter(
    [
      { path: '/login', element: <p>login page</p> },
      {
        path: '/app',
        element: <AppShell entries={entries} />,
        children: [
          {
            errorElement: <RouteError />,
            element: <Outlet />,
            children: [
              { index: true, element: <h1>Overview page</h1> },
              { path: 'claims', element: <h1>Claims page</h1> },
              { path: 'boom', Component: Boom },
            ],
          },
        ],
      },
    ],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
  return router
}

beforeEach(async () => {
  resetSessionForTests()
  window.localStorage.clear()
  server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: makeJwt({ sub: 'inv@org-a.test', organizationName: 'Org A' }) })))
  await signIn('inv@org-a.test', 'password-123')
})

afterEach(() => {
  endSession()
})

describe('AppShell', () => {
  it('names whose workspace this is and who is signed in', () => {
    setup()
    expect(screen.getByText('Org A')).toBeInTheDocument()
    expect(screen.getByText('inv@org-a.test')).toBeInTheDocument()
  })

  it('has one main landmark that the skip link can reach, and a labelled main navigation', () => {
    setup()
    expect(screen.getAllByRole('main')).toHaveLength(1)
    expect(screen.getByRole('main')).toHaveAttribute('id', 'main')
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
  })

  it('lists exactly the destinations it is given, with the current one marked', () => {
    setup('/app/claims')
    const nav = screen.getByRole('navigation', { name: 'Main' })
    expect(within(nav).getAllByRole('link').map((a) => a.textContent)).toEqual(['Overview', 'Claims'])
    expect(within(nav).getByRole('link', { name: 'Claims' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Overview' })).not.toHaveAttribute('aria-current')
  })

  it('shows only implemented destinations by default', async () => {
    const { navEntries } = await import('./nav')
    expect(navEntries.map((e) => e.label)).toEqual(['Overview', 'Claims', 'History imports'])
    expect(navEntries.map((e) => e.label).join(' ')).not.toMatch(/activity|audit|settings|notification/i)
  })

  it('carries no invented counters, notifications or activity', () => {
    setup()
    expect(document.body.textContent).not.toMatch(/notification|unread|new activity|recent activity|\b\d+ (new|pending|alerts?)\b/i)
  })

  it('signs out through the server, ends the session and leaves for the sign-in page', async () => {
    let revoked = 0
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        revoked++
        return new HttpResponse(null, { status: 204 })
      }),
    )
    setup()
    await userEvent.click(screen.getByRole('button', { name: /Sign out/ }))
    await screen.findByText('login page')
    expect(revoked).toBe(1)
  })

  it('signs out only once however often the button is pressed while the server is still answering', async () => {
    let revoked = 0
    server.use(
      http.post('*/api/v1/auth/logout', async () => {
        revoked++
        await delay(150)
        return new HttpResponse(null, { status: 204 })
      }),
    )
    setup()
    const button = screen.getByRole('button', { name: /Sign out/ })
    await userEvent.dblClick(button)
    await userEvent.click(button)
    expect(button).toHaveAttribute('aria-busy', 'true')
    await screen.findByText('login page')
    expect(revoked).toBe(1)
  })
})

describe('AppShell narrow-screen menu', () => {
  it('starts closed and opens and closes with its button', async () => {
    setup()
    const button = screen.getByRole('button', { name: 'Menu' })
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveAttribute('aria-controls', screen.getByLabelText('Workspace').id)
    await userEvent.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByLabelText('Workspace')).toHaveAttribute('data-open', 'true')
    await userEvent.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  it('closes on Escape and returns focus to its button', async () => {
    setup()
    const button = screen.getByRole('button', { name: 'Menu' })
    await userEvent.click(button)
    within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Claims' }).focus()
    await userEvent.keyboard('{Escape}')
    expect(button).toHaveAttribute('aria-expanded', 'false')
    expect(button).toHaveFocus()
  })

  it('closes when a destination is chosen', async () => {
    setup()
    await userEvent.click(screen.getByRole('button', { name: 'Menu' }))
    await userEvent.click(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Claims' }))
    await screen.findByRole('heading', { name: 'Claims page' })
    expect(screen.getByRole('button', { name: 'Menu' })).toHaveAttribute('aria-expanded', 'false')
  })
})

describe('AppShell connection status', () => {
  it('says so when the browser goes offline, and stops saying so when it returns', async () => {
    setup()
    expect(screen.queryByText('You are offline.')).toBeNull()
    const online = vi.spyOn(navigator, 'onLine', 'get')
    online.mockReturnValue(false)
    act(() => {
      window.dispatchEvent(new Event('offline'))
    })
    expect(await screen.findByRole('status')).toHaveTextContent('You are offline.')
    online.mockReturnValue(true)
    act(() => {
      window.dispatchEvent(new Event('online'))
    })
    await waitFor(() => expect(screen.queryByText('You are offline.')).toBeNull())
  })
})

describe('RouteError inside the shell', () => {
  it('keeps the shell usable and says nothing about the cause', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    setup('/app/boom')
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong on this page.')
    expect(document.body.textContent).not.toMatch(/SECRET|select \* from|Boom/)
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reload the page' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to the workspace' })).toHaveAttribute('href', '/app')
  })

  it('recovers by navigating away', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    setup('/app/boom')
    await screen.findByRole('alert')
    await userEvent.click(within(screen.getByRole('navigation', { name: 'Main' })).getByRole('link', { name: 'Claims' }))
    expect(await screen.findByRole('heading', { name: 'Claims page' })).toBeInTheDocument()
  })
})
