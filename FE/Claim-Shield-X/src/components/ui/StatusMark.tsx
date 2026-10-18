import type { ReactNode } from 'react'
import { cx } from '@/utils/cx'
import { Icon } from './Icon'
import type { IconName } from './Icon'
import styles from './StatusMark.module.css'

/** Visual states shared by every status in the product. Each has its own shape, so colour is never the only signal. */
export type MarkState = 'pending' | 'active' | 'done' | 'attention' | 'blocked' | 'failed' | 'skipped' | 'unknown' | 'info'

const shape: Record<MarkState, IconName> = {
  pending: 'ring',
  active: 'ring-half',
  done: 'check-square',
  attention: 'alert',
  blocked: 'block',
  failed: 'cross-octagon',
  skipped: 'dash-circle',
  unknown: 'question',
  info: 'info',
}

type StatusMarkProps = {
  state: MarkState
  children: ReactNode
  /** ISO timestamp of the last change, shown beside the label when the backend provides it. */
  at?: string
  atLabel?: string
  boxed?: boolean
  className?: string
}

export function StatusMark({ state, children, at, atLabel, boxed, className }: StatusMarkProps) {
  return (
    <span className={cx(styles.mark, styles[state], boxed && styles.boxed, className)} data-state={state}>
      <Icon name={shape[state]} size={16} />
      <span>{children}</span>
      {at && atLabel ? (
        <time className={styles.time} dateTime={at}>
          {atLabel}
        </time>
      ) : null}
    </span>
  )
}
