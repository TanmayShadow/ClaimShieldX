import { useState } from 'react'
import { isApiError } from '@/apis/errors'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Button } from '@/components/ui/Button'
import { TextAreaField } from '@/components/ui/Field'
import { useStartInvestigation } from './hooks'
import styles from './Investigation.module.css'
import type { Investigation } from './schemas'

const MAX_NOTE = 500

type StartPanelProps = {
  claimId: string
  /** The checklist says the one document the stage cannot do without is missing: the run will wait for it. */
  willWait: boolean
  onStarted: (investigation: Investigation) => void
  /** The server refused because one is already running. The list is about to show it, so the page must say why. */
  onAlreadyActive?: () => void
}

export function StartPanel({ claimId, willWait, onStarted, onAlreadyActive }: StartPanelProps) {
  const [note, setNote] = useState('')
  const start = useStartInvestigation(claimId, (i) => {
    setNote('')
    onStarted(i)
  })
  const tooLong = note.trim().length > MAX_NOTE

  return (
    <form
      className={styles.startForm}
      onSubmit={(e) => {
        e.preventDefault()
        if (start.isPending || tooLong) return
        start.mutate(note.trim() || undefined, {
          onError: (error) => {
            if (isApiError(error) && error.code === 'INVESTIGATION_ALREADY_ACTIVE') onAlreadyActive?.()
          },
        })
      }}
      noValidate
    >
      <p>
        An investigation prepares the evidence, reads any invoice or estimate that has not been read, and runs the deterministic checks. It does not decide anything about the claim.
        {willWait ? ' The document this claim stage cannot do without has not been received, so the investigation will wait for it and carry on as soon as it is uploaded.' : ''}
      </p>
      <TextAreaField
        label="Note"
        optional
        rows={3}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        hint={`Up to ${MAX_NOTE} characters. It is kept for whoever reads this investigation later and is never sent to a model.`}
        error={tooLong ? `Use at most ${MAX_NOTE} characters.` : undefined}
      />
      {start.isError ? <ErrorPanel error={start.error} subject="that claim" /> : null}
      <div>
        <Button type="submit" variant="primary" loading={start.isPending} loadingLabel="Starting" disabled={tooLong}>
          Start investigation
        </Button>
      </div>
    </form>
  )
}
