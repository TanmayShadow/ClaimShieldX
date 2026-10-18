/** A controllable XMLHttpRequest for tests that need upload progress, which no network mock can simulate. */
export class FakeXhr {
  static all: FakeXhr[] = []
  method = ''
  url = ''
  headers: Record<string, string> = {}
  body: FormData | null = null
  status = 0
  responseText = ''
  timeout = 0
  aborted = false
  responseHeaders: Record<string, string> = {}
  upload = { onprogress: null as unknown } as unknown as XMLHttpRequestUpload
  onload: (() => void) | null = null
  onerror: (() => void) | null = null
  onabort: (() => void) | null = null
  ontimeout: (() => void) | null = null

  constructor() {
    FakeXhr.all.push(this)
  }
  open(method: string, url: string) {
    this.method = method
    this.url = url
  }
  setRequestHeader(k: string, v: string) {
    this.headers[k] = v
  }
  getResponseHeader(k: string) {
    return this.responseHeaders[k] ?? null
  }
  send(body: FormData) {
    this.body = body
  }
  abort() {
    this.aborted = true
    this.onabort?.()
  }
  progress(loaded: number, total: number) {
    ;(this.upload as unknown as { onprogress: (e: unknown) => void }).onprogress({ lengthComputable: true, loaded, total })
  }
  reply(status: number, body: unknown, headers: Record<string, string> = {}) {
    this.status = status
    this.responseText = typeof body === 'string' ? body : JSON.stringify(body)
    this.responseHeaders = headers
    this.onload?.()
  }
  static reset() {
    FakeXhr.all = []
  }
  static last(): FakeXhr {
    const x = FakeXhr.all.at(-1)
    if (!x) throw new Error('no upload request was made')
    return x
  }
}
