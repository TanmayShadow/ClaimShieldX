import styles from './Sections.module.css'

export function AtAGlance() {
  return (
    <section className={styles.glance} aria-label="At a glance">
      <div className="container">
        <dl className={styles.spec}>
          <div>
            <dt>For</dt>
            <dd>Motor-insurance investigators and claim reviewers.</dd>
          </div>
          <div>
            <dt>Goes in</dt>
            <dd>The claim record, its documents such as invoices and estimates, and the earlier claims your organization supplies as CSV.</dd>
          </div>
          <div>
            <dt>Comes out</dt>
            <dd>Checks that cite their sources and questions worth asking. A reviewable report ranks where to look first. The decision stays with a person.</dd>
          </div>
        </dl>
      </div>
    </section>
  )
}
