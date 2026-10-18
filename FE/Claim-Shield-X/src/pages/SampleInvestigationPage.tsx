import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { StageIcon } from '@/components/illustrations/StageIcons'
import type { StageIconName } from '@/components/illustrations/StageIcons'
import { PublicShell } from '@/components/layout/PublicShell'
import { Button } from '@/components/ui/Button'
import { buttonClass } from '@/components/ui/buttonClass'
import { PageHeader } from '@/components/ui/PageHeader'
import { ChecksStep } from '@/features/sample/ChecksStep'
import { sampleClaim } from '@/features/sample/data'
import { DocumentsStep } from '@/features/sample/DocumentsStep'
import { ExplanationsStep } from '@/features/sample/ExplanationsStep'
import { HistoryStep } from '@/features/sample/HistoryStep'
import { focusFor, stepIds } from '@/features/sample/refs'
import type { Focus, StepId } from '@/features/sample/refs'
import { ReviewStep } from '@/features/sample/ReviewStep'
import styles from '@/features/sample/Sample.module.css'
import type { EvidenceRef } from '@/features/sample/types'
import { SessionAction } from '@/features/auth/SessionAction'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

const nav = [
  { label: 'Home', to: '/', kind: 'route' as const },
  { label: 'Sample investigation', to: '/sample-investigation', kind: 'route' as const },
]

const steps: Array<{ id: StepId; label: string; icon: StageIconName; kind: string; title: string; intro: string }> = [
  { id: 'documents', label: 'Documents', icon: 'extraction', kind: 'In the prototype', title: 'Read the documents', intro: 'Choose a document, then a field. The page marks where on the document the value came from. What could not be read stays unknown.' },
  { id: 'checks', label: 'Checks', icon: 'checks', kind: 'In the prototype', title: 'Run the deterministic checks', intro: 'Plain code checks figures, dates and repeats. Choose a result to see its values, its evidence and the harmless explanations to rule out first.' },
  { id: 'history', label: 'History', icon: 'history', kind: 'In the prototype', title: 'Compare with earlier claims', intro: 'Earlier claims that share a token with this one, each with the reason it matched and what is not known about it.' },
  { id: 'explanations', label: 'Explanations', icon: 'explanations', kind: 'Planned', title: 'Weigh competing explanations', intro: 'Two explanations for the same facts, side by side, with what supports each, what weakens it and what is unknown.' },
  { id: 'review', label: 'Review', icon: 'review', kind: 'Planned', title: 'A person decides', intro: 'The reviewer chooses what happens next and says why. The tool never takes this step.' },
]

export function SampleInvestigationPage() {
  useDocumentTitle('Sample investigation')
  const [params, setParams] = useSearchParams()
  const requested = params.get('step')
  const step: StepId = stepIds.includes(requested as StepId) ? (requested as StepId) : 'documents'
  const index = stepIds.indexOf(step)
  const meta = steps[index]!

  const [focus, setFocus] = useState<Focus>({})
  const [announce, setAnnounce] = useState('')
  const [tick, setTick] = useState(0)
  const pending = useRef<StepId | null>(null)

  const go = useCallback(
    (next: StepId) => {
      setParams({ step: next })
    },
    [setParams],
  )

  const openRef = useCallback(
    (ref: EvidenceRef) => {
      const target = focusFor(ref)
      if (!target) return
      pending.current = target.step
      setFocus(target.focus)
      setTick((n) => n + 1)
      go(target.step)
      setAnnounce(`Opened the source in the ${steps[stepIds.indexOf(target.step)]!.label.toLowerCase()} step.`)
    },
    [go],
  )

  // After a citation opens its source, put keyboard focus on it. The address change can land after the state change,
  // so wait until the target step is the one on screen.
  useEffect(() => {
    if (pending.current !== step) return
    pending.current = null
    document.querySelector<HTMLElement>('[data-focus-target]')?.focus()
  }, [step, focus, tick])

  const props = { focus, setFocus, openRef }

  return (
    <PublicShell nav={nav} actions={<SessionAction />} footerNote="This walkthrough is a fiction. Every name, number and document in it is SYNTHETIC DEMO DATA.">
      <div className={styles.banner} data-synthetic>
        <div className={`container ${styles.bannerInner}`}>
          <span className={styles.bannerStamp}>Synthetic demo data</span>
          <span>
            <strong>This case is fictional.</strong> It is read-only and is not connected to any real claim, document or server.
          </span>
        </div>
      </div>

      <div className={`container ${styles.page}`}>
        <PageHeader
          breadcrumbs={[{ label: 'Home', to: '/' }, { label: 'Sample investigation' }]}
          title="Sample investigation"
          description="Walk one made-up claim through the stages of an investigation. Choose anything: nothing here is saved, and no real data is involved."
        />

        <p className={styles.caseStrip}>
          <span>{sampleClaim.internalId}</span>
          <span>{sampleClaim.stage}</span>
          <span>{sampleClaim.incidentType}</span>
          <span>Synthetic</span>
        </p>

        <nav className={styles.route} aria-label="Walkthrough steps">
          <ol>
            {steps.map((s, i) => (
              <li key={s.id}>
                <button type="button" className={styles.stepButton} aria-current={s.id === step ? 'step' : undefined} onClick={() => go(s.id)}>
                  <span className={styles.stepIcon}>
                    <StageIcon name={s.icon} size={44} />
                  </span>
                  <span className={styles.stepText}>
                    <span className={styles.stepNumber}>{String(i + 1).padStart(2, '0')}</span>
                    <span className={styles.stepName}>{s.label}</span>
                    <span className={styles.stepKind}>{s.kind}</span>
                  </span>
                </button>
              </li>
            ))}
          </ol>
        </nav>

        <div className={styles.intro}>
          <h2>
            Step {index + 1} of {steps.length}: {meta.title}
          </h2>
          <p>{meta.intro}</p>
        </div>

        <div role="status" className="sr-only">
          {announce}
        </div>

        {step === 'documents' ? <DocumentsStep {...props} /> : null}
        {step === 'checks' ? <ChecksStep {...props} /> : null}
        {step === 'history' ? <HistoryStep {...props} /> : null}
        {step === 'explanations' ? <ExplanationsStep {...props} /> : null}
        {step === 'review' ? <ReviewStep /> : null}

        <div className={styles.pager}>
          {index > 0 ? (
            <Button icon="arrow-left" onClick={() => go(stepIds[index - 1]!)}>
              Back
            </Button>
          ) : (
            <span />
          )}
          {index < stepIds.length - 1 ? (
            <Button variant="primary" onClick={() => go(stepIds[index + 1]!)}>
              Next: {steps[index + 1]!.label}
            </Button>
          ) : (
            <Link className={buttonClass({ variant: 'primary' })} to="/">
              Finish and return to the start page
            </Link>
          )}
        </div>
      </div>
    </PublicShell>
  )
}
