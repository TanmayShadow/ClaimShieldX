import { DEFAULT_RETURN_PATH, safeReturnPath } from './returnPath'

describe('safeReturnPath', () => {
  it.each(['/app', '/app/claims', '/app/claims/123/documents', '/app/claims?stage=FNOL&page=2', '/app/claims#top'])('keeps %s', (path) => {
    expect(safeReturnPath(path)).toBe(path)
  })

  it.each([
    '//evil.example',
    '//evil.example/app',
    '/\\evil.example',
    '\\\\evil.example',
    'https://evil.example/app',
    'http://localhost/app',
    'javascript:alert(1)',
    'data:text/html,<script>',
    '/login',
    '/',
    '/application',
    '/apps/claims',
    '/app/../login',
    '/app/./x',
    '%2F%2Fevil.example',
    '/%2F%2Fevil.example',
    '/app/%2e%2e/login',
    '/app\n/claims',
    '/app\u0000',
    '',
    'app/claims',
    '/app/%E0%A4%A',
  ])('replaces %j with the default', (path) => {
    expect(safeReturnPath(path)).toBe(DEFAULT_RETURN_PATH)
  })

  it.each([undefined, null, 42, {}, [], ['/app']])('replaces a non-string (%j)', (value) => {
    expect(safeReturnPath(value)).toBe(DEFAULT_RETURN_PATH)
  })

  it('rejects absurdly long values', () => {
    expect(safeReturnPath(`/app/${'a'.repeat(600)}`)).toBe(DEFAULT_RETURN_PATH)
  })
})
