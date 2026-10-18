import { useState } from 'react'
import { Link } from 'react-router'
import { describeApiError } from '@/apis/describeError'
import { Button } from '@/components/ui/Button'
import { buttonClass } from '@/components/ui/buttonClass'
import { Callout } from '@/components/ui/Callout'
import { ConfirmActionDialog } from '@/components/ui/ConfirmActionDialog'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Sheet } from '@/components/ui/Sheet'
import { StatusMark } from '@/components/ui/StatusMark'
import { documentTypeLabel } from '@/features/documents/labels'
import { formatDateTime, formatDateTimeWithZone, formatTime } from '@/utils/format'
import { useCancelInvestigation, useRetryInvestigation } from './hooks'
import styles from './Investigation.module.css'
import { InvestigationRoute } from './InvestigationRoute'
import { STAGE_ORDER, investigationErrorMessage, investigationStatusMark, isActive, isTerminal, stageLabel } from './labels'
import type { StageId } from './labels'
import type { Investigation } from './schemas'
import { StageInspector } from './StageInspector'

type ViewProps = {
  claimId: string
  investigation: Investigation
  dataUpdatedAt: number
  fetching: boolean
  refreshError: unknown
  onRefresh: () => void
}

const time = (iso: string | null | undefined) =>
  iso ? (
    <time dateTime={iso} title={formatDateTimeWithZone(iso)}>
      {formatDateTime(iso)}
    </time>
  ) : (
    'Not yet'
  )

