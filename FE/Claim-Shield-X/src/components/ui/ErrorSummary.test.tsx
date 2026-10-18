import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ErrorSummary } from './ErrorSummary'

describe('ErrorSummary', () => {
  it('is announced and can take focus from the form', () => {
    render(<ErrorSummary title="2 things need fixing" items={[{ id: 'a', label: 'Claim reference', message: 'Enter the claim reference.' }]} />)
    const alert = screen.getByRole('alert')
    expect(alert).toHaveAttribute('tabindex', '-1')
    expect(alert).toHaveTextContent('2 things need fixing')
  })

  it('moves focus to the field an entry names, without changing the address', async () => {
    const before = window.location.hash
    render(
      <>
        <ErrorSummary title="Fix these" items={[{ id: 'internalId', label: 'Claim reference', message: 'Enter the claim reference.' }]} />
        <input id="internalId" aria-label="Claim reference field" />
      </>,
    )
    await userEvent.click(screen.getByRole('link', { name: 'Claim reference' }))
    expect(screen.getByLabelText('Claim reference field')).toHaveFocus()
    expect(window.location.hash).toBe(before)
  })

  it('shows each message as text', () => {
    render(<ErrorSummary title="Fix" items={[{ id: 'x', label: 'Policy token', message: '<b>bold</b> Enter it.' }]} />)
    expect(screen.getByText(/<b>bold<\/b> Enter it\./)).toBeInTheDocument()
    expect(document.querySelector('b')).toBeNull()
  })
})
