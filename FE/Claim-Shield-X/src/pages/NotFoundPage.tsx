import { Link } from 'react-router'
import { PageHeader } from '@/components/ui/PageHeader'
import { buttonClass } from '@/components/ui/buttonClass'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

export function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <main id="main" className="container" tabIndex={-1}>
      <PageHeader title="Page not found" description="There is no page at this address. It may have moved, or the link may be mistyped." />
      <Link className={buttonClass({ variant: 'secondary' })} to="/">
        Go to the start page
      </Link>
    </main>
  )
}
