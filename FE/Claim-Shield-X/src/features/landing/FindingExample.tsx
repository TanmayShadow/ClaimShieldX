import { Icon } from '@/components/ui/Icon'
import { Sheet } from '@/components/ui/Sheet'
import { Stamp } from '@/components/ui/Stamp'
import { syntheticFinding as f } from './content'
import styles from './Sections.module.css'

type Entry = { label: string; source: string }

function Column({ kind, title, icon, items }: { kind: 'supports' | 'weakens' | 'unknown'; title: string; icon: 'check-square' | 'dash-circle' | 'question'; items: Entry[] }) {
  return (
    <div className={`${styles.column} ${styles[kind]}`}>
      <h4>
        <Icon name={icon} size={18} />
        {title}
      </h4>
      <ul>
        {items.map((item) => (
          <li key={item.label}>
            <span>{item.label}</span>
            <span className={styles.source}>{item.source}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

export function FindingExample() {
  return (
    <section className={styles.section} id="evidence" aria-labelledby="evidence-title">
      <div className="container">
        <div className={styles.findingGrid}>
          <div className={styles.sectionHead}>
            <h2 id="evidence-title">A finding shows its evidence, not just a score</h2>
            <p>
              Every result lists what supports it, what weakens it, and what is not known. An inconsistency is a reason to look, never proof of anything.
              There is no number here because nothing in this example earns one.
            </p>
            <p className={styles.fineprint}>
              The example on the right is built from the same rule the prototype runs, with made-up figures. It is not connected to any claim.
            </p>
          </div>

          <Sheet tab={`Finding ${f.id} · worked example`} folded>
            <div className={styles.findingHead}>
              <Stamp tone="blueprint">Data</Stamp>
              <div className={styles.findingTitle}>
                <p className={styles.rule}>Rule: {f.rule}</p>
                <h3>{f.title}</h3>
              </div>
            </div>
            <p>{f.summary}</p>
            <div className={styles.columns}>
              <Column kind="supports" title="Supports" icon="check-square" items={f.supports} />
              <Column kind="weakens" title="Weakens" icon="dash-circle" items={f.weakens} />
              <Column kind="unknown" title="Not known" icon="question" items={f.unknown} />
            </div>
            <div className={styles.next}>
              <strong>Suggested next step</strong>
              <span>{f.next}</span>
            </div>
          </Sheet>
        </div>
      </div>
    </section>
  )
}
