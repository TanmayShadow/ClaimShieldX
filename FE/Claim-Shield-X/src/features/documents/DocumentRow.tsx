import { useEffect, useState } from 'react'
import { describeApiError } from '@/apis/describeError'
import { Button } from '@/components/ui/Button'
import { StatusMark } from '@/components/ui/StatusMark'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './Documents.module.css'
import { ExtractionSummary } from './ExtractionSummary'
import { useStartExtraction } from './hooks'
import { documentStatusMark, documentTypeLabel, isExtractable } from './labels'
import type { DocumentDto } from './schemas'
import { formatBytes } from './uploadRules'
import { saveDocument, useBlobUrl } from './useBlobUrl'

type DocumentRowProps = {
  claimId: string
  doc: DocumentDto
  highlighted: boolean
  onReplace: (doc: DocumentDto) => void
}

/** Seconds since `active` became true. Honest time elapsed, never a made-up percentage. */
function useElapsed(active: boolean): number {
  const [seconds, setSeconds] = useState(0)
  useEffect(() => {
    if (!active) return
    const start = Date.now()
    const id = window.setInterval(() => setSeconds(Math.floor((Date.now() - start) / 1000)), 1000)
    return () => {
      window.clearInterval(id)
      setSeconds(0)
    }
  }, [active])
  return active ? seconds : 0
}

const isImage = (contentType: string) => contentType === 'image/jpeg' || contentType === 'image/png'

export function DocumentRow({ claimId, doc, highlighted, onReplace }: DocumentRowProps) {
  const status = documentStatusMark(doc.status, doc.documentType)
  const read = useStartExtraction(claimId)
  const elapsed = useElapsed(read.isPending)
  const [details, setDetails] = useState(false)
  const [preview, setPreview] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<unknown>(null)
  const blob = useBlobUrl(claimId, doc.id, preview)

  const canRead = isExtractable(doc.documentType) && !doc.superseded && ['RECEIVED', 'NEEDS_REVIEW', 'FAILED'].includes(doc.status)
  const wasRead = isExtractable(doc.documentType) && ['VERIFIED', 'NEEDS_REVIEW', 'FAILED'].includes(doc.status)
  const processing = doc.status === 'PROCESSING'

  async function download() {
    setDownloading(true)
    setDownloadError(null)
    try {
      await saveDocument(claimId, doc.id, doc.fileName)
    } catch (error) {
      setDownloadError(error)
    } finally {
      setDownloading(false)
    }
  }

  const readError = read.isError ? describeApiError(read.error, 'that document') : null
  const finished = read.isSuccess ? documentStatusMark(read.data.documentStatus, doc.documentType).label : null

  return (
    <>
      <tr role="row" data-highlight={highlighted || undefined} data-superseded={doc.superseded || undefined} id={`doc-${doc.id}`} tabIndex={-1}>
        <th scope="row" role="rowheader">
          <span className={styles.name}>
            <span>{documentTypeLabel(doc.documentType)}</span>
            <span className={styles.mono}>{doc.fileName}</span>
            {doc.externalDocumentId ? <span className={styles.mono}>Your id: {doc.externalDocumentId}</span> : null}
          </span>
        </th>
        <td role="cell" data-label="State">
          <span className={styles.name}>
            <StatusMark state={status.state}>{status.label}</StatusMark>
            {doc.superseded ? <span className={styles.mono}>Replaced by a newer file</span> : null}
            {doc.supersedesDocumentId ? <span className={styles.mono}>Replaces an earlier file</span> : null}
          </span>
        </td>
        <td role="cell" data-label="Received">
          <time className={styles.mono} dateTime={doc.receivedAt} title={formatDateTimeWithZone(doc.receivedAt)}>
            {formatDateTime(doc.receivedAt)}
          </time>
        </td>
        <td role="cell" data-label="Size">
          <span className={styles.mono}>{formatBytes(doc.sizeBytes)}</span>
        </td>
        <td role="cell">
          <div className={styles.actions}>
            <Button size="sm" icon="download" onClick={() => void download()} loading={downloading} loadingLabel="Downloading" aria-label={`Download: ${doc.fileName}`}>
              Download
            </Button>
            {isImage(doc.contentType) ? (
              <Button size="sm" icon="eye" onClick={() => setPreview((p) => !p)} aria-expanded={preview} aria-label={`${preview ? 'Hide preview' : 'Preview'}: ${doc.fileName}`}>
                {preview ? 'Hide preview' : 'Preview'}
              </Button>
            ) : null}
            {canRead ? (
              <Button size="sm" variant="secondary" onClick={() => read.mutate(doc.id)} loading={read.isPending} loadingLabel={`Reading ${elapsed}s`} aria-label={`${doc.status === 'RECEIVED' ? 'Read document' : 'Read again'}: ${doc.fileName}`}>
                {doc.status === 'RECEIVED' ? 'Read document' : 'Read again'}
              </Button>
            ) : null}
            {wasRead ? (
              <Button size="sm" onClick={() => setDetails((d) => !d)} aria-expanded={details} aria-label={`${details ? 'Hide result' : 'Reading result'}: ${doc.fileName}`}>
                {details ? 'Hide result' : 'Reading result'}
              </Button>
            ) : null}
            {!doc.superseded ? (
              <Button size="sm" variant="ghost" onClick={() => onReplace(doc)} aria-label={`Replace: ${doc.fileName}`}>
                Replace
              </Button>
            ) : null}
          </div>
          {processing && !read.isPending ? <p className={styles.mono}>Being read now. Refresh the list to see the result.</p> : null}
          {read.isPending ? <p className={styles.mono}>Reading for {elapsed} s. This can take a few minutes, and the server decides when it is done.</p> : null}
          <div role="status" aria-live="polite">
            {finished ? <p className={styles.mono}>Finished reading: {finished}.</p> : null}
            {readError ? (
              <p className={styles.mono} role="alert" style={{ color: 'var(--danger)' }}>
                {readError.title} {readError.message}
              </p>
            ) : null}
            {downloadError ? (
              <p className={styles.mono} role="alert" style={{ color: 'var(--danger)' }}>
                {describeApiError(downloadError, 'that file').title}
              </p>
            ) : null}
          </div>
        </td>
      </tr>
      {details ? (
        <tr role="row">
          <td role="cell" colSpan={5}>
            <ExtractionSummary claimId={claimId} documentId={doc.id} />
          </td>
        </tr>
      ) : null}
      {preview ? (
        <tr role="row">
          <td role="cell" colSpan={5}>
            {blob.status === 'loading' ? <p className={styles.mono}>Loading the preview…</p> : null}
            {blob.status === 'error' ? <p className={styles.mono}>{describeApiError(blob.error, 'that file').title}</p> : null}
            {blob.status === 'ready' && blob.url ? <img className={styles.preview} src={blob.url} alt={`Preview of ${doc.fileName}`} /> : null}
          </td>
        </tr>
      ) : null}
    </>
  )
}
