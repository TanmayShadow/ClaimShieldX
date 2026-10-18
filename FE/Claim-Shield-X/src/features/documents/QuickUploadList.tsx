import type { ReactNode } from 'react'
import { UploadCell } from './Checklist'
import styles from './Documents.module.css'
import { DOCUMENT_TYPES, documentTypeLabel } from './labels'
import type { DocumentUploader } from './useDocumentUploader'

/** Shown only when the checklist cannot be loaded, so a document can still be added: one Upload button per kind of document. */
export function QuickUploadList({ uploader, status }: { uploader: DocumentUploader; status: ReactNode }) {
  return (
    <div className={styles.stack}>
      {status}
      <ul className={styles.quickList} aria-label="Kinds of document you can upload">
        {DOCUMENT_TYPES.map((type) => (
          <li key={type}>
            <span>{documentTypeLabel(type)}</span>
            <UploadCell documentType={type} files={0} uploader={uploader} />
          </li>
        ))}
      </ul>
    </div>
  )
}
