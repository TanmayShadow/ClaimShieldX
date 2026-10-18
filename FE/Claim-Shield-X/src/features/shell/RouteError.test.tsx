import { render, screen } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { RouteError } from './RouteError'

function renderWith(thrower: () => never, homeTo?: string) {
  const router = createMemoryRouter([{ path: '/', Component: thrower, errorElement: <RouteError homeTo={homeTo} /> }])
  render(<RouterProvider router={router} />)
}

describe('RouteError', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  it('explains a page that crashed without exposing why', async () => {
    renderWith(() => {
      throw new Error('TypeError: x is undefined at SecretComponent.tsx:12')
    })
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong on this page.')
    expect(document.body.textContent).not.toMatch(/TypeError|SecretComponent/)
  })

  it('tells a person whose tab is out of date to reload', async () => {
    renderWith(() => {
      throw new TypeError('Failed to fetch dynamically imported module: /assets/ClaimsPage-abc123.js')
    })
    expect(await screen.findByText('This page could not be loaded.')).toBeInTheDocument()
    expect(screen.getByText(/may have been updated while this tab was open/)).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/ClaimsPage-abc123/)
  })

  it('points home to the place it is told to', async () => {
    renderWith(() => {
      throw new Error('x')
    }, '/')
    expect(await screen.findByRole('link', { name: 'Go to the workspace' })).toHaveAttribute('href', '/')
  })

  it('treats an unknown address as not found, not as a crash', async () => {
    const router = createMemoryRouter([{ path: '/', element: <p>home</p>, errorElement: <RouteError /> }], { initialEntries: ['/nowhere'] })
    render(<RouterProvider router={router} />)
    expect(await screen.findByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).toBeNull()
  })
})
