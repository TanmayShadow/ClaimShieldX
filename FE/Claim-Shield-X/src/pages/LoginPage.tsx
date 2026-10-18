import { useEffect } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { FieldInvestigator } from '@/components/illustrations/FieldInvestigator'
import { BrandMark } from '@/components/illustrations/BrandMark'
import { Callout } from '@/components/ui/Callout'
import { Skeleton } from '@/components/ui/Skeleton'
import { LoginForm } from '@/features/auth/LoginForm'
import styles from '@/features/auth/Login.module.css'
import { safeReturnPath } from '@/features/auth/returnPath'
import { ensureSession, useSession } from '@/features/auth/session'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

export function LoginPage() {
  useDocumentTitle('Sign in')
  const session = useSession()
  const location = useLocation()
  const navigate = useNavigate()
  const [params] = useSearchParams()

  const fromState = (location.state as { from?: unknown } | null)?.from
  const returnTo = safeReturnPath(typeof fromState === 'string' ? fromState : params.get('returnTo'))

  useEffect(() => {
    void ensureSession({ force: false })
  }, [])

  if (session.status === 'authenticated') return <Navigate to={returnTo} replace />

  return (
    <div className={styles.page}>
      <aside className={styles.brand}>
        <Link to="/" className={styles.brandLink}>
          <BrandMark size={32} />
          <span>ClaimShield X</span>
        </Link>
        <div className={styles.statement}>
          <h2>Open the case file. Follow the evidence.</h2>
          <p>Sign in to see your organization&rsquo;s claims, their documents and what the checks found. The decision is always a person&rsquo;s.</p>
        </div>
        <div className={styles.art}>
          <FieldInvestigator pose="peek" />
        </div>
      </aside>

      <main id="main" className={styles.panel} tabIndex={-1}>
        <div className={styles.formWrap}>
          <div>
            <h1 data-page-title tabIndex={-1}>
              Sign in
            </h1>
            <p className={styles.lede}>Use the email address and password for your account.</p>
          </div>

          {session.reason === 'expired' ? (
            <Callout tone="info" title="Your session has ended.">
              Sign in again to carry on where you were.
            </Callout>
          ) : null}
          {session.reason === 'signed-out' ? (
            <Callout tone="neutral" title="You have signed out.">
              Nothing from your session is kept in this browser.
            </Callout>
          ) : null}

          {session.status === 'unknown' ? <Skeleton label="Checking for an existing session" count={3} /> : <LoginForm onSignedIn={() => navigate(returnTo, { replace: true })} />}

          <div className={styles.fine}>
            <p>There is no password reset or self-service sign-up in this prototype.</p>
            <p>
              This prototype is for synthetic data only. <Link to="/sample-investigation">Look at the sample investigation</Link> without an account.
            </p>
          </div>
          <Link className={styles.back} to="/">
            Back to the start page
          </Link>
        </div>
      </main>
    </div>
  )
}