/** One investigation: where it is, what it is waiting for, what it can do next, and each stage's own record. Everything shown is what the server reported. */
export function InvestigationView({ claimId, investigation: inv, dataUpdatedAt, fetching, refreshError, onRefresh }: ViewProps) {
  const [selected, setSelected] = useState<StageId | null>(null)
  const [confirmCancel, setConfirmCancel] = useState(false)
  const cancel = useCancelInvestigation(claimId, inv.id)
  const retry = useRetryInvestigation(claimId, inv.id)

  const mark = investigationStatusMark(inv.status, inv.cancelRequested)
  const running = STAGE_ORDER.find((id) => inv.stages.find((s) => s.stage === id)?.status === 'RUNNING')
  const failedStage = STAGE_ORDER.find((id) => inv.stages.find((s) => s.stage === id)?.status === 'FAILED')
  // Show the stage that matters now: the running one, else the failed one, else the last one that finished, else the first.
  const finished = [...STAGE_ORDER].reverse().find((id) => ['OK', 'DEGRADED'].includes(inv.stages.find((s) => s.stage === id)?.status ?? ''))
  const shown: StageId = selected ?? running ?? failedStage ?? finished ?? 'PREPARING_EVIDENCE'
  const finishedCount = inv.stages.filter((s) => ['OK', 'DEGRADED'].includes(s.status)).length
  const completedWithGaps = inv.status === 'COMPLETED' && inv.stages.some((s) => s.status === 'DEGRADED')

  return (
    <div className={styles.stack}>
      <Sheet tab="Investigation" folded>
        <div className={styles.head}>
          <h2>{isTerminal(inv.status) ? 'Latest investigation' : 'Investigation in progress'}</h2>
          <StatusMark state={mark.state} boxed>
            {mark.label}
          </StatusMark>
        </div>
        <dl className={styles.facts} style={{ marginTop: 'var(--space-4)' }}>
          <div>
            <dt>Requested</dt>
            <dd>{time(inv.createdAt)}</dd>
          </div>
          <div>
            <dt>Started</dt>
            <dd>{time(inv.startedAt)}</dd>
          </div>
          <div>
            <dt>{isTerminal(inv.status) ? 'Ended' : 'Last change'}</dt>
            <dd>{time(isTerminal(inv.status) ? inv.finishedAt : inv.updatedAt)}</dd>
          </div>
          {inv.maxAttempts ? (
            <div>
              <dt>Attempt</dt>
              <dd>
                {inv.attempt ?? 1} of {inv.maxAttempts}
              </dd>
            </div>
          ) : null}
          <div>
            <dt>Stages finished</dt>
            <dd>
              {finishedCount} of {STAGE_ORDER.length}
            </dd>
          </div>
        </dl>
        {inv.note ? <p className={styles.note} style={{ marginTop: 'var(--space-4)' }}>Note: {inv.note}</p> : null}

        <div className={styles.updated} style={{ marginTop: 'var(--space-4)' }}>
          {dataUpdatedAt > 0 ? <span>Last updated {formatTime(dataUpdatedAt)}</span> : null}
          <Button size="sm" icon="refresh" onClick={onRefresh} loading={fetching} loadingLabel="Refreshing">
            Refresh
          </Button>
          {isActive(inv.status) ? <span>This page checks again on its own while work is under way.</span> : null}
        </div>
      </Sheet>

      {refreshError ? (
        <Callout tone="caution" title="This view may be out of date.">
          The latest check could not reach the server ({describeApiError(refreshError).title.replace(/\.$/, '').toLowerCase()}). It will keep trying. What is shown is what was last received.
        </Callout>
      ) : null}

      <div role="status" aria-live="polite" className="sr-only">
        {`Investigation: ${mark.label}. ${finishedCount} of ${STAGE_ORDER.length} stages finished.`}
      </div>

      {inv.status === 'WAITING_FOR_EVIDENCE' ? (
        <Callout tone="info" title="Waiting for a document. This is not a failure.">
          <p>
            {inv.waitingFor && inv.waitingFor.length > 0
              ? `Needed: ${inv.waitingFor.map((w) => `${documentTypeLabel(w.documentType)} (${w.code === 'DOCUMENT_MISSING' ? 'not received' : 'could not be read'})`).join(', ')}. `
              : ''}
            Upload it on the <Link to="../documents">Documents tab</Link> and the investigation resumes at once. It also looks again every few seconds.
          </p>
        </Callout>
      ) : null}

      {inv.status === 'RETRY_WAIT' ? (
        <Callout tone="caution" title="A temporary problem. It will try again.">
          <p>
            {investigationErrorMessage(inv.error?.code)} Attempt {inv.attempt ?? 1} of {inv.maxAttempts ?? 3} will start {inv.nextRetryAt ? <>at {time(inv.nextRetryAt)}</> : 'soon'}. Work already finished is kept.
          </p>
        </Callout>
      ) : null}

      {inv.status === 'FAILED' ? (
        <Callout
          tone="danger"
          title="The investigation could not finish."
          actions={
            <Button size="sm" icon="refresh" onClick={() => retry.mutate()} loading={retry.isPending} loadingLabel="Starting again">
              Try again
            </Button>
          }
        >
          <p>
            {investigationErrorMessage(inv.error?.code)} {failedStage ? `It stopped at "${stageLabel(failedStage).toLowerCase()}". ` : ''}
            Trying again keeps the stages that finished and runs the one that failed.
          </p>
        </Callout>
      ) : null}

      {inv.status === 'CANCELLED' ? (
        <Callout tone="neutral" title="This investigation was cancelled.">
          <p>Work that finished before the cancel is kept. A new investigation can be started.</p>
        </Callout>
      ) : null}

      {inv.status === 'COMPLETED' ? (
        <Callout
          tone="evidence"
          title="The investigation finished."
          actions={
            inv.reportId ? (
              <Link className={buttonClass({ variant: 'primary', size: 'sm' })} to="../report">
                Open the report
              </Link>
            ) : undefined
          }
        >
          <p>
            Evidence was prepared, documents were read, and the checks were run{completedWithGaps ? ', though part of one stage could not be done (see the stage marked "Finished, partly")' : ''}.{' '}
            {inv.reportId ? 'A report was written. Its review-priority score ranks where a person should look first. It is not a finding about the claim.' : 'No report was written for this investigation.'}
          </p>
        </Callout>
      ) : null}

      {inv.cancelRequested && isActive(inv.status) ? (
        <Callout tone="info" title="Cancel requested.">
          <p>It will stop at the next safe point, between stages or between documents. That can take a moment.</p>
        </Callout>
      ) : null}

      {retry.isError ? <ErrorPanel error={retry.error} subject="that investigation" /> : null}
      {cancel.isError ? <ErrorPanel error={cancel.error} subject="that investigation" /> : null}

      <div>
        <InvestigationRoute investigation={inv} selected={shown} onSelect={setSelected} />
      </div>

      <Sheet tab={`Stage · ${stageLabel(shown).toLowerCase()}`}>
        <StageInspector investigation={inv} stage={shown} />
      </Sheet>

      {isActive(inv.status) && !inv.cancelRequested ? (
        <div>
          <Button onClick={() => setConfirmCancel(true)}>Cancel investigation</Button>
        </div>
      ) : null}

      <ConfirmActionDialog
        open={confirmCancel}
        title="Cancel this investigation?"
        consequence="It stops. Work already finished is kept, and a new investigation can be started afterwards. A stage that is running stops at its next safe point, which can take a moment."
        confirmLabel="Cancel investigation"
        cancelLabel="Keep it running"
        tone="danger"
        pending={cancel.isPending}
        onCancel={() => setConfirmCancel(false)}
        onConfirm={() => cancel.mutate(undefined, { onSettled: () => setConfirmCancel(false) })}
      />
    </div>
  )
}
