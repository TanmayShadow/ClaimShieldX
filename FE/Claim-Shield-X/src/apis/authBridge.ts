/**
 * The HTTP client needs the current access token and a way to renew it, but the session store depends on the client
 * for login. This small registry breaks that cycle: the session store registers itself here once.
 */
export type AuthBridge = {
  getToken: () => string | null
  /** Renews the access token through the refresh cookie. At most one renewal runs at a time. Rejects when the session is over. */
  refresh: () => Promise<string>
  /** The server says this session cannot continue. */
  sessionLost: (reason: 'expired' | 'invalid') => void
}

let bridge: AuthBridge = {
  getToken: () => null,
  refresh: () => Promise.reject(new Error('No session is registered')),
  sessionLost: () => {},
}

export function registerAuthBridge(next: AuthBridge): void {
  bridge = next
}

export function authBridge(): AuthBridge {
  return bridge
}
