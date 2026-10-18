import { Button } from '@/components/ui/Button'
import { StatusMark } from '@/components/ui/StatusMark'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './Investigation.module.css'
import { investigationStatusMark } from './labels'
import type { Investigation } from './schemas'

type HistoryProps = { items: Investigation[]; currentId: string; onSelect: (id: string) => void }

export function History({ items, currentId, onSelect }: HistoryProps) {
  return (
    <table className={styles.history} role="table">
      <caption className="sr-only">Investigations of this claim, newest first</caption>
      <thead role="rowgroup">
        <tr role="row">
          <th scope="col" role="columnheader">
            Queued
          </th>
          <th scope="col" role="columnheader">
            State
          </th>
          <th scope="col" role="columnheader">
            Attempt
          </th>
          <th scope="col" role="columnheader">
            <span className="sr-only">Actions</span>
          </th>
        </tr>
      </thead>
      <tbody role="rowgroup">
        {items.map((inv) => {
          const mark = investigationStatusMark(inv.status, inv.cancelRequested)
          return (
            <tr key={inv.id} role="row" data-current={inv.id === currentId || undefined}>
              <th scope="row" role="rowheader">
                <time dateTime={inv.createdAt} title={formatDateTimeWithZone(inv.createdAt)}>
                  {formatDateTime(inv.createdAt)}
                </time>
              </th>
              <td role="cell">
                <StatusMark state={mark.state}>{mark.label}</StatusMark>
              </td>
              <td role="cell">{inv.maxAttempts ? `${inv.attempt ?? 1} of ${inv.maxAttempts}` : 'Not recorded'}</td>
              <td role="cell">
                {inv.id === currentId ? (
                  <span className={styles.mono}>Shown above</span>
                ) : (
                  <Button size="sm" onClick={() => onSelect(inv.id)} aria-label={`Show the investigation queued ${formatDateTime(inv.createdAt)}`}>
                    Show
                  </Button>
                )}
              </td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
