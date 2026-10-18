import { useState } from 'react'
import { BrandMark } from '@/components/illustrations/BrandMark'
import { ClaimFile } from '@/components/illustrations/ClaimFile'
import { FieldInvestigator } from '@/components/illustrations/FieldInvestigator'
import { Button } from '@/components/ui/Button'
import { Callout } from '@/components/ui/Callout'
import { ConfirmActionDialog } from '@/components/ui/ConfirmActionDialog'
import { EmptyState } from '@/components/ui/EmptyState'
import { SelectField, TextAreaField, TextField } from '@/components/ui/Field'
import { PageHeader } from '@/components/ui/PageHeader'
import { Sheet } from '@/components/ui/Sheet'
import { Skeleton } from '@/components/ui/Skeleton'
import { Stamp } from '@/components/ui/Stamp'
import { StatusMark } from '@/components/ui/StatusMark'
import type { MarkState } from '@/components/ui/StatusMark'
import { contrastRatio } from '@/utils/contrast'
import { useDocumentTitle } from '@/utils/useDocumentTitle'
import styles from './DesignSandboxPage.module.css'

const palette: Array<{ token: string; use: string; against: string }> = [
  { token: 'paper', use: 'Page canvas', against: 'ink' },
  { token: 'paper-deep', use: 'Alternate grouping', against: 'ink' },
  { token: 'surface', use: 'Sheets, forms, readable content', against: 'ink' },
  { token: 'ink', use: 'Text, navigation, dark panels', against: 'paper' },
  { token: 'ink-soft', use: 'Secondary text', against: 'paper' },
  { token: 'line', use: 'Dividers only (below 3:1 by design)', against: 'surface' },
  { token: 'line-strong', use: 'Borders of controls', against: 'surface' },
  { token: 'signal', use: 'One primary action per view', against: 'ink' },
  { token: 'signal-ink', use: 'Accent text', against: 'paper' },
  { token: 'caution', use: 'Attention marker fill', against: 'ink' },
  { token: 'caution-ink', use: 'Attention text', against: 'paper' },
  { token: 'evidence', use: 'Evidence marker fill', against: 'ink' },
  { token: 'evidence-ink', use: 'Evidence and completed text', against: 'paper' },
  { token: 'blueprint', use: 'Neutral information, focus ring', against: 'paper' },
  { token: 'danger', use: 'Errors and destructive actions', against: 'paper' },
]

function readToken(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(`--${name}`).trim()
}

function Swatch({ token, use, against }: { token: string; use: string; against: string }) {
  const value = readToken(token)
  const other = readToken(against)
  const ratio = value && other ? contrastRatio(value, other).toFixed(2) : 'n/a'
  return (
    <li className={styles.swatch}>
      <div className={styles.chip} style={{ background: `var(--${token})` }} />
      <div className={styles.swatchText}>
        <strong>--{token}</strong>
        <span className="mono">{value}</span>
        <span>{use}</span>
        <span className="mono">
          {ratio}:1 vs {against}
        </span>
      </div>
    </li>
  )
}

const markStates: Array<[MarkState, string]> = [
  ['pending', 'Queued'],
  ['active', 'Running'],
  ['done', 'Completed'],
  ['attention', 'Needs review'],
  ['blocked', 'Not ready'],
  ['failed', 'Failed'],
  ['skipped', 'Skipped'],
  ['unknown', 'Not available'],
  ['info', 'Received'],
]

