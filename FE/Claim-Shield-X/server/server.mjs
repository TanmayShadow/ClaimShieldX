// Production server: serves the built app and forwards /api to the backend on the same origin.
// The backend sets no CORS headers and its refresh cookie is SameSite=Strict, so the browser must see one origin.
// No dependencies, so the container image is just Node plus `dist/`.
import { createReadStream, existsSync, statSync } from 'node:fs'
import http from 'node:http'
import https from 'node:https'
import { dirname, extname, join, normalize, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

const HOP_BY_HOP = new Set(['connection', 'keep-alive', 'proxy-authenticate', 'proxy-authorization', 'te', 'trailer', 'transfer-encoding', 'upgrade', 'host'])

const TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.woff': 'font/woff',
  '.txt': 'text/plain; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
}

// Everything is same-origin: scripts, styles, self-hosted fonts, the API. blob: is for previews of uploaded images.
export const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self'",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ')

function securityHeaders(req) {
  const headers = {
    'Content-Security-Policy': CSP,
    'X-Content-Type-Options': 'nosniff',
    'Referrer-Policy': 'no-referrer',
    'Permissions-Policy': 'camera=(), microphone=(), geolocation=(), payment=()',
    'Cross-Origin-Opener-Policy': 'same-origin',
    'X-Frame-Options': 'DENY',
  }
  if (req.headers['x-forwarded-proto'] === 'https') headers['Strict-Transport-Security'] = 'max-age=31536000'
  return headers
}

function json(res, status, body, extra = {}) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', ...extra })
  res.end(JSON.stringify(body))
}

/** @param {{ backendUrl: string, root: string, proxyTimeoutMs?: number }} options */
export function createApp({ backendUrl, root, proxyTimeoutMs = 240_000 }) {
  const backend = new URL(backendUrl)
  if (backend.protocol !== 'https:' && backend.protocol !== 'http:') throw new Error('BACKEND_URL must be http(s)')
  const transport = backend.protocol === 'https:' ? https : http
  const base = resolve(root)

  function proxy(req, res, headers) {
    const forwarded = {}
    for (const [name, value] of Object.entries(req.headers)) if (!HOP_BY_HOP.has(name)) forwarded[name] = value
    forwarded.host = backend.host
    forwarded['x-forwarded-proto'] = req.headers['x-forwarded-proto'] ?? 'http'
    forwarded['x-forwarded-for'] = [req.headers['x-forwarded-for'], req.socket.remoteAddress].filter(Boolean).join(', ')

    const upstream = transport.request(
      { protocol: backend.protocol, hostname: backend.hostname, port: backend.port || undefined, method: req.method, path: req.url, headers: forwarded, timeout: proxyTimeoutMs },
      (reply) => {
        const out = {}
        for (const [name, value] of Object.entries(reply.headers)) if (!HOP_BY_HOP.has(name)) out[name] = value
        res.writeHead(reply.statusCode ?? 502, { ...out, ...headers })
        reply.pipe(res)
      },
    )
    const fail = (status, code) => {
      if (res.headersSent) return res.destroy()
      json(res, status, { code, status, message: 'The API could not be reached.' }, headers)
    }
    upstream.on('timeout', () => {
      upstream.destroy()
      fail(504, 'GATEWAY_TIMEOUT')
    })
    upstream.on('error', () => fail(502, 'BAD_GATEWAY'))
    // A browser that gives up (closes the tab, cancels the upload) must not leave the upstream request running.
    res.on('close', () => {
      if (!res.writableEnded) upstream.destroy()
    })
    req.pipe(upstream)
  }

  function serveFile(req, res, file, headers, immutable) {
    const type = TYPES[extname(file)] ?? 'application/octet-stream'
    res.writeHead(200, { 'Content-Type': type, 'Cache-Control': immutable ? 'public, max-age=31536000, immutable' : 'no-store', ...headers })
    if (req.method === 'HEAD') return res.end()
    createReadStream(file).pipe(res)
  }

  return http.createServer((req, res) => {
    const headers = securityHeaders(req)
    const url = new URL(req.url ?? '/', 'http://local')

    if (url.pathname === '/healthz') return json(res, 200, { status: 'ok' })
    if (url.pathname === '/api' || url.pathname.startsWith('/api/')) return proxy(req, res, headers)

    if (req.method !== 'GET' && req.method !== 'HEAD') return json(res, 405, { code: 'METHOD_NOT_ALLOWED', status: 405 }, { ...headers, Allow: 'GET, HEAD' })

    let pathname
    try {
      pathname = decodeURIComponent(url.pathname)
    } catch {
      return json(res, 400, { code: 'BAD_REQUEST', status: 400 }, headers)
    }
    if (pathname.includes('\0')) return json(res, 400, { code: 'BAD_REQUEST', status: 400 }, headers)
    const file = normalize(join(base, pathname))
    // Never leave the build folder, whatever the path contains.
    if (file !== base && !file.startsWith(base + sep)) return json(res, 404, { code: 'NOT_FOUND', status: 404 }, headers)

    if (existsSync(file) && statSync(file).isFile()) return serveFile(req, res, file, headers, pathname.startsWith('/assets/'))
    // A path that looks like a file is a missing file. Anything else is an app route, which the browser router resolves.
    if (extname(pathname)) return json(res, 404, { code: 'NOT_FOUND', status: 404 }, headers)
    serveFile(req, res, join(base, 'index.html'), headers, false)
  })
}

const entry = process.argv[1] ? resolve(process.argv[1]) : ''
if (entry === fileURLToPath(import.meta.url)) {
  const backendUrl = process.env.BACKEND_URL
  if (!backendUrl) {
    console.error('BACKEND_URL is required, for example https://claimshield-api-xxxx.a.run.app')
    process.exit(1)
  }
  const root = process.env.STATIC_ROOT ?? join(dirname(fileURLToPath(import.meta.url)), '..', 'dist')
  const port = Number(process.env.PORT ?? 8080)
  const app = createApp({ backendUrl, root, proxyTimeoutMs: Number(process.env.PROXY_TIMEOUT_MS ?? 240_000) })
  app.listen(port, () => console.log(`serving ${root} on :${port}, /api -> ${new URL(backendUrl).origin}`))
  for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => app.close(() => process.exit(0)))
}
