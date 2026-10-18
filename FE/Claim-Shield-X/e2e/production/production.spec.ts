import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { ORGANIZATION, apiToken, backendUp, createClaimViaApi, fixture, registerUser, signInThroughUi, uniqueRef, uploadFromRow } from '../support'

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the production server.')
})

/** Anything the browser refuses for policy reasons shows up here; the app must never trip its own policy. */
function watchPolicy(page: Page) {
  const problems: string[] = []
  page.on('console', (m) => {
    if (/Content Security Policy|Refused to/i.test(m.text())) problems.push(m.text())
  })
  page.on('pageerror', (e) => problems.push(e.message))
  return problems
}

test('serves the landing page with its policy, its own fonts, and nothing the policy refuses', async ({ page }) => {
  const problems = watchPolicy(page)
  const response = await page.goto('/')
  expect(response!.headers()['content-security-policy']).toContain("default-src 'self'")
  expect(response!.headers()['x-content-type-options']).toBe('nosniff')
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
  const fonts = await page.evaluate(() => [...document.fonts].filter((f) => f.status === 'loaded').map((f) => f.family))
  expect(fonts.join(' ')).toMatch(/Syne/)
  expect(fonts.join(' ')).toMatch(/DM Sans/)
  const external = await page.evaluate(() => performance.getEntriesByType('resource').map((r) => r.name).filter((n) => !n.startsWith(location.origin)))
  expect(external).toEqual([])
  expect((await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21aa']).analyze()).violations).toEqual([])
  expect(problems).toEqual([])
})

test('a whole session through the production server: sign in, reload on a deep link, investigate, check, and sign out', async ({ page, request }) => {
  const problems = watchPolicy(page)
  const token = await apiToken(request, ORGANIZATION, 'prodseed')
  const claim = await createClaimViaApi(request, token, { internalId: uniqueRef('PROD'), stage: 'FNOL' })
  const email = await registerUser(request, 'prod', ORGANIZATION)

  await page.goto('/login')
  await signInThroughUi(page, email)
  await expect(page).toHaveURL(/\/app$/)

  // A deep link survives a reload: the page is served for it, and the HttpOnly refresh cookie came through the forward.
  await page.goto(`/app/claims/${claim.id}/investigation`)
  await expect(page.getByRole('heading', { name: 'No investigation yet' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading', { name: 'No investigation yet' })).toBeVisible()
  const cookies = await page.context().cookies()
  const refresh = cookies.find((c) => c.name === 'refreshToken')
  expect(refresh?.httpOnly).toBe(true)
  expect(refresh?.path).toBe('/api/v1/auth')
  expect(await page.evaluate(() => document.cookie)).not.toContain('refreshToken')

  await page.getByRole('button', { name: 'Start investigation' }).click()
  await expect(page.getByText('Waiting for a document. This is not a failure.')).toBeVisible({ timeout: 20_000 })

  // An upload through the forward (multipart, progress) resumes it.
  await page.getByRole('link', { name: 'Documents tab' }).first().click()
  await uploadFromRow(page, 'CLAIM_FORM', fixture('synthetic-estimate.pdf'))
  await expect(page.getByText(/synthetic-estimate\.pdf is stored/)).toBeVisible()

  await page.getByRole('navigation', { name: 'Claim sections' }).getByRole('link', { name: 'Investigation' }).click()
  await expect(page.getByText('The investigation finished.')).toBeVisible({ timeout: 30_000 })

  await page.getByRole('navigation', { name: 'Claim sections' }).getByRole('link', { name: 'Checks' }).click()
  await expect(page.getByRole('heading', { name: 'Checks on this claim' })).toBeVisible()
  await expect(page.getByRole('list', { name: 'Rule results' }).getByRole('heading', { level: 3 })).toHaveCount(8)

  await page.getByRole('navigation', { name: 'Claim sections' }).getByRole('link', { name: 'Earlier claims' }).click()
  await expect(page.getByText('No historical claims have been imported, so nothing could be compared.')).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page).toHaveURL(/\/login/)
  await page.goto(`/app/claims/${claim.id}/checks`)
  await expect(page).toHaveURL(/\/login/)
  expect(problems).toEqual([])
})

test('an uploaded photograph previews through the session, which the policy must allow', async ({ page, request }) => {
  const problems = watchPolicy(page)
  const token = await apiToken(request, ORGANIZATION, 'prodimg')
  const claim = await createClaimViaApi(request, token, { internalId: uniqueRef('PRODIMG'), stage: 'FNOL' })
  const email = await registerUser(request, 'prodimg', ORGANIZATION)
  await page.goto('/login')
  await signInThroughUi(page, email)
  await expect(page).toHaveURL(/\/app$/)
  const response = await request.post(`/api/v1/claims/${claim.id}/documents`, {
    headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': `prod-img-${Date.now()}` },
    multipart: { documentType: 'DAMAGE_PHOTOGRAPH', file: { name: 'synthetic-photo.png', mimeType: 'image/png', buffer: (await import('../support')).fixtureBytes('synthetic-photo.png') } },
  })
  expect(response.status()).toBe(201)
  await page.goto(`/app/claims/${claim.id}/documents`)
  await page.getByRole('button', { name: /Preview/ }).first().click()
  const image = page.getByRole('img', { name: 'Preview of synthetic-photo.png' })
  await expect(image).toBeVisible()
  await expect.poll(() => image.evaluate((el: HTMLImageElement) => el.naturalWidth)).toBeGreaterThan(0)
  expect(problems).toEqual([])
})

test('the API is only reachable under /api, and an unknown file is a 404, not the app', async ({ request }) => {
  expect((await request.get('/assets/does-not-exist.js', { failOnStatusCode: false })).status()).toBe(404)
  expect((await request.get('/api/v1/claims', { failOnStatusCode: false })).status()).toBe(401)
  expect((await request.get('/healthz')).status()).toBe(200)
})
