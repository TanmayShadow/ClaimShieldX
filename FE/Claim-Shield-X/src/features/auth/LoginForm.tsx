import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { Callout } from '@/components/ui/Callout'
import { TextField } from '@/components/ui/Field'
import { PasswordField } from '@/components/ui/PasswordField'
import { loginErrorMessage } from './loginErrors'
import { validateLogin } from './loginSchema'
import type { LoginFieldErrors } from './loginSchema'
import styles from './Login.module.css'
import { signIn } from './session'

type LoginFormProps = { onSignedIn: () => void }

export function LoginForm({ onSignedIn }: LoginFormProps) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<LoginFieldErrors>({})
  const [failure, setFailure] = useState<{ title: string; detail: string } | null>(null)
  const [pending, setPending] = useState(false)
  const emailRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (pending) return
    setFailure(null)

    const checked = validateLogin({ email, password })
    if (!checked.ok) {
      setErrors(checked.errors)
      ;(checked.errors.email ? emailRef : passwordRef).current?.focus()
      return
    }
    setErrors({})

    setPending(true)
    try {
      await signIn(checked.values.email, checked.values.password)
      onSignedIn()
    } catch (error) {
      const message = loginErrorMessage(error)
      setFailure(message)
      if (message.wrongCredentials) {
        setPassword('')
        passwordRef.current?.focus()
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {failure ? (
        <Callout tone="danger" title={failure.title}>
          {failure.detail}
        </Callout>
      ) : null}
      <TextField
        label="Email address"
        type="email"
        name="email"
        autoComplete="username"
        inputMode="email"
        autoCapitalize="none"
        spellCheck={false}
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        error={errors.email}
        ref={emailRef}
      />
      <PasswordField
        label="Password"
        name="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        error={errors.password}
        ref={passwordRef}
      />
      <Button type="submit" variant="primary" block loading={pending} loadingLabel="Signing in">
        Sign in
      </Button>
    </form>
  )
}
