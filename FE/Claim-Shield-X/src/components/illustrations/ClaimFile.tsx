import { INK } from './ink'
import { Inked } from './InkFilter'

type ClaimFileProps = { className?: string; title?: string }

const paper = 'var(--surface)'

/**
 * The hero illustration: a case folder holding three sheets, joined by a dotted evidence path,
 * with an investigator looking over the edge through a magnifier. It carries no figures from a real claim.
 */
export function ClaimFile({ className, title }: ClaimFileProps) {
  return (
    <svg
      className={className}
      viewBox="0 0 600 440"
      fill="none"
      focusable="false"
      {...(title ? { role: 'img', 'aria-label': title } : { 'aria-hidden': true })}
    >
      <Inked scale={2.2}>
        {/* investigator, behind the folder */}
        <g transform="translate(352 -22) scale(0.74)">
          <path d="M34 258 C34 196 52 150 110 144 C168 150 186 196 186 258 Z" fill="var(--paper-deep)" {...INK.props} />
          <rect x="98" y="122" width="24" height="26" rx="6" fill={paper} {...INK.props} />
          <circle cx="110" cy="96" r="38" fill={paper} {...INK.props} />
          <circle cx="96" cy="100" r="3.2" fill="var(--ink)" />
          <circle cx="124" cy="100" r="3.2" fill="var(--ink)" />
          <path d="M99 114 Q110 123 121 114" {...INK.props} strokeWidth={2.6} />
          <path d="M72 88 C72 62 90 52 112 52 C134 52 150 64 150 88 Z" fill="var(--signal)" {...INK.props} />
          <path d="M148 88 C158 86 170 90 176 98 C164 98 154 96 148 92" fill="var(--signal)" {...INK.props} />
        </g>

        {/* folder back panel with tab */}
        <path d="M44 138 V402 q0 10 10 10 H516 q10 0 10 -10 V132 q0 -10 -10 -10 H262 l-22 -26 H62 q-18 0 -18 18 z" fill="var(--paper-deep)" {...INK.props} />

        {/* sheet 1: invoice */}
        <g transform="rotate(-6 150 270)">
          <rect x="74" y="112" width="190" height="262" rx="3" fill={paper} {...INK.props} />
          <rect x="92" y="98" width="46" height="18" rx="3" fill="var(--paper-deep)" {...INK.props} strokeWidth={2.2} />
          <path d="M92 142 H200 M92 162 H240 M92 182 H226 M92 202 H240 M92 222 H210" {...INK.props} strokeWidth={2.2} />
          <path d="M92 262 H246" {...INK.props} strokeWidth={2.2} />
          <path d="M150 288 H246" stroke="var(--signal)" strokeWidth="5" strokeLinecap="round" />
          <path d="M92 288 H130" {...INK.props} strokeWidth={2.2} />
        </g>

        {/* sheet 2: estimate */}
        <g transform="rotate(2 280 260)">
          <rect x="176" y="96" width="190" height="262" rx="3" fill={paper} {...INK.props} />
          <rect x="194" y="82" width="46" height="18" rx="3" fill="var(--paper-deep)" {...INK.props} strokeWidth={2.2} />
          <path d="M194 126 H300 M194 146 H340 M194 166 H322" {...INK.props} strokeWidth={2.2} />
          <rect x="190" y="182" width="164" height="22" fill="var(--caution)" opacity="0.7" />
          <path d="M194 193 H336" {...INK.props} strokeWidth={2.2} />
          <path d="M194 222 H330 M194 242 H318 M194 262 H340" {...INK.props} strokeWidth={2.2} />
        </g>

        {/* sheet 3: damage photograph */}
        <g transform="rotate(8 420 270)">
          <rect x="316" y="112" width="180" height="240" rx="3" fill={paper} {...INK.props} />
          <rect x="334" y="98" width="46" height="18" rx="3" fill="var(--paper-deep)" {...INK.props} strokeWidth={2.2} />
          <rect x="330" y="140" width="152" height="108" fill="var(--paper-deep)" {...INK.props} strokeWidth={2.2} />
          <path d="M342 222 q4 -26 30 -30 l18 -22 h46 l20 22 q22 4 22 30 z" fill={paper} {...INK.props} strokeWidth={2.4} />
          <circle cx="376" cy="226" r="9" fill={paper} {...INK.props} strokeWidth={2.4} />
          <circle cx="448" cy="226" r="9" fill={paper} {...INK.props} strokeWidth={2.4} />
          <path d="M430 196 q10 -10 22 -4 q4 10 -4 16 q-12 4 -18 -12" stroke="var(--signal)" strokeWidth="3.5" strokeLinecap="round" fill="none" />
          <path d="M330 272 H470 M330 292 H440" {...INK.props} strokeWidth={2.2} />
        </g>

        {/* folder front panel */}
        <path d="M44 314 H526 V402 q0 10 -10 10 H54 q-10 0 -10 -10 z" fill="var(--paper)" {...INK.props} />
        <rect x="72" y="338" width="168" height="52" rx="3" fill={paper} {...INK.props} strokeWidth={2.4} />
        <path d="M88 356 H176 M88 372 H146" {...INK.props} strokeWidth={2.2} />
        <rect x="196" y="354" width="26" height="22" rx="2" fill="none" stroke="var(--ink)" strokeWidth="2.2" strokeDasharray="3 4" />

        {/* evidence path */}
        <path d="M198 283 C228 296 240 262 258 238 C300 196 372 170 420 214" stroke="var(--signal)" strokeWidth="3.5" strokeLinecap="round" strokeDasharray="1 11" fill="none" />
        <g strokeWidth="2.6" stroke="var(--ink)" fill={paper}>
          <circle cx="198" cy="283" r="13" />
          <circle cx="258" cy="238" r="13" />
          <circle cx="420" cy="214" r="13" />
        </g>
        <g fill="var(--ink)" fontFamily="var(--font-mono)" fontSize="14" fontWeight="500" textAnchor="middle">
          <text x="198" y="288">1</text>
          <text x="258" y="243">2</text>
          <text x="420" y="219">3</text>
        </g>

        {/* magnifier, held from above */}
        <circle cx="318" cy="182" r="50" fill="var(--surface)" fillOpacity="0.6" {...INK.props} strokeWidth={4} />
        <path d="M290 168 H344 M288 184 H322" {...INK.props} strokeWidth={2.6} />
        <rect x="288" y="190" width="40" height="12" fill="var(--caution)" opacity="0.8" />
        <path d="M353 147 L402 98" {...INK.props} strokeWidth={9} />
        <path d="M424 112 C414 106 408 100 402 98" stroke="var(--ink)" strokeWidth="15" strokeLinecap="round" fill="none" />
        <path d="M424 112 C414 106 408 100 402 98" stroke="var(--paper-deep)" strokeWidth="9" strokeLinecap="round" fill="none" />
        <circle cx="402" cy="98" r="10" fill="var(--surface)" {...INK.props} />
      </Inked>
    </svg>
  )
}
