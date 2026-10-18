import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EvidenceReference } from './EvidenceReference'

describe('EvidenceReference', () => {
  it('opens its source when activated', async () => {
    const onOpen = vi.fn()
    render(<EvidenceReference kind="document" label="Final invoice, grand total" detail="page 1" onOpen={onOpen} />)
    const button = screen.getByRole('button', { name: 'Document Final invoice, grand total page 1 (opens the source)' })
    await userEvent.click(button)
    expect(onOpen).toHaveBeenCalledTimes(1)
  })

  it('keeps its visible text inside its accessible name, so voice control can say what it sees', () => {
    render(<EvidenceReference kind="document" label="Final invoice, grand total" detail="page 1" onOpen={() => {}} />)
    const button = screen.getByRole('button')
    const visible = button.textContent!.replace('(opens the source)', '').replace(/\s+/g, ' ').trim()
    expect(button).not.toHaveAttribute('aria-label')
    expect(visible).toBe('Document Final invoice, grand total page 1')
    expect(button).toHaveAccessibleName(expect.stringContaining(visible))
  })

  it('can be used from the keyboard', async () => {
    const onOpen = vi.fn()
    render(<EvidenceReference kind="rule" label="Estimate-to-invoice variance" onOpen={onOpen} />)
    screen.getByRole('button').focus()
    await userEvent.keyboard('{Enter}')
    await userEvent.keyboard(' ')
    expect(onOpen).toHaveBeenCalledTimes(2)
  })

  it('renders something that cannot be opened as plain text, not as a dead button', () => {
    render(<EvidenceReference kind="missing" label="Supplementary estimate: not received" />)
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.getByText('Supplementary estimate: not received')).toBeInTheDocument()
    expect(screen.getByText('Not available')).toBeInTheDocument()
  })

  it('ignores an open handler on a missing item', () => {
    render(<EvidenceReference kind="missing" label="Nothing here" onOpen={() => {}} />)
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('shows quoted document text as text, never as markup', () => {
    const { container } = render(<EvidenceReference kind="document" label={'<img src=x onerror=alert(1)> Grand total'} onOpen={() => {}} />)
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByText('<img src=x onerror=alert(1)> Grand total')).toBeInTheDocument()
  })
})
