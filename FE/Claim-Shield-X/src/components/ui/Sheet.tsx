import type { ElementType, HTMLAttributes, ReactNode } from 'react'
import { cx } from '@/utils/cx'
import styles from './Sheet.module.css'

type SheetProps = HTMLAttributes<HTMLElement> & {
  as?: ElementType
  /** Evidence-tab label above the sheet, for example "Document 03". Plain text only. */
  tab?: ReactNode
  folded?: boolean
  padded?: boolean
}

/** A case-file sheet: the one surface used for forms, documents and readable content. */
export function Sheet({ as: Tag = 'section', tab, folded = false, padded = true, className, children, ...rest }: SheetProps) {
  return (
    <div className={cx(styles.wrap, Boolean(tab) && styles.withTab)}>
      {tab ? <span className={styles.tab}>{tab}</span> : null}
      <Tag {...rest} className={cx(styles.sheet, folded && styles.folded, padded && styles.pad, className)}>
        {children}
      </Tag>
    </div>
  )
}
