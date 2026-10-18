import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { OTHER_ORGANIZATION, ORGANIZATION, apiToken, backendUp, createClaimViaApi, registerUser, signInThroughUi, uniqueRef } from './support'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy.')
})

const axeViolations = async (page: Page) =>
  (await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()).violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)

async function open(page: Page, request: APIRequestContext, label: string, tab: 'checks' | 'history') {
  const token = await apiToken(request, ORGANIZATION, `${label}seed`)
  const claim = await createClaimViaApi(request, token, { internalId: uniqueRef(label.toUpperCase()), stage: 'FNOL' })
  const email = await registerUser(request, label, ORGANIZATION)
  await page.goto('/login')
  await signInThroughUi(page, email)
  await expect(page).toHaveURL(/\/app$/)
  await page.goto(`/app/claims/${claim.id}/${tab}`)
  return { token, claim }
}

const RUN_ID = 'bbbbbbbb-0000-4000-8000-000000000009'
const flaggedRun = (claimId: string) => ({
  id: RUN_ID,
  claimId,
  ruleSetVersion: '1',
  createdAt: new Date().toISOString(),
  counts: { pass: 1, flag: 1, incomplete: 0, notApplicable: 0, error: 0 },
  inputFingerprint: 'x',
  reusedExistingRun: false,
  results: [
    { id: 'r1', ruleId: 'DATE_CONSISTENCY', label: 'Date consistency', outcome: 'PASS', reliability: 'RELIABLE', reliabilityReasons: [], explanation: '3 date comparisons are consistent.', values: { timeZone: 'Asia/Kolkata' }, evidence: [], considerations: [] },
    {
      id: 'r2',
      ruleId: 'ESTIMATE_INVOICE_VARIANCE',
      label: 'Estimate-to-invoice variance',
      outcome: 'FLAG',
      severity: 'REVIEW',
      reliability: 'RELIABLE',
      reliabilityReasons: [],
      explanation: 'The final invoice total of INR 62510.00 is above the estimate of INR 45000.50.',
      values: { invoiceTotal: '62510.00', comparisons: [{ basis: 'CLAIM_INITIAL_ESTIMATE', differencePercent: '38.90' }] },
      evidence: [
        { kind: 'DOCUMENT_FIELD', documentId: 'dddddddd-0000-4000-8000-000000000001', field: 'grandTotal', page: 1, quote: 'Grand total 62,510.00' },
        { kind: 'CLAIM_FIELD', field: 'financial.initialEstimateAmount' },
      ],
      considerations: ['Supplementary damage or repairs found after the estimate was made.'],
    },
  ],
})

test.describe('checks (real backend)', () => {
  test('starts empty, runs the real rules once, and says nothing changed when run again', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'run', 'checks')
    await expect(page.getByRole('heading', { name: 'The checks have not been run yet' })).toBeVisible()
    await page.getByRole('button', { name: 'Run the checks' }).click()
    await expect(page.getByRole('heading', { name: 'Checks on this claim' })).toBeVisible()

    const api = await (await request.get(`/api/v1/claims/${claim.id}/rule-runs/latest`, { headers: { Authorization: `Bearer ${token}` } })).json()
    expect(api.results).toHaveLength(8)
    const list = page.getByRole('list', { name: 'Rule results' })
    await expect(list.getByRole('heading', { level: 3 })).toHaveCount(8)
    const counts = page.getByRole('list', { name: 'Results by outcome' })
    await expect(counts).toContainText(`${api.counts.pass} passed`)
    await expect(counts).toContainText(`${api.counts.incomplete} incomplete`)
    await expect(counts).toContainText(`${api.counts.notApplicable} not applicable`)
    // The real explanation text is shown as the server wrote it.
    for (const r of api.results.slice(0, 3)) await expect(list).toContainText(r.explanation)
    // A missing claim form is incomplete evidence, never a finding.
    await expect(list.getByRole('heading', { name: 'Required evidence' }).locator('xpath=..').locator('xpath=..')).toContainText('Incomplete')

    await page.getByRole('button', { name: 'Check again' }).click()
    await expect(page.getByText('Nothing has changed since this run.')).toBeVisible()
    const runs = await (await request.get(`/api/v1/claims/${claim.id}/rule-runs`, { headers: { Authorization: `Bearer ${token}` } })).json()
    expect(runs.totalElements).toBe(1)
  })

  test('a changed claim gives a new run, and the Checks tab survives a reload', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'change', 'checks')
    await page.getByRole('button', { name: 'Run the checks' }).click()
    await expect(page.getByRole('heading', { name: 'Checks on this claim' })).toBeVisible()
    const form = await request.post(`/api/v1/claims/${claim.id}/documents`, {
      headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': `e2e-chg-${Date.now()}` },
      multipart: { documentType: 'CLAIM_FORM', file: { name: 'synthetic-estimate.pdf', mimeType: 'application/pdf', buffer: (await import('./support')).fixtureBytes('synthetic-estimate.pdf') } },
    })
    expect(form.status()).toBe(201)
    await page.getByRole('button', { name: 'Check again' }).click()
    await expect(page.getByText('The claim changed, so these checks were run again.')).toBeVisible()
    expect((await (await request.get(`/api/v1/claims/${claim.id}/rule-runs`, { headers: { Authorization: `Bearer ${token}` } })).json()).totalElements).toBe(2)
    await page.reload()
    await expect(page.getByRole('heading', { name: 'Checks on this claim' })).toBeVisible()
  })

  test('shows a flagged result with its other explanations and its sources (served from a fixture)', async ({ page, request }) => {
    const { claim } = await open(page, request, 'flag', 'checks')
    await page.route('**/api/v1/claims/*/rule-runs/latest', (route) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(flaggedRun(claim.id)) }))
    await page.reload()
    const cards = page.getByRole('list', { name: 'Rule results' }).getByRole('listitem')
    await expect(cards.first()).toContainText('Estimate-to-invoice variance')
    await expect(cards.first()).toContainText('Flag · review')
    await expect(cards.first()).toContainText('Other explanations to rule out')
    await expect(cards.first()).toContainText('Supplementary damage or repairs found after the estimate was made.')
    await expect(cards.first().getByRole('button', { name: /Grand total/ })).toBeVisible()
    await expect(page.locator('main')).not.toContainText(/fraud/i)
    await cards.first().getByRole('button', { name: /Grand total/ }).click()
    await expect(page).toHaveURL(new RegExp(`/app/claims/${claim.id}/documents$`))
  })

  test('another organization cannot see the claim, so it cannot see its checks', async ({ page, request }) => {
    const owner = await apiToken(request, ORGANIZATION, 'chkowner')
    const claim = await createClaimViaApi(request, owner, { internalId: uniqueRef('CHKSEC'), stage: 'FNOL' })
    await request.post(`/api/v1/claims/${claim.id}/rule-runs`, { headers: { Authorization: `Bearer ${owner}` } })
    const intruder = await apiToken(request, OTHER_ORGANIZATION, 'chkintruder')
    expect((await request.get(`/api/v1/claims/${claim.id}/rule-runs/latest`, { headers: { Authorization: `Bearer ${intruder}` } })).status()).toBe(404)
    const email = await registerUser(request, 'chkspy', OTHER_ORGANIZATION)
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    await page.goto(`/app/claims/${claim.id}/checks`)
    await expect(page.getByRole('alert')).toContainText('We could not find that claim.')
  })
})

