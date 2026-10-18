import { Link, useParams, useSearchParams } from 'react-router'
import { buttonClass } from '@/components/ui/buttonClass'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { PageHeader } from '@/components/ui/PageHeader'
import { Skeleton } from '@/components/ui/Skeleton'
import { useImport } from '@/features/imports/hooks'
import { ImportDetail } from '@/features/imports/ImportDetail'
import { ROW_STATUSES, importTypeLabel } from '@/features/imports/labels'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

const looksLikeUuid = (value: string | undefined) => !!value && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value)

export function ImportDetailPage() {
  const { importId } = useParams()
  const [search, setSearch] = useSearchParams()
  const valid = looksLikeUuid(importId)
  const record = useImport(valid ? importId! : '')
  useDocumentTitle('History import')

  const statusParam = search.get('status') ?? undefined
  const status = statusParam && (ROW_STATUSES as readonly string[]).includes(statusParam) ? statusParam : undefined
  const pageParam = Number.parseInt(search.get('page') ?? '0', 10)
  const page = Number.isFinite(pageParam) && pageParam > 0 ? pageParam : 0

  const crumbs = [{ label: 'Overview', to: '/app' }, { label: 'History imports', to: '/app/imports' }, { label: 'Import' }]
  const back = (
    <Link className={buttonClass()} to="/app/imports">
      Back to the imports
    </Link>
  )

  if (!valid) return <><PageHeader breadcrumbs={crumbs} title="Import not found" description="That address is not an import." />{back}</>
  if (record.isPending) return <><PageHeader breadcrumbs={crumbs} title="Import" /><Skeleton variant="block" label="Loading the import" /></>
  if (record.isError) {
    return (
      <>
        <PageHeader breadcrumbs={crumbs} title="Import" />
        <ErrorPanel error={record.error} subject="that import" onRetry={() => void record.refetch()} retrying={record.isFetching} extra={back} />
      </>
    )
  }
  return (
    <>
      <PageHeader breadcrumbs={crumbs} title={importTypeLabel(record.data.importType)} description="One file, what the server did with every row of it." />
      <ImportDetail
        record={record.data}
        status={status}
        page={page}
        onChange={(patch) => {
          const next = new URLSearchParams(search)
          if ('status' in patch) {
            if (patch.status) next.set('status', patch.status)
            else next.delete('status')
          }
          if ('page' in patch) {
            if (patch.page) next.set('page', String(patch.page))
            else next.delete('page')
          }
          setSearch(next)
        }}
      />
    </>
  )
}
