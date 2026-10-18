import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { buttonClass } from '@/components/ui/buttonClass'
import { PageHeader } from '@/components/ui/PageHeader'

function isChunkLoadFailure(error: unknown): boolean {
  const text = error instanceof Error ? `${error.name} ${error.message}` : ''
  return /dynamically imported module|Importing a module script failed|Loading chunk|error loading dynamically/i.test(text)
}

/**
 * Shown when a page fails to load or crashes while drawing. It says what to do and nothing about why: the message,
 * stack and component names stay in the console for the developer and never reach the screen.
 */
export function RouteError({ homeTo = '/app' }: { homeTo?: string }) {
  const error = useRouteError()

  if (import.meta.env.DEV) console.error(error)

  if (isRouteErrorResponse(error) && error.status === 404) {
    return (
      <div>
        <PageHeader title="Page not found" description="There is no page at this address." />
        <Link className={buttonClass()} to={homeTo}>
          Go to the workspace
        </Link>
      </div>
    )
  }

  const stale = isChunkLoadFailure(error)
  return (
    <div role="alert">
      <PageHeader
        title={stale ? 'This page could not be loaded.' : 'Something went wrong on this page.'}
        description={
          stale
            ? 'The application may have been updated while this tab was open. Reloading gets the newest version. Nothing you did has been lost.'
            : 'The page hit a problem it could not recover from. Nothing was changed. Reloading usually fixes it.'
        }
      />
      <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
        <button type="button" className={buttonClass({ variant: 'primary' })} onClick={() => window.location.reload()}>
          Reload the page
        </button>
        <Link className={buttonClass()} to={homeTo}>
          Go to the workspace
        </Link>
      </div>
    </div>
  )
}
