import { Link } from 'react-router'
import { buttonClass } from '@/components/ui/buttonClass'
import { Anatomy } from '@/features/landing/Anatomy'
import { AtAGlance } from '@/features/landing/AtAGlance'
import { Boundaries } from '@/features/landing/Boundaries'
import { FindingExample } from '@/features/landing/FindingExample'
import { Hero } from '@/features/landing/Hero'
import { HumanInControl } from '@/features/landing/HumanInControl'
import { LandingFooter } from '@/features/landing/LandingFooter'
import { LandingHeader } from '@/features/landing/LandingHeader'
import type { NavItem } from '@/features/landing/LandingHeader'
import { SecuritySection } from '@/features/landing/SecuritySection'
import { FinalCta } from '@/features/landing/FinalCta'
import { SessionAction } from '@/features/auth/SessionAction'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

const nav: NavItem[] = [
  { label: 'How it works', to: '#how-it-works', kind: 'anchor' },
  { label: 'Evidence', to: '#evidence', kind: 'anchor' },
  { label: 'Human review', to: '#human', kind: 'anchor' },
  { label: 'Boundaries', to: '#boundaries', kind: 'anchor' },
  { label: 'Security', to: '#security', kind: 'anchor' },
  { label: 'Sample investigation', to: '/sample-investigation', kind: 'route' },
]

export function LandingPage() {
  useDocumentTitle(undefined)
  return (
    <>
      <LandingHeader items={nav} actions={<SessionAction />} />
      <main id="main" tabIndex={-1}>
        <Hero
          actions={
            <Link className={buttonClass({ variant: 'secondary' })} to="/sample-investigation">
              Explore the sample investigation
            </Link>
          }
        />
        <AtAGlance />
        <Anatomy />
        <FindingExample />
        <HumanInControl />
        <Boundaries />
        <SecuritySection />
        <FinalCta />
      </main>
      <LandingFooter />
    </>
  )
}
