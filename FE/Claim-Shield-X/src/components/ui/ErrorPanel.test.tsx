import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError, networkError } from '@/apis/errors'
import { ErrorPanel } from './ErrorPanel'

describe('ErrorPanel', () => {
  it('announces the problem and offers a retry when retrying can help', async () => {
    const onRetry = vi.fn()
    render(<ErrorPanel error={networkError()} onRetry={onRetry} />)
    expect(screen.getByRole('alert')).toHaveTextContent('The server could not be reached.')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
    expect(screen.getByText('Code NETWORK_ERROR')).toBeInTheDocument()
  })

  it('offers no retry for something retrying cannot fix', () => {
    render(<ErrorPanel error={new ApiError({ status: 403, code: 'FORBIDDEN', message: 'x' })} onRetry={() => {}} />)
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
    expect(screen.getByText('You do not have permission to do this.')).toBeInTheDocument()
  })

  it('shows a busy retry that ignores further clicks', async () => {
    const onRetry = vi.fn()
    render(<ErrorPanel error={networkError()} onRetry={onRetry} retrying />)
    const button = screen.getByRole('button', { name: 'Trying again' })
    expect(button).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(button)
    expect(onRetry).not.toHaveBeenCalled()
  })

  it('never shows what the server said', () => {
    render(<ErrorPanel error={new ApiError({ status: 500, code: 'INTERNAL_ERROR', message: 'java.lang.NullPointerException at Foo.java:9' })} />)
    expect(document.body.textContent).not.toMatch(/NullPointer|Foo\.java/)
  })
})
