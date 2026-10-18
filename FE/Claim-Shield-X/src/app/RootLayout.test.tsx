import { act, render, screen } from '@testing-library/react'
import { createMemoryRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { PageHeader } from '@/components/ui/PageHeader'
import { RootLayout } from './RootLayout'

function setup() {
  const router = createMemoryRouter(
    [
      {
        path: '/',
        Component: RootLayout,
        children: [
          { index: true, element: <PageHeader title="Home" /> },
          { path: 'next', element: <PageHeader title="Next page" /> },
        ],
      },
    ],
    { initialEntries: ['/'] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe('RootLayout', () => {
  it('offers a skip link to the main content', () => {
    setup()
    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveAttribute('href', '#main')
  })

  it('leaves focus alone on first load, so the skip link stays the first tab stop', () => {
    setup()
    expect(document.body).toHaveFocus()
  })

  it('moves focus to the new page title after a client-side navigation', async () => {
    const router = setup()
    await act(async () => {
      await router.navigate('/next')
    })
    expect(screen.getByRole('heading', { level: 1, name: 'Next page' })).toHaveFocus()
  })
})
