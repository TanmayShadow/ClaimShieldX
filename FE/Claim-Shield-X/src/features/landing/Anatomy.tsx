import { useId, useRef, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { StageIcon } from '@/components/illustrations/StageIcons'
import { Sheet } from '@/components/ui/Sheet'
import { StatusMark } from '@/components/ui/StatusMark'
import styles from './Anatomy.module.css'
import { stages } from './content'

const availabilityLabel = { available: '', planned: 'Planned' } as const

/** The investigation workflow as an interactive route. The tags say which stages exist today. */
export function Anatomy() {
  const base = useId()
  const [index, setIndex] = useState(0)
  const tabs = useRef<Array<HTMLButtonElement | null>>([])
  const stage = stages[index]!

  function select(next: number, focus = false) {
    const clamped = (next + stages.length) % stages.length
    setIndex(clamped)
    if (focus) tabs.current[clamped]?.focus()
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const keys: Record<string, number | undefined> = {
      ArrowRight: index + 1,
      ArrowDown: index + 1,
      ArrowLeft: index - 1,
      ArrowUp: index - 1,
      Home: 0,
      End: stages.length - 1,
    }
    const next = keys[event.key]
    if (next === undefined) return
    event.preventDefault()
    select(next, true)
  }

  return (
    <section className={styles.band} id="how-it-works" aria-labelledby="anatomy-title">
      <div className="container">
        <div className={styles.intro}>
          <h2 id="anatomy-title">Anatomy of an investigation</h2>
          <p>
            This is the workflow the product is built around. Choose a stage to see what
            goes in, what comes out, and what stays with a person.
          </p>
        </div>

        <div className={styles.route}>
          <div className={styles.list} role="tablist" aria-label="Stages of an investigation" onKeyDown={onKeyDown}>
            {stages.map((s, i) => (
              <button
                key={s.id}
                ref={(el) => {
                  tabs.current[i] = el
                }}
                type="button"
                role="tab"
                id={`${base}-tab-${i}`}
                aria-selected={i === index}
                aria-controls={`${base}-panel`}
                tabIndex={i === index ? 0 : -1}
                className={styles.tab}
                onClick={() => select(i)}
              >
                <span className={styles.icon}>
                  <StageIcon name={s.id} size={52} />
                </span>
                <span className={styles.text}>
                  <span className={styles.step}>{String(i + 1).padStart(2, '0')}</span>
                  <span className={styles.name}>{s.title}</span>
                  <span className={styles.state}>
                    <StatusMark state={s.availability === 'available' ? 'done' : 'pending'}>{availabilityLabel[s.availability]}</StatusMark>
                  </span>
                </span>
              </button>
            ))}
          </div>
        </div>

        <div className={styles.panel} role="tabpanel" id={`${base}-panel`} aria-labelledby={`${base}-tab-${index}`} tabIndex={0}>
          <Sheet tab={`Stage ${index + 1} of ${stages.length}`} folded>
            <div className={styles.panelGrid}>
              <div className={styles.panelHead}>
                <h3>{stage.title}</h3>
                <p>{stage.summary}</p>
                <StatusMark state={stage.availability === 'available' ? 'done' : 'pending'} boxed>
                  {availabilityLabel[stage.availability]}
                </StatusMark>
                <p style={{ color: 'var(--ink-soft)', fontSize: 'var(--text-sm)' }}>{stage.note}</p>
              </div>
              <dl className={styles.facts}>
                <div>
                  <dt>Goes in</dt>
                  <dd>{stage.goesIn}</dd>
                </div>
                <div>
                  <dt>Comes out</dt>
                  <dd>{stage.comesOut}</dd>
                </div>
                <div className={styles.human}>
                  <dt>Stays with a person</dt>
                  <dd>{stage.staysHuman}</dd>
                </div>
              </dl>
            </div>
          </Sheet>
        </div>
      </div>
    </section>
  )
}
