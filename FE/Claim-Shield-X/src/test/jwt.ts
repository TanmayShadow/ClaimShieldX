function b64url(text: string): string {
  return btoa(String.fromCharCode(...new TextEncoder().encode(text))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

/** An unsigned token shaped like the backend's, for tests. The client never verifies the signature. */
export function makeJwt(claims: Record<string, unknown> = {}): string {
  const payload = {
    sub: 'investigator@org-a.test',
    userId: '00000000-0000-4000-8000-000000000001',
    organizationId: '00000000-0000-4000-8000-0000000000aa',
    organizationName: 'Org A',
    iat: 1_792_276_800,
    exp: 1_792_278_600,
    ...claims,
  }
  return `${b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))}.${b64url(JSON.stringify(payload))}.signature`
}
