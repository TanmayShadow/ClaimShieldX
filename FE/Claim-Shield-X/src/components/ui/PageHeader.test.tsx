import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { PageHeader } from './PageHeader'

describe('PageHeader', () => {
  it('renders the page title as the single h1, focusable by script', () => {
    render(
      <MemoryRouter>
        <PageHeader title="Claims" description="Everything in your organization." />
      </MemoryRouter>,
    )
    const heading = screen.getByRole('heading', { level: 1, name: 'Claims' })
    expect(heading).toHaveAttribute('tabindex', '-1')
    expect(screen.getByText('Everything in your organization.')).toBeInTheDocument()
  })

  it('renders breadcrumbs as a labelled list with the current page marked', () => {
    render(
      <MemoryRouter>
        <PageHeader title="CLM-1" breadcrumbs={[{ label: 'Claims', to: '/app/claims' }, { label: 'CLM-1' }]} />
      </MemoryRouter>,
    )
    const nav = screen.getByRole('navigation', { name: 'Breadcrumb' })
    expect(screen.getByRole('link', { name: 'Claims' })).toHaveAttribute('href', '/app/claims')
    expect(nav.querySelector('[aria-current="page"]')).toHaveTextContent('CLM-1')
  })

  it('renders actions', () => {
    render(
      <MemoryRouter>
        <PageHeader title="Claims" actions={<button>New claim</button>} />
      </MemoryRouter>,
    )
    expect(screen.getByRole('button', { name: 'New claim' })).toBeInTheDocument()
  })
})
