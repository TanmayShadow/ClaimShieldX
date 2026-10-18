import { INK } from './ink'
import { Inked } from './InkFilter'

type FieldInvestigatorProps = {
  /** `point`: clipboard in one hand, the other arm raised toward something on its right. `peek`: holds a magnifier up. */
  pose?: 'peek' | 'point'
  className?: string
  title?: string
}

const coat = 'var(--paper-deep)'

function Arm({ d }: { d: string }) {
  return (
    <>
      <path d={d} stroke={INK.stroke} strokeWidth="15" strokeLinecap="round" />
      <path d={d} stroke={coat} strokeWidth="9" strokeLinecap="round" />
    </>
  )
}

/**
 * The field investigator. Simple geometry, one stroke weight, a clipboard and a magnifier.
 * It points at evidence and never holds a verdict: no gavel, no stamp, no badge.
 */
export function FieldInvestigator({ pose = 'point', className, title }: FieldInvestigatorProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 290 260"
      fill="none"
      focusable="false"
      {...(title ? { role: 'img', 'aria-label': title } : { 'aria-hidden': true })}
    >
      {/* the raised arm sits behind the coat so it grows out of the shoulder */}
      <Inked scale={1.4}>
        {pose === 'point' ? (
          <>
            <Arm d="M168 190 C190 186 206 168 214 142" />
            <circle cx="215" cy="134" r="10" fill="var(--surface)" {...INK.props} />
            <path d="M219 125 l9 -17" {...INK.props} strokeWidth={5} />
          </>
        ) : (
          <>
            <Arm d="M168 196 C196 194 212 178 220 156" />
            <path d="M222 144 L246 112" {...INK.props} strokeWidth={7} />
            <circle cx="221" cy="150" r="10" fill="var(--surface)" {...INK.props} />
            <circle cx="250" cy="76" r="36" fill="var(--surface)" fillOpacity="0.92" {...INK.props} strokeWidth={4} />
            <path d="M234 70 H264 M234 84 H256" {...INK.props} strokeWidth={2.4} />
          </>
        )}
      </Inked>

      <Inked scale={1.4}>
        <path d="M34 258 C34 196 52 150 110 144 C168 150 186 196 186 258 Z" fill={coat} {...INK.props} />
        <path d="M110 144 V258" {...INK.props} strokeWidth={2.2} />
        <path d="M110 144 L92 168 L110 182 L128 168 Z" fill="var(--surface)" {...INK.props} strokeWidth={2.2} />
        <circle cx="116" cy="206" r="3" fill="var(--ink)" />
        <circle cx="116" cy="230" r="3" fill="var(--ink)" />

        <rect x="98" y="122" width="24" height="26" rx="6" fill="var(--surface)" {...INK.props} />
        <circle cx="110" cy="96" r="38" fill="var(--surface)" {...INK.props} />
        <circle cx="96" cy="100" r="3.2" fill="var(--ink)" />
        <circle cx="124" cy="100" r="3.2" fill="var(--ink)" />
        <path d="M99 114 Q110 123 121 114" {...INK.props} strokeWidth={2.6} />

        <path d="M72 88 C72 62 90 52 112 52 C134 52 150 64 150 88 Z" fill="var(--signal)" {...INK.props} />
        <path d="M148 88 C158 86 170 90 176 98 C164 98 154 96 148 92" fill="var(--signal)" {...INK.props} />
        <path d="M100 56 Q110 66 124 56" {...INK.props} strokeWidth={2} />
      </Inked>

      {pose === 'point' ? (
        <Inked scale={1.4}>
          {/* clipboard held against the chest, in front of the coat */}
          <g transform="rotate(-8 56 184)">
            <rect x="32" y="152" width="48" height="62" rx="4" fill="var(--surface)" {...INK.props} />
            <rect x="44" y="146" width="24" height="12" rx="3" fill="var(--ink)" />
            <path d="M42 172 H70 M42 182 H70 M42 192 H60" {...INK.props} strokeWidth={2.2} />
            <path d="M42 203 l5 5 9 -11" stroke="var(--evidence)" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
          </g>
          <circle cx="78" cy="204" r="9" fill="var(--surface)" {...INK.props} />
        </Inked>
      ) : null}
    </svg>
  )
}
