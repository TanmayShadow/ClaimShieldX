import { render, screen } from '@testing-library/react'
import { Callout } from './Callout'
import { EmptyState } from './EmptyState'
import { Sheet } from './Sheet'
import { Skeleton } from './Skeleton'

describe('Callout', () => {
  it('announces danger immediately and everything else politely', () => {
    const { rerender } = render(<Callout tone="danger" title="Could not load" />)
    expect(screen.getByRole('alert')).toHaveTextContent('Could not load')
    rerender(<Callout tone="info" title="FYI" />)
    expect(screen.queryByRole('alert')).toBeNull()
    expect(screen.getByRole('status')).toHaveTextContent('FYI')
  })

  it('does not add a heading to the document outline', () => {
    render(<Callout title="Note" />)
    expect(screen.queryByRole('heading')).toBeNull()
  })
})

describe('Skeleton', () => {
  it('tells assistive technology what is loading', () => {
    render(<Skeleton label="Loading claims" />)
    expect(screen.getByRole('status')).toHaveTextContent('Loading claims…')
    expect(screen.getByRole('status')).toHaveAttribute('aria-busy', 'true')
  })
})

describe('EmptyState', () => {
  it('states what is empty and offers the next step', () => {
    render(<EmptyState title="No claims yet" action={<button>Create a claim</button>}>Claims appear here.</EmptyState>)
    expect(screen.getByRole('heading', { name: 'No claims yet' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create a claim' })).toBeInTheDocument()
  })
})

describe('Sheet', () => {
  it('renders its evidence tab as plain text', () => {
    render(<Sheet tab="<b>Document 03</b>">content</Sheet>)
    expect(screen.getByText('<b>Document 03</b>')).toBeInTheDocument()
    expect(document.querySelector('b')).toBeNull()
  })
})
