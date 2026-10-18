import { Callout } from '@/components/ui/Callout'
import { Sheet } from '@/components/ui/Sheet'
import { Stamp } from '@/components/ui/Stamp'
import { StatusMark } from '@/components/ui/StatusMark'
import { sampleHistory } from './data'
import type { StepProps } from './StepProps'
import styles from './Sample.module.css'

const ambiguityText: Record<string, string> = {
  OUTCOME_NOT_RECORDED: 'Outcome not recorded: not known, never cleared',
  WEAK_TOKEN_ONLY: 'Weak token only: context, not a match on its own',
}

const relationText = { SAME_VEHICLE: 'Same vehicle token', SAME_POLICY: 'Same policy token', SAME_CLAIMANT: 'Same claimant token' } as const

export function HistoryStep({ focus }: StepProps) {
  const h = sampleHistory
  return (
    <div className={styles.stack}>
      <div className={styles.heading}>
        <h3>Earlier claims that relate to this one</h3>
        <StatusMark state="info" boxed>
          Matches found
        </StatusMark>
      </div>
      <p className={styles.mono}>
        Compared with {h.dataAvailability.historicalClaimRows} imported claim rows and {h.dataAvailability.garageHistoryRows} garage-history rows (made up).{' '}
        {h.totalPriorClaims} earlier claims relate to this one.
      </p>

      {h.matches.map((m) => {
        const selected = focus.historyId === m.source.sourceRecordId
        return (
          <div key={m.source.sourceRecordId} data-focus-target={selected ? '' : undefined} tabIndex={selected ? -1 : undefined}>
            <Sheet tab={`Earlier claim · ${m.source.sourceRecordId}`} folded>
              <div className={styles.matchHead}>
                <h4 style={{ fontSize: 'var(--text-lg)' }}>{m.externalClaimId}</h4>
                <Stamp tone={m.strength === 'EXACT' ? 'blueprint' : 'neutral'}>{m.strength === 'EXACT' ? 'Exact match' : 'Context only'}</Stamp>
              </div>
              <ul className={styles.tags} aria-label="How it matched">
                <li className={styles.tag}>{relationText[m.relation]}</li>
                <li className={styles.tag}>
                  {m.daysBetween} days {m.timing.toLowerCase()}
                </li>
                <li className={styles.tag}>Method: exact token</li>
              </ul>
              <dl className={styles.kv}>
                <dt>Incident</dt>
                <dd>{m.incidentAt}</dd>
                <dt>Type</dt>
                <dd>{m.claimType}</dd>
                <dt>Status label</dt>
                <dd className="mono">{m.claimStatus} (the insurer&rsquo;s label, not interpreted)</dd>
                <dt>Garage</dt>
                <dd className="mono">{m.garageId}</dd>
                <dt>Damage areas</dt>
                <dd className="mono">{m.damageAreaCodes.length > 0 ? m.damageAreaCodes.join(', ') : 'none recorded'}</dd>
                <dt>Matched on</dt>
                <dd className="mono">{m.matchedFields.join(', ')}</dd>
              </dl>
              <p style={{ marginTop: 'var(--space-3)' }}>{m.explanation}</p>
              <ul className={styles.tags} aria-label="Limits of this match">
                {m.ambiguities.map((a) => (
                  <li className={styles.tag} key={a}>
                    {ambiguityText[a] ?? a}
                  </li>
                ))}
              </ul>
              <p className={styles.mono}>
                Source: import {m.source.importId} · row {m.source.rowNumber} · record {m.source.sourceRecordId}
              </p>
            </Sheet>
          </div>
        )
      })}

      <Callout tone="caution" title={`Garage ${h.garage.garageId}: context, not evidence`}>
        <p>
          {h.garage.importedHistoricalClaims} imported claims name this garage. {h.garage.caution}
        </p>
      </Callout>

      <p className={styles.meaning}>
        If no history had been imported, this page would say <strong>no historical data available</strong>. That is not the same as <strong>no match found</strong>, and the
        product keeps the two apart.
      </p>
    </div>
  )
}
