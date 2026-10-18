import { Link } from 'react-router'
import { StatusMark } from '@/components/ui/StatusMark'
import { readinessMark } from '@/features/claims/readiness'
import { documentTypeLabel } from '@/features/documents/labels'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import styles from './Investigation.module.css'
import { extractionOutcomeLabels, investigationErrorMessage, isUnbuilt, stageDisplay, stageLabel, stagePurpose } from './labels'
import type { StageId } from './labels'
import { bandLabel, completenessLabel, recommendedActionLabel } from '@/features/report/labels'
import { extractingSummarySchema, hypothesesSummarySchema, preparingSummarySchema, reportSummarySchema, rulesSummarySchema, validatingSummarySchema } from './schemas'
import type { Investigation } from './schemas'

const countLabels: Record<string, string> = { pass: 'Pass', flag: 'Flag', incomplete: 'Incomplete', notApplicable: 'Not applicable', error: 'Could not run' }

function When({ iso, label }: { iso: string | null | undefined; label: string }) {
  if (!iso) return null
  return (
    <>
      <dt>{label}</dt>
      <dd>
        <time dateTime={iso} title={formatDateTimeWithZone(iso)}>
          {formatDateTime(iso)}
        </time>
      </dd>
    </>
  )
}

/** What one stage did, from the backend's own record of it. Shows counts and ids only; nothing read from a document. */
export function StageInspector({ investigation, stage }: { investigation: Investigation; stage: StageId }) {
  const s = investigation.stages.find((x) => x.stage === stage)
  const mark = stageDisplay(stage, s, investigation.status)
  const notBuilt = isUnbuilt(stage, s)

  return (
    <div className={styles.inspector} data-focus-target tabIndex={-1} aria-label={`Details of ${stageLabel(stage)}`} role="region">
      <div className={styles.head}>
        <h3>{stageLabel(stage)}</h3>
        <StatusMark state={mark.state} boxed>
          {mark.label}
        </StatusMark>
      </div>
      <p>{stagePurpose[stage]}</p>

      {notBuilt ? (
        <p className={styles.note}>This stage is not built yet, so it does not run. That is not a result: nothing is checked here and nothing is found.</p>
      ) : null}

      <dl className={styles.rows}>
        <When iso={s?.startedAt} label="Started" />
        <When iso={s?.finishedAt} label="Finished" />
        {s?.attempts ? (
          <>
            <dt>{stage === 'PREPARING_EVIDENCE' ? 'Times checked' : 'Attempts'}</dt>
            <dd>{s.attempts}</dd>
          </>
        ) : null}
        {s?.errorCode ? (
          <>
            <dt>Problem</dt>
            <dd>{investigationErrorMessage(s.errorCode)}</dd>
          </>
        ) : null}
      </dl>

      {stage === 'PREPARING_EVIDENCE' ? <Preparing investigation={investigation} summary={s?.summary} /> : null}
      {stage === 'EXTRACTING' ? <Extracting summary={s?.summary} /> : null}
      {stage === 'RUNNING_RULES' ? <Rules investigation={investigation} summary={s?.summary} /> : null}
      {stage === 'BUILDING_HYPOTHESES' ? <Hypotheses summary={s?.summary} /> : null}
      {stage === 'VALIDATING_FINDINGS' ? <Validating summary={s?.summary} /> : null}
      {stage === 'GENERATING_REPORT' ? <ReportStage summary={s?.summary} /> : null}
    </div>
  )
}

