import type { ButtonHTMLAttributes, MouseEvent } from 'react'
import { buttonClass, spinnerClass } from './buttonClass'
import type { ButtonSize, ButtonVariant } from './buttonClass'
import { Icon } from './Icon'
import type { IconName } from './Icon'

export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: ButtonSize
  block?: boolean
  icon?: IconName
  /** While true the button keeps focus but ignores activation, so a request cannot be submitted twice. */
  loading?: boolean
  loadingLabel?: string
}

export function Button({
  variant = 'secondary',
  size = 'md',
  block,
  icon,
  loading = false,
  loadingLabel,
  type = 'button',
  className,
  children,
  onClick,
  disabled,
  ...rest
}: ButtonProps) {
  function handleClick(event: MouseEvent<HTMLButtonElement>) {
    if (loading) {
      event.preventDefault()
      return
    }
    onClick?.(event)
  }

  return (
    <button
      {...rest}
      type={type}
      className={[buttonClass({ variant, size, block }), className].filter(Boolean).join(' ')}
      disabled={disabled}
      aria-busy={loading || undefined}
      aria-disabled={loading || undefined}
      onClick={handleClick}
    >
      {loading ? <span className={spinnerClass} aria-hidden="true" /> : icon ? <Icon name={icon} size={18} /> : null}
      <span>{loading && loadingLabel ? loadingLabel : children}</span>
    </button>
  )
}
