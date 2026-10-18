import { useLocation, useOutletContext } from 'react-router'
import { Callout } from '@/components/ui/Callout'
import { ClaimRecord } from '@/features/claims/ClaimRecord'
import type { ClaimOutletContext } from './ClaimWorkspacePage'

export function ClaimRecordPage() {
  const { claim } = useOutletContext<ClaimOutletContext>()
  const created = (useLocation().state as { created?: string } | null)?.created
  return (
    <>
      {created ? (
        <div style={{ marginBottom: 'var(--space-5)' }}>
          <Callout tone="evidence" title={`Claim ${created} was created.`}>
            It is shown below exactly as the server stored it. Check the details: the claim cannot be edited in this release.
          </Callout>
        </div>
      ) : null}
      <ClaimRecord claim={claim} />
    </>
  )
}
