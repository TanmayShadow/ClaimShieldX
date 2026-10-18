import { StatusMark } from '@/components/ui/StatusMark'
import { humanizeCode } from '@/features/checks/text'
import styles from './Report.module.css'
import { bandLabel, bandMark, recommendedActionLabel } from './labels'
import type { Report } from './schemas'

// The band limits are the backend's defaults (LOW below 20, MEDIUM from 20, HIGH from 50) and are shown only as a scale, never as a verdict.
export function PriorityPanel({ report }: { report: Report }) {
  const p = report.reviewPriority
  const mark = bandMark(p.band)
  const components = p.components ?? []
  const percent = Math.max(0, Math.min(100, p.score))
  return (
    <section className={styles.priority} aria-labelledby="priority-title">
      <h3 id="priority-title">Review priority</h3>
      <div className={styles.scoreRow}>
        <p className={styles.score}>
          <span className={styles.scoreNumber}>{p.score}</span> <span className={styles.scoreOf}>of 100</span>
        </p>
        <StatusMark state={mark.state} boxed>
          {mark.label}
        </StatusMark>
      </div>
      <div className={styles.meter} role="img" aria-label={`Score ${p.score} of 100, ${bandLabel(p.band).toLowerCase()} band. Bands begin at 20 and 50.`}>
        <span className={styles.meterFill} style={{ width: `${percent}%` }} />
        <span className={styles.tick} style={{ left: '20%' }} />
        <span className={styles.tick} style={{ left: '50%' }} />
      </div>
      <dl className={styles.facts}>
        <div>
          <dt>Suggested next step</dt>
          <dd>{recommendedActionLabel(p.recommendedAction)}</dd>
        </div>
      </dl>
      {components.length > 0 ? (
        <table className={styles.components}>
          <caption>Where the points come from</caption>
          <thead>
            <tr>
              <th scope="col">Check</th>
              <th scope="col">How material</th>
              <th scope="col">Points</th>
            </tr>
          </thead>
          <tbody>
            {components.map((c) => (
              <tr key={c.ruleId}>
                <th scope="row">{humanizeCode(c.ruleId)}</th>
                <td>{c.severity === 'REVIEW' ? 'Worth reviewing' : c.severity === 'INFO' ? 'Noted' : (c.severity ?? 'Not recorded')}</td>
                <td>{c.points}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className={styles.note}>No check flagged anything, so no points were added.</p>
      )}
      <p className={styles.note}>{report.notice ?? 'The review-priority score ranks where a person should look first. It is not a probability, not a finding about the claim and not a decision.'}</p>
    </section>
  )
}
