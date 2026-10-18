import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { IMPORT_ADMIN_EMAIL, ORGANIZATION, adminToken, backendUp, claimBody, claimsCsv, registerUser, signInThroughUi, uniqueRef } from './support'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']
const axeViolations = async (page: Page) =>
  (await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()).violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy.')
})

async function signInAsAdmin(page: Page, request: APIRequestContext) {
  await adminToken(request) // fails with a clear message when e2e/seed-history.sh was not run
  await page.goto('/login')
  await signInThroughUi(page, IMPORT_ADMIN_EMAIL)
  await expect(page).toHaveURL(/\/app$/)
  await page.goto('/app/imports')
  await expect(page.getByRole('heading', { name: 'History imports', level: 1 })).toBeVisible()
}

const csv = (name: string, buffer: Buffer) => ({ name, mimeType: 'text/csv', buffer })

async function send(page: Page, file: { name: string; mimeType: string; buffer: Buffer }, opts: { type?: string; source?: string } = {}) {
  if (opts.type) await page.getByLabel('What the file holds').selectOption(opts.type)
  await page.getByLabel('File', { exact: true }).setInputFiles(file)
  if (opts.source) await page.getByLabel(/^Source label/).fill(opts.source)
  await page.getByRole('button', { name: 'Import the file' }).click()
}

