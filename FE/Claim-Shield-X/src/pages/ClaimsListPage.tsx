import { Link, useSearchParams } from 'react-router'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { Button } from '@/components/ui/Button'
import { buttonClass } from '@/components/ui/buttonClass'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { SelectField } from '@/components/ui/Field'
import { PageHeader } from '@/components/ui/PageHeader'
import { Skeleton } from '@/components/ui/Skeleton'
import { ClaimsTable } from '@/features/claims/ClaimsTable'
import styles from '@/features/claims/ClaimsList.module.css'
import { useClaimsList } from '@/features/claims/hooks'
import { CLAIM_STAGES, stageLabel } from '@/features/claims/labels'
import { PAGE_SIZES, SORTS, parseListParams, toSearch } from '@/features/claims/listParams'
import type { ClaimListParams, ClaimSort } from '@/features/claims/listParams'
import { formatTime, pluralize } from '@/utils/format'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

const sortLabels: Record<ClaimSort, string> = {
  'createdAt,desc': 'Newest first',
  'createdAt,asc': 'Oldest first',
  'internalId,asc': 'Reference A to Z',
  'internalId,desc': 'Reference Z to A',
}

export function ClaimsListPage() {
  useDocumentTitle('Claims')
  const [search, setSearch] = useSearchParams()
  const params = parseListParams(search)
  const query = useClaimsList(params)

  function change(patch: Partial<ClaimListParams>) {
    // Changing the filter, sort or size starts again at the first page.
    const resetsPage = 'stage' in patch || 'sort' in patch || 'size' in patch
    setSearch(toSearch({ ...params, ...patch, ...(resetsPage ? { page: 0 } : {}) }))
  }

  const data = query.data
  const showingFrom = data && data.items.length > 0 ? data.page * data.size + 1 : 0
  const showingTo = data && data.items.length > 0 ? data.page * data.size + data.items.length : 0

  return (
    <>
      <PageHeader
        title="Claims"
        description="Every claim in your organization, with the state of its evidence."
        actions={
          <Link className={buttonClass({ variant: 'primary' })} to="/app/claims/new">
            Create claim
          </Link>
        }
      />

      <div className={styles.toolbar}>
        <SelectField label="Claim stage" value={params.stage ?? ''} onChange={(e) => change({ stage: (e.target.value || undefined) as ClaimListParams['stage'] })}>
          <option value="">All stages</option>
          {CLAIM_STAGES.map((stage) => (
            <option key={stage} value={stage}>
              {stageLabel(stage)}
            </option>
          ))}
        </SelectField>
        <SelectField label="Sort by" value={params.sort} onChange={(e) => change({ sort: e.target.value as ClaimSort })}>
          {SORTS.map((s) => (
            <option key={s} value={s}>
              {sortLabels[s]}
            </option>
          ))}
        </SelectField>
        <SelectField label="Claims per page" value={String(params.size)} onChange={(e) => change({ size: Number(e.target.value) })}>
          {PAGE_SIZES.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </SelectField>
        <div className={styles.refresh}>
          {query.dataUpdatedAt > 0 ? <span>Updated {formatTime(query.dataUpdatedAt)}</span> : null}
          <Button size="sm" icon="refresh" onClick={() => void query.refetch()} loading={query.isFetching && !query.isPending} loadingLabel="Refreshing">
            Refresh
          </Button>
        </div>
      </div>

      {query.isPending ? <Skeleton variant="rows" count={6} label="Loading claims" /> : null}

      {query.isError ? (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <ErrorPanel error={query.error} subject="the claims" onRetry={() => void query.refetch()} retrying={query.isFetching} />
        </div>
      ) : null}

      {data ? (
        <>
          <p className={styles.summary} role="status">
            {data.totalElements === 0
              ? params.stage
                ? `No claims at the stage "${stageLabel(params.stage)}".`
                : 'No claims yet.'
              : data.items.length === 0
                ? `Page ${data.page + 1} is past the last page.`
                : `Showing ${showingFrom} to ${showingTo} of ${pluralize(data.totalElements, 'claim')}${params.stage ? ` at "${stageLabel(params.stage)}"` : ''}.`}
          </p>

          {data.items.length > 0 ? (
            <ClaimsTable items={data.items} sort={params.sort} listSearch={toSearch(params).toString() ? `?${toSearch(params).toString()}` : ''} busy={query.isPlaceholderData} />
          ) : null}

          {data.totalElements === 0 && !params.stage ? (
            <EmptyState
              title="No claims yet"
              illustration={<EmptyFolder />}
              action={
                <Link className={buttonClass({ variant: 'primary' })} to="/app/claims/new">
                  Create the first claim
                </Link>
              }
            >
              Claims you create appear here, each with a checklist of the documents it still needs.
            </EmptyState>
          ) : null}

          {data.totalElements === 0 && params.stage ? (
            <EmptyState title="Nothing at this stage" action={<Button onClick={() => change({ stage: undefined })}>Show all stages</Button>}>
              No claim in your organization is at this stage. Other stages may have claims.
            </EmptyState>
          ) : null}

          {data.totalElements > 0 && data.items.length === 0 ? (
            <EmptyState title="There is nothing on this page" action={<Button onClick={() => setSearch(toSearch({ ...params, page: 0 }))}>Go to the first page</Button>}>
              The list is shorter than it was when this address was saved.
            </EmptyState>
          ) : null}

          {data.totalPages > 1 && data.items.length > 0 ? (
            <nav className={styles.pager} aria-label="Pagination">
              <span>
                Page {data.page + 1} of {data.totalPages}
              </span>
              <div className={styles.pagerButtons}>
                <Button icon="arrow-left" disabled={data.page === 0} onClick={() => setSearch(toSearch({ ...params, page: data.page - 1 }))}>
                  Previous
                </Button>
                <Button disabled={data.page + 1 >= data.totalPages} onClick={() => setSearch(toSearch({ ...params, page: data.page + 1 }))}>
                  Next
                </Button>
              </div>
            </nav>
          ) : null}
        </>
      ) : null}
    </>
  )
}
