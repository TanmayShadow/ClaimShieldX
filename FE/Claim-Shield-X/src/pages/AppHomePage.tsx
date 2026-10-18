import { Link } from 'react-router'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { buttonClass } from '@/components/ui/buttonClass'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { PageHeader } from '@/components/ui/PageHeader'
import { Skeleton } from '@/components/ui/Skeleton'
import { StatusMark } from '@/components/ui/StatusMark'
import { useClaimsList } from '@/features/claims/hooks'
import { stageLabel } from '@/features/claims/labels'
import styles from '@/features/claims/NeedsAttention.module.css'
import { readinessMark, readinessMeaning } from '@/features/claims/readiness'
import { formatTime, pluralize } from '@/utils/format'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

// The largest page the API gives. Counts below cover only what was fetched, and say so when that is not everything.
const OVERVIEW_PARAMS = { page: 0, size: 100, sort: 'createdAt,desc' } as const
const SHOWN = 8

export function AppHomePage() {
  useDocumentTitle('Overview')
  const query = useClaimsList(OVERVIEW_PARAMS)
  const data = query.data

  const items = data?.items ?? []
  const count = (level: string) => items.filter((c) => c.readiness.level === level).length
  const notReady = count('NOT_READY')
  const warnings = count('READY_WITH_WARNINGS')
  const ready = count('READY')
  const attention = [...items.filter((c) => c.readiness.level === 'NOT_READY'), ...items.filter((c) => c.readiness.level === 'READY_WITH_WARNINGS')].slice(0, SHOWN)
  const covers = data ? (data.totalElements > data.items.length ? `your ${data.items.length} newest of ${data.totalElements} claims` : `all ${pluralize(data.totalElements, 'claim')}`) : ''

  return (
    <>
      <PageHeader
        title="Overview"
        description="Which claims are waiting on evidence, from the document checklist."
        actions={
          <Link className={buttonClass({ variant: 'primary' })} to="/app/claims/new">
            Create claim
          </Link>
        }
      />

      {query.isPending ? <Skeleton variant="rows" count={4} label="Loading the overview" /> : null}
      {query.isError ? <ErrorPanel error={query.error} subject="your claims" onRetry={() => void query.refetch()} retrying={query.isFetching} /> : null}

      {data && data.totalElements === 0 ? (
        <EmptyState
          title="No claims yet"
          illustration={<EmptyFolder />}
          action={
            <Link className={buttonClass({ variant: 'primary' })} to="/app/claims/new">
              Create the first claim
            </Link>
          }
        >
          Once claims exist, this page shows which ones are waiting on documents.
        </EmptyState>
      ) : null}

      {data && data.totalElements > 0 ? (
        <>
          <dl className={styles.summary} aria-label="Claims by evidence readiness">
            <div>
              <dt>Claims</dt>
              <dd>{data.totalElements}</dd>
            </div>
            <div>
              <dt>Not ready</dt>
              <dd>{notReady}</dd>
            </div>
            <div>
              <dt>Ready with warnings</dt>
              <dd>{warnings}</dd>
            </div>
            <div>
              <dt>Ready</dt>
              <dd>{ready}</dd>
            </div>
          </dl>
          <p className={styles.scope}>
            Readiness counts cover {covers}. Updated {formatTime(query.dataUpdatedAt)}.{' '}
            <button type="button" onClick={() => void query.refetch()} disabled={query.isFetching} style={{ background: 'none', border: 0, padding: 0, textDecoration: 'underline', textDecorationColor: 'var(--signal)', textDecorationThickness: 2 }}>
              {query.isFetching ? 'Refreshing' : 'Refresh'}
            </button>
          </p>

          <div className={styles.heading}>
            <h2>Waiting on evidence</h2>
            <Link to="/app/claims">View all claims</Link>
          </div>
          {attention.length === 0 ? (
            <p>No claim is waiting on evidence among the claims counted above.</p>
          ) : (
            <ul className={styles.list}>
              {attention.map((claim) => {
                const mark = readinessMark(claim.readiness)
                return (
                  <li key={claim.id}>
                    <span>
                      <Link className={styles.ref} to={`/app/claims/${claim.id}`}>
                        {claim.internalId}
                      </Link>{' '}
                      <span style={{ color: 'var(--ink-soft)', fontSize: 'var(--text-sm)' }}>· {stageLabel(claim.currentStage)}</span>
                    </span>
                    <StatusMark state={mark.state}>
                      {mark.label}
                      {mark.detail ? ` · ${mark.detail}` : ''}
                    </StatusMark>
                  </li>
                )
              })}
            </ul>
          )}

          <details className={styles.meanings}>
            <summary>What these readiness levels mean</summary>
            <dl>
              <dt>Not ready</dt>
              <dd>{readinessMeaning.NOT_READY}</dd>
              <dt>Ready with warnings</dt>
              <dd>{readinessMeaning.READY_WITH_WARNINGS}</dd>
              <dt>Ready</dt>
              <dd>{readinessMeaning.READY}</dd>
            </dl>
          </details>
        </>
      ) : null}
    </>
  )
}
