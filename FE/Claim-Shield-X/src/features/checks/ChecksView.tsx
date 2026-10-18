import { Button } from '@/components/ui/Button'
import { Callout } from '@/components/ui/Callout'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { StatusMark } from '@/components/ui/StatusMark'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './Checks.module.css'
import { ResultCard } from './ResultCard'
import type { RuleRun } from './schemas'
import type { UseMutationResult } from '@tanstack/react-query'

// Most important to read first. A flag worth reviewing leads; "not applicable" is background.
const ORDER = ['FLAG', 'ERROR', 'INCOMPLETE', 'PASS', 'NOT_APPLICABLE']
const rank = (r: RuleRun['results'][number]) => ORDER.indexOf(r.outcome) * 10 + (r.outcome === 'FLAG' && r.severity === 'INFO' ? 1 : 0)

type ChecksViewProps = {
  run: RuleRun
  documentNames: Map<string, string>
  rerun: UseMutationResult<RuleRun, Error, void>
}

export function ChecksView({ run, documentNames, rerun }: ChecksViewProps) {
  const results = [...run.results].sort((a, b) => rank(a) - rank(b))
  const { counts } = run
  return (
    <div className={styles.stack}>
      <div className={styles.head}>
        <div>
          <h2>Checks on this claim</h2>
          <p className={styles.lede}>
            Run <time dateTime={run.createdAt} title={formatDateTimeWithZone(run.createdAt)}>{formatDateTime(run.createdAt)}</time>, rule set {run.ruleSetVersion}. A flag is something to look at against the documents. It is not a conclusion about the claim.
          </p>
        </div>
        <Button onClick={() => rerun.mutate()} loading={rerun.isPending} loadingLabel="Checking">
          Check again
        </Button>
      </div>

      <p className="sr-only" role="status">
        {rerun.isSuccess ? (rerun.data.reusedExistingRun ? 'Nothing has changed, so the stored run is current.' : 'A new run was stored.') : ''}
      </p>
      {rerun.isSuccess && rerun.data.reusedExistingRun ? <Callout tone="info" title="Nothing has changed since this run.">The claim, its documents and the history are the same, so the stored run is still current and no new run was stored.</Callout> : null}
      {rerun.isSuccess && !rerun.data.reusedExistingRun ? <Callout tone="evidence" title="The claim changed, so these checks were run again.">A new run was stored. The earlier run is kept unchanged.</Callout> : null}
      {rerun.isError ? <ErrorPanel error={rerun.error} subject="the checks" onRetry={() => rerun.mutate()} retrying={rerun.isPending} /> : null}

      <ul className={styles.counts} aria-label="Results by outcome">
        <li><StatusMark state="attention">{counts.flag} flagged</StatusMark></li>
        <li><StatusMark state="unknown">{counts.incomplete} incomplete</StatusMark></li>
        <li><StatusMark state="done">{counts.pass} passed</StatusMark></li>
        <li><StatusMark state="skipped">{counts.notApplicable} not applicable</StatusMark></li>
        {counts.error > 0 ? <li><StatusMark state="failed">{counts.error} could not run</StatusMark></li> : null}
      </ul>

      <ol className={styles.results} aria-label="Rule results">
        {results.map((r) => (
          <ResultCard key={r.id} result={r} documentNames={documentNames} />
        ))}
      </ol>
    </div>
  )
}
