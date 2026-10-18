import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { registerAuthBridge } from '@/apis/authBridge'
import { percent, useUpload } from './useUpload'

class FakeXhr {
  static all: FakeXhr[] = []
  headers: Record<string, string> = {}
  status = 0
  responseText = ''
  timeout = 0
  aborted = false
  upload = { onprogress: null as unknown } as unknown as XMLHttpRequestUpload
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  ontimeout: (() => void) | null = null
  constructor() {
    FakeXhr.all.push(this)
  }
  open() {}
  setRequestHeader(k: string, v: string) {
    this.headers[k] = v
  }
  getResponseHeader() {
    return null
  }
  send() {}
  abort() {
    this.aborted = true
    this.onabort?.()
  }
  progress(loaded: number, total: number) {
    ;(this.upload as unknown as { onprogress: (e: unknown) => void }).onprogress({ lengthComputable: true, loaded, total })
  }
  reply(status: number, body: unknown) {
    this.status = status
    this.responseText = JSON.stringify(body)
    this.onload?.()
  }
}

const doc = { id: 'd1', claimId: 'c1', documentType: 'FINAL_INVOICE', fileName: 'a.pdf', contentType: 'application/pdf', sizeBytes: 5, sha256: 'a'.repeat(64), status: 'RECEIVED', externalDocumentId: null, supersedesDocumentId: null, superseded: false, receivedAt: 't' }

const file = (name = 'a.pdf') => new File(['hello'], name, { type: 'application/pdf', lastModified: 1 })
const params = (f = file()) => ({ claimId: 'c1', file: f, documentType: 'FINAL_INVOICE' })

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient()
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

beforeEach(() => {
  FakeXhr.all = []
  vi.stubGlobal('XMLHttpRequest', FakeXhr)
  registerAuthBridge({ getToken: () => 'tok', refresh: () => Promise.reject(new Error('x')), sessionLost: () => {} })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('useUpload', () => {
  it('moves from sending to waiting for the server, and is done only when the server answers', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper })
    expect(result.current.state.phase).toBe('idle')
    let pending: Promise<void>
    act(() => {
      pending = result.current.submit(params())
    })
    expect(result.current.state.phase).toBe('sending')
    act(() => FakeXhr.all[0]!.progress(2, 10))
    expect(result.current.state).toMatchObject({ phase: 'sending', loaded: 2, total: 10 })
    expect(percent(result.current.state)).toBe(20)
    act(() => FakeXhr.all[0]!.progress(10, 10))
    expect(result.current.state.phase).toBe('confirming')
    expect(percent(result.current.state)).toBe(99) // never 100 before the server confirms
    await act(async () => {
      FakeXhr.all[0]!.reply(201, doc)
      await pending
    })
    expect(result.current.state.phase).toBe('done')
    expect(result.current.state.result?.document.id).toBe('d1')
  })

  it('refuses to start a second upload while one is running', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper })
    act(() => {
      void result.current.submit(params())
    })
    await act(async () => {
      await result.current.submit(params(file('b.pdf')))
    })
    expect(FakeXhr.all).toHaveLength(1)
    expect(result.current.busy).toBe(true)
  })

  it('reuses the idempotency key when the same request is retried after a failure', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper })
    const f = file()
    await act(async () => {
      const p = result.current.submit(params(f))
      FakeXhr.all[0]!.onerror?.()
      await p
    })
    expect(result.current.state.phase).toBe('failed')
    await act(async () => {
      const p = result.current.submit(params(f))
      FakeXhr.all[1]!.reply(201, doc)
      await p
    })
    expect(FakeXhr.all[1]!.headers['Idempotency-Key']).toBe(FakeXhr.all[0]!.headers['Idempotency-Key'])
    expect(result.current.state.phase).toBe('done')
  })

  it('uses a new key for a different file, and for the next upload after a success', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper })
    await act(async () => {
      const p = result.current.submit(params(file('a.pdf')))
      FakeXhr.all[0]!.onerror?.()
      await p
    })
    await act(async () => {
      const p = result.current.submit(params(file('different.pdf')))
      FakeXhr.all[1]!.reply(201, doc)
      await p
    })
    await act(async () => {
      const p = result.current.submit(params(file('different.pdf')))
      FakeXhr.all[2]!.reply(409, { status: 409, code: 'DUPLICATE_DOCUMENT', message: 'x' })
      await p
    })
    const keys = FakeXhr.all.map((x) => x.headers['Idempotency-Key'])
    expect(new Set(keys).size).toBe(3)
  })

  it('does not hide a failure: it keeps the error for the screen', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper })
    await act(async () => {
      const p = result.current.submit(params())
      FakeXhr.all[0]!.reply(415, { status: 415, code: 'UNSUPPORTED_MEDIA_TYPE', message: 'x' })
      await p
    })
    expect(result.current.state.phase).toBe('failed')
    expect(result.current.state.error).toMatchObject({ code: 'UNSUPPORTED_MEDIA_TYPE' })
  })

  it('cancels, aborting the transfer, and says cancelled rather than failed', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper })
    let pending: Promise<void>
    act(() => {
      pending = result.current.submit(params())
    })
    await act(async () => {
      result.current.cancel()
      await pending
    })
    expect(FakeXhr.all[0]!.aborted).toBe(true)
    expect(result.current.state.phase).toBe('cancelled')
  })

  it('drops a reused-key answer’s key so the next attempt is a fresh request', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper })
    const f = file()
    await act(async () => {
      const p = result.current.submit(params(f))
      FakeXhr.all[0]!.reply(422, { status: 422, code: 'IDEMPOTENCY_KEY_REUSED', message: 'x' })
      await p
    })
    await act(async () => {
      const p = result.current.submit(params(f))
      FakeXhr.all[1]!.reply(201, doc)
      await p
    })
    expect(FakeXhr.all[1]!.headers['Idempotency-Key']).not.toBe(FakeXhr.all[0]!.headers['Idempotency-Key'])
  })

  it('resets to idle', async () => {
    const { result } = renderHook(() => useUpload(), { wrapper })
    await act(async () => {
      const p = result.current.submit(params())
      FakeXhr.all[0]!.onerror?.()
      await p
    })
    act(() => result.current.reset())
    expect(result.current.state.phase).toBe('idle')
  })

  it('stops a transfer that is still running when the person leaves the page', () => {
    const { result, unmount } = renderHook(() => useUpload(), { wrapper })
    act(() => {
      void result.current.submit(params())
    })
    unmount()
    expect(FakeXhr.all[0]!.aborted).toBe(true)
  })
})
