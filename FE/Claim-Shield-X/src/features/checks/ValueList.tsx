import type { ReactNode } from 'react'
import { humanizeCode, humanizeKey } from './text'
import styles from './Checks.module.css'

const MAX_DEPTH = 3

function scalar(value: unknown): string {
  if (value === null || value === undefined || value === '') return 'Not recorded'
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  if (typeof value === 'string') return humanizeCode(value)
  return String(value)
}

function render(value: unknown, depth: number): ReactNode {
  if (Array.isArray(value)) {
    if (value.length === 0) return 'None'
    if (value.every((v) => v === null || typeof v !== 'object')) return value.map(scalar).join(', ')
    return (
      <ul className={styles.nested}>
        {value.map((item, i) => (
          <li key={i}>{render(item, depth + 1)}</li>
        ))}
      </ul>
    )
  }
  if (value && typeof value === 'object') {
    // Past the depth limit the raw structure is shown as text rather than silently dropped.
    if (depth >= MAX_DEPTH) return <code>{JSON.stringify(value)}</code>
    return <ValueList values={value as Record<string, unknown>} depth={depth + 1} />
  }
  return scalar(value)
}

/** What a rule used, as plain text. Every string is rendered as text, never markup. */
export function ValueList({ values, depth = 0 }: { values: Record<string, unknown>; depth?: number }) {
  const entries = Object.entries(values)
  if (entries.length === 0) return null
  return (
    <dl className={styles.values}>
      {entries.map(([key, value]) => (
        <div key={key}>
          <dt>{humanizeKey(key)}</dt>
          <dd>{render(value, depth)}</dd>
        </div>
      ))}
    </dl>
  )
}
