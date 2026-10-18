import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { OTHER_ORGANIZATION, ORGANIZATION, apiToken, backendUp, createClaimViaApi, fixture, getInvestigationViaApi, registerUser, signInThroughUi, startInvestigationViaApi, uniqueRef, uploadViaApi } from './support'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy.')
})

const axeViolations = async (page: Page) =>
  (await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()).violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)

/** A signed-in browser on the investigation tab of a fresh FNOL claim. Its mandatory document is the claim form, which is stored, not read by a model, so no model is ever called. */
async function open(page: Page, request: APIRequestContext, label: string) {
  const token = await apiToken(request, ORGANIZATION, `${label}seed`)
  const claim = await createClaimViaApi(request, token, { internalId: uniqueRef(label.toUpperCase()), stage: 'FNOL' })
  const email = await registerUser(request, label, ORGANIZATION)
  await page.goto('/login')
  await signInThroughUi(page, email)
  await expect(page).toHaveURL(/\/app$/)
  await page.goto(`/app/claims/${claim.id}/investigation`)
  await expect(page.getByRole('heading', { name: 'No investigation yet' })).toBeVisible()
  return { token, claim, email }
}

const node = (page: Page, label: RegExp) => page.getByRole('group', { name: /Investigation route/ }).getByRole('button', { name: label })

const iso = (offsetSeconds = 0) => new Date(Date.now() + offsetSeconds * 1000).toISOString()

/** A contract-shaped investigation for tests that need a state the real backend will not hold still long enough to inspect. */
function fake(patch: Record<string, unknown> & { statuses?: Record<string, string> } = {}) {
  const { statuses = {}, ...rest } = patch
  const stageIds = ['PREPARING_EVIDENCE', 'EXTRACTING', 'RUNNING_RULES', 'BUILDING_HYPOTHESES', 'VALIDATING_FINDINGS', 'GENERATING_REPORT']
  return {
    id: '99999999-0000-4000-8000-000000000001',
    claimId: '11111111-0000-4000-8000-000000000001',
    claimVersion: 1,
    status: 'EXTRACTING',
    attempt: 1,
    maxAttempts: 3,
    nextRetryAt: null,
    createdAt: iso(-60),
    startedAt: iso(-50),
    updatedAt: iso(-1),
    finishedAt: null,
    cancelRequested: false,
    note: null,
    ruleRunId: null,
    snapshotId: null,
    reportStatus: null,
    stages: stageIds.map((id) => {
      const status = statuses[id] ?? 'PENDING'
      return { stage: id, status, attempts: status === 'PENDING' ? 0 : 1, startedAt: status === 'PENDING' ? null : iso(-50), finishedAt: ['OK', 'DEGRADED', 'FAILED', 'SKIPPED'].includes(status) ? iso(-20) : null, errorCode: null as string | null, summary: {} }
    }),
    waitingFor: null,
    error: null,
    ...rest,
  }
}

async function serve(page: Page, claimInvestigations: unknown[], one?: (id: string) => unknown) {
  await page.route('**/api/v1/claims/*/investigations?*', (route) =>
    route.request().method() === 'GET' ? route.fulfill({ contentType: 'application/json', body: JSON.stringify({ items: claimInvestigations, page: 0, size: 20, totalElements: claimInvestigations.length, totalPages: 1 }) }) : route.continue(),
  )
  await page.route('**/api/v1/investigations/*', (route) => {
    if (route.request().method() !== 'GET') return route.continue()
    const id = route.request().url().split('/').pop()!.split('?')[0]!
    const body = one ? one(id) : claimInvestigations.find((i) => (i as { id: string }).id === id)
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
  })
}

