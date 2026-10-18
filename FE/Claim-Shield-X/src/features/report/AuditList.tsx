import { useState } from 'react'
import { Button } from '@/components/ui/Button'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './Report.module.css'
import { useAudit } from './hooks'
import { auditEventLabel } from './labels'

export function AuditList({ investigationId }: { investigationId: string }) {
  const [page, setPage] = useState(0)
  const audit = useAudit(investigationId, page)
  return (
    <section className={styles.audit} aria-labelledby="audit-title">
      <h3 id="audit-title">History of this investigation</h3>
      <p className={styles.note}>Every start, finish and decision is recorded with the time. Only ids, counts and codes are kept here, never a document or a reason.</p>
      {audit.isPending ? <Skeleton variant="rows" count={3} label="Loading the history" /> : null}
      {audit.isError ? <ErrorPanel error={audit.error} subject="the history" onRetry={() => void audit.refetch()} retrying={audit.isFetching} /> : null}
      {audit.data ? (
        <>
          <ol className={styles.auditList} aria-label="Investigation history, oldest first">
            {audit.data.items.map((e) => (
              <li key={e.id}>
                <time dateTime={e.createdAt} title={formatDateTimeWithZone(e.createdAt)}>
                  {formatDateTime(e.createdAt)}
                </time>
                <span>{auditEventLabel(e.eventType)}</span>
                <span className={styles.refId}>{e.actorUserId ? 'a person' : 'the system'}</span>
              </li>
            ))}
          </ol>
          {audit.data.totalPages > 1 ? (
            <div className={styles.pager}>
              <Button size="sm" onClick={() => setPage((p) => p - 1)} disabled={page === 0}>
                Earlier
              </Button>
              <span>
                Page {page + 1} of {audit.data.totalPages}
              </span>
              <Button size="sm" onClick={() => setPage((p) => p + 1)} disabled={page + 1 >= audit.data.totalPages}>
                Later
              </Button>
            </div>
          ) : null}
        </>
      ) : null}
    </section>
  )
}
