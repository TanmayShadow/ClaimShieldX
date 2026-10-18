import { isApiError } from '@/apis/errors'

/**
 * What to tell a person whose sign-in failed. Wrong password and unknown email are deliberately one message, as they are
 * one response from the server, so the page cannot be used to find out which emails have accounts.
 */
export function loginErrorMessage(error: unknown): { title: string; detail: string; wrongCredentials: boolean } {
  if (isApiError(error)) {
    if (error.code === 'INVALID_CREDENTIALS') {
      return { title: 'The email or password is not correct.', detail: 'Check both and try again.', wrongCredentials: true }
    }
    if (error.code === 'VALIDATION_FAILED') {
      return { title: 'The email or password could not be accepted.', detail: 'Check what you typed and try again.', wrongCredentials: false }
    }
    if (error.code === 'RATE_LIMITED') {
      const wait = error.retryAfterSeconds ? ` Wait about ${error.retryAfterSeconds} seconds.` : ' Wait a moment.'
      return { title: 'Too many sign-in attempts.', detail: `${wait} Then try again.`.trim(), wrongCredentials: false }
    }
    if (error.code === 'NETWORK_ERROR' || error.code === 'REQUEST_TIMEOUT') {
      return { title: 'The server could not be reached.', detail: 'Check your connection and try again. Nothing was signed in.', wrongCredentials: false }
    }
  }
  return { title: 'Signing in did not work.', detail: 'The service had a problem. Try again in a moment.', wrongCredentials: false }
}
