import { Link, useOutletContext } from 'react-router'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { EmptyState } from '@/components/ui/EmptyState'
import { buttonClass } from '@/components/ui/buttonClass'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { useInvestigations } from '@/features/investigations/hooks'
import { EvidenceView } from '@/features/report/EvidenceView'
import { useEvidence, useGraph } from '@/features/report/hooks'
import type { ClaimOutletContext } from './ClaimWorkspacePage'

/** The frozen evidence of the claim's newest investigation that got as far as the checks. */
export function ClaimEvidencePage() {
  const { claim } = useOutletContext<ClaimOutletContext>()
  const list = useInvestigations(claim.id)
  const frozen = list.data?.items.find((i) => i.snapshotId)
  const evidence = useEvidence(frozen?.id)
  const graph = useGraph(frozen?.id)

  if (list.isPending || (frozen && evidence.isPending)) return <Skeleton variant="block" label="Loading the evidence" />
  if (list.isError) return <ErrorPanel error={list.error} subject="the investigations" onRetry={() => void list.refetch()} retrying={list.isFetching} />
  if (!frozen) {
    return (
      <EmptyState
        title="No evidence has been frozen yet"
        illustration={<EmptyFolder />}
        action={
          <Link className={buttonClass({ variant: 'primary' })} to="../investigation">
            Go to the investigation
          </Link>
        }
      >
        When an investigation has run the checks, the claim, its documents, the readings, the rule results and the earlier claims are frozen as one set of evidence. Everything in the report cites only that set.
      </EmptyState>
    )
  }
  if (evidence.isError) return <ErrorPanel error={evidence.error} subject="the evidence" onRetry={() => void evidence.refetch()} retrying={evidence.isFetching} />
  return <EvidenceView evidence={evidence.data!} graph={graph.data} />
}
