import { cx } from '@/utils/cx'
import { Icon } from '@/components/ui/Icon'
import styles from './EvidenceReference.module.css'

export type EvidenceKind = 'document' | 'rule' | 'history' | 'claim' | 'missing'

const kindLabel: Record<EvidenceKind, string> = {
  document: 'Document',
  rule: 'Rule result',
  history: 'History record',
  claim: 'Claim record',
  missing: 'Not available',
}

type EvidenceReferenceProps = {
  kind: EvidenceKind
  label: string
  /** Page, row or other locator, shown as plain text. */
  detail?: string
  /** Opens the real source. A reference with nothing to open is an explicit unknown and renders as plain text. */
  onOpen?: () => void
  className?: string
}

/** A citation. It is always text; clicking it opens the item it points at. All strings are rendered as text, never markup. */
export function EvidenceReference({ kind, label, detail, onOpen, className }: EvidenceReferenceProps) {
  const body = (
    <>
      <span className={styles.kind}>{kindLabel[kind]}</span> <span className={styles.label}>{label}</span>
      {detail ? (
        <>
          {' '}
          <span className={styles.detail}>{detail}</span>
        </>
      ) : null}
    </>
  )

  if (!onOpen || kind === 'missing') {
    return (
      <span className={cx(styles.ref, styles.missing, className)}>
        <Icon name="question" size={14} />
        {body}
      </span>
    )
  }

  return (
    // The visible text is the accessible name (WCAG 2.5.3); the hidden suffix says what activating it does.
    <button type="button" className={cx(styles.ref, className)} onClick={onOpen}>
      {body} <span className="sr-only">(opens the source)</span>
      <Icon name="arrow-right" size={14} className={styles.go} />
    </button>
  )
}