export function DesignSandboxPage() {
  useDocumentTitle('Design sandbox')
  const [dialog, setDialog] = useState(false)
  const [pending, setPending] = useState(false)
  const [loading, setLoading] = useState(false)

  function confirm() {
    setPending(true)
    window.setTimeout(() => {
      setPending(false)
      setDialog(false)
    }, 900)
  }

  return (
    <main id="main" className="container" tabIndex={-1}>
      <PageHeader
        breadcrumbs={[{ label: 'Start', to: '/' }, { label: 'Design sandbox' }]}
        title="Design sandbox"
        description="Development-only page for reviewing tokens and primitives. It is not part of the production build and holds no claim data."
      />

      <div className={styles.page}>
        <Sheet tab="01 · Type" folded>
          <div className={styles.cols}>
            <div className={styles.specimen}>
              <p className="mono">Display · Syne 700</p>
              <p className={styles.display} style={{ fontFamily: 'var(--font-display)', fontWeight: 700 }}>
                Follow the evidence.
              </p>
            </div>
            <div className={styles.specimen}>
              <h3>Title · Syne 700</h3>
              <p>
                Body text is DM Sans at 15px with a 1.55 line height. It stays readable at 200% zoom, keeps lines near 66 characters, and
                never goes below 12px even for metadata.
              </p>
              <p className="mono" style={{ marginTop: 'var(--space-2)' }}>
                CLM-DEMO-1001 · 2026-10-18T09:12:44Z · sha256 9f2c…
              </p>
            </div>
          </div>
        </Sheet>

        <Sheet tab="02 · Palette (ratios computed from the live tokens)">
          <ul className={styles.swatches}>
            {palette.map((p) => (
              <Swatch key={p.token} {...p} />
            ))}
          </ul>
        </Sheet>

        <Sheet tab="03 · Buttons">
          <div className={styles.grid}>
            <div className={styles.row}>
              <Button variant="primary" icon="arrow-right">
                Primary action
              </Button>
              <Button variant="secondary">Secondary</Button>
              <Button variant="ghost">Ghost</Button>
              <Button variant="danger">Destructive</Button>
              <Button variant="primary" size="sm">
                Small
              </Button>
            </div>
            <div className={styles.row}>
              <Button variant="primary" disabled>
                Disabled
              </Button>
              <Button variant="secondary" disabled>
                Disabled
              </Button>
              <Button
                variant="primary"
                loading={loading}
                loadingLabel="Saving"
                onClick={() => {
                  setLoading(true)
                  window.setTimeout(() => setLoading(false), 1200)
                }}
              >
                Click to load
              </Button>
            </div>
          </div>
        </Sheet>

        <Sheet tab="04 · Fields">
          <div className={styles.cols}>
            <TextField label="Claim reference" hint="The insurer's own id, unique in your organization." placeholder="CLM-DEMO-1001" mono />
            <TextField label="Garage" error="Garage id is required" defaultValue="" required />
            <TextField label="Notes" optional disabled defaultValue="Read-only value" />
            <SelectField label="Claim stage" defaultValue="FNOL">
              <option value="FNOL">First notice of loss</option>
              <option value="ESTIMATE_RECEIVED">Estimate received</option>
            </SelectField>
            <TextAreaField label="Rationale" hint="Between 10 and 2000 characters." optional />
          </div>
        </Sheet>

        <Sheet tab="05 · Status marks and stamps">
          <div className={styles.grid}>
            <ul className={styles.row} style={{ listStyle: 'none' }}>
              {markStates.map(([state, label]) => (
                <li key={state}>
                  <StatusMark state={state}>{label}</StatusMark>
                </li>
              ))}
            </ul>
            <ul className={styles.row} style={{ listStyle: 'none' }}>
              {markStates.map(([state, label]) => (
                <li key={state}>
                  <StatusMark state={state} boxed at="2026-10-18T09:12:44Z" atLabel="09:12">
                    {label}
                  </StatusMark>
                </li>
              ))}
            </ul>
            <div className={styles.row}>
              <Stamp>Received</Stamp>
              <Stamp tone="evidence">Checked</Stamp>
              <Stamp tone="caution">Needs review</Stamp>
              <Stamp tone="blueprint">Data</Stamp>
              <Stamp tone="danger">Unavailable</Stamp>
            </div>
          </div>
        </Sheet>

        <Sheet tab="06 · Messages">
          <div className={styles.grid}>
            <Callout tone="info" title="Readiness describes evidence, not the claim">
              A missing document means a check could not run. It is never a negative finding.
            </Callout>
            <Callout tone="caution" title="Garage estimate has not been received">
              Checks that cannot run without it: estimate-to-invoice variance.
            </Callout>
            <Callout tone="evidence" title="Totals agree with the line items">
              Recalculated from the line items within a 1.00 tolerance.
            </Callout>
            <Callout tone="danger" title="The server could not be reached" actions={<Button size="sm">Try again</Button>}>
              Your work has not been lost. Check the connection and try again.
            </Callout>
          </div>
        </Sheet>

        <Sheet tab="07 · Loading and empty">
          <div className={styles.cols}>
            <Skeleton variant="rows" count={3} label="Loading claims" />
            <Skeleton variant="lines" count={4} label="Loading summary" />
            <EmptyState title="No claims yet" action={<Button variant="primary">Create the first claim</Button>}>
              Claims you create appear here with the state of their evidence.
            </EmptyState>
          </div>
        </Sheet>

        <Sheet tab="08 · On ink">
          <div className={styles.dark}>
            <p style={{ fontFamily: 'var(--font-display)', fontSize: 'var(--text-xl)', fontWeight: 700 }}>Dark panels are for navigation.</p>
            <p>
              Text is paper on ink (<span className="mono">{contrastRatio('#f4f0e7', '#182321').toFixed(1)}:1</span>). Accent is signal on ink.
            </p>
            <p style={{ color: 'var(--signal)' }}>Selected path in signal orange</p>
          </div>
        </Sheet>

        <Sheet tab="09 · Field-guide illustrations">
          <div className={styles.cols}>
            <div style={{ gridColumn: '1 / -1', maxWidth: '38rem' }}>
              <ClaimFile title="Sandbox preview of the hero illustration" />
            </div>
            <div style={{ maxWidth: '14rem' }}>
              <FieldInvestigator pose="point" title="Investigator pointing" />
            </div>
            <div style={{ maxWidth: '14rem' }}>
              <FieldInvestigator pose="peek" title="Investigator with a magnifier" />
            </div>
            <BrandMark size={64} />
          </div>
        </Sheet>

        <Sheet tab="10 · Confirmation dialog">
          <div className={styles.stack}>
            <p className={styles.note}>
              Opens a native modal dialog. Focus moves inside, Tab stays inside, Escape cancels, and focus returns to this button.
            </p>
            <div>
              <Button variant="danger" onClick={() => setDialog(true)}>
                Open confirmation
              </Button>
            </div>
          </div>
        </Sheet>
      </div>

      <ConfirmActionDialog
        open={dialog}
        title="Remove this example?"
        consequence="This only affects the sandbox. In the product, a confirmation always says exactly what will change and who can see it."
        confirmLabel="Remove"
        tone="danger"
        pending={pending}
        onConfirm={confirm}
        onCancel={() => setDialog(false)}
      />
    </main>
  )
}
