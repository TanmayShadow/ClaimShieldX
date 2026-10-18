/**
 * Where to go after signing in. Only a path inside the signed-in area is honoured, so a crafted link such as
 * /login?returnTo=//evil.example can never send a person to another site (open redirect).
 */
export const DEFAULT_RETURN_PATH = '/app'

function hasControlCharacter(value: string): boolean {
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i)
    if (code < 0x20 || code === 0x7f) return true
  }
  return false
}

export function safeReturnPath(raw: unknown): string {
  if (typeof raw !== 'string' || raw.length === 0 || raw.length > 500) return DEFAULT_RETURN_PATH
  let decoded: string
  try {
    decoded = decodeURIComponent(raw)
  } catch {
    return DEFAULT_RETURN_PATH
  }
  for (const candidate of [raw, decoded]) {
    if (!candidate.startsWith('/')) return DEFAULT_RETURN_PATH
    if (candidate.startsWith('//') || candidate.includes('\\') || hasControlCharacter(candidate)) return DEFAULT_RETURN_PATH
    if (candidate.split(/[?#]/)[0]!.split('/').some((s) => s === '..' || s === '.')) return DEFAULT_RETURN_PATH
  }
  const path = raw.split(/[?#]/)[0]!
  if (path !== '/app' && !path.startsWith('/app/')) return DEFAULT_RETURN_PATH
  return raw
}
