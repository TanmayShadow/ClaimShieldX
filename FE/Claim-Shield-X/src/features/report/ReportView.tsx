import { useMemo } from 'react'
import { Link } from 'react-router'
import { Callout } from '@/components/ui/Callout'
import { StatusMark } from '@/components/ui/StatusMark'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import { AuditList } from './AuditList'
import { DecisionPanel } from './DecisionPanel'
import { FindingCard } from './FindingCard'
import { useEvidence } from './hooks'
import { completenessLabel, findingGroup, findingGroups, unavailableSectionLabel } from './labels'
import { PriorityPanel } from './PriorityPanel'
import styles from './Report.module.css'
import type { Report } from './schemas'

export function ReportView({ report }: { report: Report }) {
  const evidence = useEvidence(report.investigationId)
  const labels = useMemo(() => new Map((evidence.data?.items ?? []).map((i) => [i.ref, i.label])), [evidence.data])
  const stale = report.reportStatus === 'STALE'
  const partial = report.completeness !== 'COMPLETE'
  const unavailable = report.unavailableSections ?? []

  return (
    <div className={styles.stack}>
      <div className={styles.head}>
        <div>
          <h2>Report</h2>
          <p className={styles.lede}>
            Version {report.reportVersion}, written{' '}
            <time dateTime={report.createdAt} title={formatDateTimeWithZone(report.createdAt)}>
              {formatDateTime(report.createdAt)}
            </time>{' '}
            from one frozen set of evidence. <Link to="../investigation">See the investigation</Link> · <Link to="../evidence">See the evidence</Link>
          </p>
        </div>
        <StatusMark state={stale ? 'attention' : 'done'} boxed>
          {stale ? 'Out of date' : 'Current'}
        </StatusMark>
      </div>

      {stale ? (
        <Callout tone="caution" title="Newer evidence exists than this report was written from.">
          A document, a reading or an imported history is newer than the evidence this report rests on. It is still readable, and a decision on it is marked as made on an out-of-date report. Run a new investigation to bring it up to date.
        </Callout>
      ) : null}
      {partial ? (
        <Callout tone="caution" title={`This report is ${completenessLabel(report.completeness).toLowerCase()}.`}>
          <ul>
            {unavailable.map((u) => (
              <li key={u}>{unavailableSectionLabel(u)}</li>
            ))}
          </ul>
          <p>The rule results and the score below are still valid. What could not be done is not shown as if it had found nothing.</p>
        </Callout>
      ) : null}

      <p className={styles.reportSummary}>{report.summary}</p>

      <PriorityPanel report={report} />

      <section aria-labelledby="findings-title" className={styles.stack}>
        <h3 id="findings-title">Findings</h3>
        {report.findings.length === 0 ? <p className={styles.note}>The report holds no findings.</p> : null}
        {findingGroups.map((g) => {
          const list = report.findings.filter((f) => findingGroup(f) === g.id)
          if (list.length === 0) return null
          return (
            <section key={g.id} aria-labelledby={`group-${g.id}`} className={styles.group}>
              <h4 id={`group-${g.id}`} className={styles.groupTitle}>
                {g.title} <span className={styles.count}>{list.length}</span>
              </h4>
              {g.intro ? <p className={styles.note}>{g.intro}</p> : null}
              <ul className={styles.findings}>
                {list.map((f) => (
                  <FindingCard key={f.id} finding={f} labels={labels} />
                ))}
              </ul>
            </section>
          )
        })}
        {report.evidenceCoverage ? (
          <p className={styles.note}>
            The frozen evidence holds {report.evidenceCoverage.itemCount} items{typeof report.evidenceCoverage.citedByFindings === 'number' ? `, and the findings cite ${report.evidenceCoverage.citedByFindings} of them` : ''}.
          </p>
        ) : null}
      </section>

      <DecisionPanel report={report} />
      <AuditList investigationId={report.investigationId} />
    </div>
  )
}
