import { StatusMark } from '@/components/ui/StatusMark'
import { capabilities, neverDoes } from './content'
import styles from './Sections.module.css'

export function Boundaries() {
  return (
    <section className={styles.section} id="boundaries" aria-labelledby="boundaries-title">
      <div className="container">
        <div className={styles.sectionHead}>
          <h2 id="boundaries-title">What exists today, and what does not</h2>
          <p>This is a working prototype built on synthetic data. The table says what is in it and what is only planned.</p>
        </div>

        <div className={styles.tableWrap}>
          <table className={styles.table} role="table">
            <caption className="sr-only">Capabilities and whether they exist </caption>
            <thead role="rowgroup">
              <tr role="row">
                <th scope="col" role="columnheader">Capability</th>
                <th scope="col" role="columnheader">Status</th>
                <th scope="col" role="columnheader">What it does</th>
              </tr>
            </thead>
            <tbody role="rowgroup">
              {capabilities.map((c) => (
                <tr key={c.name} role="row">
                  <th scope="row" role="rowheader">{c.name}</th>
                  <td role="cell">
                    <StatusMark state={c.availability === 'available' ? 'done' : 'pending'}>
                      {c.availability === 'available' ? '' : 'Planned'}
                    </StatusMark>
                  </td>
                  <td role="cell">{c.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className={styles.never}>
          <h3>By design, it will not</h3>
          <ul>
            {neverDoes.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
        </div>
      </div>
    </section>
  )
}
