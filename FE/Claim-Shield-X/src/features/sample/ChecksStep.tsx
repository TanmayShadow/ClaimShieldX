import { useRef } from 'react'
import { scrollToDetail } from '@/utils/scrollToDetail'
import { EvidenceReference } from '@/components/evidence/EvidenceReference'
import { Sheet } from '@/components/ui/Sheet'
import { StatusMark } from '@/components/ui/StatusMark'
import { ruleOutcomeMark, ruleOutcomeMeaning } from '@/features/rules/outcome'
import { ruleOutcomeCounts, sampleRules } from './data'
import { resolveRef } from './refs'
import type { StepProps } from './StepProps'
import styles from './Sample.module.css'

export function ChecksStep({ focus, setFocus, openRef }: StepProps) {
  const rule = sampleRules.find((r) => r.ruleId === focus.ruleId) ?? sampleRules.find((r) => r.outcome === 'FLAG') ?? sampleRules[0]!
  const mark = ruleOutcomeMark(rule.outcome, rule.severity)
  const detail = useRef<HTMLDivElement>(null)

  return (
    <div className={styles.split}>
      <div>
        <h3 className={styles.heading}>Deterministic checks</h3>
        <ul className={styles.counts} aria-label="Results by outcome">
          {Object.entries(ruleOutcomeCounts).map(([outcome, count]) => {
            const m = ruleOutcomeMark(outcome as keyof typeof ruleOutcomeMeaning)
            return (
              <li key={outcome}>
                <StatusMark state={m.state}>
                  {count} {m.label.toLowerCase()}
                </StatusMark>
              </li>
            )
          })}
        </ul>
        <ul className={styles.ruleList}>
          {sampleRules.map((r) => {
            const m = ruleOutcomeMark(r.outcome, r.severity)
            return (
              <li key={r.ruleId}>
                <button type="button" className={styles.item} aria-pressed={r.ruleId === rule.ruleId} onClick={() => {
                    setFocus({ ruleId: r.ruleId })
                    window.requestAnimationFrame(() => scrollToDetail(detail.current))
                  }}>
                  <span className={styles.itemTitle}>{r.label}</span>
                  <StatusMark state={m.state}>{m.label}</StatusMark>
                </button>
              </li>
            )
          })}
        </ul>
      </div>

      <div className={styles.stack} data-focus-target="" tabIndex={-1} ref={detail}>
        <Sheet tab={`Rule result · ${rule.ruleId}`} folded>
          <div className={styles.heading}>
            <h3>{rule.label}</h3>
            <StatusMark state={mark.state} boxed>
              {mark.label}
            </StatusMark>
          </div>
          <p>{rule.explanation}</p>

          <p className={styles.meaning} style={{ marginTop: 'var(--space-4)' }}>
            <strong className={styles.lead}>What {mark.label.toLowerCase()} means</strong> {ruleOutcomeMeaning[rule.outcome]}
          </p>

          <h4 className={styles.subhead}>Values used</h4>
          <dl className={styles.kv}>
            {rule.values.map((v) => (
              <div key={v.label} style={{ display: 'contents' }}>
                <dt>{v.label}</dt>
                <dd>{v.value}</dd>
              </div>
            ))}
          </dl>

          {rule.evidence.length > 0 ? (
            <>
              <h4 className={styles.subhead}>Evidence</h4>
              <ul className={styles.evidenceList}>
                {rule.evidence.map((ref, i) => {
                  const r = resolveRef(ref)
                  return (
                    <li key={i}>
                      <EvidenceReference kind={r.kind} label={r.label} detail={r.detail} onOpen={r.openable ? () => openRef(ref) : undefined} />
                    </li>
                  )
                })}
              </ul>
            </>
          ) : null}

          {rule.considerations ? (
            <>
              <h4 className={styles.subhead}>Harmless explanations to rule out first</h4>
              <ul className={styles.consider}>
                {rule.considerations.map((c) => (
                  <li key={c}>{c}</li>
                ))}
              </ul>
            </>
          ) : null}
        </Sheet>
      </div>
    </div>
  )
}
