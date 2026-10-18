import { FieldInvestigator } from '@/components/illustrations/FieldInvestigator'
import styles from './Sections.module.css'

const actions = ['REQUEST_MORE_EVIDENCE', 'ESCALATE_FOR_FURTHER_INVESTIGATION', 'NO_FURTHER_CHECKS_NEEDED', 'DEFER_REVIEW']

export function HumanInControl() {
  return (
    <section className={styles.human} id="human" aria-labelledby="human-title">
      <div className={`container ${styles.humanGrid}`}>
        <div className={styles.humanCopy}>
          <h2 id="human-title">The decision stays with a person.</h2>
          <p>
            ClaimShield X assists the analysis. It never approves, rejects or settles a claim, and it never calls one fraudulent. The investigator
            reads the evidence and records what happens next, with a written reason that cannot be edited afterwards.
          </p>
          <ul className={styles.actionsList} aria-label="Recorded review actions">
            {actions.map((a) => (
              <li key={a}>{a}</li>
            ))}
          </ul>
          <p className={styles.plannedNote}>Decisions are recorded on the Report tab of a finished investigation. None of these values means approve, reject, settle or fraud.</p>
        </div>
        <div className={styles.humanArt}>
          <FieldInvestigator pose="point" />
        </div>
      </div>
    </section>
  )
}
