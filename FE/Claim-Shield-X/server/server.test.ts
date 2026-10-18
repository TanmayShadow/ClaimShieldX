// @vitest-environment node
import { mkdirSync, mkdtempSync, writeFileSync } from 'node:fs'
import http from 'node:http'
import type { AddressInfo } from 'node:net'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
// @ts-expect-error plain JavaScript module with no type declarations
import { createApp, CSP } from './server.mjs'

let backend: http.Server
let app: http.Server
let slowMs = 0
let seen: { method?: string; url?: string; headers: http.IncomingHttpHeaders; body: string }
let origin = ''
let dist = ''

const listen = (server: http.Server) => new Promise<number>((r) => server.listen(0, '127.0.0.1', () => r((server.address() as AddressInfo).port)))

beforeAll(async () => {
  dist = mkdtempSync(join(tmpdir(), 'dist-'))
  mkdirSync(join(dist, 'assets'))
  writeFileSync(join(dist, 'index.html'), '<!doctype html><title>app</title>')
  writeFileSync(join(dist, 'assets', 'app-abc123.js'), 'console.log(1)')
  writeFileSync(join(dist, 'robots.txt'), 'x')
  backend = http.createServer((req, res) => {
    let body = ''
    req.on('data', (c) => (body += c))
    req.on('end', () => {
      seen = { method: req.method, url: req.url, headers: req.headers, body }
      setTimeout(() => {
        if (req.url?.includes('boom')) return req.socket.destroy()
        res.writeHead(201, { 'Content-Type': 'application/json', 'Set-Cookie': 'refreshToken=abc; HttpOnly; Path=/api/v1/auth; SameSite=Strict; Secure', Connection: 'keep-alive', 'X-Backend': 'yes' })
        res.end(JSON.stringify({ echoed: body.length }))
      }, slowMs)
    })
  })
  const backendPort = await listen(backend)
  app = createApp({ backendUrl: `http://127.0.0.1:${backendPort}`, root: dist, proxyTimeoutMs: 300 })
  origin = `http://127.0.0.1:${await listen(app)}`
})

afterAll(() => {
  app.close()
  backend.close()
})

describe('the static app', () => {
  it('serves the page with a strict policy and never caches it', async () => {
    const res = await fetch(`${origin}/`)
    expect(res.status).toBe(200)
    expect(res.headers.get('content-type')).toContain('text/html')
    expect(res.headers.get('cache-control')).toBe('no-store')
    expect(res.headers.get('content-security-policy')).toBe(CSP)
    expect(CSP).toContain("default-src 'self'")
    expect(CSP).toContain("frame-ancestors 'none'")
    expect(CSP).not.toMatch(/script-src[^;]*unsafe/)
    expect(res.headers.get('x-content-type-options')).toBe('nosniff')
    expect(res.headers.get('referrer-policy')).toBe('no-referrer')
  })

  it('serves an app route as the page, so a refresh or a shared link works', async () => {
    for (const path of ['/app/claims/123/investigation', '/login', '/sample-investigation']) {
      const res = await fetch(`${origin}${path}`)
      expect(res.status, path).toBe(200)
      expect(await res.text()).toContain('<title>app</title>')
    }
  })

  it('caches built assets for a year and says a missing file is missing', async () => {
    const asset = await fetch(`${origin}/assets/app-abc123.js`)
    expect(asset.headers.get('cache-control')).toBe('public, max-age=31536000, immutable')
    expect(asset.headers.get('content-type')).toContain('javascript')
    expect((await fetch(`${origin}/assets/gone.js`)).status).toBe(404)
    expect((await fetch(`${origin}/robots.txt`)).headers.get('cache-control')).toBe('no-store')
  })

  it('adds the transport header only behind https', async () => {
    expect((await fetch(`${origin}/`)).headers.get('strict-transport-security')).toBeNull()
    expect((await fetch(`${origin}/`, { headers: { 'x-forwarded-proto': 'https' } })).headers.get('strict-transport-security')).toContain('max-age=')
  })

  it('stays inside the build folder whatever the path says', async () => {
    const raw = (path: string) =>
      new Promise<{ status: number; body: string }>((resolve) => {
        const port = new URL(origin).port
        const req = http.request({ host: '127.0.0.1', port, path, method: 'GET' }, (res) => {
          let body = ''
          res.on('data', (c) => (body += c))
          res.on('end', () => resolve({ status: res.statusCode ?? 0, body }))
        })
        req.end()
      })
    for (const path of ['/../package.json', '/%2e%2e/package.json', '/assets/../../package.json', '/..%2f..%2fetc/passwd', '/%00']) {
      const r = await raw(path)
      expect(r.body, path).not.toContain('"name"')
      expect(r.body, path).not.toContain('root:')
    }
    expect((await raw('/%E0%A4%A')).status).toBe(400)
  })

  it('answers a health check and refuses other methods on pages', async () => {
    expect(await (await fetch(`${origin}/healthz`)).json()).toEqual({ status: 'ok' })
    const res = await fetch(`${origin}/login`, { method: 'POST' })
    expect(res.status).toBe(405)
    expect(res.headers.get('allow')).toBe('GET, HEAD')
  })
})

