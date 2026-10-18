import { useEffect, useId, useRef } from 'react'
import type { ReactNode, SyntheticEvent } from 'react'
import { Button } from './Button'
import styles from './ConfirmActionDialog.module.css'

type ConfirmActionDialogProps = {
  open: boolean
  title: string
  /** What will happen if the user confirms. Required: a confirmation that does not say what it does is not a confirmation. */
  consequence: ReactNode
  confirmLabel: string
  cancelLabel?: string
  tone?: 'primary' | 'danger'
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/** Modal confirmation on the native <dialog>: focus is trapped, Escape cancels, focus returns to the opener. */
export function ConfirmActionDialog({
  open,
  title,
  consequence,
  confirmLabel,
  cancelLabel = 'Cancel',
  tone = 'primary',
  pending = false,
  onConfirm,
  onCancel,
}: ConfirmActionDialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const bodyId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  function handleCancel(event: SyntheticEvent<HTMLDialogElement>) {
    // Escape: keep the dialog under React's control, and never let it vanish mid-request.
    event.preventDefault()
    if (!pending) onCancel()
  }

  return (
    <dialog ref={ref} className={styles.dialog} aria-labelledby={titleId} aria-describedby={bodyId} onCancel={handleCancel}>
      {open ? (
        <div className={styles.inner}>
          <h2 className={styles.title} id={titleId}>
            {title}
          </h2>
          <div className={styles.consequence} id={bodyId}>
            {consequence}
          </div>
          <div className={styles.actions}>
            <Button variant="secondary" onClick={onCancel} disabled={pending} autoFocus>
              {cancelLabel}
            </Button>
            <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={onConfirm} loading={pending} loadingLabel="Working">
              {confirmLabel}
            </Button>
          </div>
        </div>
      ) : null}
    </dialog>
  )
}
