import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { StatusMark } from '@/components/ui/StatusMark'
import { formatBytes } from '@/features/documents/uploadRules'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import { useImportRows } from './hooks'
import styles from './Imports.module.css'
import { ROW_STATUSES, importStatusMark, importTypeLabel, rowErrorLabel, rowStatusLabels, rowStatusMark } from './labels'
import { ImportResult } from './ImportResult'
import type { HistoricalImport } from './schemas'

type ImportDetailProps = {
  record: HistoricalImport
  status: string | undefined
  page: number
  onChange: (patch: { status?: string | undefined; page?: number }) => void
}

function When({ iso, label }: { iso: string | null | undefined; label: string }) {
  if (!iso) return null
  return (
    <div>
      <dt>{label}</dt>
      <dd>
        <time dateTime={iso} title={formatDateTimeWithZone(iso)}>
          {formatDateTime(iso)}
        </time>
      </dd>
    </div>
  )
}

export function ImportDetail({ record, status, page, onChange }: ImportDetailProps) {
  const { counts } = record
  const hasRows = counts.totalRows > 0
  const rows = useImportRows(record.id, status, page, hasRows)
  const mark = importStatusMark(record.status)

  return (
    <div className={styles.stack}>
      <dl className={styles.facts}>
        <div>
          <dt>Holds</dt>
          <dd>{importTypeLabel(record.importType)}</dd>
        </div>
        <div>
          <dt>Source</dt>
          <dd className={styles.mono}>{record.source ?? 'Not given'}</dd>
        </div>
        <div>
          <dt>Result</dt>
          <dd>
            <StatusMark state={mark.state} boxed>
              {mark.label}
            </StatusMark>
          </dd>
        </div>
        <When iso={record.createdAt} label="Received" />
        <When iso={record.finishedAt} label="Finished" />
        {typeof record.sizeBytes === 'number' ? (
          <div>
            <dt>Size</dt>
            <dd>{formatBytes(record.sizeBytes)}</dd>
          </div>
        ) : null}
        {record.sha256 ? (
          <div>
            <dt>Checksum</dt>
            <dd className={styles.mono} title={record.sha256}>
              {record.sha256.slice(0, 16)}…
            </dd>
          </div>
        ) : null}
      </dl>

      <ImportResult result={record} />

      <dl className={styles.facts} aria-label="Row counts">
        <div>
          <dt>Rows in the file</dt>
          <dd>{counts.totalRows}</dd>
        </div>
        <div>
          <dt>Stored</dt>
          <dd>{counts.accepted}</dd>
        </div>
        <div>
          <dt>Rejected</dt>
          <dd>{counts.rejected}</dd>
        </div>
        <div>
          <dt>Already stored</dt>
          <dd>{counts.skippedDuplicate}</dd>
        </div>
      </dl>

      {hasRows ? (
        <section aria-labelledby="rows-title" className={styles.stack}>
          <div className={styles.head}>
            <h2 id="rows-title">Rows</h2>
            <div className={styles.filter} role="group" aria-label="Show rows">
              <Button size="sm" onClick={() => onChange({ status: undefined, page: 0 })} aria-pressed={!status}>
                All
              </Button>
              {ROW_STATUSES.map((s) => (
                <Button key={s} size="sm" onClick={() => onChange({ status: s, page: 0 })} aria-pressed={status === s}>
                  {rowStatusLabels[s]}
                </Button>
              ))}
            </div>
          </div>
          <p className={styles.note}>Rows count from 1, not including the header. A message never repeats the value that was wrong, so it is safe to share.</p>

          {rows.isPending ? <Skeleton variant="rows" count={4} label="Loading the rows" /> : null}
          {rows.isError ? <ErrorPanel error={rows.error} subject="the rows" onRetry={() => void rows.refetch()} retrying={rows.isFetching} /> : null}
          {rows.data && rows.data.items.length === 0 ? <EmptyState title="No rows match" headingLevel={3}>There are no rows with this result.</EmptyState> : null}
          {rows.data && rows.data.items.length > 0 ? (
            <>
              <div className={styles.tableWrap}>
                <table className={styles.table} role="table">
                  <caption className="sr-only">Rows of this import</caption>
                  <thead role="rowgroup">
                    <tr role="row">
                      <th scope="col" role="columnheader">Row</th>
                      <th scope="col" role="columnheader">Record id</th>
                      <th scope="col" role="columnheader">Result</th>
                      <th scope="col" role="columnheader">What to fix</th>
                    </tr>
                  </thead>
                  <tbody role="rowgroup">
                    {rows.data.items.map((r) => {
                      const m = rowStatusMark(r.status)
                      const errors = r.errors ?? []
                      return (
                        <tr key={r.rowNumber} role="row">
                          <th scope="row" role="rowheader" data-label="Row">{r.rowNumber}</th>
                          <td role="cell" data-label="Record id" className={styles.mono}>{r.sourceRecordId ?? 'Not given'}</td>
                          <td role="cell" data-label="Result">
                            <StatusMark state={m.state}>{m.label}</StatusMark>
                          </td>
                          <td role="cell" data-label="What to fix">
                            {errors.length === 0 ? (
                              <span className={styles.note}>Nothing</span>
                            ) : (
                              <ul className={styles.errors}>
                                {errors.map((e, i) => (
                                  <li key={i}>
                                    {e.field ? <span className={styles.mono}>{e.field}</span> : <span>The row</span>}: <strong>{rowErrorLabel(e.code)}</strong>
                                    {e.message ? <> &mdash; {e.message}</> : null}
                                  </li>
                                ))}
                              </ul>
                            )}
                          </td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
              {rows.data.totalPages > 1 ? (
                <nav className={styles.pager} aria-label="Pagination">
                  <span>
                    Page {rows.data.page + 1} of {rows.data.totalPages}
                  </span>
                  <div className={styles.pagerButtons}>
                    <Button icon="arrow-left" disabled={page === 0} onClick={() => onChange({ page: page - 1 })}>
                      Previous
                    </Button>
                    <Button disabled={page + 1 >= rows.data.totalPages} onClick={() => onChange({ page: page + 1 })}>
                      Next
                    </Button>
                  </div>
                </nav>
              ) : null}
            </>
          ) : null}
        </section>
      ) : null}
    </div>
  )
}
