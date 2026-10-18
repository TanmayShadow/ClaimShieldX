import type { ReactNode } from 'react'
import { LandingFooter } from '@/features/landing/LandingFooter'
import { LandingHeader } from '@/features/landing/LandingHeader'
import type { NavItem } from '@/features/landing/LandingHeader'

type PublicShellProps = { nav: NavItem[]; actions?: ReactNode; children: ReactNode; footerNote?: string }

/** Header, main landmark and footer shared by the public pages. */
export function PublicShell({ nav, actions, children, footerNote }: PublicShellProps) {
  return (
    <>
      <LandingHeader items={nav} actions={actions} />
      <main id="main" tabIndex={-1}>
        {children}
      </main>
      <LandingFooter note={footerNote} />
    </>
  )
}
