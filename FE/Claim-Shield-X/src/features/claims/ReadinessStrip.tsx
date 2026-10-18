import { Link } from 'react-router'
import { StatusMark } from '@/components/ui/StatusMark'
import { useRequirements } from '@/features/documents/hooks'
import styles from './ClaimTabs.module.css'
import { readinessMark } from './readiness'

/** The claim's evidence readiness at a glance, from the checklist. A quiet line: it never blocks the page if the checklist cannot load. */
export function ReadinessStrip({ claimId }: { claimId: string }) {
  const query = useRequirements(claimId)
  if (query.isPending) return <div className={styles.strip}><span>Checking the document checklist…</span></div>
  if (query.isError) {
    return (
      <div className={styles.strip}>
        <span>Evidence readiness is not available right now.</span>
        <button type="button" onClick={() => void query.refetch()} style={{ background: 'none', border: 0, padding: 0, textDecoration: 'underline', textDecorationColor: 'var(--signal)', textDecorationThickness: 2 }}>
          Try again
        </button>
      </div>
    )
  }
  const mark = readinessMark({ level: query.data.readiness, blockerCount: query.data.blockers.length, warningCount: query.data.warnings.length })
  return (
    <div className={styles.strip}>
      <strong>Evidence</strong>
      <StatusMark state={mark.state}>{mark.label}</StatusMark>
      {mark.detail ? <span>{mark.detail}</span> : null}
      <Link to="documents">See the checklist</Link>
    </div>
  )
}
