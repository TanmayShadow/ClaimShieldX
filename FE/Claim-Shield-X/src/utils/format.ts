/** Display formatting. Times arrive as UTC instants and are shown in the viewer's time zone, with the zone available on hover. */

const MISSING = 'Not available'

function parse(iso: string | null | undefined): Date | null {
  if (!iso) return null
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

export function viewerTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return 'UTC'
  }
}

export function formatDateTime(iso: string | null | undefined): string {
  const date = parse(iso)
  if (!date) return MISSING
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false }).format(date)
}

export function formatDate(iso: string | null | undefined): string {
  const date = parse(iso)
  if (!date) return MISSING
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }).format(date)
}

export function formatTime(date: Date | number): string {
  return new Intl.DateTimeFormat('en-IN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }).format(date)
}

/** Full timestamp with zone, for a tooltip or a `title`. */
export function formatDateTimeWithZone(iso: string | null | undefined): string {
  const date = parse(iso)
  if (!date) return MISSING
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'long' }).format(date)
}

export function formatMoney(amount: number | null | undefined, currency: string | null | undefined): string {
  if (amount === null || amount === undefined || !Number.isFinite(amount)) return MISSING
  const code = currency ?? ''
  try {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: code, minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(amount)
  } catch {
    return `${code} ${amount.toFixed(2)}`.trim()
  }
}

export function pluralize(count: number, one: string, many = `${one}s`): string {
  return `${count} ${count === 1 ? one : many}`
}
