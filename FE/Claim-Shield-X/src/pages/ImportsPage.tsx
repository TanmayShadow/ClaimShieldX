import { useState } from 'react'
import { isApiError } from '@/apis/errors'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { Button } from '@/components/ui/Button'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { PageHeader } from '@/components/ui/PageHeader'
import { Skeleton } from '@/components/ui/Skeleton'
import { useImports } from '@/features/imports/hooks'
import { ImportForm } from '@/features/imports/ImportForm'
import styles from '@/features/imports/Imports.module.css'
import { ImportsTable } from '@/features/imports/ImportsTable'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

export function ImportsPage() {
  useDocumentTitle('History imports')
  const [page, setPage] = useState(0)
  const imports = useImports(page)
  // The sign-in token carries no role, so the server is asked: only an organization administrator may see or add imports.
  const forbidden = imports.isError && isApiError(imports.error) && imports.error.status === 403

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Overview', to: '/app' }, { label: 'History imports' }]}
        title="History imports"
        description="Earlier claims and garage figures your organization supplies as CSV. They are what a new claim is compared with."
      />
      {forbidden ? (
        <EmptyState title="Only an organization administrator manages history imports" illustration={<EmptyFolder />}>
          Your account can read claims, documents and reports, but not add or view history. Ask the person who manages your access to import it, or to give you that access. Earlier claims then appear on each claim&rsquo;s &ldquo;Earlier claims&rdquo; tab.
        </EmptyState>
      ) : (
        <div className={styles.layout}>
          <section className={styles.section} aria-labelledby="add-title">
            <h2 id="add-title">Add a file</h2>
            <p className={styles.lede}>
              Files are stored apart from live claims and never change. Sending the same file twice changes nothing. A row that cannot be used is rejected with its reason and the rest are kept. Nothing here is a finding: a match is something to check against the evidence.
            </p>
            <ImportForm />
          </section>

          <section className={styles.section} aria-labelledby="list-title">
            <h2 id="list-title">Earlier imports</h2>
            {imports.isPending ? <Skeleton variant="rows" count={3} label="Loading imports" /> : null}
            {imports.isError ? <ErrorPanel error={imports.error} subject="the imports" onRetry={() => void imports.refetch()} retrying={imports.isFetching} /> : null}
            {imports.data && imports.data.items.length === 0 ? <p className={styles.note}>Nothing has been imported yet. Until history is imported, a claim&rsquo;s earlier-claims check says it had nothing to compare with. That is not the same as finding no match.</p> : null}
            {imports.data && imports.data.items.length > 0 ? (
              <>
                <ImportsTable items={imports.data.items} />
                {imports.data.totalPages > 1 ? (
                  <nav className={styles.pager} aria-label="Pagination">
                    <span>
                      Page {imports.data.page + 1} of {imports.data.totalPages}
                    </span>
                    <div className={styles.pagerButtons}>
                      <Button icon="arrow-left" disabled={page === 0} onClick={() => setPage(page - 1)}>
                        Previous
                      </Button>
                      <Button disabled={page + 1 >= imports.data.totalPages} onClick={() => setPage(page + 1)}>
                        Next
                      </Button>
                    </div>
                  </nav>
                ) : null}
              </>
            ) : null}
          </section>
        </div>
      )}
    </>
  )
}
