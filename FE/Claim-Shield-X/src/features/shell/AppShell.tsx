import { useId, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { BrandMark } from '@/components/illustrations/BrandMark'
import { Callout } from '@/components/ui/Callout'
import { Icon } from '@/components/ui/Icon'
import { signOut, useSession } from '@/features/auth/session'
import styles from './AppShell.module.css'
import { navEntries } from './nav'
import type { NavEntry } from './nav'
import { useOnlineStatus } from './useOnlineStatus'

type AppShellProps = { entries?: NavEntry[] }

/**
 * The signed-in frame: navigation, whose workspace this is, and the way out. It shows only what the backend supports
 * and carries no counters, notifications or activity it cannot back.
 */
export function AppShell({ entries = navEntries }: AppShellProps) {
  const session = useSession()
  const location = useLocation()
  const navigate = useNavigate()
  const online = useOnlineStatus()
  const menuId = useId()
  // The menu is open only for the page it was opened on, so navigating closes it without an effect.
  const [openAt, setOpenAt] = useState<string | null>(null)
  const open = openAt === location.pathname
  const [signingOut, setSigningOut] = useState(false)
  const menuButton = useRef<HTMLButtonElement>(null)

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === 'Escape' && open) {
      setOpenAt(null)
      menuButton.current?.focus()
    }
  }

  async function leave() {
    if (signingOut) return
    setSigningOut(true)
    await signOut()
    navigate('/login', { replace: true })
  }

  const organization = session.user?.organizationName ?? 'Your organization'

  return (
    <div className={styles.shell} onKeyDown={onKeyDown}>
      <div className={styles.topbar}>
        <Link to="/app" className={styles.brand}>
          <BrandMark size={28} />
          <span>ClaimShield X</span>
        </Link>
        <button ref={menuButton} type="button" className={styles.menuButton} aria-expanded={open} aria-controls={menuId} onClick={() => setOpenAt(open ? null : location.pathname)}>
          <Icon name={open ? 'x' : 'menu'} size={18} />
          <span>Menu</span>
        </button>
      </div>

      <aside id={menuId} className={styles.side} data-open={open} aria-label="Workspace">
        <Link to="/app" className={styles.brand}>
          <BrandMark size={28} />
          <span>ClaimShield X</span>
        </Link>

        <div className={styles.workspace}>
          <span className={styles.workspaceLabel}>Workspace</span>
          <span className={styles.org}>{organization}</span>
        </div>

        <nav className={styles.nav} aria-label="Main">
          <ul>
            {entries.map((entry) => (
              <li key={entry.to}>
                <NavLink to={entry.to} end={entry.end}>
                  <Icon name={entry.icon} size={18} />
                  <span>{entry.label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>

        <div className={styles.account}>
          <span className={styles.workspaceLabel}>Signed in as</span>
          <span className={styles.email} title={session.user?.email}>
            {session.user?.email ?? 'Unknown account'}
          </span>
          <button type="button" className={styles.signOut} onClick={leave} aria-busy={signingOut || undefined}>
            <Icon name="logout" size={16} />
            <span>{signingOut ? 'Signing out' : 'Sign out'}</span>
          </button>
        </div>
      </aside>

      <main id="main" className={styles.main} tabIndex={-1}>
        <div className={styles.content}>
          {!online ? (
            <div className={styles.banner}>
              <Callout tone="caution" title="You are offline.">
                Pages already open stay readable, but nothing can be loaded or saved until the connection returns.
              </Callout>
            </div>
          ) : null}
          <Outlet />
        </div>
      </main>
    </div>
  )
}
