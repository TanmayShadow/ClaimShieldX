import { Callout } from '@/components/ui/Callout'
import { StatusMark } from '@/components/ui/StatusMark'
import type { MarkState } from '@/components/ui/StatusMark'
import { humanizeCode, sentenceList } from '@/features/checks/text'
import { formatDate, pluralize } from '@/utils/format'
import styles from './History.module.css'
import type { HistoryMatch, HistoryMatches } from './schemas'

// The four statuses are kept apart on purpose: "no data" is not "no match".
const statusText: Record<string, { tone: 'info' | 'caution' | 'evidence'; title: string; body: string }> = {
  MATCHES_FOUND: { tone: 'info', title: 'Earlier claims relate to this one.', body: 'Each is listed once, with the reason. A relationship is something to check against the evidence. It is not a finding.' },
  NO_MATCH_FOUND: { tone: 'evidence', title: 'No earlier claim in the imported history relates to this one.', body: 'This only covers the history that was imported.' },
  NO_HISTORICAL_DATA_AVAILABLE: { tone: 'caution', title: 'No historical claims have been imported, so nothing could be compared.', body: 'This is not the same as finding no match.' },
  HISTORY_IMPORT_FAILED: { tone: 'caution', title: 'The latest history import failed and no history is stored.', body: 'The absence of a match says nothing about this claim.' },
}

const relationText: Record<string, string> = { SAME_VEHICLE: 'Same vehicle', SAME_POLICY: 'Same policy', SAME_CLAIMANT: 'Same claimant' }
const timingText: Record<string, string> = { EARLIER: 'earlier', SAME_DAY: 'the same day', LATER: 'later' }

function timing(m: HistoryMatch): string {
  if (!m.timing) return 'Timing not recorded'
  if (m.timing === 'SAME_DAY') return 'The same day'
  return `${m.daysBetween === null || m.daysBetween === undefined ? 'Some days' : pluralize(m.daysBetween, 'day')} ${timingText[m.timing] ?? humanizeCode(m.timing)}`
}

function strengthMark(m: HistoryMatch): { state: MarkState; label: string } {
  return m.strength === 'EXACT' ? { state: 'attention', label: 'Exact link' } : { state: 'info', label: 'Context only' }
}

export function RelatedClaims({ data }: { data: HistoryMatches }) {
  const known = statusText[data.status]
  return (
    <div className={styles.stack}>
      <Callout tone={known?.tone ?? 'info'} title={known?.title ?? 'The history lookup returned a state this page does not know.'}>
        {known?.body ?? 'Nothing is concluded from it.'}
      </Callout>
      {data.dataAvailability?.latestClaimsImportFailed && data.status === 'MATCHES_FOUND' ? <Callout tone="caution" title="The latest import failed.">These matches come from earlier data and may be out of date.</Callout> : null}
      {data.truncated ? <Callout tone="caution" title="The list is incomplete.">{data.totalPriorClaims ?? 'More'} earlier claims relate to this one; only the closest are shown.</Callout> : null}

      {data.matches.length > 0 ? (
        <ol className={styles.matches} aria-label="Earlier claims that relate to this one">
          {data.matches.map((m) => {
            const mark = strengthMark(m)
            return (
              <li key={m.externalClaimId} className={styles.match}>
                <div className={styles.matchHead}>
                  <h3 className="mono">{m.externalClaimId}</h3>
                  <StatusMark state={mark.state} boxed>
                    {mark.label}
                  </StatusMark>
                  <span>{relationText[m.relation] ?? humanizeCode(m.relation)}</span>
                </div>
                <p>{m.explanation}</p>
                <dl className={styles.facts}>
                  <div><dt>When</dt><dd>{timing(m)}</dd></div>
                  {m.incidentAt ? <div><dt>Incident</dt><dd>{formatDate(m.incidentAt)}</dd></div> : null}
                  {m.claimType ? <div><dt>Type</dt><dd>{humanizeCode(m.claimType)}</dd></div> : null}
                  {m.claimStatus ? <div><dt>Insurer&rsquo;s status</dt><dd>{humanizeCode(m.claimStatus)}</dd></div> : null}
                  {m.garageId ? <div><dt>Garage</dt><dd className="mono">{m.garageId}</dd></div> : null}
                  {m.matchedFields?.length ? <div><dt>Shared</dt><dd>{sentenceList(m.matchedFields)}</dd></div> : null}
                </dl>
                {m.ambiguities?.length ? <p className={styles.ambiguity}>To keep in mind: {sentenceList(m.ambiguities, '; ')}.</p> : null}
              </li>
            )
          })}
        </ol>
      ) : null}

      {data.garage?.garageId ? (
        <p className={styles.note}>
          Garage <span className="mono">{data.garage.garageId}</span>: {pluralize(data.garage.importedHistoricalClaims ?? 0, 'imported historical claim')}. {data.garage.caution}
        </p>
      ) : null}
      {data.notes?.length ? (
        <ul className={styles.notes}>
          {data.notes.map((n, i) => (
            <li key={i}>{n}</li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