test.describe('who can import', () => {
  test('an account that is not an administrator is told so, and is shown no form', async ({ page, request }) => {
    const email = await registerUser(request, 'notadmin', ORGANIZATION)
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'History imports' }).click()
    await expect(page.getByRole('heading', { name: 'Only an organization administrator manages history imports' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Import the file' })).toHaveCount(0)
    await expect(page.locator('main')).not.toContainText('FORBIDDEN')
  })
})

test.describe('importing (real backend, administrator)', () => {
  test('the template for each kind downloads with the exact column names', async ({ page, request }) => {
    await signInAsAdmin(page, request)
    for (const [type, label, file, firstColumn] of [
      ['HISTORICAL_CLAIMS', 'earlier claims', 'historical-claims-template-v1.csv', 'source_record_id,external_claim_id'],
      ['GARAGE_HISTORY', 'garage history', 'garage-history-template-v1.csv', 'source_record_id,garage_id'],
    ] as const) {
      await page.getByLabel('What the file holds').selectOption(type)
      const [download] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: `Download the ${label} template` }).click()])
      expect(download.suggestedFilename()).toBe(file)
      const stream = await download.createReadStream()
      let text = ''
      for await (const chunk of stream) text += chunk
      expect(text.startsWith(firstColumn)).toBe(true)
    }
  })

  test('a valid file is stored, listed, and its rows can be read', async ({ page, request }) => {
    await signInAsAdmin(page, request)
    const a = uniqueRef('IMPA')
    const b = uniqueRef('IMPB')
    await send(page, csv('two-claims.csv', claimsCsv([{ id: a, claim: `CLM-${a}`, vehicle: `VEH-${a}` }, { id: b, claim: `CLM-${b}`, vehicle: `VEH-${b}` }])), { source: 'E2E_VALID' })
    await expect(page.getByText('2 rows stored.')).toBeVisible()
    await expect(page.getByText('Claims you open from now on are compared with this history.')).toBeVisible()

    const token = await adminToken(request)
    const list = await (await request.get('/api/v1/historical-imports', { headers: { Authorization: `Bearer ${token}` } })).json()
    const record = list.items[0]
    expect(record).toMatchObject({ source: 'E2E_VALID', status: 'COMPLETED', counts: { totalRows: 2, accepted: 2, rejected: 0, skippedDuplicate: 0 } })

    const table = page.getByRole('table', { name: 'Imports of your organization, newest first' })
    await expect(table.getByRole('row').nth(1)).toContainText('E2E_VALID')
    await expect(table.getByRole('row').nth(1)).toContainText('Stored')
    await expect(table.getByRole('row').nth(1)).toContainText('2 stored · 0 rejected · 0 already stored')

    await page.getByRole('link', { name: 'See every row' }).click()
    await expect(page).toHaveURL(new RegExp(`/app/imports/${record.id}$`))
    await expect(page.getByRole('heading', { name: 'Earlier claims', level: 1 })).toBeVisible()
    const rows = page.getByRole('table', { name: 'Rows of this import' })
    await expect(rows.getByRole('row')).toHaveCount(3)
    await expect(rows).toContainText(a)
    await expect(page.getByRole('group', { name: 'Row counts' }).or(page.getByLabel('Row counts'))).toContainText('Rows in the file')
  })

  test('rejected rows come with the reason for each, can be filtered, and the valid ones are kept', async ({ page, request }) => {
    await signInAsAdmin(page, request)
    const ok = uniqueRef('IMPOK')
    const bad = uniqueRef('IMPBAD')
    const worse = uniqueRef('IMPWORSE')
    const buffer = claimsCsv(
      [{ id: ok, claim: `CLM-${ok}`, vehicle: `VEH-${ok}` }],
      [
        `${bad},CLM-${bad},1,,POL-X,,G1,not-a-date,2026-06-02,COLLISION,CLOSED_PAID,,1.00,1.00,INR,x,FRONT_BUMPER,2026-06-10`,
        `${worse},CLM-${worse},1,VEH-${worse},POL-X,,G1,2026-06-01,2026-06-02,SPACESHIP,CLOSED_PAID,,1.00,1.00,INR,x,FRONT_BUMPER,2026-06-10`,
      ],
    )
    await send(page, csv('mixed.csv', buffer), { source: 'E2E_MIXED' })
    await expect(page.getByText('1 row stored, 2 rows rejected.')).toBeVisible()
    await expect(page.getByText(/rows that were valid are kept/)).toBeVisible()

    await page.getByRole('link', { name: 'See the rejected rows' }).click()
    await expect(page).toHaveURL(/status=REJECTED/)
    const rows = page.getByRole('table', { name: 'Rows of this import' })
    await expect(rows.getByRole('row')).toHaveCount(3)
    await expect(rows).toContainText('vehicle_token')
    await expect(rows).toContainText('Required value missing')
    await expect(rows).toContainText('incident_at')
    await expect(rows).toContainText('Not a valid date')
    await expect(rows).toContainText('claim_type')
    await expect(rows).toContainText('Not an allowed value')
    await expect(page.getByRole('button', { name: 'Rejected' })).toHaveAttribute('aria-pressed', 'true')
    // The message never repeats the bad value, and the page shows it as text.
    await expect(rows).not.toContainText('not-a-date')
    await expect(rows).not.toContainText('SPACESHIP')

    await page.getByRole('button', { name: 'Stored', exact: true }).click()
    await expect(page).toHaveURL(/status=ACCEPTED/)
    await expect(rows.getByRole('row')).toHaveCount(2)
    await expect(rows).toContainText(ok)
    await page.getByRole('button', { name: 'All', exact: true }).click()
    await expect(rows.getByRole('row')).toHaveCount(4)

    const token = await adminToken(request)
    const list = await (await request.get('/api/v1/historical-imports', { headers: { Authorization: `Bearer ${token}` } })).json()
    expect(list.items[0]).toMatchObject({ status: 'COMPLETED_WITH_ERRORS', counts: { totalRows: 3, accepted: 1, rejected: 2 } })
  })

  test('sending the same file again changes nothing, and says so', async ({ page, request }) => {
    await signInAsAdmin(page, request)
    const a = uniqueRef('IMPDUP')
    const buffer = claimsCsv([{ id: a, claim: `CLM-${a}`, vehicle: `VEH-${a}` }])
    await send(page, csv('once.csv', buffer))
    await expect(page.getByText('1 row stored.')).toBeVisible()
    await page.getByRole('button', { name: 'Add another file' }).click()
    await send(page, csv('twice.csv', buffer))
    await expect(page.getByText('Nothing new was stored.')).toBeVisible()
    await expect(page.getByText(/1 row had already been stored and was left as it is/)).toBeVisible()
  })

  test('a file the server cannot use is a result with a reason, and nothing is stored', async ({ page, request }) => {
    await signInAsAdmin(page, request)
    await send(page, csv('wrong-columns.csv', Buffer.from('a,b\n1,2\n')))
    await expect(page.getByText('Nothing was stored.', { exact: true })).toBeVisible()
    await expect(page.getByText(/The first row does not match the template/)).toBeVisible()
    await expect(page.getByText(/Missing required columns: source_record_id/)).toBeVisible()
    const table = page.getByRole('table', { name: 'Imports of your organization, newest first' })
    await expect(table.getByRole('row').nth(1)).toContainText('Nothing stored')
    await expect(table.getByRole('row').nth(1)).toContainText('None')
  })

  test('refuses an unsuitable file before any request is made', async ({ page, request }) => {
    await signInAsAdmin(page, request)
    const posts: string[] = []
    page.on('request', (r) => r.method() === 'POST' && r.url().includes('/historical-imports') && posts.push(r.url()))
    await page.getByRole('button', { name: 'Import the file' }).click()
    await expect(page.getByText('Choose a CSV file to send.')).toBeVisible()

    await page.getByLabel('File', { exact: true }).setInputFiles({ name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('x') })
    await expect(page.getByText(/Choose a \.csv file/)).toBeVisible()
    await page.getByRole('button', { name: 'Import the file' }).click()

    await page.getByLabel('File', { exact: true }).setInputFiles({ name: 'empty.csv', mimeType: 'text/csv', buffer: Buffer.alloc(0) })
    await expect(page.getByText('That file is empty.')).toBeVisible()

    await page.getByLabel('File', { exact: true }).setInputFiles({ name: 'big.csv', mimeType: 'text/csv', buffer: Buffer.alloc(5 * 1024 * 1024 + 1, 65) })
    await expect(page.getByText(/Imports can be at most 5\.0 MB/)).toBeVisible()
    await page.getByRole('button', { name: 'Import the file' }).click()

    await page.getByLabel('File', { exact: true }).setInputFiles(csv('ok.csv', claimsCsv([{ id: 'X', claim: 'CLM-X', vehicle: 'VEH-X' }])))
    await page.getByLabel(/^Source label/).fill('bad label with spaces')
    await expect(page.getByText(/Use 1 to 100 letters, digits or/)).toBeVisible()
    await page.getByRole('button', { name: 'Import the file' }).click()
    expect(posts).toEqual([])
  })

  test('a dropped connection is safe to repeat: the same key, and one import', async ({ page, request }) => {
    await signInAsAdmin(page, request)
    const a = uniqueRef('IMPKEY')
    const keys: string[] = []
    let dropped = false
    await page.route('**/api/v1/historical-imports', (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      keys.push(route.request().headers()['idempotency-key'] ?? '')
      if (!dropped) {
        dropped = true
        return route.abort('connectionrefused')
      }
      return route.continue()
    })
    const token = await adminToken(request)
    const before = (await (await request.get('/api/v1/historical-imports', { headers: { Authorization: `Bearer ${token}` } })).json()).totalElements
    await send(page, csv('retry.csv', claimsCsv([{ id: a, claim: `CLM-${a}`, vehicle: `VEH-${a}` }])))
    await expect(page.getByText('The server could not be reached.')).toBeVisible()
    await page.getByRole('button', { name: 'Import the file' }).click()
    await expect(page.getByText('1 row stored.')).toBeVisible()
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBe(keys[1])
    expect((await (await request.get('/api/v1/historical-imports', { headers: { Authorization: `Bearer ${token}` } })).json()).totalElements).toBe(before + 1)
  })

  test('what was imported shows up on a claim, on its Earlier claims tab', async ({ page, request }) => {
    test.setTimeout(120_000)
    const token = await adminToken(request)
    const vehicle = `VEH-${uniqueRef('LINK')}`
    // The claim helper uses a fixed vehicle token, so a claim with this test's own token is made directly.
    const body = claimBody({ internalId: uniqueRef('LINKED'), stage: 'FNOL' })
    const own = await request.post('/api/v1/claims', { headers: { Authorization: `Bearer ${token}` }, data: { ...body, vehicle: { ...body.vehicle, vehicleToken: vehicle } } })
    expect(own.status()).toBe(201)
    const ownClaim = await own.json()

    await signInAsAdmin(page, request)
    await page.goto(`/app/claims/${ownClaim.id}/history`)
    await expect(page.getByText(/No earlier claim in the imported history relates to this one|No historical claims have been imported/)).toBeVisible()

    const earlier = uniqueRef('EARLIER')
    await page.goto('/app/imports')
    await send(page, csv('earlier.csv', claimsCsv([{ id: earlier, claim: `CLM-${earlier}`, vehicle, incidentAt: '2026-06-01' }])))
    await expect(page.getByText('1 row stored.')).toBeVisible()

    await page.goto(`/app/claims/${ownClaim.id}/history`)
    const list = page.getByRole('list', { name: 'Earlier claims that relate to this one' })
    await expect(list).toContainText(`CLM-${earlier}`)
    await expect(list).toContainText('Same vehicle')
    await expect(list).toContainText('Exact link')
  })
})

