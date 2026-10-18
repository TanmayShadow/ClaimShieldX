import { Sheet } from '@/components/ui/Sheet'
import { formatDate, formatDateTime, formatDateTimeWithZone, formatMoney } from '@/utils/format'
import styles from './ClaimRecord.module.css'
import { incidentTypeLabel, stageLabel } from './labels'
import type { ClaimDetail } from './schemas'

function Value({ children, mono }: { children: string | null | undefined; mono?: boolean }) {
  if (children === null || children === undefined || children === '') return <span className={styles.none}>Not provided</span>
  return <span className={mono ? styles.mono : undefined}>{children}</span>
}

function Rows({ rows }: { rows: Array<[string, string | null | undefined, boolean?]> }) {
  return (
    <dl className={styles.rows}>
      {rows.map(([label, value, mono]) => (
        <div key={label} style={{ display: 'contents' }}>
          <dt>{label}</dt>
          <dd>
            <Value mono={mono}>{value}</Value>
          </dd>
        </div>
      ))}
    </dl>
  )
}

/** The stored claim, shown back exactly as the server returned it. Nothing is derived or judged here. */
export function ClaimRecord({ claim }: { claim: ClaimDetail }) {
  const money = (amount: number | null | undefined) => (amount === null || amount === undefined ? null : formatMoney(amount, claim.financial.currency))
  return (
    <>
      <dl className={styles.strip} aria-label="Claim at a glance">
        <div>
          <dt>Stage</dt>
          <dd>{stageLabel(claim.currentStage)}</dd>
        </div>
        <div>
          <dt>Incident type</dt>
          <dd>{incidentTypeLabel(claim.incident.incidentType)}</dd>
        </div>
        <div>
          <dt>Claim version</dt>
          <dd>{claim.claimVersion}</dd>
        </div>
        <div>
          <dt>Created</dt>
          <dd>
            <time dateTime={claim.createdAt} title={formatDateTimeWithZone(claim.createdAt)}>
              {formatDateTime(claim.createdAt)}
            </time>
          </dd>
        </div>
        <div>
          <dt>Last updated</dt>
          <dd>
            <time dateTime={claim.updatedAt} title={formatDateTimeWithZone(claim.updatedAt)}>
              {formatDateTime(claim.updatedAt)}
            </time>
          </dd>
        </div>
      </dl>

      <Sheet tab="Claim record" folded>
        <div className={styles.groups}>
          <section className={styles.group}>
            <h3>Policy</h3>
            <Rows
              rows={[
                ['Policy token', claim.policy.policyToken, true],
                ['Policy type', claim.policy.policyType],
                ['Starts', claim.policy.policyStartDate ? formatDate(claim.policy.policyStartDate) : null],
                ['Ends', claim.policy.policyEndDate ? formatDate(claim.policy.policyEndDate) : null],
              ]}
            />
          </section>
          <section className={styles.group}>
            <h3>Claimant</h3>
            <Rows rows={[['Claimant token', claim.claimant?.claimantToken, true]]} />
          </section>
          <section className={styles.group}>
            <h3>Garage and vehicle</h3>
            <Rows
              rows={[
                ['Garage id', claim.garage.garageId, true],
                ['Vehicle token', claim.vehicle.vehicleToken, true],
                ['Make', claim.vehicle.make],
                ['Model', claim.vehicle.model],
              ]}
            />
          </section>
          <section className={styles.group}>
            <h3>Incident</h3>
            <Rows
              rows={[
                ['Happened', formatDateTime(claim.incident.incidentAt)],
                ['Reported', claim.incident.reportedAt ? formatDateTime(claim.incident.reportedAt) : null],
                ['Type', incidentTypeLabel(claim.incident.incidentType)],
                ['Location precision', claim.incident.permittedLocationPrecision],
              ]}
            />
          </section>
          <section className={styles.group}>
            <h3>Money</h3>
            <Rows
              rows={[
                ['Currency', claim.financial.currency, true],
                ['Initial estimate', money(claim.financial.initialEstimateAmount)],
                ['Approved estimate', money(claim.financial.approvedEstimateAmount)],
                ['Deductible', money(claim.financial.deductible)],
              ]}
            />
          </section>
        </div>
      </Sheet>
    </>
  )
}