function Preparing({ investigation, summary }: { investigation: Investigation; summary: Record<string, unknown> | null | undefined }) {
  const parsed = preparingSummarySchema.safeParse(summary ?? {})
  const data = parsed.success ? parsed.data : {}
  const blockers = Array.isArray(data.blockers) ? data.blockers.length : (data.blockers ?? 0)
  const warnings = Array.isArray(data.warnings) ? data.warnings.length : (data.warnings ?? 0)
  const mark = data.readiness ? readinessMark({ level: data.readiness, blockerCount: blockers, warningCount: warnings }) : null
  const waiting = investigation.status === 'WAITING_FOR_EVIDENCE' ? (investigation.waitingFor ?? []) : []
  return (
    <>
      {mark ? (
        <dl className={styles.rows}>
          <dt>Evidence readiness</dt>
          <dd>
            <StatusMark state={mark.state}>{mark.label}</StatusMark> <span className={styles.mono}>{mark.detail}</span>
          </dd>
          {data.documents && Object.keys(data.documents).length > 0 ? (
            <>
              <dt>Documents held</dt>
              <dd>
                {Object.entries(data.documents)
                  .map(([type, n]) => `${documentTypeLabel(type)}: ${n}`)
                  .join(', ')}
              </dd>
            </>
          ) : null}
        </dl>
      ) : null}
      {waiting.length > 0 ? (
        <p className={styles.note}>
          Waiting for: {waiting.map((w) => `${documentTypeLabel(w.documentType)} (${w.code === 'DOCUMENT_MISSING' ? 'not received' : w.code === 'DOCUMENT_FAILED' ? 'could not be read' : 'needed'})`).join(', ')}.
        </p>
      ) : null}
      <p>
        <Link to="../documents">Open the Documents tab</Link> to see the checklist this stage worked from.
      </p>
    </>
  )
}

function Extracting({ summary }: { summary: Record<string, unknown> | null | undefined }) {
  const parsed = extractingSummarySchema.safeParse(summary ?? {})
  if (!parsed.success) return null
  const d = parsed.data
  const counts: Array<[string, number | undefined]> = [
    ['Read now', d.read],
    ['Already read', d.alreadyRead],
    ['Read, needs review', d.needsReview],
    ['Could not be read', d.failed],
    ['Will be tried again', d.willRetry],
    ['Being read elsewhere', d.busy],
  ]
  const shown = counts.filter(([, n]) => n)
  const docs = d.documents ?? []
  return (
    <>
      {shown.length > 0 ? (
        <dl className={styles.rows}>
          {shown.map(([label, n]) => (
            <div key={label} style={{ display: 'contents' }}>
              <dt>{label}</dt>
              <dd>{n}</dd>
            </div>
          ))}
        </dl>
      ) : (
        <p className={styles.note}>No invoice or estimate needed reading, so this stage had nothing to do.</p>
      )}
      {docs.length > 0 ? (
        <>
          <strong>Documents this stage looked at</strong>
          <ul className={styles.docList}>
            {docs.map((doc) => (
              <li key={doc.documentId}>
                <span>{documentTypeLabel(doc.documentType)}</span>
                <span>{extractionOutcomeLabels[doc.outcome] ?? doc.outcome}</span>
              </li>
            ))}
          </ul>
        </>
      ) : null}
      {shown.length > 0 ? (
        <p className={styles.note}>
          Reading checks what a document says. It does not show that the document is true. <Link to="../documents">See the documents</Link>
        </p>
      ) : null}
    </>
  )
}

function Rules({ investigation, summary }: { investigation: Investigation; summary: Record<string, unknown> | null | undefined }) {
  const parsed = rulesSummarySchema.safeParse(summary ?? {})
  const d = parsed.success ? parsed.data : {}
  const counts = (Object.keys(countLabels) as Array<keyof typeof countLabels>).flatMap((k) => (typeof d[k as keyof typeof d] === 'number' ? [[k, d[k as keyof typeof d] as number] as const] : []))
  const runId = investigation.ruleRunId ?? d.ruleRunId
  if (!runId && counts.length === 0) return null
  return (
    <>
      <dl className={styles.rows}>
        {runId ? (
          <>
            <dt>Rule run</dt>
            <dd className={styles.mono}>{runId}</dd>
          </>
        ) : null}
        {counts.length > 0 ? (
          <>
            <dt>Results</dt>
            <dd>{counts.map(([k, n]) => `${n} ${countLabels[k]!.toLowerCase()}`).join(' · ')}</dd>
          </>
        ) : null}
        {d.reusedExistingRun ? (
          <>
            <dt>Note</dt>
            <dd>Nothing had changed since an earlier run, so that run was reused.</dd>
          </>
        ) : null}
      </dl>
      <p className={styles.note}>A flag is something to check, not a conclusion. A rule that could not run is missing evidence, never a negative finding.</p>
    </>
  )
}

