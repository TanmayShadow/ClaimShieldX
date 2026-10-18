import { useMemo } from 'react'
import { useOutletContext } from 'react-router'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { ChecksView } from '@/features/checks/ChecksView'
import { useLatestRuleRun, useRunRules } from '@/features/checks/hooks'
import { useDocuments } from '@/features/documents/hooks'
import type { ClaimOutletContext } from './ClaimWorkspacePage'

export function ClaimChecksPage() {
  const { claim } = useOutletContext<ClaimOutletContext>()
  const latest = useLatestRuleRun(claim.id)
  const documents = useDocuments(claim.id)
  const run = useRunRules(claim.id)
  const documentNames = useMemo(() => new Map((documents.data?.items ?? []).map((d) => [d.id, d.fileName])), [documents.data])

  if (latest.isPending) return <Skeleton variant="block" label="Loading the checks" />
  if (latest.isError) return <ErrorPanel error={latest.error} subject="the checks" onRetry={() => void latest.refetch()} retrying={latest.isFetching} />

  if (latest.data === null) {
    return (
      <>
        <EmptyState
          title="The checks have not been run yet"
          illustration={<EmptyFolder />}
          action={
            <Button variant="primary" onClick={() => run.mutate()} loading={run.isPending} loadingLabel="Checking">
              Run the checks
            </Button>
          }
        >
          Eight deterministic checks compare the claim record, its documents and the imported history: figures, dates and repeats. No model is involved, and nothing is decided about the claim.
        </EmptyState>
        {run.isError ? <ErrorPanel error={run.error} subject="the checks" onRetry={() => run.mutate()} retrying={run.isPending} /> : null}
      </>
    )
  }

  return <ChecksView run={latest.data} documentNames={documentNames} rerun={run} />
}
