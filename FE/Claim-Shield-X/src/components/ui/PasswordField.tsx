import { useState } from 'react'
import type { InputHTMLAttributes, Ref } from 'react'
import { FieldShell } from './Field'
import fieldStyles from './Field.module.css'
import { Icon } from './Icon'
import styles from './PasswordField.module.css'

type PasswordFieldProps = {
  label: string
  hint?: string
  error?: string
  ref?: Ref<HTMLInputElement>
} & Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'aria-describedby' | 'aria-invalid'>

/** A password input with a proper show/hide toggle. The toggle's name always starts with its visible word (WCAG 2.5.3). */
export function PasswordField({ label, hint, error, required, id, className, ref, ...rest }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)
  return (
    <FieldShell label={label} hint={hint} error={error} required={required} id={id} className={className}>
      {(aria) => (
        <div className={styles.wrap}>
          <input {...rest} {...aria} ref={ref} type={visible ? 'text' : 'password'} className={fieldStyles.control} autoCapitalize="none" spellCheck={false} />
          <button type="button" className={styles.toggle} aria-label={visible ? 'Hide password' : 'Show password'} onClick={() => setVisible((v) => !v)}>
            <Icon name={visible ? 'eye-off' : 'eye'} size={16} />
            <span aria-hidden="true">{visible ? 'Hide' : 'Show'}</span>
          </button>
        </div>
      )}
    </FieldShell>
  )
}
