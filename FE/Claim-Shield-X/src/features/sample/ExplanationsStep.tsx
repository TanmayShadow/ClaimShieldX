import { EvidenceReference } from '@/components/evidence/EvidenceReference'
import { Callout } from '@/components/ui/Callout'
import { Icon } from '@/components/ui/Icon'
import { Sheet } from '@/components/ui/Sheet'
import { Stamp } from '@/components/ui/Stamp'
import { sampleHypotheses } from './data'
import { resolveRef } from './refs'
import type { StepProps } from './StepProps'
import styles from './Sample.module.css'
import type { EvidenceRef } from './types'

type Entry = { text: string; ref: EvidenceRef }

function Block({ kind, title, icon, items, openRef }: { kind: string; title: string; icon: 'check-square' | 'dash-circle' | 'question'; items: Entry[]; openRef: StepProps['openRef'] }) {
  return (
    <div className={`${styles.evidenceBlock} ${styles[kind]}`}>
      <h5>
        <Icon name={icon} size={16} />
        {title}
      </h5>
      <ul>
        {items.map((item) => {
          const r = resolveRef(item.ref)
          return (
            <li key={item.text}>
              <span>{item.text}</span>
              <EvidenceReference kind={r.kind} label={r.label} detail={r.detail} onOpen={r.openable ? () => openRef(item.ref) : undefined} />
            </li>
          )
        })}
      </ul>
    </div>
  )
}

export function ExplanationsStep({ openRef }: StepProps) {
  return (
    <div className={styles.stack} data-focus-target="" tabIndex={-1}>
      <Callout tone="caution" title="Written by hand for this example">
        <p>
          The two explanations below were written by a person to show how competing explanations look: side by side, each with its own support, counter-evidence and
          unknowns, and every citation opening the evidence it names. In a real investigation a model proposes them and code checks every citation. A model did not
          write these, and neither is a verdict.
        </p>
      </Callout>

      <div className={styles.hypotheses}>
        {sampleHypotheses.map((h) => (
          <Sheet key={h.id} tab={`Explanation ${h.id} · synthetic`} folded>
            <Stamp tone="caution">Hypothesis · not a finding</Stamp>
            <h3 style={{ margin: 'var(--space-3) 0 var(--space-1)', fontSize: 'var(--text-lg)' }}>{h.title}</h3>
            <p className={styles.mono}>{h.stance}</p>
            <Block kind="supports" title="Supports" icon="check-square" items={h.supports} openRef={openRef} />
            <Block kind="weakens" title="Weakens or counts against" icon="dash-circle" items={h.weakens} openRef={openRef} />
            <Block kind="unknownBlock" title="Not known" icon="question" items={h.unknown} openRef={openRef} />
          </Sheet>
        ))}
      </div>
    </div>
  )
}
