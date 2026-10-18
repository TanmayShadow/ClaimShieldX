import { Link } from 'react-router'
import { buttonClass } from '@/components/ui/buttonClass'
import { PageHeader } from '@/components/ui/PageHeader'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

export function AppNotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <>
      <PageHeader title="Page not found" description="There is no page at this address in the workspace. It may have moved, or the link may be mistyped." />
      <Link className={buttonClass()} to="/app">
        Go to the overview
      </Link>
    </>
  )
}
