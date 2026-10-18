import { useOutletContext } from 'react-router'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { useHistoryMatches } from '@/features/checks/hooks'
import { RelatedClaims } from '@/features/history/RelatedClaims'
import type { ClaimOutletContext } from './ClaimWorkspacePage'

export function ClaimHistoryPage() {
  const { claim } = useOutletContext<ClaimOutletContext>()
  const history = useHistoryMatches(claim.id)
  return (
    <section aria-labelledby="related-title" style={{ display: 'grid', gap: 'var(--space-4)' }}>
      <div>
        <h2 id="related-title" style={{ fontSize: 'var(--text-xl)' }}>
          Earlier claims
        </h2>
        <p style={{ margin: 'var(--space-2) 0 0', maxWidth: 'var(--measure)', color: 'var(--ink-soft)' }}>
          Claims from the imported history that share this claim&rsquo;s vehicle, policy or claimant token. Only exact tokens are compared. Nothing here is a finding.
        </p>
      </div>
      {history.isPending ? <Skeleton variant="rows" count={3} label="Loading earlier claims" /> : null}
      {history.isError ? <ErrorPanel error={history.error} subject="the earlier claims" onRetry={() => void history.refetch()} retrying={history.isFetching} /> : null}
      {history.data ? <RelatedClaims data={history.data} /> : null}
    </section>
  )
}