test.describe('a whole investigation (real backend)', () => {
  test('waits for the missing document, resumes the moment it is uploaded, and finishes, all without reloading the page', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'flow')
    await expect(page.getByText(/will wait for it and carry on as soon as it is uploaded/)).toBeVisible()
    await page.getByLabel(/^Note/).fill('e2e: wait then resume')
    await page.getByRole('button', { name: 'Start investigation' }).click()

    await expect(page.getByText('Waiting for a document. This is not a failure.')).toBeVisible({ timeout: 20_000 })
    await expect(page.getByText(/Claim form \(not received\)/).first()).toBeVisible()
    await expect(page.getByText('Note: e2e: wait then resume')).toBeVisible()
    await expect(node(page, /Evidence prepared/)).toContainText('Waiting for a document')
    await expect(node(page, /Documents read/)).toContainText('Waiting its turn')
    for (const label of [/Explanations/, /Evidence validated/, /Report assembled/]) await expect(node(page, label)).toContainText('Waiting its turn')
    await expect(page.locator('[data-flow="true"]')).toHaveCount(0)

    const list = await request.get(`/api/v1/claims/${claim.id}/investigations`, { headers: { Authorization: `Bearer ${token}` } })
    const investigationId = (await list.json()).items[0].id as string
    expect((await getInvestigationViaApi(request, token, investigationId)).body.status).toBe('WAITING_FOR_EVIDENCE')

    // Someone else uploads the claim form. The page, which only polls, must notice and show the run finishing.
    await uploadViaApi(request, token, claim.id, 'CLAIM_FORM', 'synthetic-estimate.pdf')
    await expect(page.getByText('The investigation finished.')).toBeVisible({ timeout: 30_000 })
    await expect(node(page, /Evidence prepared/)).toContainText('Finished')
    await expect(node(page, /Documents read/)).toContainText('Nothing to do')
    await expect(node(page, /Checks run/)).toContainText('Finished')
    // The model is asked only when a rule flagged something (a repeated fixture file can flag one), so the explanation stage may be skipped.
    await expect(node(page, /Explanations/)).toContainText(/Finished|Skipped: nothing flagged/)
    for (const label of [/Evidence validated/, /Report assembled/]) await expect(node(page, label)).toContainText('Finished')
    await expect(page.getByText(/A report was written\./)).toBeVisible()
    await expect(page.getByRole('link', { name: 'Open the report' })).toBeVisible()

    // What the page says matches what the backend recorded.
    const done = (await getInvestigationViaApi(request, token, investigationId)).body
    expect(done.status).toBe('COMPLETED')
    expect(done.reportStatus).toBe('CURRENT')
    expect(done.reportId).toBeTruthy()
    await node(page, /Checks run/).click()
    const inspector = page.getByRole('region', { name: 'Details of Checks run' })
    await expect(inspector).toContainText(done.ruleRunId)
    const rules = done.stages.find((s: { stage: string }) => s.stage === 'RUNNING_RULES').summary as Record<string, number>
    await expect(inspector).toContainText(`${rules.incomplete} incomplete`)
    await expect(inspector).toContainText(`${rules.flag} flag`)
    await node(page, /Documents read/).click()
    await expect(page.getByText(/No invoice or estimate needed reading/)).toBeVisible()
  })

  test('stops asking the server once the investigation has ended', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'stop')
    await uploadViaApi(request, token, claim.id, 'CLAIM_FORM', 'synthetic-estimate.pdf')
    await page.getByRole('button', { name: 'Start investigation' }).click()
    await expect(page.getByText('The investigation finished.')).toBeVisible({ timeout: 30_000 })
    await page.waitForTimeout(500)
    let polls = 0
    page.on('request', (r) => r.url().includes('/api/v1/investigations/') && r.method() === 'GET' && polls++)
    await page.waitForTimeout(7000)
    expect(polls).toBe(0)
  })

  test('can be cancelled while waiting, after a confirmation, and a new one can be started', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'cancel')
    await page.getByRole('button', { name: 'Start investigation' }).click()
    await expect(page.getByText('Waiting for a document. This is not a failure.')).toBeVisible({ timeout: 20_000 })

    await page.getByRole('button', { name: 'Cancel investigation' }).click()
    const dialog = page.getByRole('dialog', { name: 'Cancel this investigation?' })
    await expect(dialog).toContainText('Work already finished is kept')
    await dialog.getByRole('button', { name: 'Keep it running' }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('button', { name: 'Cancel investigation' })).toBeFocused()
    const first = (await (await request.get(`/api/v1/claims/${claim.id}/investigations`, { headers: { Authorization: `Bearer ${token}` } })).json()).items[0]
    expect(first.status).toBe('WAITING_FOR_EVIDENCE')

    await page.getByRole('button', { name: 'Cancel investigation' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel investigation' }).click()
    await expect(page.getByText('This investigation was cancelled.')).toBeVisible({ timeout: 10_000 })
    expect((await getInvestigationViaApi(request, token, first.id)).body.status).toBe('CANCELLED')

    await expect(page.getByRole('heading', { name: 'Start another investigation' })).toBeVisible()
    await page.getByRole('button', { name: 'Start investigation' }).click()
    await expect(page.getByRole('table', { name: 'Investigations of this claim, newest first' })).toBeVisible({ timeout: 20_000 })
    await expect(page.getByRole('table', { name: 'Investigations of this claim, newest first' }).getByRole('row')).toHaveCount(3)
  })

  test('only one investigation can run at a time: a second request is explained, and the running one is shown', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'active')
    const started = await startInvestigationViaApi(request, token, claim.id)
    expect(started.status).toBe(202)
    // The page still believes there is nothing to watch; starting one from it must be refused in words.
    await page.getByRole('button', { name: 'Start investigation' }).click()
    await expect(page.getByText('An investigation is already under way for this claim.')).toBeVisible()
    await expect(page.getByText(/Waiting for a document|Queued/).first()).toBeVisible({ timeout: 20_000 })
    expect((await request.get(`/api/v1/claims/${claim.id}/investigations`, { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.json())).totalElements).toBe(1)
  })

  test('a dropped connection while starting is safe to repeat: the same key, and one investigation', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'retrykey')
    const keys: string[] = []
    let dropped = false
    await page.route('**/api/v1/claims/*/investigations', (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      keys.push(route.request().headers()['idempotency-key'] ?? '')
      if (!dropped) {
        dropped = true
        return route.abort('connectionrefused')
      }
      return route.continue()
    })
    await page.getByRole('button', { name: 'Start investigation' }).click()
    await expect(page.getByText('The server could not be reached.')).toBeVisible()
    await page.getByRole('button', { name: 'Start investigation' }).click()
    await expect(page.getByText(/Waiting for a document|Queued/).first()).toBeVisible({ timeout: 20_000 })
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBe(keys[1])
    expect((await request.get(`/api/v1/claims/${claim.id}/investigations`, { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.json())).totalElements).toBe(1)
  })

  test('uploading the missing document from the Documents tab resumes it, and the readiness strip follows', async ({ page, request }) => {
    await open(page, request, 'tabs')
    await page.getByRole('button', { name: 'Start investigation' }).click()
    await expect(page.getByText('Waiting for a document. This is not a failure.')).toBeVisible({ timeout: 20_000 })
    await page.getByRole('link', { name: 'Documents tab' }).first().click()
    await expect(page.getByRole('heading', { name: 'Add a document' })).toBeVisible()
    await page.getByLabel('Document type').selectOption('CLAIM_FORM')
    await page.getByLabel('File', { exact: true }).setInputFiles(fixture('synthetic-estimate.pdf'))
    await page.getByRole('button', { name: 'Upload', exact: true }).click()
    await expect(page.getByText('synthetic-estimate.pdf is stored.')).toBeVisible()
    await page.getByRole('navigation', { name: 'Claim sections' }).getByRole('link', { name: 'Investigation' }).click()
    await expect(page.getByText('The investigation finished.')).toBeVisible({ timeout: 30_000 })
  })
})

