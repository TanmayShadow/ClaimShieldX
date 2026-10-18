import { INK } from './ink'
import { Inked } from './InkFilter'

export type StageIconName = 'intake' | 'extraction' | 'checks' | 'history' | 'explanations' | 'review' | 'validation' | 'report'

type StageIconProps = { name: StageIconName; size?: number; className?: string }

const sheet = 'var(--surface)'

const art: Record<StageIconName, React.ReactNode> = {
  validation: (
    <>
      <path d="M8 8 H36 L44 16 V52 H8 Z" fill={sheet} {...INK.props} />
      <path d="M14 22 H34 M14 30 H30" {...INK.props} strokeWidth={2.2} />
      <circle cx="42" cy="42" r="13" fill={sheet} {...INK.props} />
      <path d="M36 42 l4 4 8 -9" stroke="var(--evidence)" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M14 38 H26" {...INK.props} strokeWidth={2.2} strokeDasharray="2 4" />
    </>
  ),
  report: (
    <>
      <path d="M12 6 H44 V54 H12 Z" fill={sheet} {...INK.props} />
      <path d="M12 6 V54" {...INK.props} strokeWidth={5} />
      <path d="M22 18 H38 M22 26 H38 M22 34 H32" {...INK.props} strokeWidth={2.2} />
      <path d="M36 54 V40 L40.5 44 L45 40 V54" fill="var(--signal)" {...INK.props} strokeWidth={2.4} />
    </>
  ),
  intake: (
    <>
      <path d="M10 38 V54 H54 V38" {...INK.props} />
      <path d="M22 8 H40 L48 16 V44 H22 Z" fill={sheet} {...INK.props} />
      <path d="M40 8 V16 H48" {...INK.props} strokeWidth={2.2} />
      <path d="M28 24 H42 M28 31 H42 M28 38 H36" {...INK.props} strokeWidth={2.2} />
    </>
  ),
  extraction: (
    <>
      <path d="M8 6 H34 L42 14 V50 H8 Z" fill={sheet} {...INK.props} />
      <path d="M14 20 H34 M14 28 H34" {...INK.props} strokeWidth={2.2} />
      <rect x="12" y="34" width="22" height="7" fill="var(--caution)" opacity="0.85" />
      <circle cx="42" cy="38" r="11" fill={sheet} fillOpacity="0.7" {...INK.props} />
      <path d="M50 46 L58 55" {...INK.props} strokeWidth={5} />
    </>
  ),
  checks: (
    <>
      <path d="M6 40 L40 16 L52 33 L18 57 Z" fill={sheet} {...INK.props} />
      <path d="M17 41 L21 44 M25 35 L29 38 M33 29 L37 32 M41 23 L45 26" {...INK.props} strokeWidth={2.2} />
      <circle cx="48" cy="18" r="10" fill={sheet} {...INK.props} />
      <path d="M43 18 l4 4 7 -8" stroke="var(--evidence)" strokeWidth="3.2" strokeLinecap="round" strokeLinejoin="round" />
    </>
  ),
  history: (
    <>
      <path d="M6 24 V50 H34 V24 H20 L17 20 H6 Z" fill={sheet} {...INK.props} />
      <path d="M30 14 V40 H58 V14 H44 L41 10 H30 Z" fill="var(--paper-deep)" {...INK.props} />
      <path d="M22 30 C30 24 34 24 40 22" stroke="var(--signal)" strokeWidth="3" strokeLinecap="round" strokeDasharray="1 7" />
    </>
  ),
  explanations: (
    <>
      <path d="M32 58 V36 M32 36 C32 28 18 28 18 18 M32 36 C32 28 46 28 46 18" {...INK.props} />
      <rect x="6" y="4" width="22" height="16" rx="3" fill={sheet} {...INK.props} />
      <rect x="36" y="4" width="22" height="16" rx="3" fill={sheet} {...INK.props} />
      <path d="M14 15 L17 8 L20 15 M15 13 H19" {...INK.props} strokeWidth={2} />
      <path d="M44 8 H47 a3 3 0 0 1 0 4 H44 M44 12 H48 a3 3 0 0 1 0 4 H44 Z" {...INK.props} strokeWidth={2} />
    </>
  ),
  review: (
    <>
      <rect x="10" y="10" width="34" height="46" rx="4" fill={sheet} {...INK.props} />
      <rect x="20" y="5" width="14" height="10" rx="3" fill="var(--ink)" />
      <path d="M17 26 H37 M17 34 H37 M17 42 H29" {...INK.props} strokeWidth={2.2} />
      <path d="M58 20 L36 52 L32 54 L33 49 L55 17 Z" fill="var(--signal)" {...INK.props} strokeWidth={2.4} />
    </>
  ),
}

/** Hand-drawn stage glyphs for the investigation route. Decorative: the stage name always sits beside them. */
export function StageIcon({ name, size = 56, className }: StageIconProps) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 64 64" fill="none" aria-hidden="true" focusable="false">
      <Inked scale={1.1}>{art[name]}</Inked>
    </svg>
  )
}
