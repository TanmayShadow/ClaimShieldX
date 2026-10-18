import { z } from 'zod'
import { apiRequest } from './client'

// The refresh token also travels in this body for non-browser clients. A browser ignores it: the HttpOnly cookie is the credential.
const tokenResponse = z.object({ accessToken: z.string().min(1) })

export async function loginRequest(email: string, password: string, signal?: AbortSignal): Promise<string> {
  const { accessToken } = await apiRequest('/auth/login', { method: 'POST', body: { email, password }, auth: false, schema: tokenResponse, signal })
  return accessToken
}

export async function refreshRequest(signal?: AbortSignal): Promise<string> {
  const { accessToken } = await apiRequest('/auth/refresh', { method: 'POST', auth: false, schema: tokenResponse, signal })
  return accessToken
}

export async function logoutRequest(signal?: AbortSignal): Promise<void> {
  await apiRequest('/auth/logout', { method: 'POST', auth: false, signal })
}
