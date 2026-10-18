import type { ReactNode } from 'react'
import { cx } from '@/utils/cx'
import styles from './Callout.module.css'
import { Icon } from './Icon'
import type { IconName } from './Icon'

type Tone = 'info' | 'caution' | 'danger' | 'evidence' | 'neutral'

const icons: Record<Tone, IconName> = {
  info: 'info',
  caution: 'alert',
  danger: 'cross-octagon',
  evidence: 'check-square',
  neutral: 'info',
}

type CalloutProps = {
  tone?: Tone
  title: string
  children?: ReactNode
  actions?: ReactNode
  className?: string
}

/** An inline message. Danger is announced immediately; everything else politely. */
export function Callout({ tone = 'info', title, children, actions, className }: CalloutProps) {
  return (
    <div className={cx(styles.callout, styles[tone], className)} role={tone === 'danger' ? 'alert' : 'status'}>
      <Icon name={icons[tone]} size={20} className={styles.icon} />
      <div className={styles.body}>
        <p className={styles.title}>{title}</p>
        {children ? <div className={styles.text}>{children}</div> : null}
        {actions ? <div className={styles.actions}>{actions}</div> : null}
      </div>
    </div>
  )
}
