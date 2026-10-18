import { useNavigate } from 'react-router'
import { EvidenceReference } from '@/components/evidence/EvidenceReference'
import type { EvidenceKind } from '@/components/evidence/EvidenceReference'
import { StatusMark } from '@/components/ui/StatusMark'
import { ruleOutcomeMeaning } from '@/features/rules/outcome'
import type { RuleOutcome } from '@/features/rules/outcome'
import styles from './Checks.module.css'
import type { EvidenceRef, RuleResult } from './schemas'
import { humanizeCode, humanizeKey } from './text'
import { ValueList } from './ValueList'

import { KNOWN, outcomeMark } from './outcomeMark'

const kindFor: Record<string, EvidenceKind> = {
  DOCUMENT_FIELD: 'document',
  DOCUMENT: 'document',
  OTHER_CLAIM_DOCUMENT: 'document',
  CLAIM_FIELD: 'claim',
  HISTORICAL_RECORD: 'history',
  CHECKLIST_ITEM: 'claim',
}

type ResultCardProps = {
  result: RuleResult
  /** Document ids to the file names the claim holds, so a citation reads as a file and not an id. */
  documentNames: Map<string, string>
}

export function ResultCard({ result, documentNames }: ResultCardProps) {
  const navigate = useNavigate()
  const mark = outcomeMark(result)
  const evidence = result.evidence ?? []
  const considerations = result.considerations ?? []
  const values = result.values ?? {}
  const hasDetail = evidence.length > 0 || considerations.length > 0 || Object.keys(values).length > 0 || (result.reliabilityReasons?.length ?? 0) > 0
  const open = result.outcome === 'FLAG' || result.outcome === 'ERROR'

  function reference(ref: EvidenceRef, index: number) {
    const kind = kindFor[ref.kind] ?? 'missing'
    const own = ref.documentId ? documentNames.get(ref.documentId) : undefined
    const label =
      ref.kind === 'DOCUMENT_FIELD' ? `${own ?? 'Document'} · ${humanizeKey(ref.field ?? 'field')}` : ref.kind === 'CHECKLIST_ITEM' ? humanizeCode(ref.documentType ?? 'Checklist item') : ref.field ? humanizeKey(ref.field.split('.').pop() ?? ref.field) : own ?? humanizeKey(ref.kind)
    const detail = [ref.page ? `page ${ref.page}` : null, ref.note ? humanizeCode(ref.note) : null].filter(Boolean).join(' · ') || undefined
    // Each kind opens the tab that holds its source: documents and checklist on Documents, the claim fields on the Record, imported history on Earlier claims.
    const target = ref.kind === 'CLAIM_FIELD' ? '..' : ref.kind === 'HISTORICAL_RECORD' ? '../history' : kind === 'missing' ? null : '../documents'
    const opens = target ? () => navigate(target) : undefined
    return (
      <li key={index}>
        <EvidenceReference kind={kind} label={label} detail={detail} onOpen={opens} />
        {ref.quote ? <blockquote className={styles.quote}>{ref.quote}</blockquote> : null}
      </li>
    )
  }

  return (
    <li className={styles.result} data-outcome={result.outcome}>
      <div className={styles.resultHead}>
        <h3>{result.label}</h3>
        <StatusMark state={mark.state} boxed>
          {mark.label}
        </StatusMark>
        {result.reliability === 'DOUBTFUL' ? <StatusMark state="unknown">Based on a doubtful reading</StatusMark> : null}
      </div>
      <p className={styles.explanation}>{result.explanation}</p>
      {hasDetail ? (
        <details className={styles.more} open={open}>
          <summary>
            Evidence, values and what else could explain it<span className="sr-only"> for {result.label}</span>
          </summary>
          {result.reliability === 'DOUBTFUL' && result.reliabilityReasons?.length ? <p>The reading behind this was doubtful: {result.reliabilityReasons.map(humanizeCode).join(', ')}.</p> : null}
          {considerations.length > 0 ? (
            <>
              <h4>Other explanations to rule out</h4>
              <ul>
                {considerations.map((c, i) => (
                  <li key={i}>{c}</li>
                ))}
              </ul>
            </>
          ) : null}
          {evidence.length > 0 ? (
            <>
              <h4>Where it comes from</h4>
              <ul className={styles.refs}>{evidence.map(reference)}</ul>
            </>
          ) : null}
          {Object.keys(values).length > 0 ? (
            <>
              <h4>What the rule used</h4>
              <ValueList values={values} />
            </>
          ) : null}
        </details>
      ) : null}
      {KNOWN.includes(result.outcome as RuleOutcome) ? <p className={styles.meaning}>{ruleOutcomeMeaning[result.outcome as RuleOutcome]}</p> : null}
    </li>
  )
}
