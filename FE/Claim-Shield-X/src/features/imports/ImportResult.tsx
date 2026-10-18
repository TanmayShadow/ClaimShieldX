import { Link } from 'react-router'
import { Callout } from '@/components/ui/Callout'
import { pluralize } from '@/utils/format'
import styles from './Imports.module.css'
import { importFailureText, importTypeLabel, isImportInFlight } from './labels'
import type { HistoricalImport } from './schemas'

/** What an import did, in words. A refused file is a result with a reason, never a silent nothing. */
export function ImportResult({ result, onAnother }: { result: HistoricalImport; onAnother?: () => void }) {
  const { counts } = result
  const rows = (
    <Link to={`/app/imports/${result.id}`} className={styles.inlineLink}>
      See every row
    </Link>
  )
  const another = onAnother ? (
    <button type="button" className={styles.linkButton} onClick={onAnother}>
      Add another file
    </button>
  ) : null

  if (isImportInFlight(result.status)) {
    return (
      <Callout tone="info" title="The file is being processed.">
        <p>This page looks again every few seconds. You can leave it: the import carries on.</p>
      </Callout>
    )
  }
  if (result.status === 'FAILED') {
    return (
      <Callout tone="danger" title="Nothing was stored.">
        <p>{importFailureText(result.failureCode)}</p>
        {result.failureDetail ? <p className={styles.detail}>{result.failureDetail}</p> : null}
        <p className={styles.callLinks}>{another}</p>
      </Callout>
    )
  }
  if (result.status === 'COMPLETED_WITH_ERRORS') {
    return (
      <Callout tone="caution" title={`${pluralize(counts.accepted, 'row')} stored, ${pluralize(counts.rejected, 'row')} rejected.`}>
        <p>
          The {importTypeLabel(result.importType).toLowerCase()} rows that were valid are kept. The rejected ones are listed with the reason for each, so they can be fixed and sent again.
          {counts.skippedDuplicate > 0 ? ` ${pluralize(counts.skippedDuplicate, 'row')} had already been stored and ${counts.skippedDuplicate === 1 ? 'was left as it is' : 'were left as they are'}.` : ''}
        </p>
        <p className={styles.callLinks}>
          <Link to={`/app/imports/${result.id}?status=REJECTED`} className={styles.inlineLink}>
            See the rejected rows
          </Link>{' '}
          {rows} {another}
        </p>
      </Callout>
    )
  }
  return (
    <Callout tone="evidence" title={counts.accepted > 0 ? `${pluralize(counts.accepted, 'row')} stored.` : 'Nothing new was stored.'}>
      <p>
        {counts.skippedDuplicate > 0
          ? `${pluralize(counts.skippedDuplicate, 'row')} had already been stored and ${counts.skippedDuplicate === 1 ? 'was left as it is' : 'were left as they are'}, so sending the same file twice changes nothing. `
          : ''}
        Claims you open from now on are compared with this history.
      </p>
      <p className={styles.callLinks}>
        {rows} {another}
      </p>
    </Callout>
  )
}