test.describe('states the real backend will not hold still (served from fixtures)', () => {
  test('shows a scheduled retry in words, with the time and attempt, and no raw code', async ({ page, request }) => {
    await open(page, request, 'retrywait')
    const inv = fake({ status: 'RETRY_WAIT', attempt: 2, nextRetryAt: iso(300), error: { code: 'MODEL_UNAVAILABLE', message: 'x' }, statuses: { PREPARING_EVIDENCE: 'OK', EXTRACTING: 'FAILED' } })
    inv.stages[1]!.errorCode = 'MODEL_UNAVAILABLE'
    await serve(page, [inv])
    await page.reload()
    await expect(page.getByText('A temporary problem. It will try again.')).toBeVisible()
    await expect(page.getByText(/The reading service was unavailable\./).first()).toBeVisible()
    await expect(page.getByText(/Attempt 2 of 3 will start at/)).toBeVisible()
    await expect(page.locator('main')).not.toContainText('MODEL_UNAVAILABLE')
    await expect(node(page, /Documents read/)).toContainText('Failed')
    await expect(page.locator('[data-flow="true"]')).toHaveCount(0)
  })

  test('shows a failure in words and retries it with an idempotency key', async ({ page, request }) => {
    await open(page, request, 'failed')
    const failed = fake({ status: 'FAILED', finishedAt: iso(-5), error: { code: 'STORAGE_UNAVAILABLE', message: 'x' }, statuses: { PREPARING_EVIDENCE: 'OK', EXTRACTING: 'FAILED' } })
    failed.stages[1]!.errorCode = 'STORAGE_UNAVAILABLE'
    let retried: { key: string | undefined; path: string } | null = null
    await serve(page, [failed])
    await page.route('**/api/v1/investigations/*/retry', (route) => {
      retried = { key: route.request().headers()['idempotency-key'], path: new URL(route.request().url()).pathname }
      return route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify(fake({ status: 'QUEUED', startedAt: null })) })
    })
    await page.reload()
    await expect(page.getByText('The investigation could not finish.')).toBeVisible()
    await expect(page.getByText(/The stored files could not be reached\./).first()).toBeVisible()
    await page.getByRole('button', { name: 'Try again' }).click()
    await expect.poll(() => retried).not.toBeNull()
    expect(retried!.path).toBe(`/api/v1/investigations/${failed.id}/retry`)
    expect(retried!.key).toMatch(/^upload-[0-9a-f-]{36}$/)
  })

  test('animates the track only while a stage is running, and not at all under reduced motion', async ({ page, request }) => {
    await open(page, request, 'motion')
    await serve(page, [fake({ status: 'EXTRACTING', statuses: { PREPARING_EVIDENCE: 'OK', EXTRACTING: 'RUNNING' } })])
    await page.reload()
    const flow = page.locator('[data-flow="true"]')
    await expect(flow).toHaveCount(1)
    expect(await flow.evaluate((el) => getComputedStyle(el).animationName)).not.toBe('none')
    expect(Number.parseFloat(await flow.evaluate((el) => getComputedStyle(el).animationDuration))).toBeGreaterThan(0.1)

    await page.emulateMedia({ reducedMotion: 'reduce' })
    expect(Number.parseFloat(await flow.evaluate((el) => getComputedStyle(el).animationDuration))).toBeLessThan(0.001)
    // The state is still readable without any motion.
    await expect(node(page, /Documents read/)).toContainText('Running now')
  })

  test('explains the daily limit of investigations', async ({ page, request }) => {
    await open(page, request, 'quota')
    await page.route('**/api/v1/claims/*/investigations', (route) =>
      route.request().method() === 'POST' ? route.fulfill({ status: 429, contentType: 'application/json', body: JSON.stringify({ code: 'INVESTIGATION_QUOTA_EXCEEDED', status: 429, message: 'x' }) }) : route.continue(),
    )
    await page.getByRole('button', { name: 'Start investigation' }).click()
    await expect(page.getByText('Your organization has reached its daily limit of investigations.')).toBeVisible()
  })

  test('keeps what it last saw and says so when a status check fails', async ({ page, request }) => {
    await open(page, request, 'stale')
    const inv = fake({ status: 'WAITING_FOR_EVIDENCE', waitingFor: [{ documentType: 'CLAIM_FORM', code: 'DOCUMENT_MISSING' }], statuses: { PREPARING_EVIDENCE: 'PENDING' } })
    await serve(page, [inv])
    await page.reload()
    await expect(page.getByText('Waiting for a document. This is not a failure.')).toBeVisible()
    await page.unroute('**/api/v1/investigations/*')
    await page.route('**/api/v1/investigations/*', (route) => route.abort('connectionrefused'))
    await page.getByRole('button', { name: 'Refresh' }).click()
    await expect(page.getByText('This view may be out of date.')).toBeVisible()
    await expect(page.getByText('Waiting for a document. This is not a failure.')).toBeVisible()
  })
})

