import { cx } from '@/utils/cx'
import styles from './Button.module.css'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'
export type ButtonSize = 'md' | 'sm'

export function buttonClass(options: { variant?: ButtonVariant; size?: ButtonSize; block?: boolean } = {}): string {
  const { variant = 'secondary', size = 'md', block = false } = options
  return cx(styles.button, styles[variant], size === 'sm' && styles.sm, block && styles.block)
}

export const spinnerClass = styles.spinner
