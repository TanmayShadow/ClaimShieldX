import type { ZodType } from 'zod'
import { config } from '@/utils/config'
import { authBridge } from './authBridge'
import { malformedResponse, networkError, normalizeErrorBody, timeoutError } from './errors'
import type { ApiError } from './errors'

type Primitive = string | number | boolean | undefined | null

export type RequestOptions<T> = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Record<string, Primitive>
  /** A plain object is sent as JSON, FormData as multipart. */
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
  /** Send the access token and renew it once if it has expired. Off only for the auth endpoints themselves. */
  auth?: boolean
  /** Validates the success body. A body that does not match is reported as MALFORMED_RESPONSE, never trusted. */
  schema?: ZodType<T>
  timeoutMs?: number
}

const DEFAULT_TIMEOUT_MS = 30_000

export function buildUrl(path: string, query?: Record<string, Primitive>): string {
  const url = new URL(`${config.apiBaseUrl}${path.startsWith('/') ? path : `/${path}`}`, window.location.origin)
  for (const [key, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value))
  }
  return config.apiBaseUrl.startsWith('/') ? `${url.pathname}${url.search}` : url.toString()
}

function isAbort(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { name?: unknown }).name === 'AbortError'
}

async function send(path: string, options: RequestOptions<unknown>, token: string | null): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers }
  if (token) headers.Authorization = `Bearer ${token}`

  let body: BodyInit | undefined
  if (options.body instanceof FormData) {
    body = options.body
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }

  const controller = new AbortController()
  let timedOut = false
  const timer = window.setTimeout(() => {
    timedOut = true
    controller.abort()
  }, options.timeoutMs ?? DEFAULT_TIMEOUT_MS)
  const onCallerAbort = () => controller.abort()
  if (options.signal?.aborted) controller.abort()
  options.signal?.addEventListener('abort', onCallerAbort)

  try {
    return await fetch(new URL(buildUrl(path, options.query), window.location.origin), { method: options.method ?? 'GET', headers, body, signal: controller.signal, credentials: 'same-origin' })
  } catch (error) {
    if (isAbort(error)) {
      if (timedOut) throw timeoutError()
      throw error
    }
    throw networkError()
  } finally {
    window.clearTimeout(timer)
    options.signal?.removeEventListener('abort', onCallerAbort)
  }
}

async function readJson(response: Response): Promise<unknown> {
  const raw = await response.text()
  if (raw === '') return undefined
  try {
    return JSON.parse(raw)
  } catch {
    return undefined
  }
}

async function toError(response: Response): Promise<ApiError> {
  return normalizeErrorBody(response.status, await readJson(response), response.headers.get('Retry-After'))
}

/**
 * Sends a request and returns the parsed body. One call to renew an expired access token and repeat the request is made,
 * and only for an authentication failure, so a request that reached the server is never repeated.
 */
export async function apiRaw(path: string, options: RequestOptions<unknown> = {}): Promise<Response> {
  const auth = options.auth ?? true
  const bridge = authBridge()

  let response = await send(path, options, auth ? bridge.getToken() : null)

  if (auth && response.status === 401) {
    const error = await toError(response)
    if (error.code === 'TOKEN_EXPIRED' || error.code === 'UNAUTHENTICATED') {
      let renewed: string
      try {
        renewed = await bridge.refresh()
      } catch {
        bridge.sessionLost('expired')
        throw error
      }
      response = await send(path, options, renewed)
      if (response.status === 401) {
        bridge.sessionLost('expired')
        throw await toError(response)
      }
    } else {
      bridge.sessionLost('invalid')
      throw error
    }
  }

  if (!response.ok) throw await toError(response)
  return response
}

export async function apiRequest<T = void>(path: string, options: RequestOptions<T> = {}): Promise<T> {
  const response = await apiRaw(path, options as RequestOptions<unknown>)
  if (response.status === 204) return undefined as T
  const json = await readJson(response)
  if (!options.schema) return json as T
  const parsed = options.schema.safeParse(json)
  if (!parsed.success) {
    // Paths only: the values may be claim data and must not reach the console.
    console.error('Unexpected response shape', path, parsed.error.issues.map((i) => i.path.join('.')))
    throw malformedResponse(response.status)
  }
  return parsed.data
}
