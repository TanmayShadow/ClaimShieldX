import { Link } from 'react-router'
import { buttonClass } from '@/components/ui/buttonClass'
import { SessionAction } from '@/features/auth/SessionAction'
import styles from './Sections.module.css'

export function FinalCta() {
  return (
    <section className={styles.cta} aria-labelledby="cta-title">
      <div className={`container ${styles.ctaInner}`}>
        <div>
          <h2 id="cta-title">Ready to follow a claim?</h2>
          <p>Walk through a made-up case first, or sign in to work with your organization&rsquo;s claims.</p>
        </div>
        <div className={styles.ctaActions}>
          <SessionAction variant="primary" />
          <Link className={buttonClass({ variant: 'secondary' })} to="/sample-investigation">
            Explore the sample investigation
          </Link>
        </div>
      </div>
    </section>
  )
}
