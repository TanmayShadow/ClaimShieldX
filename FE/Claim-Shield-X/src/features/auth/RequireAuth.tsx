import { useEffect } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { Skeleton } from '@/components/ui/Skeleton'
import { ensureSession, useSession } from './session'

/**
 * Keeps signed-out visitors out of the signed-in area. This is a courtesy for the person at the keyboard:
 * the backend decides what any request may do, and answers 401 or 403 whatever this guard shows.
 */
export function RequireAuth() {
  const session = useSession()
  const location = useLocation()

  useEffect(() => {
    void ensureSession({ force: true })
  }, [])

  if (session.status === 'unknown') {
    return (
      <div className="container" style={{ paddingBlock: 'var(--space-7)' }}>
        <Skeleton label="Checking your session" count={4} />
      </div>
    )
  }

  if (session.status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  }

  return <Outlet />
}
