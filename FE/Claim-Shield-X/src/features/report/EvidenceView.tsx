import { useEffect } from 'react'
import { useLocation } from 'react-router'
import { StatusMark } from '@/components/ui/StatusMark'
import { ValueList } from '@/features/checks/ValueList'
import { humanizeCode, sentenceList } from '@/features/checks/text'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './Report.module.css'
import { evidenceGroupLabels, evidenceGroupOrder, evidenceStateMark, relationshipLabel } from './labels'
import type { Evidence, Graph } from './schemas'

export function EvidenceView({ evidence, graph }: { evidence: Evidence; graph: Graph | undefined }) {
  const { hash } = useLocation()
  const target = hash.startsWith('#ev-') ? hash.slice(1) : null

  // A citation from the report lands on its item: scrolled into view and focused so a keyboard or screen-reader user arrives there too.
  useEffect(() => {
    if (!target) return
    const el = document.getElementById(target)
    el?.scrollIntoView?.({ block: 'center' })
    el?.focus()
  }, [target])

  const groups = [...new Set([...evidenceGroupOrder, ...evidence.items.map((i) => i.type)])]
    .map((type) => ({ type, items: evidence.items.filter((i) => i.type === type) }))
    .filter((g) => g.items.length > 0)
  const nodes = new Map((graph?.nodes ?? []).map((n) => [n.id, n.label]))

  return (
    <div className={styles.stack}>
      <div className={styles.head}>
        <div>
          <h2>Evidence</h2>
          <p className={styles.lede}>
            {evidence.itemCount} items, frozen{evidence.createdAt ? ' on ' : ''}
            {evidence.createdAt ? (
              <time dateTime={evidence.createdAt} title={formatDateTimeWithZone(evidence.createdAt)}>
                {formatDateTime(evidence.createdAt)}
              </time>
            ) : null}{' '}
            when the checks finished. Nothing here changes afterwards, and every finding in the report cites only these items. A value read from a document is a model&rsquo;s reading, never treated as true on its own.
          </p>
        </div>
      </div>

      {graph ? (
        <section className={styles.evidenceGroup} aria-labelledby="relations-title">
          <h3 id="relations-title">Relationships</h3>
          {graph.edges.length === 0 ? (
            <p className={styles.note}>No other claim is related to this one. A shared garage alone is context, not a link.</p>
          ) : (
            <ul className={styles.relations} aria-label="Related claims">
              {graph.edges.map((e) => (
                <li key={e.ref} className={styles.relation}>
                  <div className={styles.evidenceHead}>
                    <strong>
                      {nodes.get(e.source) ?? e.source} <span aria-hidden="true">&rarr;</span>
                      <span className="sr-only"> is linked to </span> {nodes.get(e.target) ?? e.target}
                    </strong>
                    <StatusMark state="info" boxed>
                      {relationshipLabel(e.relationship)}
                    </StatusMark>
                  </div>
                  <p className={styles.note}>
                    Matched on {e.fields && e.fields.length > 0 ? sentenceList(e.fields) : 'a recorded token'}
                    {e.method ? ` by ${humanizeCode(e.method).toLowerCase()}` : ''}. {e.uncertainty ?? ''}
                  </p>
                </li>
              ))}
            </ul>
          )}
        </section>
      ) : null}

      {groups.map((g) => (
        <section key={g.type} className={styles.evidenceGroup} aria-labelledby={`ev-group-${g.type}`}>
          <h3 id={`ev-group-${g.type}`}>
            {evidenceGroupLabels[g.type] ?? humanizeCode(g.type)} <span className={styles.count}>{g.items.length}</span>
          </h3>
          <ul className={styles.evidenceList}>
            {g.items.map((item) => {
              const mark = evidenceStateMark(item.state)
              const values = item.data ?? {}
              return (
                <li key={item.ref} id={`ev-${item.ref}`} tabIndex={-1} className={styles.evidenceItem} data-target={target === `ev-${item.ref}` || undefined}>
                  <div className={styles.evidenceHead}>
                    <strong>{item.label}</strong>
                    <StatusMark state={mark.state}>{mark.label}</StatusMark>
                  </div>
                  <span className={styles.refId}>{item.ref}</span>
                  {Object.keys(values).length > 0 ? (
                    <details className={styles.more}>
                      <summary>
                        What it holds<span className="sr-only"> for {item.label}</span>
                      </summary>
                      <ValueList values={values} />
                    </details>
                  ) : null}
                </li>
              )
            })}
          </ul>
        </section>
      ))}
    </div>
  )
}
