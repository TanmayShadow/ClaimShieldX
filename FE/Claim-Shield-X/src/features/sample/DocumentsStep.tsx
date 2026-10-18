import { useRef } from 'react'
import { scrollToDetail } from '@/utils/scrollToDetail'
import { Sheet } from '@/components/ui/Sheet'
import { StatusMark } from '@/components/ui/StatusMark'
import type { MarkState } from '@/components/ui/StatusMark'
import { sampleClaim, sampleDocuments } from './data'
import type { StepProps } from './StepProps'
import styles from './Sample.module.css'
import type { SampleDocument } from './types'

const statusState: Record<SampleDocument['status'], MarkState> = { VERIFIED: 'done', NEEDS_REVIEW: 'attention', RECEIVED: 'info' }

function DocumentSheet({ doc, anchor }: { doc: SampleDocument; anchor?: string }) {
  const s = doc.sheet!
  const mark = (id: string, text: string) => (anchor === id ? <mark className={styles.hit}>{text}</mark> : text)
  return (
    <div className={styles.paper} role="group" aria-label={`${doc.typeLabel} as printed, synthetic`}>
      <div className={styles.paperHead}>
        <div>
          <h4>{s.title}</h4>
          <p className={styles.mono}>{s.issuer}</p>
        </div>
        <dl className={styles.paperMeta}>
          {s.meta.map((m) => (
            <div key={m.label}>
              <dt>{m.label}</dt>
              <dd>{m.anchor ? mark(m.anchor, m.value) : m.value}</dd>
            </div>
          ))}
        </dl>
      </div>
      <table className={styles.lines}>
        <caption className="sr-only">Line items</caption>
        <thead>
          <tr>
            <th scope="col">Description</th>
            <th scope="col">Amount (INR)</th>
          </tr>
        </thead>
        <tbody>
          {s.lines.map((l) => (
            <tr key={l.id}>
              <td>{mark(l.id, l.description)}</td>
              <td>{mark(l.id, l.amount)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          {s.totals.map((t) => (
            <tr key={t.id}>
              <th scope="row">{t.label}</th>
              <td>{mark(t.id, t.amount)}</td>
            </tr>
          ))}
        </tfoot>
      </table>
    </div>
  )
}

export function DocumentsStep({ focus, setFocus }: StepProps) {
  const doc = sampleDocuments.find((d) => d.id === focus.documentId) ?? sampleDocuments[1]!
  const field = doc.fields.find((f) => f.name === focus.field)
  const claimTarget = Boolean(focus.claimField)
  const detail = useRef<HTMLDivElement>(null)
  const hit = (key: string) => (focus.claimField === key ? styles.hit : undefined)

  return (
    <div className={styles.split}>
      <div className={styles.stack}>
        <div data-focus-target={claimTarget ? '' : undefined} tabIndex={claimTarget ? -1 : undefined}>
          <Sheet tab="Claim record · synthetic" padded>
            <dl className={styles.kv}>
              <dt>Reference</dt>
              <dd className="mono">{sampleClaim.internalId}</dd>
              <dt>Stage</dt>
              <dd>{sampleClaim.stage}</dd>
              <dt>Incident</dt>
              <dd className={hit('incident.incidentAt')}>
                {sampleClaim.incidentType}, {sampleClaim.incidentAt}
              </dd>
              <dt>Reported</dt>
              <dd>{sampleClaim.reportedAt}</dd>
              <dt>Policy period</dt>
              <dd>{sampleClaim.policyPeriod}</dd>
              <dt>Vehicle token</dt>
              <dd className="mono">{sampleClaim.vehicleToken}</dd>
              <dt>Garage</dt>
              <dd className="mono">{sampleClaim.garageId}</dd>
              <dt>Initial estimate</dt>
              <dd className={hit('financial.initialEstimateAmount')}>{sampleClaim.initialEstimate}</dd>
            </dl>
          </Sheet>
        </div>

        <div>
          <h3 className={styles.heading}>Documents</h3>
          <ul className={styles.list}>
            {sampleDocuments.map((d) => (
              <li key={d.id}>
                <button type="button" className={styles.item} aria-pressed={d.id === doc.id} onClick={() => {
                    setFocus({ documentId: d.id })
                    window.requestAnimationFrame(() => scrollToDetail(detail.current))
                  }}>
                  <span className={styles.itemTitle}>{d.typeLabel}</span>
                  <span className={styles.mono}>{d.fileName}</span>
                  <StatusMark state={statusState[d.status]}>{d.statusLabel}</StatusMark>
                </button>
              </li>
            ))}
          </ul>
        </div>
      </div>

      <div className={styles.stack} data-focus-target={claimTarget ? undefined : ''} tabIndex={claimTarget ? undefined : -1} ref={detail}>
        <div className={styles.heading}>
          <h3>{doc.typeLabel}</h3>
          <StatusMark state={statusState[doc.status]} boxed>
            {doc.statusLabel}
          </StatusMark>
        </div>

        {doc.note ? <p className={styles.meaning}>{doc.note}</p> : null}

        {doc.sheet ? (
          <>
            <DocumentSheet doc={doc} anchor={field?.anchor} />

            <div>
              <h4 className={styles.subhead}>What the system read</h4>
              <table className={styles.fields}>
                <caption className="sr-only">Fields read from {doc.typeLabel}</caption>
                <thead>
                  <tr>
                    <th scope="col">Field</th>
                    <th scope="col">As printed</th>
                    <th scope="col">Normalized</th>
                  </tr>
                </thead>
                <tbody>
                  {doc.fields.map((f) => (
                    <tr key={f.name} data-selected={f.name === field?.name}>
                      <th scope="row">
                        <button type="button" className={styles.fieldButton} aria-pressed={f.name === field?.name} onClick={() => setFocus({ documentId: doc.id, field: f.name })}>
                          {f.name}
                        </button>
                      </th>
                      <td>{f.state === 'EXTRACTED' ? f.value : <em>Unknown · {f.unknownReason?.toLowerCase().replace('_', ' ')}</em>}</td>
                      <td className="mono">{f.normalizedValue ?? 'not available'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {field ? (
              <div className={styles.sourceBox} aria-live="polite">
                {field.state === 'EXTRACTED' ? (
                  <>
                    <strong>
                      Source of {field.name}: page {field.page}
                    </strong>
                    <span className={styles.quote}>&ldquo;{field.quote}&rdquo;</span>
                    <span className={styles.mono}>The quote is the model&rsquo;s reading of the document. It is shown as plain text.</span>
                  </>
                ) : (
                  <>
                    <strong>{field.name} is unknown</strong>
                    <span>The document does not show it. Nothing was guessed or filled in.</span>
                  </>
                )}
              </div>
            ) : (
              <p className={styles.mono}>Choose a field to see where on the document it came from.</p>
            )}
          </>
        ) : (
          <div className={styles.photo}>
            <p>Synthetic placeholder. A real photograph would be stored here and could be downloaded, but it is not read.</p>
          </div>
        )}
      </div>
    </div>
  )
}
