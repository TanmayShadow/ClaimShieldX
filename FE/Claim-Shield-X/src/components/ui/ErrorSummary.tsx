import type { Ref } from 'react'
import styles from './ErrorSummary.module.css'

export type ErrorSummaryItem = { id: string; label: string; message: string }

type ErrorSummaryProps = { title: string; items: ErrorSummaryItem[]; ref?: Ref<HTMLDivElement> }

/**
 * The list of things to fix at the top of a form. The parent moves focus here after a failed submit, and each entry
 * moves focus to its field, so a keyboard or screen-reader user lands on the first problem without hunting for it.
 */
export function ErrorSummary({ title, items, ref }: ErrorSummaryProps) {
  return (
    <div className={styles.summary} role="alert" tabIndex={-1} ref={ref}>
      <p className={styles.title}>{title}</p>
      <ul className={styles.list}>
        {items.map((item) => (
          <li key={item.id}>
            <a
              href={`#${item.id}`}
              onClick={(event) => {
                event.preventDefault()
                const field = document.getElementById(item.id)
                field?.focus()
                field?.scrollIntoView?.({ block: 'center' })
              }}
            >
              {item.label}
            </a>
            : {item.message}
          </li>
        ))}
      </ul>
    </div>
  )
}
