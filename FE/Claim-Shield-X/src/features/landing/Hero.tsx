import { ClaimFile } from '@/components/illustrations/ClaimFile'
import { Stamp } from '@/components/ui/Stamp'
import { buttonClass } from '@/components/ui/buttonClass'
import styles from './Hero.module.css'

type HeroProps = {
  /** Extra calls to action, added by the tasks that create their destinations (sample case, sign-in). */
  actions?: React.ReactNode
}

export function Hero({ actions }: HeroProps) {
  return (
    <section className={styles.hero} aria-labelledby="hero-title">
      <div className={`container ${styles.grid}`}>
        <div className={styles.copy}>
          <p className={styles.eyebrow}>Motor-claim investigation workbench</p>
          <h1 className={styles.title} id="hero-title">
            Every claim has a story.{' '}
            <span className={styles.emphasis}>Follow the evidence.</span>
          </h1>
          <p className={styles.lede}>
            ClaimShield X brings claim documents, validation checks, supplied claim history and competing explanations into one reviewable workspace.
            It helps a team see what supports a concern, what weakens it, and what still needs a human answer.
          </p>
          <div className={styles.actions}>
            <a className={buttonClass({ variant: 'primary' })} href="#how-it-works">
              See how an investigation works
            </a>
            {actions}
          </div>
          <p className={styles.note}>Prototype. Every example on this page uses synthetic data.</p>
        </div>
        <figure className={styles.art} style={{ margin: 0 }}>
          <ClaimFile title="Illustration: a case folder holding an invoice, an estimate and a damage photograph, joined by a dotted evidence path. An investigator looks over the edge through a magnifier." />
          <figcaption className={styles.caption}>
            <Stamp tone="blueprint">Illustration · no real claim</Stamp>
          </figcaption>
        </figure>
      </div>
    </section>
  )
}
