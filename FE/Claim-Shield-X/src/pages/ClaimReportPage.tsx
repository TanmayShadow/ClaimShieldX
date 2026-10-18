import { useOutletContext } from 'react-router'
import { Link } from 'react-router'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { EmptyState } from '@/components/ui/EmptyState'
import { buttonClass } from '@/components/ui/buttonClass'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { useLatestReport } from '@/features/report/hooks'
import { ReportView } from '@/features/report/ReportView'
import type { ClaimOutletContext } from './ClaimWorkspacePage'

export function ClaimReportPage() {
  const { claim } = useOutletContext<ClaimOutletContext>()
  const report = useLatestReport(claim.id)
  if (report.isPending) return <Skeleton variant="block" label="Loading the report" />
  if (report.isError) return <ErrorPanel error={report.error} subject="the report" onRetry={() => void report.refetch()} retrying={report.isFetching} />
  if (report.data === null) {
    return (
      <EmptyState
        title="There is no report yet"
        illustration={<EmptyFolder />}
        action={
          <Link className={buttonClass({ variant: 'primary' })} to="../investigation">
            Go to the investigation
          </Link>
        }
      >
        A report is written when an investigation finishes. It ranks where a person should look first, lists what the checks found and what could not be checked, and is where a decision is recorded.
      </EmptyState>
    )
  }
  return <ReportView report={report.data} />
}
