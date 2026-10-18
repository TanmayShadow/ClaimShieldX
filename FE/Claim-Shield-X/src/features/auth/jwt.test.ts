import { makeJwt } from '@/test/jwt'
import { decodeAccessToken } from './jwt'

describe('decodeAccessToken', () => {
  it('reads who the token was issued to', () => {
    expect(decodeAccessToken(makeJwt())).toEqual({
      email: 'investigator@org-a.test',
      organizationName: 'Org A',
      userId: '00000000-0000-4000-8000-000000000001',
      expiresAt: 1_792_278_600_000,
    })
  })

  it('handles non-ASCII organization names', () => {
    expect(decodeAccessToken(makeJwt({ organizationName: 'Bīma Sūraksha ₹' }))?.organizationName).toBe('Bīma Sūraksha ₹')
  })

  it('tolerates missing optional claims', () => {
    const user = decodeAccessToken(makeJwt({ organizationName: undefined, userId: undefined, exp: undefined }))
    expect(user).toMatchObject({ email: 'investigator@org-a.test', organizationName: null, userId: null, expiresAt: null })
  })

  it.each(['', 'abc', 'a.b', 'a.b.c.d', 'a..c', 'a.!!!.c', `a.${btoa('not json')}.c`, `a.${btoa('"just a string"')}.c`, `a.${btoa('[]')}.c`])('returns null for %j', (token) => {
    expect(decodeAccessToken(token)).toBeNull()
  })

  it('requires a subject', () => {
    expect(decodeAccessToken(makeJwt({ sub: undefined }))).toBeNull()
    expect(decodeAccessToken(makeJwt({ sub: '' }))).toBeNull()
    expect(decodeAccessToken(makeJwt({ sub: 42 }))).toBeNull()
  })

  it('never throws on hostile input', () => {
    expect(() => decodeAccessToken('<script>.<script>.<script>')).not.toThrow()
  })
})
