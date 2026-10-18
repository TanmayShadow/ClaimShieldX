import { useState } from 'react'
import { useOutletContext } from 'react-router'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { Checklist } from '@/features/documents/Checklist'
import styles from '@/features/documents/Documents.module.css'
import { DocumentsTable } from '@/features/documents/DocumentsTable'
import { useDocuments, useRequirements } from '@/features/documents/hooks'
import type { DocumentDto } from '@/features/documents/schemas'
import { UploadPanel } from '@/features/documents/UploadPanel'
import { formatTime } from '@/utils/format'
import type { ClaimOutletContext } from './ClaimWorkspacePage'

export function ClaimDocumentsPage() {
  const { claim } = useOutletContext<ClaimOutletContext>()
  const documents = useDocuments(claim.id)
  const requirements = useRequirements(claim.id)
  const [replacing, setReplacing] = useState<DocumentDto | null>(null)
  const [highlightId, setHighlightId] = useState<string | null>(null)

  const current = documents.data?.items.filter((d) => !d.superseded) ?? []

  function showDocument(id: string) {
    setHighlightId(id)
    window.requestAnimationFrame(() => {
      const row = document.getElementById(`doc-${id}`)
      row?.scrollIntoView?.({ block: 'center' })
      row?.focus()
    })
  }

  function replace(doc: DocumentDto) {
    setReplacing(doc)
    window.requestAnimationFrame(() => {
      const input = document.getElementById('document-file')
      input?.scrollIntoView?.({ block: 'center' })
      input?.focus()
    })
  }

  return (
    <div className={styles.layout}>
      <section className={styles.section} aria-labelledby="checklist-title">
        <h2 id="checklist-title">What this claim still needs</h2>
        <p className={styles.lede}>The documents the claim stage expects, which have arrived, and which checks each gap stops. A missing document means a check could not run. It is never a finding.</p>
        {requirements.isPending ? <Skeleton variant="rows" count={4} label="Loading the checklist" /> : null}
        {requirements.isError ? <ErrorPanel error={requirements.error} subject="the checklist" onRetry={() => void requirements.refetch()} retrying={requirements.isFetching} /> : null}
        {requirements.data ? <Checklist requirements={requirements.data} /> : null}
      </section>

      <section className={styles.section} aria-labelledby="upload-title">
        <h2 id="upload-title">Add a document</h2>
        <p className={styles.lede}>Uploading stores a file. It does not read it or judge it. Invoices and estimates can be read afterwards, from the list below.</p>
        <UploadPanel claimId={claim.id} requirements={requirements.data} currentDocuments={current} replacing={replacing} onStopReplacing={() => setReplacing(null)} onShowDocument={showDocument} />
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
            Add the first document above. The checklist updates as soon as the server confirms it.
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
