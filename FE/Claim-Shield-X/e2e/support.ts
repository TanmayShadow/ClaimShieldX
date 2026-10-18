import { createHash, randomBytes } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { expect } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'

export const PASSWORD = 'Passw0rd!x1'
export const ORGANIZATION = 'Org A'
/** Seeded in the scratch database: a second tenant, and one that never holds any claim. */
export const OTHER_ORGANIZATION = 'Org B'
export const EMPTY_ORGANIZATION = 'Org E'

let counter = 0

/** True when the real backend answers through the dev proxy. Tests that need it skip, loudly, when it does not. */
export async function backendUp(request: APIRequestContext): Promise<boolean> {
  try {
    const response = await request.get('/api/v1/claims', { failOnStatusCode: false, timeout: 5000 })
    return response.status() === 401 && (await response.json()).code === 'UNAUTHENTICATED'
  } catch {
    return false
  }
}

/** Registers a throwaway user in the scratch organization through the real API. */
export async function registerUser(request: APIRequestContext, label = 'user', organizationName = ORGANIZATION): Promise<string> {
  const email = `e2e-${label}-${Date.now()}-${counter++}@org-a.test`
  const response = await request.post('/api/v1/users/register', { data: { email, password: PASSWORD, organizationName } })
  expect(response.status(), 'registering a test user').toBe(201)
  return email
}

export async function signInThroughUi(page: Page, email: string, password = PASSWORD): Promise<void> {
  await page.getByLabel('Email address').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Sign in' }).click()
}

export type NewClaim = { internalId: string; stage?: string; incidentType?: string }

export function claimBody({ internalId, stage = 'FINAL_INVOICE_RECEIVED', incidentType = 'COLLISION' }: NewClaim) {
  return {
    internalId,
    claimVersion: 1,
    currentStage: stage,
    policy: { policyToken: 'POL-TOKEN-1001', policyType: 'COMPREHENSIVE', policyStartDate: '2026-01-01T00:00:00+05:30', policyEndDate: '2026-12-31T00:00:00+05:30' },
    claimant: { claimantToken: 'CLT-TOKEN-1001' },
    garage: { garageId: 'GARAGE-DEMO-10' },
    vehicle: { vehicleToken: 'VEH-TOKEN-1001', make: 'DEMO_MAKE', model: 'DEMO_MODEL' },
    incident: { incidentAt: '2026-09-10T10:30:00+05:30', reportedAt: '2026-09-11T09:00:00+05:30', incidentType, permittedLocationPrecision: 'CITY' },
    financial: { initialEstimateAmount: 45000.5, currency: 'INR', deductible: 1000 },
  }
}

/** Signs a throwaway user in through the API only (its own session, so it never disturbs a browser session) and returns a bearer token. */
export async function apiToken(request: APIRequestContext, organizationName = ORGANIZATION, label = 'api'): Promise<string> {
  const email = await registerUser(request, label, organizationName)
  const response = await request.post('/api/v1/auth/login', { data: { email, password: PASSWORD } })
  expect(response.status(), 'API login').toBe(200)
  return (await response.json()).accessToken as string
}

export async function createClaimViaApi(request: APIRequestContext, token: string, claim: NewClaim): Promise<{ id: string; internalId: string }> {
  const response = await request.post('/api/v1/claims', { headers: { Authorization: `Bearer ${token}` }, data: claimBody(claim) })
  expect(response.status(), `creating ${claim.internalId}`).toBe(201)
  return response.json()
}

export async function claimCount(request: APIRequestContext, token: string, query = ''): Promise<number> {
  const response = await request.get(`/api/v1/claims?size=1${query}`, { headers: { Authorization: `Bearer ${token}` } })
  expect(response.status()).toBe(200)
  return (await response.json()).totalElements as number
}

let ref = 0
export const uniqueRef = (prefix = 'E2E') => `${prefix}-${Date.now().toString(36).toUpperCase()}-${ref++}`


export const fixture = (name: string) => resolve(import.meta.dirname, 'fixtures', name)
export const fixtureBytes = (name: string) => readFileSync(fixture(name))
export const sha256 = (data: Buffer) => createHash('sha256').update(data).digest('hex')

/** A valid-looking PDF of a chosen size that is unique every call, so the server's duplicate check never trips. */
export function bigPdf(megabytes: number): Buffer {
  return Buffer.concat([Buffer.from('%PDF-1.4\n% synthetic padding\n'), randomBytes(24), Buffer.alloc(Math.floor(megabytes * 1024 * 1024), 0x20), Buffer.from('\n%%EOF\n')])
}

export async function listDocumentsViaApi(request: APIRequestContext, token: string, claimId: string) {
  const response = await request.get(`/api/v1/claims/${claimId}/documents?size=100`, { headers: { Authorization: `Bearer ${token}` } })
  expect(response.status()).toBe(200)
  return (await response.json()) as { items: Array<{ id: string; fileName: string; status: string; superseded: boolean; documentType: string; sha256: string }>; totalElements: number }
}

export async function startInvestigationViaApi(request: APIRequestContext, token: string, claimId: string, note?: string) {
  const response = await request.post(`/api/v1/claims/${claimId}/investigations`, {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': `e2e-${Date.now()}-${Math.random().toString(16).slice(2)}` },
    data: note ? { note } : undefined,
    failOnStatusCode: false,
  })
  return { status: response.status(), body: await response.json() }
}

export async function getInvestigationViaApi(request: APIRequestContext, token: string, id: string) {
  const response = await request.get(`/api/v1/investigations/${id}`, { headers: { Authorization: `Bearer ${token}` }, failOnStatusCode: false })
  return { status: response.status(), body: await response.json() }
}

export async function uploadViaApi(request: APIRequestContext, token: string, claimId: string, documentType: string, name: string) {
  const response = await request.post(`/api/v1/claims/${claimId}/documents`, {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': `e2e-doc-${Date.now()}-${Math.random().toString(16).slice(2)}` },
    multipart: { documentType, file: { name, mimeType: 'application/pdf', buffer: fixtureBytes(name) } },
  })
  expect(response.status(), `uploading ${name}`).toBe(201)
  return response.json()
}

/** Seeded by e2e/seed-history.sh: an organization that holds the synthetic claim history. */
export const HISTORY_ORGANIZATION = 'Org H'
/** A vehicle token that appears twice in that history, 45 and 90 days before the claims these tests create. */
export const REPEAT_VEHICLE_TOKEN = 'VEH-REPEAT-01'

export function repeatVehicleClaimBody(internalId: string) {
  const body = claimBody({ internalId, stage: 'FNOL' })
  return { ...body, vehicle: { ...body.vehicle, vehicleToken: REPEAT_VEHICLE_TOKEN }, garage: { garageId: 'GARAGE-SYN-03' } }
}

export async function createRepeatVehicleClaim(request: APIRequestContext, token: string, internalId: string): Promise<{ id: string; internalId: string }> {
  const response = await request.post('/api/v1/claims', { headers: { Authorization: `Bearer ${token}` }, data: repeatVehicleClaimBody(internalId) })
  expect(response.status(), `creating ${internalId}`).toBe(201)
  return response.json()
}

export async function waitForInvestigation(request: APIRequestContext, token: string, id: string, seconds = 150) {
  const deadline = Date.now() + seconds * 1000
  for (;;) {
    const { body } = await getInvestigationViaApi(request, token, id)
    if (['COMPLETED', 'FAILED', 'CANCELLED'].includes(body.status)) return body
    if (Date.now() > deadline) throw new Error(`investigation ${id} still ${body.status} after ${seconds}s`)
    await new Promise((r) => setTimeout(r, 2000))
  }
}
