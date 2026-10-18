/**
 * Public runtime configuration. Everything here ships in the browser bundle, so it must never hold a secret.
 * The API base is kept relative by default so the browser stays on the page's own origin: the backend sets no CORS
 * headers, and its refresh cookie is SameSite=Strict.
 */
export function parseApiBaseUrl(raw: string | undefined): string {
  const value = (raw ?? '/api/v1').trim().replace(/\/+$/, '')
  if (value === '') throw new Error('VITE_API_BASE_URL must not be empty')
  if (value.startsWith('/')) {
    if (value.startsWith('//') || value.includes('..') || /[?#\s\\]/.test(value)) {
      throw new Error('VITE_API_BASE_URL must be a plain path such as /api/v1')
    }
    return value
  }
  let url: URL
  try {
    url = new URL(value)
  } catch {
    throw new Error('VITE_API_BASE_URL must be a path or an https URL')
  }
  const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1'
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && local)) {
    throw new Error('VITE_API_BASE_URL must use https (http is allowed only for localhost)')
  }
  if (url.search || url.hash || url.username || url.password) {
    throw new Error('VITE_API_BASE_URL must not contain a query, fragment or credentials')
  }
  return `${url.origin}${url.pathname.replace(/\/+$/, '')}`
}

export const config = {
  apiBaseUrl: parseApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
} as const
