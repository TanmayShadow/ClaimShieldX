import { useId } from 'react'
import type { InputHTMLAttributes, ReactNode, Ref, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import { cx } from '@/utils/cx'
import styles from './Field.module.css'
import { Icon } from './Icon'

type ControlAria = {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
  required?: boolean
}

type FieldShellProps = {
  label: string
  hint?: string
  error?: string
  required?: boolean
  /** Marks a field the form does not need. Most claim fields are required, so optional ones are labelled instead. */
  optional?: boolean
  id?: string
  className?: string
  children: (aria: ControlAria) => ReactNode
}

export function FieldShell({ label, hint, error, required, optional, id, className, children }: FieldShellProps) {
  const generated = useId()
  const controlId = id ?? generated
  const hintId = hint ? `${controlId}-hint` : undefined
  const errorId = error ? `${controlId}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className={cx(styles.field, className)}>
      <label className={styles.label} htmlFor={controlId}>
        {label}
        {optional ? <span className={styles.optional}>optional</span> : null}
      </label>
      {hint ? (
        <p className={styles.hint} id={hintId}>
          {hint}
        </p>
      ) : null}
      {children({
        id: controlId,
        'aria-describedby': describedBy,
        'aria-invalid': error ? true : undefined,
        required,
      })}
      {error ? (
        <p className={styles.error} id={errorId}>
          <Icon name="alert" size={16} />
          <span>{error}</span>
        </p>
      ) : null}
    </div>
  )
}

type CommonProps = Pick<FieldShellProps, 'label' | 'hint' | 'error' | 'optional'>

export type TextFieldProps = CommonProps &
  Omit<InputHTMLAttributes<HTMLInputElement>, 'aria-describedby' | 'aria-invalid'> & { mono?: boolean; ref?: Ref<HTMLInputElement> }

export function TextField({ label, hint, error, optional, required, id, className, mono, ref, ...rest }: TextFieldProps) {
  return (
    <FieldShell label={label} hint={hint} error={error} optional={optional} required={required} id={id} className={className}>
      {(aria) => <input {...rest} {...aria} ref={ref} className={cx(styles.control, mono && styles.mono)} />}
    </FieldShell>
  )
}

export type SelectFieldProps = CommonProps &
  Omit<SelectHTMLAttributes<HTMLSelectElement>, 'aria-describedby' | 'aria-invalid'>

export function SelectField({ label, hint, error, optional, required, id, className, children, ...rest }: SelectFieldProps) {
  return (
    <FieldShell label={label} hint={hint} error={error} optional={optional} required={required} id={id} className={className}>
      {(aria) => (
        <div className={styles.selectWrap}>
          <select {...rest} {...aria} className={styles.control}>
            {children}
          </select>
          <Icon name="chevron-down" size={18} />
        </div>
      )}
    </FieldShell>
  )
}

export type TextAreaFieldProps = CommonProps &
  Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'aria-describedby' | 'aria-invalid'>

export function TextAreaField({ label, hint, error, optional, required, id, className, ...rest }: TextAreaFieldProps) {
  return (
    <FieldShell label={label} hint={hint} error={error} optional={optional} required={required} id={id} className={className}>
      {(aria) => <textarea {...rest} {...aria} className={styles.control} />}
    </FieldShell>
  )
}