test.describe('tenants (real backend)', () => {
  test('another organization can neither read nor cancel an investigation, and the claim looks missing to it', async ({ page, request }) => {
    const owner = await apiToken(request, ORGANIZATION, 'invowner')
    const claim = await createClaimViaApi(request, owner, { internalId: uniqueRef('INVSEC'), stage: 'FNOL' })
    const started = await startInvestigationViaApi(request, owner, claim.id)
    expect(started.status).toBe(202)
    const intruder = await apiToken(request, OTHER_ORGANIZATION, 'invintruder')
    expect((await getInvestigationViaApi(request, intruder, started.body.id)).status).toBe(404)
    const cancel = await request.post(`/api/v1/investigations/${started.body.id}/cancel`, { headers: { Authorization: `Bearer ${intruder}`, 'Idempotency-Key': 'e2e-cancel-intruder-1' }, failOnStatusCode: false })
    expect(cancel.status()).toBe(404)
    expect((await getInvestigationViaApi(request, owner, started.body.id)).body.status).not.toBe('CANCELLED')

    const email = await registerUser(request, 'invspy', OTHER_ORGANIZATION)
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    await page.goto(`/app/claims/${claim.id}/investigation`)
    await expect(page.getByRole('alert')).toContainText('We could not find that claim.')
  })
})

