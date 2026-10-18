import { Link } from 'react-router'
import type { ReactNode } from 'react'
import styles from './PageHeader.module.css'

export type Crumb = { label: string; to?: string }

type PageHeaderProps = {
  title: ReactNode
  description?: ReactNode
  breadcrumbs?: Crumb[]
  actions?: ReactNode
}

/** Title, one-sentence context, breadcrumbs and the view's primary action. The h1 can take programmatic focus on navigation. */
export function PageHeader({ title, description, breadcrumbs, actions }: PageHeaderProps) {
  return (
    <header className={styles.header}>
      {breadcrumbs && breadcrumbs.length > 0 ? (
        <nav className={styles.crumbs} aria-label="Breadcrumb">
          <ol>
            {breadcrumbs.map((crumb, index) => {
              const last = index === breadcrumbs.length - 1
              return (
                <li key={`${crumb.label}-${index}`}>
                  {crumb.to && !last ? <Link to={crumb.to}>{crumb.label}</Link> : <span aria-current={last ? 'page' : undefined}>{crumb.label}</span>}
                </li>
              )
            })}
          </ol>
        </nav>
      ) : null}
      <div className={styles.row}>
        <div className={styles.titles}>
          <h1 className={styles.title} tabIndex={-1} data-page-title>
            {title}
          </h1>
          {description ? <p className={styles.description}>{description}</p> : null}
        </div>
        {actions ? <div className={styles.actions}>{actions}</div> : null}
      </div>
    </header>
  )
}
