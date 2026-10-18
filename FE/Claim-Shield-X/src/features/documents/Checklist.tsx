import { Callout } from '@/components/ui/Callout'
import { StatusMark } from '@/components/ui/StatusMark'
import { readinessMark, readinessMeaning } from '@/features/claims/readiness'
import styles from './Documents.module.css'
import { checkLabel, documentTypeLabel, evidenceStateMark, requirementLabel } from './labels'
import type { Requirements } from './schemas'

/** What the claim's stage expects, what has arrived, and which checks each gap stops. It describes evidence, never the claim. */
export function Checklist({ requirements }: { requirements: Requirements }) {
  const mark = readinessMark({ level: requirements.readiness, blockerCount: requirements.blockers.length, warningCount: requirements.warnings.length })
  const meaning = (readinessMeaning as Record<string, string>)[requirements.readiness]
  const general = requirements.warnings.filter((w) => !w.documentType)

  return (
    <div className={styles.stack}>
      <div className={styles.readinessLine}>
        <StatusMark state={mark.state} boxed>
          {mark.label}
        </StatusMark>
        {mark.detail ? <span className={styles.meaning}>{mark.detail}</span> : null}
      </div>
      {meaning ? <p className={styles.meaning}>{meaning}</p> : null}

      {requirements.blockers.length > 0 ? (
        <div className={styles.issues}>
          {requirements.blockers.map((b, i) => (
            <Callout key={`${b.code}-${b.documentType ?? i}`} tone="caution" title={b.message} />
          ))}
        </div>
      ) : null}
      {general.map((w) => (
        <Callout key={w.code} tone="info" title={w.message} />
      ))}

      {requirements.items.length > 0 ? (
        <table className={styles.checklist} role="table">
          <caption className="sr-only">Documents this claim stage expects</caption>
          <thead role="rowgroup">
            <tr role="row">
              <th scope="col" role="columnheader">
                Document
              </th>
              <th scope="col" role="columnheader">
                Expected
              </th>
              <th scope="col" role="columnheader">
                State
              </th>
              <th scope="col" role="columnheader">
                Files
              </th>
              <th scope="col" role="columnheader">
                Checks it stops
              </th>
            </tr>
          </thead>
          <tbody role="rowgroup">
            {requirements.items.map((item) => {
              const state = evidenceStateMark(item.evidenceState)
              const na = item.requirement === 'NOT_APPLICABLE'
              return (
                <tr key={item.documentType} role="row" className={na ? styles.na : undefined}>
                  <th scope="row" role="rowheader">
                    {documentTypeLabel(item.documentType)}
                  </th>
                  <td role="cell" data-label="Expected">{requirementLabel(item.requirement, item.mandatory)}</td>
                  <td role="cell" data-label="State">{na ? <span>Not needed at this stage</span> : <StatusMark state={state.state}>{state.label}</StatusMark>}</td>
                  <td role="cell" data-label="Files">{item.documentIds.length}</td>
                  <td role="cell" data-label="Checks it stops">
                    {item.blocks.length > 0 ? (
                      <ul className={styles.blocks}>
                        {item.blocks.map((b) => (
                          <li key={b}>{checkLabel(b)}</li>
                        ))}
                      </ul>
                    ) : (
                      <span className={styles.na}>None</span>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      ) : null}
    </div>
  )
}
