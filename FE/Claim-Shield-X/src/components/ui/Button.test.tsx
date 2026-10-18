import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Button } from './Button'

describe('Button', () => {
  it('is a button that does not submit forms unless asked', () => {
    render(<Button>Save</Button>)
    expect(screen.getByRole('button', { name: 'Save' })).toHaveAttribute('type', 'button')
  })

  it('calls onClick', async () => {
    const onClick = vi.fn()
    render(<Button onClick={onClick}>Save</Button>)
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(onClick).toHaveBeenCalledTimes(1)
  })

  it('while loading it stays focusable, reports busy, swaps its label and ignores clicks', async () => {
    const onClick = vi.fn()
    render(
      <Button loading loadingLabel="Saving" onClick={onClick}>
        Save
      </Button>,
    )
    const button = screen.getByRole('button', { name: 'Saving' })
    expect(button).toHaveAttribute('aria-busy', 'true')
    expect(button).toHaveAttribute('aria-disabled', 'true')
    expect(button).not.toBeDisabled()
    await userEvent.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })

  it('does not submit its form while loading', async () => {
    const onSubmit = vi.fn((e: { preventDefault: () => void }) => e.preventDefault())
    render(
      <form onSubmit={onSubmit}>
        <Button type="submit" loading>
          Send
        </Button>
      </form>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('honours disabled', async () => {
    const onClick = vi.fn()
    render(
      <Button disabled onClick={onClick}>
        Save
      </Button>,
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(onClick).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled()
  })

  it('hides a decorative icon from assistive technology', () => {
    render(<Button icon="arrow-right">Next</Button>)
    expect(screen.getByRole('button', { name: 'Next' }).querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })
})
