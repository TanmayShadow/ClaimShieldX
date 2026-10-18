import { Link } from 'react-router'
import { StatusMark } from '@/components/ui/StatusMark'
import { humanizeCode } from '@/features/checks/text'
import styles from './Report.module.css'
import { findingMark, hypothesisReviewLabel } from './labels'
import type { Finding } from './schemas'

type FindingCardProps = {
  finding: Finding
  /** Evidence reference to its human label, from the frozen snapshot; a reference not found there is shown as it is. */
  labels: Map<string, string>
}

function Refs({ title, refs, labels }: { title: string; refs: string[]; labels: Map<string, string> }) {
  if (refs.length === 0) return null
  return (
    <>
      <p className={styles.label}>{title}</p>
      <ul className={styles.refs}>
        {refs.map((ref) => (
          <li key={ref}>
            <Link to={`../evidence#ev-${ref}`} className={styles.ref}>
              <span>{labels.get(ref) ?? humanizeCode(ref)}</span> <span className={styles.refId}>{ref}</span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  )
}

export function FindingCard({ finding, labels }: FindingCardProps) {
  const mark = findingMark(finding)
  const isHypothesis = finding.type === 'HYPOTHESIS'
  const counter = finding.counterEvidence ?? []
  const missing = finding.missingChecks ?? []
  return (
    <li className={styles.finding} data-type={finding.type}>
      <div className={styles.findingHead}>
        <h5 className={styles.findingTitle}>{finding.title}</h5>
        <StatusMark state={mark.state} boxed>
          {mark.label}
        </StatusMark>
      </div>
      {isHypothesis ? <p className={styles.sourceNote}>Proposed by a model. {hypothesisReviewLabel(finding.status)}. A hypothesis to verify, not a conclusion.</p> : null}
      <p className={styles.summary}>{finding.summary}</p>
      <details className={styles.more} open={finding.type === 'FACT' && finding.status === 'NEEDS_REVIEW'}>
        <summary>
          Other explanations, what to check next and the evidence<span className="sr-only"> for {finding.title}</span>
        </summary>
        {counter.length > 0 ? (
          <>
            <p className={styles.label}>{isHypothesis ? 'Legitimate explanations and counter-evidence' : 'Other explanations to rule out'}</p>
            <ul>
              {counter.map((c, i) => (
                <li key={i}>{c}</li>
              ))}
            </ul>
          </>
        ) : null}
        {missing.length > 0 ? (
          <>
            <p className={styles.label}>Still to check</p>
            <ul>
              {missing.map((m, i) => (
                <li key={i}>{humanizeCode(m)}</li>
              ))}
            </ul>
          </>
        ) : null}
        {finding.recommendation ? (
          <>
            <p className={styles.label}>Suggested next step</p>
            <p>{finding.recommendation}</p>
          </>
        ) : null}
        <Refs title="Evidence it rests on" refs={finding.supportingEvidenceRefs ?? []} labels={labels} />
        <Refs title="Evidence that points the other way" refs={finding.counterEvidenceRefs ?? []} labels={labels} />
      </details>
    </li>
  )
}