describe('the API forward', () => {
  it('passes method, path, query, body and the caller\'s headers through, and the answer back, cookie included', async () => {
    slowMs = 0
    const res = await fetch(`${origin}/api/v1/claims?size=1`, { method: 'POST', headers: { Authorization: 'Bearer abc', 'Idempotency-Key': 'k-1', 'Content-Type': 'application/json', Cookie: 'refreshToken=r1' }, body: '{"a":1}' })
    expect(res.status).toBe(201)
    expect(await res.json()).toEqual({ echoed: 7 })
    expect(res.headers.get('x-backend')).toBe('yes')
    expect(res.headers.get('set-cookie')).toContain('refreshToken=abc; HttpOnly; Path=/api/v1/auth; SameSite=Strict; Secure')
    expect(seen.method).toBe('POST')
    expect(seen.url).toBe('/api/v1/claims?size=1')
    expect(seen.body).toBe('{"a":1}')
    expect(seen.headers.authorization).toBe('Bearer abc')
    expect(seen.headers['idempotency-key']).toBe('k-1')
    expect(seen.headers.cookie).toBe('refreshToken=r1')
    // The backend sees its own host, and is told what the browser used.
    expect(seen.headers.host).toMatch(/^127\.0\.0\.1:\d+$/)
    expect(seen.headers['x-forwarded-for']).toContain('127.0.0.1')
  })

  it('adds the security headers to API answers too', async () => {
    const res = await fetch(`${origin}/api/v1/anything`)
    expect(res.headers.get('x-content-type-options')).toBe('nosniff')
  })

  it('streams a large upload through whole', async () => {
    const big = Buffer.alloc(5 * 1024 * 1024, 7)
    const res = await fetch(`${origin}/api/v1/claims/x/documents`, { method: 'POST', body: big, headers: { 'Content-Type': 'application/octet-stream' } })
    expect(await res.json()).toEqual({ echoed: big.length })
  })

  it('answers 502 in the API\'s own error shape when the backend drops the connection', async () => {
    const res = await fetch(`${origin}/api/v1/boom`)
    expect(res.status).toBe(502)
    expect(await res.json()).toMatchObject({ code: 'BAD_GATEWAY', status: 502 })
  })

  it('answers 504 when the backend is too slow, instead of hanging', async () => {
    slowMs = 1200
    const res = await fetch(`${origin}/api/v1/slow`)
    slowMs = 0
    expect(res.status).toBe(504)
    expect(await res.json()).toMatchObject({ code: 'GATEWAY_TIMEOUT' })
  })

  it('answers 502 when nothing is listening', async () => {
    const dead = createApp({ backendUrl: 'http://127.0.0.1:9', root: dist })
    const port = await listen(dead)
    const res = await fetch(`http://127.0.0.1:${port}/api/v1/x`)
    expect(res.status).toBe(502)
    dead.close()
  })

  it('does not take /apix as the API', async () => {
    const res = await fetch(`${origin}/apix`)
    expect(res.status).toBe(200)
    expect(await res.text()).toContain('<title>app</title>')
  })

  it('refuses a backend address that is not http(s)', () => {
    expect(() => createApp({ backendUrl: 'file:///etc', root: dist })).toThrow(/http/)
  })
})
