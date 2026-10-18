import { Link } from 'react-router'
import { StatusMark } from '@/components/ui/StatusMark'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './ClaimsList.module.css'
import { stageLabel } from './labels'
import type { ClaimSort } from './listParams'
import { readinessMark } from './readiness'
import type { ClaimSummary } from './schemas'

type ClaimsTableProps = {
  items: ClaimSummary[]
  sort: ClaimSort
  /** The list's own address parameters, handed to the claim page so its "back" link returns to the same view. */
  listSearch: string
  busy?: boolean
}

function ariaSort(sort: ClaimSort, field: 'createdAt' | 'internalId'): 'ascending' | 'descending' | 'none' {
  const [by, direction] = sort.split(',')
  if (by !== field) return 'none'
  return direction === 'asc' ? 'ascending' : 'descending'
}

/** Explicit ARIA roles keep the table semantics when the layout stacks into rows on phones. */
export function ClaimsTable({ items, sort, listSearch, busy }: ClaimsTableProps) {
  return (
    <div className={styles.tableWrap} aria-busy={busy || undefined}>
      <table className={styles.table} role="table">
        <caption className="sr-only">Claims in your organization</caption>
        <thead role="rowgroup">
          <tr role="row">
            <th scope="col" role="columnheader" aria-sort={ariaSort(sort, 'internalId')}>
              Reference
            </th>
            <th scope="col" role="columnheader">
              Stage
            </th>
            <th scope="col" role="columnheader">
              Evidence readiness
            </th>
            <th scope="col" role="columnheader" aria-sort={ariaSort(sort, 'createdAt')}>
              Created
            </th>
          </tr>
        </thead>
        <tbody role="rowgroup">
          {items.map((claim) => {
            const mark = readinessMark(claim.readiness)
            return (
              <tr key={claim.id} role="row">
                <th scope="row" role="rowheader">
                  <Link className={styles.reference} to={`/app/claims/${claim.id}`} state={{ listSearch }}>
                    {claim.internalId}
                  </Link>
                </th>
                <td role="cell">{stageLabel(claim.currentStage)}</td>
                <td role="cell">
                  <span className={styles.readiness}>
                    <StatusMark state={mark.state}>{mark.label}</StatusMark>
                    {mark.detail ? <span className={styles.detail}>{mark.detail}</span> : null}
                  </span>
                </td>
                <td role="cell">
                  <time className={styles.time} dateTime={claim.createdAt} title={formatDateTimeWithZone(claim.createdAt)}>
                    {formatDateTime(claim.createdAt)}
                  </time>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
