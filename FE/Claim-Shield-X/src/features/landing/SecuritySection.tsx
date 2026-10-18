import { notClaimed, securityControls } from './content'
import styles from './Sections.module.css'

export function SecuritySection() {
  return (
    <section className={styles.section} id="security" aria-labelledby="security-title" style={{ paddingTop: 0 }}>
      <div className="container">
        <div className={styles.sectionHead}>
          <h2 id="security-title">Security and responsible use</h2>
          <p>Only controls the prototype already implements are listed. Nothing here is a certification.</p>
        </div>
        <div className={styles.securityGrid}>
          <ol className={styles.controls} aria-label="Implemented controls">
            {securityControls.map((c) => (
              <li key={c.id}>
                <span>{c.id}</span>
                <p>{c.text}</p>
              </li>
            ))}
          </ol>
          <div className={styles.responsible}>
            <h3>Responsible use</h3>
            <p>
              Use synthetic data only. While the free model tier is in use, the provider&rsquo;s terms allow people to review what is sent to the
              model, so real claim data must not be uploaded.
            </p>
            <h3>Not claimed</h3>
            <ul>
              {notClaimed.map((line) => (
                <li key={line}>{line}</li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </section>
  )
}
