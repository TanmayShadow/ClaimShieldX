import { useState } from 'react'
import type { DragEvent, ReactNode } from 'react'
import { Button } from '@/components/ui/Button'
import { Callout } from '@/components/ui/Callout'
import { StatusMark } from '@/components/ui/StatusMark'
import { readinessMark, readinessMeaning } from '@/features/claims/readiness'
import styles from './Documents.module.css'
import { checkLabel, documentTypeLabel, evidenceStateMark, requirementLabel } from './labels'
import type { Requirements } from './schemas'
import type { DocumentUploader } from './useDocumentUploader'

type ChecklistProps = {
  requirements: Requirements
  /** Gives every row an Upload button that opens the file chooser for that document type. */
  uploader: DocumentUploader
  /** Rendered just above the table, so an upload's progress is next to the button that started it. */
  status?: ReactNode
}

/** One row's Upload button, and the row's drop target. The accessible name begins with the visible words (WCAG 2.5.3). */
/** `needed` marks the one document the stage cannot do without and has not received: its button is the one that stands out. */
export function UploadCell({ documentType, files, uploader, needed = false }: { documentType: string; files: number; uploader: DocumentUploader; needed?: boolean }) {
  const active = uploader.busy && uploader.target?.documentType === documentType && !uploader.target?.replaces
  const label = files > 0 ? 'Add another' : 'Upload'
  return (
    <Button
      size="sm"
      variant={needed ? 'primary' : 'secondary'}
      icon="upload"
      onClick={() => uploader.choose({ documentType })}
      loading={active}
      loadingLabel={uploader.state.phase === 'confirming' ? 'Waiting for the server' : 'Sending'}
      disabled={(uploader.busy && !active) || uploader.full}
      // While it is working, its words are the loading words; otherwise the name says which document it is for.
      aria-label={active ? undefined : `${label} ${documentTypeLabel(documentType).toLowerCase()}`}
    >
      {label}
    </Button>
  )
}

/** A checklist row that also accepts a dropped file. */
function Row({ documentType, className, children, uploader }: { documentType: string; className?: string; children: ReactNode; uploader: DocumentUploader }) {
  const [over, setOver] = useState(false)
  const accepting = !uploader.busy && !uploader.full
  function onDrop(event: DragEvent) {
    event.preventDefault()
    setOver(false)
    if (accepting) uploader.dropOn({ documentType }, event.dataTransfer.files[0])
  }
  return (
    <tr
      role="row"
      className={className}
      data-over={over || undefined}
      onDragOver={(e) => {
        if (!accepting) return
        e.preventDefault()
        setOver(true)
      }}
      onDragLeave={() => setOver(false)}
      onDrop={onDrop}
    >
      {children}
    </tr>
  )
}

/** What the claim's stage expects, what has arrived, and which checks each gap stops. It describes evidence, never the claim. */
export function Checklist({ requirements, uploader, status }: ChecklistProps) {
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

      {status}

      {requirements.items.length > 0 ? (
        <table className={styles.checklist} role="table">
          <caption className="sr-only">Documents this claim stage expects</caption>
          <thead role="rowgroup">
            <tr role="row">
              <th scope="col" role="columnheader">
                Document
              </th>
              <th scope="col" role="columnheader">
                Upload
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
                <Row key={item.documentType} documentType={item.documentType} className={na ? styles.na : undefined} uploader={uploader}>
                  <th scope="row" role="rowheader">
                    {documentTypeLabel(item.documentType)}
                  </th>
                  <td role="cell" data-label="Upload">
                    <UploadCell documentType={item.documentType} files={item.documentIds.length} uploader={uploader} needed={item.mandatory && item.documentIds.length === 0} />
                  </td>
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
                </Row>
              )
            })}
            {/* The checklist never lists "other", so anything else worth keeping with the claim has its own row. */}
            <Row documentType="OTHER" uploader={uploader}>
              <th scope="row" role="rowheader">
                {documentTypeLabel('OTHER')}
              </th>
              <td role="cell" data-label="Upload">
                <UploadCell documentType="OTHER" files={0} uploader={uploader} />
              </td>
              <td role="cell" data-label="Expected">
                <span className={styles.na}>Not on the checklist</span>
              </td>
              <td role="cell" data-label="State">
                <span className={styles.na}>Kept for people to open</span>
              </td>
              <td role="cell" data-label="Files">
                <span className={styles.na}>&ndash;</span>
              </td>
              <td role="cell" data-label="Checks it stops">
                <span className={styles.na}>None</span>
              </td>
            </Row>
          </tbody>
        </table>
      ) : null}
    </div>
  )
}
