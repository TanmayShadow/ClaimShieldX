import { useEffect } from 'react'
import { Link } from 'react-router'
import { buttonClass } from '@/components/ui/buttonClass'
import { ensureSession, useSession } from './session'

type SessionActionProps = { variant?: 'primary' | 'secondary'; className?: string }

/** "Sign in" for visitors, "Open workspace" for someone already signed in. Public pages call the server only if this browser signed in before. */
export function SessionAction({ variant = 'secondary', className }: SessionActionProps) {
  const session = useSession()
  useEffect(() => {
    void ensureSession({ force: false })
  }, [])
  const signedIn = session.status === 'authenticated'
  return (
    <Link className={[buttonClass({ variant }), className].filter(Boolean).join(' ')} to={signedIn ? '/app' : '/login'}>
      {signedIn ? 'Open workspace' : 'Sign in'}
    </Link>
  )
}