test.describe('quality of the investigation tab', () => {
  for (const size of [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'mobile', width: 390, height: 844 },
  ]) {
    test(`passes axe in every state and keeps to the screen at ${size.name}`, async ({ page, request }) => {
      test.setTimeout(120_000)
      await page.setViewportSize({ width: size.width, height: size.height })
      const { token, claim } = await open(page, request, `axe${size.name}`)
      const problems: string[] = []
      page.on('console', (m) => m.type() === 'error' && problems.push(m.text()))
      page.on('pageerror', (e) => problems.push(e.message))
      const check = async (state: string) => {
        expect(await axeViolations(page), `${state} at ${size.name}`).toEqual([])
        expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), `${state} sideways`).toBeLessThanOrEqual(0)
      }
      const shot = (n: string) => page.screenshot({ path: `e2e/.artifacts/inv-${n}-${size.name}.png`, fullPage: true })

      await check('empty')
      await shot('empty')

      await page.getByRole('button', { name: 'Start investigation' }).click()
      await expect(page.getByText('Waiting for a document. This is not a failure.')).toBeVisible({ timeout: 20_000 })
      await check('waiting')
      await shot('waiting')

      await page.getByRole('button', { name: 'Cancel investigation' }).click()
      await expect(page.getByRole('dialog')).toBeVisible()
      await check('cancel dialog')
      await page.getByRole('dialog').getByRole('button', { name: 'Keep it running' }).click()

      await uploadViaApi(request, token, claim.id, 'CLAIM_FORM', 'synthetic-estimate.pdf')
      await expect(page.getByText('The investigation finished.')).toBeVisible({ timeout: 30_000 })
      await node(page, /Checks run/).click()
      await check('finished')
      await shot('finished')

      await serve(page, [fake({ status: 'FAILED', finishedAt: iso(-5), error: { code: 'MODEL_QUOTA_EXHAUSTED', message: 'x' }, statuses: { PREPARING_EVIDENCE: 'OK', EXTRACTING: 'FAILED' } })])
      await page.reload()
      await expect(page.getByText('The investigation could not finish.')).toBeVisible()
      await check('failed')
      await shot('failed')

      await serve(page, [fake({ status: 'EXTRACTING', statuses: { PREPARING_EVIDENCE: 'OK', EXTRACTING: 'RUNNING' } })])
      await page.reload()
      await expect(node(page, /Documents read/)).toContainText('Running now')
      await check('running')
      await shot('running')

      expect(problems.filter((p) => !/Failed to load resource|status of (404|409|429)/.test(p))).toEqual([])
    })
  }

  test('the route and the inspector work from the keyboard', async ({ page, request }) => {
    await open(page, request, 'keys')
    await serve(page, [fake({ status: 'EXTRACTING', statuses: { PREPARING_EVIDENCE: 'OK', EXTRACTING: 'RUNNING' } })])
    await page.reload()
    await node(page, /Documents read/).focus()
    await page.keyboard.press('Tab')
    await expect(node(page, /Checks run/)).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(node(page, /Checks run/)).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByRole('heading', { level: 3, name: 'Checks run' })).toBeVisible()
    await page.getByRole('button', { name: 'Cancel investigation' }).focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toBeHidden()
    await expect(page.getByRole('button', { name: 'Cancel investigation' })).toBeFocused()
  })

  test('stays within the screen at 320px with a full route', async ({ page, request }) => {
    await page.setViewportSize({ width: 320, height: 800 })
    await open(page, request, 'narrowinv')
    await serve(page, [fake({ status: 'EXTRACTING', statuses: { PREPARING_EVIDENCE: 'OK', EXTRACTING: 'RUNNING' } })])
    await page.reload()
    await expect(page.getByRole('group', { name: /Investigation route/ })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
  })
})
