import { useCallback, useEffect, useReducer, useRef } from 'react'
import { isUploadAbort, uploadDocument } from '@/apis/upload'
import type { UploadHandle, UploadResult } from '@/apis/upload'
import { useRefreshClaimEvidence } from './hooks'
import { newIdempotencyKey } from './uploadRules'

export type UploadPhase = 'idle' | 'sending' | 'confirming' | 'done' | 'failed' | 'cancelled'

export type UploadState = {
  phase: UploadPhase
  loaded: number
  total: number
  error: unknown
  result: UploadResult | null
  fileName: string
}

const initial: UploadState = { phase: 'idle', loaded: 0, total: 0, error: null, result: null, fileName: '' }

type Action =
  | { type: 'start'; fileName: string }
  | { type: 'progress'; loaded: number; total: number }
  | { type: 'done'; result: UploadResult }
  | { type: 'failed'; error: unknown }
  | { type: 'cancelled' }
  | { type: 'reset' }

function reducer(state: UploadState, action: Action): UploadState {
  switch (action.type) {
    case 'start':
      return { ...initial, phase: 'sending', fileName: action.fileName }
    case 'progress': {
      if (state.phase !== 'sending' && state.phase !== 'confirming') return state
      // Every byte sent is NOT "uploaded": the server has not answered yet.
      const sentAll = action.total > 0 && action.loaded >= action.total
      return { ...state, phase: sentAll ? 'confirming' : 'sending', loaded: action.loaded, total: action.total }
    }
    case 'done':
      return { ...state, phase: 'done', result: action.result, error: null }
    case 'failed':
      return { ...state, phase: 'failed', error: action.error }
    case 'cancelled':
      return { ...state, phase: 'cancelled' }
    case 'reset':
      return initial
  }
}

export type UploadParams = {
  claimId: string
  file: File
  documentType: string
  externalDocumentId?: string
  supersedesDocumentId?: string
}

const signatureOf = (p: UploadParams) =>
  JSON.stringify([p.claimId, p.file.name, p.file.size, p.file.lastModified, p.file.type, p.documentType, p.externalDocumentId ?? '', p.supersedesDocumentId ?? ''])

/**
 * One upload at a time, as a small state machine. The same Idempotency-Key is used for every retry of the same request,
 * which is what makes a retry after a dropped connection safe: the server returns its first answer instead of storing twice.
 * A different file, type or option gets a new key, and so does the next upload after a success.
 */
export function useUpload() {
  const [state, dispatch] = useReducer(reducer, initial)
  const handle = useRef<UploadHandle | null>(null)
  const key = useRef<{ signature: string; value: string } | null>(null)
  const refresh = useRefreshClaimEvidence()
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      handle.current?.abort()
    }
  }, [])

  const submit = useCallback(
    async (params: UploadParams) => {
      if (handle.current) return
      const signature = signatureOf(params)
      if (key.current?.signature !== signature) key.current = { signature, value: newIdempotencyKey() }
      dispatch({ type: 'start', fileName: params.file.name })

      const h = uploadDocument({ ...params, idempotencyKey: key.current.value }, (loaded, total) => {
        if (mounted.current) dispatch({ type: 'progress', loaded, total })
      })
      handle.current = h
      try {
        const result = await h.promise
        key.current = null // the next upload, even of the same file, is a new request
        if (mounted.current) dispatch({ type: 'done', result })
        void refresh()
      } catch (error) {
        if (!mounted.current) return
        if (isUploadAbort(error)) {
          dispatch({ type: 'cancelled' })
          // The server may have received everything before the cancel took effect, so look at what it holds.
          void refresh()
        } else {
          // A reused key with a different request cannot be retried as is.
          if ((error as { code?: string }).code === 'IDEMPOTENCY_KEY_REUSED') key.current = null
          dispatch({ type: 'failed', error })
        }
      } finally {
        handle.current = null
      }
    },
    [refresh],
  )

  const cancel = useCallback(() => handle.current?.abort(), [])
  const reset = useCallback(() => {
    key.current = null
    dispatch({ type: 'reset' })
  }, [])

  return { state, submit, cancel, reset, busy: state.phase === 'sending' || state.phase === 'confirming' }
}

export function percent(state: Pick<UploadState, 'loaded' | 'total'>): number {
  if (state.total <= 0) return 0
  return Math.min(99, Math.floor((state.loaded / state.total) * 100))
}
