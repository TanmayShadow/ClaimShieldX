export type SessionUser = {
  email: string
  organizationName: string | null
  userId: string | null
  /** Epoch milliseconds, for display only. */
  expiresAt: number | null
}

function base64UrlToText(segment: string): string | null {
  try {
    const padded = segment.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(segment.length / 4) * 4, '=')
    const bytes = Uint8Array.from(atob(padded), (c) => c.charCodeAt(0))
    return new TextDecoder().decode(bytes)
  } catch {
    return null
  }
}

/**
 * Reads who the access token was issued to, so the shell can show the signed-in email and organization.
 * The signature is NOT checked: the browser cannot, and this is for display only. Authorization is always the server's.
 */
export function decodeAccessToken(token: string): SessionUser | null {
  const parts = token.split('.')
  if (parts.length !== 3 || !parts[1]) return null
  const json = base64UrlToText(parts[1])
  if (!json) return null
  try {
    const payload: unknown = JSON.parse(json)
    if (!payload || typeof payload !== 'object') return null
    const p = payload as Record<string, unknown>
    if (typeof p.sub !== 'string' || p.sub === '') return null
    return {
      email: p.sub,
      organizationName: typeof p.organizationName === 'string' ? p.organizationName : null,
      userId: typeof p.userId === 'string' ? p.userId : null,
      expiresAt: typeof p.exp === 'number' ? p.exp * 1000 : null,
    }
  } catch {
    return null
  }
}