test.describe('earlier claims (real backend)', () => {
  test('says honestly that no history has been imported', async ({ page, request }) => {
    await open(page, request, 'hist', 'history')
    await expect(page.getByText('No historical claims have been imported, so nothing could be compared.')).toBeVisible()
    await expect(page.getByText('This is not the same as finding no match.', { exact: true })).toBeVisible()
    await expect(page.getByText(/context only and never evidence of wrongdoing/)).toBeVisible()
  })
})

for (const size of [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`checks and earlier claims pass axe and fit the screen at ${size.name}`, async ({ page, request }) => {
    test.setTimeout(120_000)
    await page.setViewportSize({ width: size.width, height: size.height })
    const { claim } = await open(page, request, `axe${size.name}`, 'checks')
    const problems: string[] = []
    page.on('pageerror', (e) => problems.push(e.message))
    const check = async (state: string) => {
      expect(await axeViolations(page), `${state} at ${size.name}`).toEqual([])
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), `${state} sideways`).toBeLessThanOrEqual(0)
    }
    const shot = (n: string) => page.screenshot({ path: `e2e/.artifacts/checks-${n}-${size.name}.png`, fullPage: true })

    await expect(page.getByRole('heading', { name: 'The checks have not been run yet' })).toBeVisible()
    await check('empty')
    await shot('empty')
    await page.getByRole('button', { name: 'Run the checks' }).click()
    await expect(page.getByRole('heading', { name: 'Checks on this claim' })).toBeVisible()
    await check('real run')
    await shot('real-run')

    await page.route('**/api/v1/claims/*/rule-runs/latest', (route) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(flaggedRun(claim.id)) }))
    await page.reload()
    await expect(page.getByText('Flag · review')).toBeVisible()
    await check('flag')
    await shot('flag')

    await page.route('**/api/v1/claims/*/history-matches', (route) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          claimId: claim.id,
          status: 'MATCHES_FOUND',
          dataAvailability: { historicalClaimRows: 232, garageHistoryRows: 57, latestClaimsImportFailed: false },
          totalPriorClaims: 2,
          truncated: false,
          matches: [
            { externalClaimId: 'CLM-SYN-REPEAT-B', relation: 'SAME_VEHICLE', strength: 'EXACT', matchedFields: ['vehicleToken', 'garageId'], timing: 'EARLIER', daysBetween: 45, claimType: 'COLLISION', claimStatus: 'CLOSED_PAID', garageId: 'GARAGE-SYN-03', incidentAt: '2026-08-01T00:00:00Z', ambiguities: ['OUTCOME_NOT_RECORDED'], explanation: 'Another claim in the imported history has the same vehicle token and is dated 45 day(s) before this one.' },
            { externalClaimId: 'CLM-SYN-OLD', relation: 'SAME_POLICY', strength: 'CONTEXT', matchedFields: ['policyToken'], timing: 'SAME_DAY', daysBetween: 0, ambiguities: ['WEAK_TOKEN_ONLY', 'ORDER_UNKNOWN_SAME_DAY'], explanation: 'Shares the policy token only.' },
          ],
          garage: { garageId: 'GARAGE-SYN-03', importedHistoricalClaims: 13, caution: 'A garage volume is context only.' },
          notes: ['Each earlier claim is listed once.'],
        }),
      }),
    )
    await page.goto(`/app/claims/${claim.id}/history`)
    await expect(page.getByRole('heading', { name: 'CLM-SYN-REPEAT-B' })).toBeVisible()
    await check('matches')
    await shot('matches')
    expect(problems).toEqual([])
  })
}