const stepAnswer: Record<string, string> = { OK: 'Answered', UNAVAILABLE: 'Could not answer', SKIPPED: 'Not asked' }

function Hypotheses({ summary }: { summary: Record<string, unknown> | null | undefined }) {
  const parsed = hypothesesSummarySchema.safeParse(summary ?? {})
  if (!parsed.success) return null
  const d = parsed.data
  if (d.reason === 'NO_CONCERNS_TO_ANALYZE') {
    return <p className={styles.note}>No rule flagged anything, so the model was not asked. That is deliberate: there was nothing to explain.</p>
  }
  return (
    <>
      <dl className={styles.rows}>
        {typeof d.hypotheses === 'number' ? (
          <>
            <dt>Possible explanations proposed</dt>
            <dd>{d.hypotheses}</dd>
          </>
        ) : null}
        {d.h1Rejected ? (
          <>
            <dt>Dropped before review</dt>
            <dd>{d.h1Rejected}</dd>
          </>
        ) : null}
        {d.h1 ? (
          <>
            <dt>Explanation step</dt>
            <dd>{stepAnswer[d.h1] ?? d.h1}</dd>
          </>
        ) : null}
        {d.h2 ? (
          <>
            <dt>Counter-evidence step</dt>
            <dd>{stepAnswer[d.h2] ?? d.h2}</dd>
          </>
        ) : null}
      </dl>
      <p className={styles.note}>An explanation is a hypothesis to verify, never a conclusion about the claim. Every one is checked in code before anyone sees it.</p>
    </>
  )
}

function Validating({ summary }: { summary: Record<string, unknown> | null | undefined }) {
  const parsed = validatingSummarySchema.safeParse(summary ?? {})
  if (!parsed.success) return null
  const d = parsed.data
  const rows: Array<[string, number | null | undefined]> = [
    ['Findings built', d.findings],
    ['Published', d.published],
    ['Explanations not supported by the evidence', d.rejectedHypotheses],
  ]
  const shown = rows.filter(([, n]) => typeof n === 'number')
  if (shown.length === 0) return null
  return (
    <dl className={styles.rows}>
      {shown.map(([label, n]) => (
        <div key={label} style={{ display: 'contents' }}>
          <dt>{label}</dt>
          <dd>{n}</dd>
        </div>
      ))}
    </dl>
  )
}

function ReportStage({ summary }: { summary: Record<string, unknown> | null | undefined }) {
  const parsed = reportSummarySchema.safeParse(summary ?? {})
  if (!parsed.success) return null
  const d = parsed.data
  if (!d.band && typeof d.score !== 'number') return null
  return (
    <>
      <dl className={styles.rows}>
        {typeof d.score === 'number' ? (
          <>
            <dt>Review-priority score</dt>
            <dd>
              {d.score} of 100{d.band ? ` (${bandLabel(d.band).toLowerCase()})` : ''}
            </dd>
          </>
        ) : null}
        {d.recommendedAction ? (
          <>
            <dt>Suggested next step</dt>
            <dd>{recommendedActionLabel(d.recommendedAction)}</dd>
          </>
        ) : null}
        {d.completeness ? (
          <>
            <dt>Completeness</dt>
            <dd>{completenessLabel(d.completeness)}</dd>
          </>
        ) : null}
      </dl>
    </>
  )
}
