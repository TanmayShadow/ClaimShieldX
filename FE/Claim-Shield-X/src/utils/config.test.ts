import { parseApiBaseUrl } from './config'

describe('parseApiBaseUrl', () => {
  it('defaults to the relative API path', () => {
    expect(parseApiBaseUrl(undefined)).toBe('/api/v1')
  })

  it('accepts a relative path and drops a trailing slash', () => {
    expect(parseApiBaseUrl('/api/v1/')).toBe('/api/v1')
  })

  it('accepts https URLs and http for localhost only', () => {
    expect(parseApiBaseUrl('https://api.example.test/v1/')).toBe('https://api.example.test/v1')
    expect(parseApiBaseUrl('http://localhost:8080/api/v1')).toBe('http://localhost:8080/api/v1')
    expect(() => parseApiBaseUrl('http://api.example.test/v1')).toThrow(/https/)
  })

  it.each(['', '   ', '//evil.example/api', '/api/../admin', '/api?x=1', '/api#frag', '/a b', 'ftp://x.test/a', 'not a url', 'https://user:pw@x.test/api', 'https://x.test/api?token=1'])(
    'rejects %j',
    (value) => {
      expect(() => parseApiBaseUrl(value)).toThrow()
    },
  )
})
