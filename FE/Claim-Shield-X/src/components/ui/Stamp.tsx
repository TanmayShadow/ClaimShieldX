import type { ReactNode } from 'react'
import { cx } from '@/utils/cx'
import styles from './Stamp.module.css'

type StampProps = {
  tone?: 'neutral' | 'signal' | 'evidence' | 'caution' | 'blueprint' | 'danger'
  children: ReactNode
  className?: string
}

/** A printed-stamp label. It always carries its meaning in words; the tone is decoration. */
export function Stamp({ tone = 'neutral', children, className }: StampProps) {
  return <span className={cx(styles.stamp, tone !== 'neutral' && styles[tone], className)}>{children}</span>
}
