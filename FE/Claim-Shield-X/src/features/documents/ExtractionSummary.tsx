import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { isApiError } from '@/apis/errors'
import { formatDateTime } from '@/utils/format'
import styles from './Documents.module.css'
import { useExtraction } from './hooks'
import { failureMessage } from './labels'

const severityText: Record<string, string> = { REVIEW: 'A person should look', INFO: 'For information' }

/** What happened when a document was read. Field values are not shown here: only whether reading worked and what needs a look. */
export function ExtractionSummary({ claimId, documentId }: { claimId: string; documentId: string }) {
  const query = useExtraction(claimId, documentId, true)

  if (query.isPending) return <Skeleton variant="lines" count={3} label="Loading the reading result" />
  if (query.isError) {
    if (isApiError(query.error) && query.error.code === 'NOT_FOUND') return <p className={styles.detail}>This document has not been read yet.</p>
    return <ErrorPanel error={query.error} subject="the reading result" onRetry={() => void query.refetch()} retrying={query.isFetching} />
  }

  const e = query.data
  const read = e.fields.filter((f) => f.state === 'EXTRACTED').length
  const unknown = e.fields.filter((f) => f.state === 'UNKNOWN').length
  return (
    <div className={styles.detail}>
      <dl>
        <dt>Attempt</dt>
        <dd>
          {e.attempt}, {e.status === 'SUCCEEDED' ? 'read' : 'could not be read'} at {formatDateTime(e.createdAt)}
        </dd>
        {e.modelId ? (
          <>
            <dt>Read by</dt>
            <dd className={styles.mono}>{e.modelId}</dd>
          </>
        ) : null}
        {e.pageCount ? (
          <>
            <dt>Pages</dt>
            <dd>{e.pageCount}</dd>
          </>
        ) : null}
        {e.status === 'SUCCEEDED' ? (
          <>
            <dt>Values</dt>
            <dd>
              {read} read, {unknown} unknown. Unknown means the document does not show it: nothing was guessed.
            </dd>
          </>
        ) : null}
      </dl>
      {e.status === 'FAILED' ? (
        <p>
          {failureMessage(e.failureCode)} {e.retryable ? 'Reading it again may work.' : 'Reading it again will not help.'}
        </p>
      ) : null}
      {e.issues.length > 0 ? (
        <>
          <strong>Things worth a look</strong>
          <ul>
            {e.issues.map((issue, i) => (
              <li key={`${issue.code}-${i}`}>
                <span className={styles.mono}>{severityText[issue.severity] ?? issue.severity}</span> <span>{issue.message}</span>
              </li>
            ))}
          </ul>
        </>
      ) : e.status === 'SUCCEEDED' ? (
        <p>Nothing was flagged while reading it.</p>
      ) : null}
      <p className={styles.mono}>Reading checks what the document says. It does not show that the document is true.</p>
    </div>
  )
}
