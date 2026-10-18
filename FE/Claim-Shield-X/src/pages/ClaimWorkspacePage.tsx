import { Link, NavLink, Outlet, useLocation, useParams } from 'react-router'
import { buttonClass } from '@/components/ui/buttonClass'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { PageHeader } from '@/components/ui/PageHeader'
import { Skeleton } from '@/components/ui/Skeleton'
import styles from '@/features/claims/ClaimTabs.module.css'
import { useClaim } from '@/features/claims/hooks'
import { ReadinessStrip } from '@/features/claims/ReadinessStrip'
import type { ClaimDetail } from '@/features/claims/schemas'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

const looksLikeUuid = (value: string | undefined) => !!value && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)

export type ClaimOutletContext = { claim: ClaimDetail }

/** One claim: its header, evidence readiness and sections. Only sections that exist and work get a tab. */
export function ClaimWorkspacePage() {
  const { claimId } = useParams()
  const location = useLocation()
  const state = location.state as { listSearch?: string } | null
  // Only a query string produced by the list is honoured, so a crafted state cannot point the breadcrumb elsewhere.
  const listSearch = typeof state?.listSearch === 'string' && /^\?[\w=%,&.-]*$/.test(state.listSearch) ? state.listSearch : ''
  const claim = useClaim(looksLikeUuid(claimId) ? claimId : undefined)
  useDocumentTitle(claim.data?.internalId ?? 'Claim')

  const back = (
    <Link className={buttonClass()} to={`/app/claims${listSearch}`}>
      Back to the claims list
    </Link>
  )

  if (!looksLikeUuid(claimId)) {
    return (
      <>
        <PageHeader breadcrumbs={[{ label: 'Claims', to: '/app/claims' }, { label: 'Not found' }]} title="Claim not found" description="That address is not a claim." />
        {back}
      </>
    )
  }

  if (claim.isPending) return <Skeleton variant="block" label="Loading the claim" />

  if (claim.isError) {
    return (
      <>
        <PageHeader breadcrumbs={[{ label: 'Claims', to: `/app/claims${listSearch}` }, { label: 'Claim' }]} title="Claim" />
        <ErrorPanel error={claim.error} subject="that claim" onRetry={() => void claim.refetch()} retrying={claim.isFetching} extra={back} />
      </>
    )
  }

  const data = claim.data
  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Claims', to: `/app/claims${listSearch}` }, { label: data.internalId }]}
        title={<span className="mono">{data.internalId}</span>}
        description="The claim record, and the documents it holds against what its stage expects."
      />
      <ReadinessStrip claimId={data.id} />
      <nav className={styles.tabs} aria-label="Claim sections">
        <ul>
          <li>
            <NavLink to="" end>
              Record
            </NavLink>
          </li>
          <li>
            <NavLink to="documents">Documents</NavLink>
          </li>
          <li>
            <NavLink to="investigation">Investigation</NavLink>
          </li>
          <li>
            <NavLink to="report">Report</NavLink>
          </li>
          <li>
            <NavLink to="evidence">Evidence</NavLink>
          </li>
          <li>
            <NavLink to="checks">Checks</NavLink>
          </li>
          <li>
            <NavLink to="history">Earlier claims</NavLink>
          </li>
        </ul>
      </nav>
      <Outlet context={{ claim: data } satisfies ClaimOutletContext} />
    </>
  )
}
