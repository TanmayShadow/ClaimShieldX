import styles from './Documents.module.css'
import { DocumentRow } from './DocumentRow'
import type { DocumentDto } from './schemas'

type DocumentsTableProps = { claimId: string; documents: DocumentDto[]; highlightId: string | null; onReplace: (doc: DocumentDto) => void }

/** Newest first, replaced files included and marked. Row content is plain text: a file name is never interpreted as markup. */
export function DocumentsTable({ claimId, documents, highlightId, onReplace }: DocumentsTableProps) {
  return (
    <table className={styles.docs} role="table">
      <caption className="sr-only">Documents stored on this claim</caption>
      <thead role="rowgroup">
        <tr role="row">
          <th scope="col" role="columnheader">
            Document
          </th>
          <th scope="col" role="columnheader">
            State
          </th>
          <th scope="col" role="columnheader">
            Received
          </th>
          <th scope="col" role="columnheader">
            Size
          </th>
          <th scope="col" role="columnheader">
            Actions
          </th>
        </tr>
      </thead>
      <tbody role="rowgroup">
        {documents.map((doc) => (
          <DocumentRow key={doc.id} claimId={claimId} doc={doc} highlighted={doc.id === highlightId} onReplace={onReplace} />
        ))}
      </tbody>
    </table>
  )
}
