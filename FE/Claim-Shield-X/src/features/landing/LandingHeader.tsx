import { Link, NavLink } from 'react-router'
import { BrandMark } from '@/components/illustrations/BrandMark'
import styles from './LandingHeader.module.css'

export type NavItem = { label: string; to: string; kind: 'anchor' | 'route' }

type LandingHeaderProps = { items: NavItem[]; actions?: React.ReactNode }

export function LandingHeader({ items, actions }: LandingHeaderProps) {
  return (
    <header className={styles.header}>
      <div className={`container ${styles.inner}`}>
        <Link to="/" className={styles.brand}>
          <BrandMark size={32} />
          <span>ClaimShield X</span>
        </Link>
        <nav className={styles.nav} aria-label="Sections">
          <ul>
            {items.map((item) => (
              <li key={item.to}>{item.kind === 'anchor' ? <a href={item.to}>{item.label}</a> : <NavLink to={item.to} end>{item.label}</NavLink>}</li>
            ))}
            {actions ? <li>{actions}</li> : null}
          </ul>
        </nav>
      </div>
    </header>
  )
}
