import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { HISTORY_ORGANIZATION, OTHER_ORGANIZATION, apiToken, backendUp, createClaimViaApi, createRepeatVehicleClaim, registerUser, signInThroughUi, startInvestigationViaApi, uniqueRef, uploadViaApi, waitForInvestigation } from './support'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

const axeViolations = async (page: Page) =>
  (await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()).violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)

type Run = { token: string; email: string; claimId: string; investigationId: string }
let run: Run | null = null

/** One real investigation shared by this file: a claim on a vehicle that appears twice in the synthetic history, with the claim form stored. */
async function realRun(request: APIRequestContext): Promise<Run> {
  if (run) return run
  const token = await apiToken(request, HISTORY_ORGANIZATION, 'reportseed')
  const claim = await createRepeatVehicleClaim(request, token, uniqueRef('RPT'))
  const history = await (await request.get(`/api/v1/claims/${claim.id}/history-matches`, { headers: { Authorization: `Bearer ${token}` } })).json()
  test.skip(history.status !== 'MATCHES_FOUND', 'BLOCKED / NOT RUN: organization "Org H" has no imported history. Run e2e/seed-history.sh first.')
  await uploadViaApi(request, token, claim.id, 'CLAIM_FORM', 'synthetic-estimate.pdf')
  const started = await startInvestigationViaApi(request, token, claim.id, 'e2e report')
  expect(started.status).toBe(202)
  const done = await waitForInvestigation(request, token, started.body.id)
  expect(done.status).toBe('COMPLETED')
  const email = await registerUser(request, 'reporter', HISTORY_ORGANIZATION)
  run = { token, email, claimId: claim.id, investigationId: started.body.id }
  return run
}

async function signedIn(page: Page, r: Run, tab: 'report' | 'evidence' | 'investigation') {
  await page.goto('/login')
  await signInThroughUi(page, r.email)
  await expect(page).toHaveURL(/\/app$/)
  await page.goto(`/app/claims/${r.claimId}/${tab}`)
}

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy.')
})

