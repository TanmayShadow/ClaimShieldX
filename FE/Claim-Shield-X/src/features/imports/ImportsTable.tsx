import { Link } from 'react-router'
import { StatusMark } from '@/components/ui/StatusMark'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './Imports.module.css'
import { importStatusMark, importTypeLabel } from './labels'
import type { HistoricalImport } from './schemas'

export function ImportsTable({ items }: { items: HistoricalImport[] }) {
  return (
    <div className={styles.tableWrap}>
      <table className={styles.table} role="table">
        <caption className="sr-only">Imports of your organization, newest first</caption>
        <thead role="rowgroup">
          <tr role="row">
            <th scope="col" role="columnheader">Imported</th>
            <th scope="col" role="columnheader">Holds</th>
            <th scope="col" role="columnheader">Source</th>
            <th scope="col" role="columnheader">Result</th>
            <th scope="col" role="columnheader">Rows</th>
          </tr>
        </thead>
        <tbody role="rowgroup">
          {items.map((i) => {
            const mark = importStatusMark(i.status)
            return (
              <tr key={i.id} role="row">
                <th scope="row" role="rowheader" data-label="Imported">
                  <Link to={`/app/imports/${i.id}`}>
                    <time dateTime={i.createdAt} title={formatDateTimeWithZone(i.createdAt)}>
                      {formatDateTime(i.createdAt)}
                    </time>
                    <span className="sr-only"> , open this import</span>
                  </Link>
                </th>
                <td role="cell" data-label="Holds">{importTypeLabel(i.importType)}</td>
                <td role="cell" data-label="Source" className={styles.mono}>{i.source ?? 'Not given'}</td>
                <td role="cell" data-label="Result">
                  <StatusMark state={mark.state}>{mark.label}</StatusMark>
                </td>
                <td role="cell" data-label="Rows">
                  {i.counts.totalRows === 0 ? 'None' : `${i.counts.accepted} stored · ${i.counts.rejected} rejected · ${i.counts.skippedDuplicate} already stored`}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
