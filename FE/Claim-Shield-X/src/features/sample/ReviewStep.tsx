import { useId, useState } from 'react'
import { Button } from '@/components/ui/Button'
import { Callout } from '@/components/ui/Callout'
import { TextAreaField } from '@/components/ui/Field'
import { Sheet } from '@/components/ui/Sheet'
import { reviewActions } from './data'
import styles from './Sample.module.css'
import type { ReviewAction } from './types'

const MIN = 10
const MAX = 2000

export function ReviewStep() {
  const legend = useId()
  const [action, setAction] = useState<ReviewAction | null>(null)
  const [rationale, setRationale] = useState('')
  const [error, setError] = useState<string | undefined>()
  const [previewed, setPreviewed] = useState<{ action: ReviewAction; rationale: string } | null>(null)

  function submit(event: React.FormEvent) {
    event.preventDefault()
    const text = rationale.trim()
    if (!action) {
      setError('Choose one of the four actions.')
      return
    }
    if (text.length < MIN || text.length > MAX) {
      setError(`Write between ${MIN} and ${MAX} characters of rationale. The reason is part of the record.`)
      return
    }
    setError(undefined)
    setPreviewed({ action, rationale: text })
  }

  const chosen = previewed ? reviewActions.find((a) => a.value === previewed.action) : null

  return (
    <div className={styles.stack} data-focus-target="" tabIndex={-1}>
      <Callout tone="caution" title="A preview, and nothing is saved">
        <p>
          This form only previews what a decision would contain. Nothing is sent or stored, and the page works the same with the network switched off. In a real
          investigation the Report tab records a decision permanently.
        </p>
      </Callout>

      <div className={styles.split}>
        <form className={styles.form} onSubmit={submit} noValidate aria-describedby={error ? `${legend}-error` : undefined}>
          <fieldset className={styles.fieldset}>
            <legend id={legend}>What does the reviewer do next?</legend>
            {reviewActions.map((a) => (
              <label key={a.value} className={styles.radio}>
                <input type="radio" name="review-action" value={a.value} checked={action === a.value} onChange={() => setAction(a.value)} />
                <span>
                  <strong>{a.label}</strong>
                  <small>{a.meaning}</small>
                </span>
              </label>
            ))}
          </fieldset>

          <TextAreaField
            label="Rationale"
            hint={`Between ${MIN} and ${MAX} characters. In the product this is written by the reviewer and cannot be edited afterwards.`}
            value={rationale}
            onChange={(e) => setRationale(e.target.value)}
            rows={4}
            maxLength={MAX + 200}
          />

          {error ? (
            <p role="alert" id={`${legend}-error`} style={{ color: 'var(--danger)', fontWeight: 500 }}>
              {error}
            </p>
          ) : null}

          <div className={styles.actionsRow}>
            <Button type="submit" variant="primary">
              Preview the entry
            </Button>
            {previewed ? (
              <Button
                onClick={() => {
                  setPreviewed(null)
                  setAction(null)
                  setRationale('')
                }}
              >
                Start again
              </Button>
            ) : null}
          </div>
        </form>

        <div className={styles.stack}>
          {previewed && chosen ? (
            <Sheet tab="Entry preview · not saved" folded aria-live="polite">
              <div className={styles.entry}>
                <h3 style={{ fontSize: 'var(--text-lg)' }}>{chosen.label}</h3>
                <dl className={styles.kv}>
                  <dt>Action</dt>
                  <dd className="mono">{previewed.action}</dd>
                  <dt>Rationale</dt>
                  <dd>{previewed.rationale}</dd>
                  <dt>Report version</dt>
                  <dd className="mono">demo-1 (synthetic)</dd>
                  <dt>Recorded by</dt>
                  <dd>The signed-in reviewer, set by the server. Not set here.</dd>
                  <dt>Time</dt>
                  <dd>Set by the server. Not set here.</dd>
                </dl>
                <p className={styles.meaning}>
                  In the product the entry is append-only: a later action never erases an earlier one. It also stores what the tool recommended at that moment. None of
                  the four actions means approve, reject, settle or fraud.
                </p>
              </div>
            </Sheet>
          ) : (
            <Sheet tab="Entry preview" padded>
              <p className={styles.mono}>Choose an action and write a rationale, then preview the entry. The preview stays on this page.</p>
            </Sheet>
          )}
        </div>
      </div>
    </div>
  )
}
