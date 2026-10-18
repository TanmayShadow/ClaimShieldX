import { authBridge } from './authBridge'
import { buildUrl } from './client'
import { ApiError, malformedResponse, networkError, normalizeErrorBody, timeoutError } from './errors'
import { documentSchema } from '@/features/documents/schemas'
import type { DocumentDto } from '@/features/documents/schemas'

export type UploadInput = {
  claimId: string
  file: File
  documentType: string
  externalDocumentId?: string
  supersedesDocumentId?: string
  /** One key per upload attempt. Reusing it on a retry is what makes the retry safe: the server returns the first result. */
  idempotencyKey: string
}

export type UploadResult = { document: DocumentDto; replayed: boolean }

export type UploadHandle = { promise: Promise<UploadResult>; abort: () => void }

type XhrLike = Pick<XMLHttpRequest, 'open' | 'send' | 'abort' | 'setRequestHeader' | 'getResponseHeader' | 'status' | 'responseText' | 'timeout' | 'upload' | 'onload' | 'onerror' | 'onabort' | 'ontimeout'>

const UPLOAD_TIMEOUT_MS = 5 * 60_000

function abortError(): Error {
  const error = new Error('The upload was cancelled.')
  error.name = 'AbortError'
  return error
}

/**
 * Sends a document with real transfer progress, which fetch cannot report. A browser's 100% means the bytes left the machine,
 * not that the server accepted the file: the caller must treat only the resolved promise as "uploaded".
 * An expired access token is renewed once and the same file is sent again with the same Idempotency-Key.
 */
export function uploadDocument(input: UploadInput, onProgress: (loaded: number, total: number) => void, xhrFactory: () => XhrLike = () => new XMLHttpRequest()): UploadHandle {
  let current: XhrLike | null = null
  let cancelled = false

  function attempt(token: string | null): Promise<{ status: number; body: unknown; replayed: boolean; retryAfter: string | null }> {
    return new Promise((resolve, reject) => {
      const xhr = xhrFactory()
      current = xhr
      xhr.open('POST', buildUrl(`/claims/${encodeURIComponent(input.claimId)}/documents`))
      xhr.setRequestHeader('Accept', 'application/json')
      xhr.setRequestHeader('Idempotency-Key', input.idempotencyKey)
      if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
      xhr.timeout = UPLOAD_TIMEOUT_MS

      const form = new FormData()
      form.append('file', input.file)
      form.append('documentType', input.documentType)
      if (input.externalDocumentId) form.append('externalDocumentId', input.externalDocumentId)
      if (input.supersedesDocumentId) form.append('supersedesDocumentId', input.supersedesDocumentId)

      xhr.upload.onprogress = (event: ProgressEvent) => {
        if (event.lengthComputable) onProgress(event.loaded, event.total)
      }
      xhr.onload = () => {
        let body: unknown
        try {
          body = xhr.responseText ? JSON.parse(xhr.responseText) : undefined
        } catch {
          body = undefined
        }
        resolve({ status: xhr.status, body, replayed: xhr.getResponseHeader('Idempotent-Replay') === 'true', retryAfter: xhr.getResponseHeader('Retry-After') })
      }
      xhr.onerror = () => reject(networkError())
      xhr.ontimeout = () => reject(timeoutError())
      xhr.onabort = () => reject(abortError())
      xhr.send(form)
    })
  }

  async function run(): Promise<UploadResult> {
    const bridge = authBridge()
    let outcome = await attempt(bridge.getToken())

    if (outcome.status === 401) {
      const first = normalizeErrorBody(401, outcome.body)
      if (first.code === 'TOKEN_EXPIRED' || first.code === 'UNAUTHENTICATED') {
        let renewed: string
        try {
          renewed = await bridge.refresh()
        } catch {
          bridge.sessionLost('expired')
          throw first
        }
        if (cancelled) throw abortError()
        onProgress(0, 1)
        outcome = await attempt(renewed)
        if (outcome.status === 401) {
          bridge.sessionLost('expired')
          throw normalizeErrorBody(401, outcome.body)
        }
      } else {
        bridge.sessionLost('invalid')
        throw first
      }
    }

    if (outcome.status < 200 || outcome.status >= 300) throw normalizeErrorBody(outcome.status, outcome.body, outcome.retryAfter)

    const parsed = documentSchema.safeParse(outcome.body)
    if (!parsed.success) {
      console.error('Unexpected upload response shape', parsed.error.issues.map((i) => i.path.join('.')))
      throw malformedResponse(outcome.status)
    }
    return { document: parsed.data, replayed: outcome.replayed }
  }

  return {
    promise: run(),
    abort: () => {
      cancelled = true
      current?.abort()
    },
  }
}

export function isUploadAbort(error: unknown): boolean {
  return error instanceof Error && error.name === 'AbortError' && !(error instanceof ApiError)
}
