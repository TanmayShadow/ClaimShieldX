import { cx } from '@/utils/cx'
import styles from './Skeleton.module.css'

type SkeletonProps = {
  variant?: 'lines' | 'block' | 'rows'
  count?: number
  /** Tells assistive technology what is loading. */
  label?: string
  className?: string
}

/** Loading placeholder. It is shaped like the content to come, and says so in text for screen readers. */
export function Skeleton({ variant = 'lines', count = 3, label = 'Loading', className }: SkeletonProps) {
  return (
    <div className={cx(styles.skeleton, className)} role="status" aria-live="polite" aria-busy="true">
      <span className="sr-only">{label}…</span>
      {variant === 'block' ? <div className={styles.block} aria-hidden="true" /> : null}
      {variant === 'lines'
        ? Array.from({ length: count }, (_, i) => <div key={i} className={styles.bar} aria-hidden="true" />)
        : null}
      {variant === 'rows'
        ? Array.from({ length: count }, (_, i) => (
            <div key={i} className={styles.row} aria-hidden="true">
              <span />
              <span />
              <span />
            </div>
          ))
        : null}
    </div>
  )
}