test.describe.serial('the report (real backend, one real investigation)', () => {
  test('a finished investigation leads to a report with a transparent score and findings, in words', async ({ page, request }) => {
    test.setTimeout(240_000)
    const r = await realRun(request)
    const api = await (await request.get(`/api/v1/investigations/${r.investigationId}/report`, { headers: { Authorization: `Bearer ${r.token}` } })).json()
    await signedIn(page, r, 'investigation')
    await expect(page.getByText('The investigation finished.')).toBeVisible()
    await expect(page.getByText(/A report was written\./)).toBeVisible()
    await page.getByRole('link', { name: 'Open the report' }).click()

    await expect(page.getByRole('heading', { name: 'Report', level: 2 })).toBeVisible()
    await expect(page.getByText(api.summary)).toBeVisible()
    const priority = page.getByRole('region', { name: 'Review priority' })
    await expect(priority).toContainText(`${api.reviewPriority.score}`)
    await expect(priority).toContainText('of 100')
    await expect(priority).toContainText('Historical relationship')
    await expect(priority).toContainText(/not a probability, not a finding about the claim/)
    await expect(page.getByText('Current', { exact: true })).toBeVisible()

    // The deterministic findings are always there, whatever the model did.
    await expect(page.getByRole('heading', { name: 'Earlier claims relate to this one' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Some expected documents are missing or need review' })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^To review/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: /^Gaps in the evidence/ })).toBeVisible()
    // A hypothesis is labelled as one, or the report says honestly why it has none. Never neither.
    const hypotheses = page.getByRole('heading', { name: /^Hypotheses to verify/ })
    const partial = page.getByText(/This report is partial\./)
    await expect(hypotheses.or(partial)).toBeVisible()
    if (await hypotheses.isVisible()) await expect(page.getByText(/A hypothesis to verify, not a conclusion/).first()).toBeVisible()
    await expect(page.locator('main')).not.toContainText(/fraud|suspicious|guilty/i)
  })

  test('a citation opens the exact evidence item, focused', async ({ page, request }) => {
    const r = await realRun(request)
    await signedIn(page, r, 'report')
    const finding = page.getByRole('heading', { name: 'Earlier claims relate to this one' }).locator('xpath=ancestor::li[1]')
    await expect(finding.getByText('Evidence it rests on')).toBeVisible()
    await finding.getByRole('link', { name: /RULE\.HISTORICAL_RELATIONSHIP/ }).click()
    await expect(page).toHaveURL(/\/evidence#ev-RULE\.HISTORICAL_RELATIONSHIP$/)
    const item = page.locator('[id="ev-RULE.HISTORICAL_RELATIONSHIP"]')
    await expect(item).toBeFocused()
    await expect(item).toContainText('Result of a rule')
  })

  test('the evidence tab lists the frozen snapshot and the relationships, matching the API', async ({ page, request }) => {
    const r = await realRun(request)
    const evidence = await (await request.get(`/api/v1/investigations/${r.investigationId}/evidence`, { headers: { Authorization: `Bearer ${r.token}` } })).json()
    await signedIn(page, r, 'evidence')
    await expect(page.getByRole('heading', { name: 'Evidence', level: 2 })).toBeVisible()
    await expect(page.getByText(`${evidence.itemCount} items, frozen`)).toBeVisible()
    for (const ref of ['CLAIM.stage', 'RULE.DATE_CONSISTENCY', 'REL.1']) await expect(page.locator(`[id="ev-${ref}"]`)).toBeVisible()
    const relations = page.getByRole('list', { name: 'Related claims' })
    await expect(relations).toContainText('CLM-SYN-REPEAT-B')
    await expect(relations).toContainText('CLM-SYN-REPEAT-A')
    await expect(relations).toContainText('Same vehicle')
    await expect(page.getByText('Recorded by the insurer').first()).toBeVisible()
  })

  test('refuses an unreasoned decision before any request, then records one that cannot be edited', async ({ page, request }) => {
    test.setTimeout(120_000)
    const r = await realRun(request)
    await signedIn(page, r, 'report')
    const posts: string[] = []
    page.on('request', (q) => q.method() === 'POST' && q.url().includes('/actions') && posts.push(q.url()))
    await page.getByRole('button', { name: 'Record decision' }).click()
    await expect(page.getByText('Choose what you decided.')).toBeVisible()
    await expect(page.getByText(/Write at least 10 characters/)).toBeVisible()
    expect(posts).toEqual([])

    await page.getByLabel('Request more evidence').check()
    await page.getByLabel('Why').fill('too short')
    await page.getByRole('button', { name: 'Record decision' }).click()
    await expect(page.getByText(/Write at least 10 characters/)).toBeVisible()
    expect(posts).toEqual([])

    await page.getByLabel('Why').fill('The policy record is missing, so I am asking the insurer for it before deciding.')
    await page.getByRole('button', { name: 'Record decision' }).click()
    const dialog = page.getByRole('dialog', { name: 'Record this decision?' })
    await expect(dialog).toContainText('cannot be changed or removed')
    await dialog.getByRole('button', { name: 'Go back' }).click()
    expect(posts).toEqual([])
    await page.getByRole('button', { name: 'Record decision' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Record decision' }).click()

    const list = page.getByRole('list', { name: 'Decisions recorded, oldest first' })
    await expect(list).toContainText('Request more evidence')
    await expect(list).toContainText('The policy record is missing')
    await expect(list).toContainText('On report version 1')
    expect(posts).toHaveLength(1)

    const actions = await (await request.get(`/api/v1/investigations/${r.investigationId}/actions`, { headers: { Authorization: `Bearer ${r.token}` } })).json()
    expect(actions).toHaveLength(1)
    expect(actions[0]).toMatchObject({ action: 'REQUEST_MORE_EVIDENCE', reportVersion: 1, reportWasStale: false })
    // The history shows it, and the form is empty again.
    await expect(page.getByRole('list', { name: 'Investigation history, oldest first' })).toContainText('Review decision recorded')
    await expect(page.getByLabel('Why')).toHaveValue('')
  })

  test('a dropped connection while recording is safe to repeat: the same key, one decision', async ({ page, request }) => {
    test.setTimeout(120_000)
    const r = await realRun(request)
    await signedIn(page, r, 'report')
    const keys: string[] = []
    let dropped = false
    await page.route('**/api/v1/investigations/*/actions', (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      keys.push(route.request().headers()['idempotency-key'] ?? '')
      if (!dropped) {
        dropped = true
        return route.abort('connectionrefused')
      }
      return route.continue()
    })
    const before = (await (await request.get(`/api/v1/investigations/${r.investigationId}/actions`, { headers: { Authorization: `Bearer ${r.token}` } })).json()).length
    await page.getByLabel('Defer the review').check()
    await page.getByLabel('Why').fill('Waiting for the garage to send the revised invoice, then I will look again.')
    await page.getByRole('button', { name: 'Record decision' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Record decision' }).click()
    await expect(page.getByRole('dialog').getByRole('alert')).toContainText('could not be reached')
    await page.getByRole('dialog').getByRole('button', { name: 'Record decision' }).click()
    await expect(page.getByRole('list', { name: 'Decisions recorded, oldest first' })).toContainText('Defer the review')
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBe(keys[1])
    const after = (await (await request.get(`/api/v1/investigations/${r.investigationId}/actions`, { headers: { Authorization: `Bearer ${r.token}` } })).json()).length
    expect(after).toBe(before + 1)
  })

  test('says in words when the report changed under the reader, and when the evidence is out of date (fixtures)', async ({ page, request }) => {
    const r = await realRun(request)
    await signedIn(page, r, 'report')
    await page.route('**/api/v1/investigations/*/actions', (route) =>
      route.request().method() === 'POST' ? route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 'REPORT_VERSION_MISMATCH', status: 409, message: 'x' }) }) : route.continue(),
    )
    await page.getByLabel('Escalate for further investigation').check()
    await page.getByLabel('Why').fill('Two earlier claims on the same vehicle within three months need a closer look.')
    await page.getByRole('button', { name: 'Record decision' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Record decision' }).click()
    await expect(page.getByRole('dialog')).toContainText('A newer report exists than the one you were reading.')

    await page.unroute('**/api/v1/investigations/*/actions')
    await page.route('**/api/v1/claims/*/reports/latest', async (route) => {
      const real = await route.fetch()
      const body = await real.json()
      await route.fulfill({ response: real, json: { ...body, reportStatus: 'STALE', completeness: 'PARTIAL', unavailableSections: ['EVIDENCE_REVIEW', 'DOCUMENT_READING'] } })
    })
    await page.reload()
    await expect(page.getByText('Newer evidence exists than this report was written from.')).toBeVisible()
    await expect(page.getByText('This report is partial.')).toBeVisible()
    await expect(page.getByText('The independent review could not answer, so no explanation was published.')).toBeVisible()
    await expect(page.getByText('This report is out of date.')).toBeVisible()
    await expect(page.getByText('Out of date', { exact: true })).toBeVisible()
  })

  test('another organization cannot read the report or the evidence, and cannot record a decision', async ({ page, request }) => {
    const r = await realRun(request)
    const intruder = await apiToken(request, OTHER_ORGANIZATION, 'rptintruder')
    for (const path of ['report', 'evidence', 'graph', 'findings', 'actions', 'audit']) {
      expect((await request.get(`/api/v1/investigations/${r.investigationId}/${path}`, { headers: { Authorization: `Bearer ${intruder}` }, failOnStatusCode: false })).status(), path).toBe(404)
    }
    expect((await request.get(`/api/v1/claims/${r.claimId}/reports/latest`, { headers: { Authorization: `Bearer ${intruder}` }, failOnStatusCode: false })).status()).toBe(404)
    const decision = await request.post(`/api/v1/investigations/${r.investigationId}/actions`, {
      headers: { Authorization: `Bearer ${intruder}`, 'Idempotency-Key': 'e2e-intruder-decision-1' },
      data: { action: 'DEFER_REVIEW', rationale: 'I should not be able to do this.', reportVersion: 1 },
      failOnStatusCode: false,
    })
    expect(decision.status()).toBe(404)
    const email = await registerUser(request, 'rptspy', OTHER_ORGANIZATION)
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    await page.goto(`/app/claims/${r.claimId}/report`)
    await expect(page.getByRole('alert')).toContainText('We could not find that claim.')
  })
})

test.describe('before there is anything to show (real backend)', () => {
  test('a claim with no investigation has no report and no evidence, and says what to do', async ({ page, request }) => {
    const token = await apiToken(request, 'Org A', 'noreportseed')
    const claim = await createClaimViaApi(request, token, { internalId: uniqueRef('NOREP'), stage: 'FNOL' })
    const email = await registerUser(request, 'noreport', 'Org A')
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    await page.goto(`/app/claims/${claim.id}/report`)
    await expect(page.getByRole('heading', { name: 'There is no report yet' })).toBeVisible()
    await page.getByRole('link', { name: 'Go to the investigation' }).click()
    await expect(page.getByRole('heading', { name: 'No investigation yet' })).toBeVisible()
    await page.goto(`/app/claims/${claim.id}/evidence`)
    await expect(page.getByRole('heading', { name: 'No evidence has been frozen yet' })).toBeVisible()
  })
})

for (const size of [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`report and evidence pass axe and fit the screen at ${size.name}`, async ({ page, request }) => {
    test.setTimeout(240_000)
    await page.setViewportSize({ width: size.width, height: size.height })
    const r = await realRun(request)
    const problems: string[] = []
    page.on('pageerror', (e) => problems.push(e.message))
    await signedIn(page, r, 'report')
    await expect(page.getByRole('heading', { name: 'Report', level: 2 })).toBeVisible()
    for (const [tab, heading] of [
      ['evidence', 'Evidence'],
      ['report', 'Report'],
    ] as const) {
      await page.goto(`/app/claims/${r.claimId}/${tab}`)
      await expect(page.getByRole('heading', { name: heading, level: 2 })).toBeVisible()
      expect(await axeViolations(page), `${tab} at ${size.name}`).toEqual([])
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), `${tab} sideways`).toBeLessThanOrEqual(0)
      await page.screenshot({ path: `e2e/.artifacts/report-${tab}-${size.name}.png`, fullPage: true })
    }
    await page.getByRole('button', { name: 'Record decision' }).click()
    await page.getByLabel('Request more evidence').check()
    await page.getByLabel('Why').fill('Axe sweep: this decision is never submitted.')
    await page.getByRole('button', { name: 'Record decision' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    expect(await axeViolations(page), `dialog at ${size.name}`).toEqual([])
    await page.getByRole('dialog').getByRole('button', { name: 'Go back' }).click()
    expect(problems).toEqual([])
  })
}
