import { useState } from 'react'
import { describeApiError } from '@/apis/describeError'
import { Button } from '@/components/ui/Button'
import { Callout } from '@/components/ui/Callout'
import { ConfirmActionDialog } from '@/components/ui/ConfirmActionDialog'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { TextAreaField } from '@/components/ui/Field'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './Report.module.css'
import { useActions, useRecordAction } from './hooks'
import { recommendedActionLabel, reviewActionLabel, reviewActionLabels } from './labels'
import type { Report } from './schemas'

const MIN = 10
const MAX = 2000

export function DecisionPanel({ report }: { report: Report }) {
  const actions = useActions(report.investigationId)
  const [action, setAction] = useState<string>('')
  const [rationale, setRationale] = useState('')
  const [touched, setTouched] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const record = useRecordAction(report.investigationId, report.claimId, () => {
    setAction('')
    setRationale('')
    setTouched(false)
    setConfirming(false)
  })
  const trimmed = rationale.trim()
  const rationaleError = trimmed.length < MIN ? `Write at least ${MIN} characters, so the reason can be understood later.` : trimmed.length > MAX ? `Use at most ${MAX} characters.` : undefined
  const actionError = action ? undefined : 'Choose what you decided.'
  const stale = report.reportStatus === 'STALE'

  function submit(e: React.FormEvent) {
    e.preventDefault()
    setTouched(true)
    if (rationaleError || actionError) return
    setConfirming(true)
  }

  return (
    <section className={styles.decision} aria-labelledby="decision-title">
      <h3 id="decision-title">Your decision</h3>
      <p className={styles.note}>The tool only ranks and explains. What happens to the claim is a person&rsquo;s decision. None of these options approves, rejects or settles a claim, and a decision cannot be edited or deleted once recorded.</p>

      <form onSubmit={submit} noValidate className={styles.decisionForm}>
        <fieldset aria-describedby={touched && actionError ? 'action-error' : undefined}>
          <legend>What did you decide?</legend>
          {Object.entries(reviewActionLabels).map(([value, text]) => (
            <label key={value} className={styles.radio}>
              <input type="radio" name="action" value={value} checked={action === value} onChange={() => setAction(value)} />
              <span>{text}</span>
            </label>
          ))}
          {touched && actionError ? (
            <p id="action-error" className={styles.error}>
              {actionError}
            </p>
          ) : null}
        </fieldset>
        <TextAreaField
          label="Why"
          rows={4}
          value={rationale}
          onChange={(e) => setRationale(e.target.value)}
          hint={`${MIN} to ${MAX} characters. Kept with your decision, for whoever reads this later.`}
          error={touched ? rationaleError : undefined}
          required
        />
        {stale ? <Callout tone="caution" title="This report is out of date.">Newer evidence exists than the report was written from. Your decision will be recorded and marked as made on an out-of-date report. You may prefer to run a new investigation first.</Callout> : null}
        {record.isError && !confirming ? <ErrorPanel error={record.error} subject="that investigation" /> : null}
        <div>
          <Button type="submit" variant="primary">
            Record decision
          </Button>
        </div>
      </form>

      <ConfirmActionDialog
        open={confirming}
        title="Record this decision?"
        consequence={
          <>
            <p>
              <strong>{action ? reviewActionLabel(action) : ''}</strong>, on report version {report.reportVersion}. It is kept with the time and your account, and it cannot be changed or removed.
            </p>
            {record.isError ? <p role="alert">{describeApiError(record.error).title} {describeApiError(record.error).message}</p> : null}
          </>
        }
        confirmLabel="Record decision"
        cancelLabel="Go back"
        pending={record.isPending}
        onConfirm={() => record.mutate({ action, rationale: trimmed, reportVersion: report.reportVersion })}
        onCancel={() => setConfirming(false)}
      />

      <h4 className={styles.subhead}>Decisions recorded</h4>
      {actions.isPending ? <Skeleton variant="rows" count={2} label="Loading decisions" /> : null}
      {actions.isError ? <ErrorPanel error={actions.error} subject="the decisions" onRetry={() => void actions.refetch()} retrying={actions.isFetching} /> : null}
      {actions.data && actions.data.length === 0 ? <p className={styles.note}>No decision has been recorded on this investigation yet.</p> : null}
      {actions.data && actions.data.length > 0 ? (
        <ol className={styles.decisions} aria-label="Decisions recorded, oldest first">
          {actions.data.map((a) => (
            <li key={a.id}>
              <p className={styles.decisionHead}>
                <strong>{reviewActionLabel(a.action)}</strong>{' '}
                <time dateTime={a.createdAt} title={formatDateTimeWithZone(a.createdAt)}>
                  {formatDateTime(a.createdAt)}
                </time>
              </p>
              <p className={styles.rationale}>{a.rationale}</p>
              <p className={styles.note}>
                On report version {a.reportVersion}
                {a.reportWasStale ? ', which was already out of date' : ''}. At that moment the tool had scored it {typeof a.score === 'number' ? `${a.score} of 100` : 'without a score'} and suggested: {recommendedActionLabel(a.recommendedAction)}.
              </p>
            </li>
          ))}
        </ol>
      ) : null}
    </section>
  )
}
