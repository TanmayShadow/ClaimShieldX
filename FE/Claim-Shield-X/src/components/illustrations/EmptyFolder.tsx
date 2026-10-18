import { INK } from './ink'
import { Inked } from './InkFilter'

type EmptyFolderProps = { className?: string; title?: string }

/** An open, empty case folder with a blank sheet waiting to be filled. Used where a list has nothing in it yet. */
export function EmptyFolder({ className, title }: EmptyFolderProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 220 150"
      fill="none"
      focusable="false"
      {...(title ? { role: 'img', 'aria-label': title } : { 'aria-hidden': true })}
    >
      <Inked scale={1.6}>
        <path d="M14 38 V128 q0 6 6 6 H200 q6 0 6 -6 V34 q0 -6 -6 -6 H98 L86 14 H24 q-10 0 -10 10 z" fill="var(--paper-deep)" {...INK.props} />
        <g transform="rotate(-3 110 80)">
          <rect x="52" y="34" width="112" height="84" rx="3" fill="var(--surface)" {...INK.props} strokeDasharray="6 7" />
          <path d="M70 62 H146 M70 78 H132 M70 94 H112" {...INK.props} strokeWidth={2.2} opacity="0.35" />
        </g>
        <path d="M14 82 H206 V128 q0 6 -6 6 H20 q-6 0 -6 -6 z" fill="var(--paper)" {...INK.props} />
        <circle cx="168" cy="52" r="15" fill="var(--caution)" {...INK.props} strokeWidth={2.4} />
        <path d="M162 48 a6 6 0 1 1 8 5.5 c-2 1 -2.5 2 -2.5 3.5 M167.5 63 v0.5" {...INK.props} strokeWidth={2.4} />
      </Inked>
    </svg>
  )
}
