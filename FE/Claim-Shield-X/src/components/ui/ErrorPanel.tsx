import type { ReactNode } from 'react'
import { describeApiError } from '@/apis/describeError'
import { Button } from './Button'
import { Callout } from './Callout'

type ErrorPanelProps = {
  error: unknown
  /** What was being loaded, in the lower case, for "We could not find ...". */
  subject?: string
  onRetry?: () => void
  retrying?: boolean
  extra?: ReactNode
}

/** A failed request, in words. Retry is offered only when trying again can help. */
export function ErrorPanel({ error, subject, onRetry, retrying = false, extra }: ErrorPanelProps) {
  const d = describeApiError(error, subject)
  return (
    <Callout
      tone="danger"
      title={d.title}
      actions={
        <>
          {onRetry && d.retryable ? (
            <Button size="sm" icon="refresh" onClick={onRetry} loading={retrying} loadingLabel="Trying again">
              Try again
            </Button>
          ) : null}
          {extra}
        </>
      }
    >
      <p>{d.message}</p>
      {d.code ? (
        <p className="mono" style={{ color: 'var(--ink-soft)', fontSize: 'var(--text-xs)' }}>
          Code {d.code}
        </p>
      ) : null}
    </Callout>
  )
}
