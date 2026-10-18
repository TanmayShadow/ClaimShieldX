import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ConfirmActionDialog } from './ConfirmActionDialog'

function setup(props: Partial<React.ComponentProps<typeof ConfirmActionDialog>> = {}) {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  render(
    <ConfirmActionDialog
      open
      title="Record this action?"
      consequence="This is stored in the audit history and cannot be edited."
      confirmLabel="Record action"
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    />,
  )
  return { onConfirm, onCancel }
}

describe('ConfirmActionDialog', () => {
  it('names the dialog and describes the consequence', () => {
    setup()
    const dialog = screen.getByRole('dialog', { name: 'Record this action?', hidden: true })
    expect(dialog).toHaveAccessibleDescription('This is stored in the audit history and cannot be edited.')
  })

  it('renders none of its content while closed', () => {
    setup({ open: false })
    expect(screen.queryByText('Record this action?')).toBeNull()
  })

  it('confirms and cancels through its buttons', async () => {
    const { onConfirm, onCancel } = setup()
    await userEvent.click(screen.getByRole('button', { name: 'Record action', hidden: true }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
    await userEvent.click(screen.getByRole('button', { name: 'Cancel', hidden: true }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('cancels on Escape', () => {
    const { onCancel } = setup()
    fireEvent(screen.getByRole('dialog', { hidden: true }), new Event('cancel', { cancelable: true }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('ignores Escape and a repeat click while the action is pending', async () => {
    const { onConfirm, onCancel } = setup({ pending: true })
    fireEvent(screen.getByRole('dialog', { hidden: true }), new Event('cancel', { cancelable: true }))
    expect(onCancel).not.toHaveBeenCalled()
    const confirm = screen.getByRole('button', { name: 'Working', hidden: true })
    expect(confirm).toHaveAttribute('aria-busy', 'true')
    await userEvent.click(confirm)
    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Cancel', hidden: true })).toBeDisabled()
  })
})
