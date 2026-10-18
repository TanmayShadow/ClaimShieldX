import { isTerminal, isWaiting } from './labels'

/**
 * How often to ask the server about an investigation, or false to stop. There is no push channel (api-contract 7.8), so the page polls
 * the one documented status endpoint, quickly while work is moving, slowly while it waits, and stops when it is over.
 * The interval grows with the age of the investigation so a long run does not hammer the server. TanStack Query also pauses
 * polling while the tab is hidden.
 */
export function pollInterval(status: string, ageMs: number): number | false {
  if (isTerminal(status)) return false
  if (isWaiting(status)) return 5_000
  if (ageMs < 30_000) return 2_000
  if (ageMs < 120_000) return 4_000
  return 8_000
}
