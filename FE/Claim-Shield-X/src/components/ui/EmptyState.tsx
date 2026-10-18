import type { ReactNode } from 'react'
import styles from './EmptyState.module.css'

type EmptyStateProps = {
  title: string
  children?: ReactNode
  illustration?: ReactNode
  action?: ReactNode
  headingLevel?: 2 | 3
}

/** For a list or view with nothing in it yet. Always names the next step. */
export function EmptyState({ title, children, illustration, action, headingLevel = 2 }: EmptyStateProps) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3'
  return (
    <div className={styles.empty}>
      {illustration ? <div className={styles.art}>{illustration}</div> : null}
      <Heading className={styles.title}>{title}</Heading>
      {children ? <div className={styles.text}>{children}</div> : null}
      {action}
    </div>
  )
}
