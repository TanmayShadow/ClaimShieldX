import { useState } from 'react'
import { useOutletContext } from 'react-router'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { TextField } from '@/components/ui/Field'
import { Skeleton } from '@/components/ui/Skeleton'
import { Checklist } from '@/features/documents/Checklist'
import styles from '@/features/documents/Documents.module.css'
import { DocumentsTable } from '@/features/documents/DocumentsTable'
import { QuickUploadList } from '@/features/documents/QuickUploadList'
import { UploadStatus } from '@/features/documents/UploadStatus'
import { FILE_CHOOSER_ID, useDocumentUploader } from '@/features/documents/useDocumentUploader'
import { ACCEPT_ATTRIBUTE } from '@/features/documents/uploadRules'
import { useDocuments, useRequirements } from '@/features/documents/hooks'
import type { DocumentDto } from '@/features/documents/schemas'
import { formatTime } from '@/utils/format'
import type { ClaimOutletContext } from './ClaimWorkspacePage'

export function ClaimDocumentsPage() {
  const { claim } = useOutletContext<ClaimOutletContext>()
  const documents = useDocuments(claim.id)
  const requirements = useRequirements(claim.id)
  const [highlightId, setHighlightId] = useState<string | null>(null)

  const current = documents.data?.items.filter((d) => !d.superseded) ?? []
  const uploader = useDocumentUploader(claim.id, current.length)

  function showDocument(id: string) {
    setHighlightId(id)
    window.requestAnimationFrame(() => {
      const row = document.getElementById(`doc-${id}`)
      row?.scrollIntoView?.({ block: 'center' })
      row?.focus()
    })
  }

  // Replacing a file is the same one step: the chooser opens for that file's type and the new file is stored beside it, marking the old one replaced.
  function replace(doc: DocumentDto) {
    uploader.choose({ documentType: doc.documentType, replaces: doc })
  }

  const status = <UploadStatus uploader={uploader} onShowDocument={showDocument} />

  return (
    <div className={styles.layout}>
      {/* One file chooser for the whole page; a row's button points it at that row's document type. */}
      <input id={FILE_CHOOSER_ID} type="file" hidden accept={ACCEPT_ATTRIBUTE} onChange={uploader.onInputChange} />

      <section className={styles.section} aria-labelledby="checklist-title">
        <h2 id="checklist-title">What this claim still needs</h2>
        <p className={styles.lede}>
          The documents the claim stage expects, which have arrived, and which checks each gap stops. Press <strong>Upload</strong> on a row to add that document: the file chooser opens and the file is sent as soon as you choose it, or drop a file on the row. A missing document means a check could not run. It is never a finding. Uploading stores a file; it does not read or judge it.
        </p>
        {requirements.isPending ? <Skeleton variant="rows" count={4} label="Loading the checklist" /> : null}
        {requirements.isError ? (
          <>
            <ErrorPanel error={requirements.error} subject="the checklist" onRetry={() => void requirements.refetch()} retrying={requirements.isFetching} />
            <QuickUploadList uploader={uploader} status={status} />
          </>
        ) : null}
        {requirements.data ? <Checklist requirements={requirements.data} uploader={uploader} status={status} /> : null}

        <details className={styles.options}>
          <summary>Options for the next upload</summary>
          <TextField
            label="Insurer's document id"
            optional
            value={uploader.externalId}
            onChange={(e) => uploader.setExternalId(e.target.value)}
            maxLength={255}
            disabled={uploader.busy}
            hint="Your own reference for the next document you upload, if it has one. It applies to that one upload only."
            mono
            autoComplete="off"
          />
        </details>
      </section>

      <section className={styles.section} aria-labelledby="docs-title">
        <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', justifyContent: 'space-between', gap: 'var(--space-3)' }}>
          <h2 id="docs-title">Documents on this claim</h2>
          <span style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', fontSize: 'var(--text-sm)', color: 'var(--ink-soft)' }}>
            {documents.dataUpdatedAt > 0 ? <span>Updated {formatTime(documents.dataUpdatedAt)}</span> : null}
            <Button size="sm" icon="refresh" onClick={() => void documents.refetch()} loading={documents.isFetching && !documents.isPending} loadingLabel="Refreshing">
              Refresh
            </Button>
          </span>
        </div>
        {documents.isPending ? <Skeleton variant="rows" count={3} label="Loading documents" /> : null}
        {documents.isError ? <ErrorPanel error={documents.error} subject="the documents" onRetry={() => void documents.refetch()} retrying={documents.isFetching} /> : null}
        {documents.data && documents.data.items.length === 0 ? (
          <EmptyState title="No documents yet" illustration={<EmptyFolder />} headingLevel={3}>
            Press Upload on a row of the checklist above. The checklist updates as soon as the server confirms the file.
          </EmptyState>
        ) : null}
        {documents.data && documents.data.items.length > 0 ? (
          <>
            <p className={styles.lede}>
              {documents.data.totalElements} {documents.data.totalElements === 1 ? 'file' : 'files'} stored, newest first. Replaced files are kept and marked.
            </p>
            <DocumentsTable claimId={claim.id} documents={documents.data.items} highlightId={highlightId} onReplace={replace} />
          </>
        ) : null}
      </section>
    </div>
  )
}
