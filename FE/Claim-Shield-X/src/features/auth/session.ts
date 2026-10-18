import { useSyncExternalStore } from 'react'
import { loginRequest, logoutRequest, refreshRequest } from '@/apis/auth'
import { registerAuthBridge } from '@/apis/authBridge'
import { queryClient } from '@/app/queryClient'
import { decodeAccessToken } from './jwt'
import type { SessionUser } from './jwt'

export type SessionState = {
  status: 'unknown' | 'authenticated' | 'anonymous'
  user: SessionUser | null
  /** Why the last session ended, so the sign-in page can say so. */
  reason?: 'expired' | 'signed-out'
}

// The access token lives only in this variable. It is never written to any storage.
let accessToken: string | null = null
let state: SessionState = { status: 'unknown', user: null }
const listeners = new Set<() => void>()

const HINT_KEY = 'claimshield.session-hint'

/** A non-secret "this browser signed in before" flag, so public pages do not call the server just to learn they are anonymous. */
function readHint(): boolean {
  try {
    return window.localStorage.getItem(HINT_KEY) === '1'
  } catch {
    return false
  }
}
function writeHint(on: boolean): void {
  try {
    if (on) window.localStorage.setItem(HINT_KEY, '1')
    else window.localStorage.removeItem(HINT_KEY)
  } catch {
    // Storage can be blocked; the session still works, it just will not be restored silently on public pages.
  }
}

function set(next: SessionState): void {
  state = next
  listeners.forEach((l) => l())
}

function begin(token: string): SessionUser {
  accessToken = token
  const user = decodeAccessToken(token) ?? { email: 'Signed in', organizationName: null, userId: null, expiresAt: null }
  writeHint(true)
  set({ status: 'authenticated', user })
  return user
}

export function endSession(reason?: 'expired' | 'signed-out'): void {
  accessToken = null
  writeHint(false)
  queryClient.clear()
  set({ status: 'anonymous', user: null, reason })
}

export function getSession(): SessionState {
  return state
}
export function getAccessToken(): string | null {
  return accessToken
}
export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
export function useSession(): SessionState {
  return useSyncExternalStore(subscribe, getSession, getSession)
}

let refreshing: Promise<string> | null = null

/**
 * Renews the access token through the refresh cookie. The backend's refresh token works exactly once, so concurrent callers
 * (several requests expiring together, or React StrictMode mounting twice) must share one call.
 */
export function refreshSession(): Promise<string> {
  if (!refreshing) {
    refreshing = refreshRequest()
      .then((token) => {
        begin(token)
        return token
      })
      .catch((error: unknown) => {
        // The sign-in hint proves this browser had a session even when this page load never held a token, so say it ended.
        endSession(accessToken !== null || state.status === 'authenticated' || readHint() ? 'expired' : undefined)
        throw error
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

let bootstrap: Promise<SessionState> | null = null

/**
 * Finds out whether this browser still has a session. `force` makes the check even without the sign-in hint (protected pages);
 * public pages pass false and make no request unless this browser signed in before.
 */
export function ensureSession(options: { force: boolean }): Promise<SessionState> {
  if (state.status !== 'unknown') return Promise.resolve(state)
  if (!options.force && !readHint()) {
    set({ status: 'anonymous', user: null })
    return Promise.resolve(state)
  }
  if (!bootstrap) {
    bootstrap = refreshSession()
      .then(() => state)
      .catch(() => state)
      .finally(() => {
        bootstrap = null
      })
  }
  return bootstrap
}

export async function signIn(email: string, password: string): Promise<SessionUser> {
  const token = await loginRequest(email, password)
  queryClient.clear()
  return begin(token)
}

/** Ends the session on the server (best effort) and here. Always ends it here, even if the server cannot be reached. */
export async function signOut(): Promise<void> {
  try {
    await logoutRequest()
  } catch {
    // The refresh token still expires on its own; the local session ends regardless.
  }
  endSession('signed-out')
}

registerAuthBridge({
  getToken: getAccessToken,
  refresh: refreshSession,
  sessionLost: () => endSession('expired'),
})

/** Test helper: returns the module to its initial state. */
export function resetSessionForTests(): void {
  accessToken = null
  refreshing = null
  bootstrap = null
  state = { status: 'unknown', user: null }
  listeners.clear()
}