for (const size of [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]) {
  test(`the import screens pass axe and fit the screen at ${size.name}`, async ({ page, request }) => {
    test.setTimeout(120_000)
    await page.setViewportSize({ width: size.width, height: size.height })
    const problems: string[] = []
    page.on('pageerror', (e) => problems.push(e.message))
    await signInAsAdmin(page, request)
    const check = async (state: string) => {
      expect(await axeViolations(page), `${state} at ${size.name}`).toEqual([])
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), `${state} sideways`).toBeLessThanOrEqual(0)
    }
    await check('form')
    await page.screenshot({ path: `e2e/.artifacts/imports-form-${size.name}.png`, fullPage: true })

    const ok = uniqueRef('AXEOK')
    const bad = uniqueRef('AXEBAD')
    await send(page, csv('axe.csv', claimsCsv([{ id: ok, claim: `CLM-${ok}`, vehicle: `VEH-${ok}` }], [`${bad},CLM-${bad},1,,POL-X,,G1,not-a-date,2026-06-02,COLLISION,CLOSED_PAID,,1.00,1.00,INR,x,FRONT_BUMPER,2026-06-10`])))
    await expect(page.getByText('1 row stored, 1 row rejected.')).toBeVisible()
    await check('result')
    await page.screenshot({ path: `e2e/.artifacts/imports-result-${size.name}.png`, fullPage: true })

    await page.getByRole('link', { name: 'See the rejected rows' }).click()
    await expect(page.getByRole('table', { name: 'Rows of this import' })).toBeVisible()
    await check('rows')
    await page.screenshot({ path: `e2e/.artifacts/imports-rows-${size.name}.png`, fullPage: true })
    expect(problems).toEqual([])
  })
}
