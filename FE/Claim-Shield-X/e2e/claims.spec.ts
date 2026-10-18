import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { EMPTY_ORGANIZATION, OTHER_ORGANIZATION, ORGANIZATION, apiToken, backendUp, claimCount, createClaimViaApi, registerUser, signInThroughUi, uniqueRef } from './support'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy.')
})

async function enter(page: Page, request: APIRequestContext, label: string, organization = ORGANIZATION) {
  const email = await registerUser(request, label, organization)
  await page.goto('/login')
  await signInThroughUi(page, email)
  await expect(page).toHaveURL(/\/app$/)
  return email
}

const axeViolations = async (page: Page) =>
  (await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()).violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)

async function fillForm(page: Page, internalId: string, extra = true) {
  await page.getByLabel('Claim reference').fill(internalId)
  await page.getByLabel('Claim stage').selectOption('FINAL_INVOICE_RECEIVED')
  await page.getByLabel('Policy token').fill('POL-TOKEN-1001')
  await page.getByLabel('Garage id').fill('GARAGE-DEMO-10')
  await page.getByLabel('Vehicle token').fill('VEH-TOKEN-1001')
  await page.getByLabel('Incident date and time').fill('2026-09-10T10:30')
  await page.getByLabel('Incident type').selectOption('COLLISION')
  if (extra) {
    await page.getByLabel(/^Policy type/).fill('COMPREHENSIVE')
    await page.getByLabel(/^Policy start date/).fill('2026-01-01')
    await page.getByLabel(/^Policy end date/).fill('2026-12-31')
    await page.getByLabel(/^Claimant token/).fill('CLT-TOKEN-1001')
    await page.getByLabel(/^Vehicle make/).fill('DEMO_MAKE')
    await page.getByLabel(/^Vehicle model/).fill('DEMO_MODEL')
    await page.getByLabel(/^Reported date and time/).fill('2026-09-11T09:00')
    await page.getByLabel(/^Location precision/).fill('CITY')
    await page.getByLabel(/^Initial estimate/).fill('45000.50')
    await page.getByLabel(/^Deductible/).fill('1000')
  }
}

test.describe('creating a claim (real backend)', () => {
  test('creates a claim and shows it back exactly as the server stored it', async ({ page, request }) => {
    await enter(page, request, 'create')
    await page.goto('/app/claims/new')
    const ref = uniqueRef('CREATE')
    await fillForm(page, ref)
    await page.getByRole('button', { name: 'Create claim' }).click()

    await expect(page).toHaveURL(/\/app\/claims\/[0-9a-f-]{36}$/)
    await expect(page.getByRole('heading', { level: 1, name: ref })).toBeVisible()
    await expect(page.getByText(`Claim ${ref} was created.`)).toBeVisible()
    const record = page.getByRole('heading', { level: 3, name: 'Money' }).locator('..')
    await expect(record).toContainText('₹45,000.50')
    await expect(record).toContainText('₹1,000.00')
    await expect(page.getByText('Approved estimate').locator('xpath=following-sibling::dd[1]')).toHaveText('Not provided')
    await expect(page.getByText('POL-TOKEN-1001')).toBeVisible()
    await expect(page.getByText('CLT-TOKEN-1001')).toBeVisible()
    await expect(page.getByText(/10 Sept?\.? 2026, 10:30/)).toBeVisible()
    await expect(page.getByText(/11 Sept?\.? 2026, 09:00/)).toBeVisible()
  })

  test('sends the contract body and nothing the server controls', async ({ page, request }) => {
    await enter(page, request, 'body')
    await page.goto('/app/claims/new')
    const ref = uniqueRef('BODY')
    await fillForm(page, ref)
    const posted = page.waitForRequest((r) => r.method() === 'POST' && r.url().endsWith('/api/v1/claims'))
    await page.getByRole('button', { name: 'Create claim' }).click()
    const body = (await posted).postDataJSON()
    expect(body).toEqual({
      internalId: ref,
      claimVersion: 1,
      currentStage: 'FINAL_INVOICE_RECEIVED',
      policy: { policyToken: 'POL-TOKEN-1001', policyType: 'COMPREHENSIVE', policyStartDate: '2025-12-31T18:30:00.000Z', policyEndDate: '2026-12-30T18:30:00.000Z' },
      claimant: { claimantToken: 'CLT-TOKEN-1001' },
      garage: { garageId: 'GARAGE-DEMO-10' },
      vehicle: { vehicleToken: 'VEH-TOKEN-1001', make: 'DEMO_MAKE', model: 'DEMO_MODEL' },
      incident: { incidentAt: '2026-09-10T05:00:00.000Z', reportedAt: '2026-09-11T03:30:00.000Z', incidentType: 'COLLISION', permittedLocationPrecision: 'CITY' },
      financial: { currency: 'INR', initialEstimateAmount: 45000.5, deductible: 1000 },
    })
    await expect(page).toHaveURL(/\/app\/claims\/[0-9a-f-]{36}$/)
  })

  test('a minimal claim (required fields only) is accepted by the real server', async ({ page, request }) => {
    await enter(page, request, 'minimal')
    await page.goto('/app/claims/new')
    const ref = uniqueRef('MIN')
    await fillForm(page, ref, false)
    await page.getByRole('button', { name: 'Create claim' }).click()
    await expect(page.getByRole('heading', { level: 1, name: ref })).toBeVisible()
    await expect(page.getByText('Not provided').first()).toBeVisible()
  })

  test('checks the form before asking the server and focuses the summary', async ({ page, request }) => {
    await enter(page, request, 'checks')
    await page.goto('/app/claims/new')
    let posts = 0
    page.on('request', (r) => r.method() === 'POST' && r.url().endsWith('/api/v1/claims') && posts++)
    await page.getByRole('button', { name: 'Create claim' }).click()
    const summary = page.getByRole('alert').filter({ hasText: 'things need fixing' })
    await expect(summary).toBeFocused()
    await expect(summary).toContainText('7 things need fixing')
    await summary.getByRole('link', { name: 'Vehicle token' }).click()
    await expect(page.getByLabel('Vehicle token')).toBeFocused()
    expect(posts).toBe(0)
  })

  test('rejects a money amount with three decimals and sends nothing', async ({ page, request }) => {
    await enter(page, request, 'money')
    await page.goto('/app/claims/new')
    await fillForm(page, uniqueRef('MONEY'), false)
    await page.getByLabel(/^Initial estimate/).fill('45000.505')
    await page.getByRole('button', { name: 'Create claim' }).click()
    await expect(page.getByLabel(/^Initial estimate/)).toHaveAccessibleDescription(/at most two decimals/)
  })

  test('says a duplicate reference is a duplicate, on the reference field', async ({ page, request }) => {
    await enter(page, request, 'dup')
    const token = await apiToken(request, ORGANIZATION, 'dupseed')
    const ref = uniqueRef('DUP')
    await createClaimViaApi(request, token, { internalId: ref })
    await page.goto('/app/claims/new')
    await fillForm(page, ref, false)
    await page.getByRole('button', { name: 'Create claim' }).click()
    await expect(page.getByLabel('Claim reference')).toHaveAccessibleDescription(/already exists in your organization/)
    await expect(page).toHaveURL(/\/app\/claims\/new$/)
    await expect(page.getByLabel('Garage id')).toHaveValue('GARAGE-DEMO-10')
  })

  test('maps the server’s validation answer onto the fields it names', async ({ page, request }) => {
    await enter(page, request, 'server400')
    await page.route('**/api/v1/claims', (route) =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({ code: 'VALIDATION_FAILED', status: 400, message: 'Request validation failed', errors: [{ field: 'vehicle.vehicleToken', message: 'Vehicle token cannot be blank' }] }),
          })
        : route.continue(),
    )
    await page.goto('/app/claims/new')
    await fillForm(page, uniqueRef('S400'), false)
    await page.getByRole('button', { name: 'Create claim' }).click()
    await expect(page.getByLabel('Vehicle token')).toHaveAccessibleDescription(/Vehicle token cannot be blank/)
    await expect(page.getByRole('alert').filter({ hasText: 'thing needs fixing' })).toBeFocused()
  })

  test('survives the network dropping during creation, keeps the input, and warns the claim may exist', async ({ page, request }) => {
    await enter(page, request, 'net')
    await page.route('**/api/v1/claims', (route) => (route.request().method() === 'POST' ? route.abort('connectionrefused') : route.continue()))
    await page.goto('/app/claims/new')
    await fillForm(page, uniqueRef('NET'), false)
    await page.getByRole('button', { name: 'Create claim' }).click()
    await expect(page.getByText('The server could not be reached.')).toBeVisible()
    await expect(page.getByText(/may already have been created/)).toBeVisible()
    await expect(page.getByLabel('Garage id')).toHaveValue('GARAGE-DEMO-10')
  })

  test('creates one claim however many times the button is pressed', async ({ page, request }) => {
    await enter(page, request, 'once')
    await page.goto('/app/claims/new')
    await fillForm(page, uniqueRef('ONCE'), false)
    let posts = 0
    page.on('request', (r) => r.method() === 'POST' && r.url().endsWith('/api/v1/claims') && posts++)
    await page.getByRole('button', { name: 'Create claim' }).dblclick()
    await expect(page).toHaveURL(/\/app\/claims\/[0-9a-f-]{36}$/)
    expect(posts).toBe(1)
  })

  test('submits from the keyboard with Enter', async ({ page, request }) => {
    await enter(page, request, 'enter')
    await page.goto('/app/claims/new')
    const ref = uniqueRef('ENTER')
    await fillForm(page, ref, false)
    await page.getByLabel('Garage id').press('Enter')
    await expect(page.getByRole('heading', { level: 1, name: ref })).toBeVisible()
  })
})

test.describe('the claims list and overview (real backend)', () => {
  test('shows the same total as the API, pages, filters and sorts exactly as the API does, and keeps the view in the address', async ({ page, request }) => {
    const seeder = await apiToken(request, ORGANIZATION, 'listseed')
    const prefix = uniqueRef('LIST')
    for (let i = 0; i < 24; i++) await createClaimViaApi(request, seeder, { internalId: `${prefix}-${String(i).padStart(2, '0')}`, stage: i % 3 === 0 ? 'FNOL' : 'FINAL_INVOICE_RECEIVED' })
    await enter(page, request, 'list')

    const total = await claimCount(request, seeder)
    const pages = Math.ceil(total / 20)
    await page.goto('/app/claims')
    await expect(page.getByText(`Showing 1 to 20 of ${total} claims.`)).toBeVisible()
    await expect(page.getByText(`Page 1 of ${pages}`)).toBeVisible()
    await expect(page.getByRole('row')).toHaveCount(21) // header + 20

    await page.getByRole('button', { name: 'Next' }).click()
    await expect(page).toHaveURL(/page=1/)
    await expect(page.getByText(`Page 2 of ${pages}`)).toBeVisible()
    await page.reload()
    await expect(page.getByText(`Page 2 of ${pages}`)).toBeVisible()
    await page.goBack()
    await expect(page.getByText(`Page 1 of ${pages}`)).toBeVisible()

    const fnol = await claimCount(request, seeder, '&stage=FNOL')
    await page.getByLabel('Claim stage').selectOption('FNOL')
    await expect(page).toHaveURL(/stage=FNOL/)
    await expect(page.getByText(new RegExp(`of ${fnol} claims at "First notice of loss"`))).toBeVisible()
    for (const cell of await page.getByRole('cell', { name: 'First notice of loss' }).all()) await expect(cell).toBeVisible()

    await page.getByLabel('Claim stage').selectOption({ label: 'All stages' })
    await expect(page).not.toHaveURL(/stage=/)
    await page.getByLabel('Sort by').selectOption('internalId,asc')
    const apiFirst = (await (await request.get('/api/v1/claims?size=1&sort=internalId,asc', { headers: { Authorization: `Bearer ${seeder}` } })).json()).items[0].internalId
    await expect(page.getByRole('row').nth(1).getByRole('rowheader')).toHaveText(apiFirst)
    await expect(page.getByRole('columnheader', { name: 'Reference' })).toHaveAttribute('aria-sort', 'ascending')
  })

  test('shows evidence readiness exactly as the backend reports it', async ({ page, request }) => {
    const seeder = await apiToken(request, ORGANIZATION, 'readyseed')
    const ref = uniqueRef('READY')
    await createClaimViaApi(request, seeder, { internalId: ref })
    const apiItem = (await (await request.get('/api/v1/claims?size=100&sort=createdAt,desc', { headers: { Authorization: `Bearer ${seeder}` } })).json()).items.find((c: { internalId: string }) => c.internalId === ref)
    expect(apiItem.readiness.level).toBe('NOT_READY')
    await enter(page, request, 'readiness')
    await page.goto('/app/claims')
    const row = page.getByRole('row').filter({ has: page.getByRole('link', { name: ref }) })
    await expect(row).toContainText('Not ready')
    await expect(row).toContainText(`${apiItem.readiness.blockerCount} required document issue, ${apiItem.readiness.warningCount} warnings`)
  })

  test('returns to the same filtered view after opening a claim', async ({ page, request }) => {
    const seeder = await apiToken(request, ORGANIZATION, 'backseed')
    const ref = uniqueRef('BACK')
    await createClaimViaApi(request, seeder, { internalId: ref, stage: 'POST_SETTLEMENT' })
    await enter(page, request, 'back')
    await page.goto('/app/claims?stage=POST_SETTLEMENT&sort=createdAt,desc')
    await page.getByRole('link', { name: ref }).click()
    await expect(page.getByRole('heading', { level: 1, name: ref })).toBeVisible()
    await page.getByRole('navigation', { name: 'Breadcrumb' }).getByRole('link', { name: 'Claims' }).click()
    await expect(page).toHaveURL(/\/app\/claims\?stage=POST_SETTLEMENT$/)
    await expect(page.getByLabel('Claim stage')).toHaveValue('POST_SETTLEMENT')
  })

  test('the overview counts and lists what the API returns', async ({ page, request }) => {
    const seeder = await apiToken(request, ORGANIZATION, 'ovseed')
    await createClaimViaApi(request, seeder, { internalId: uniqueRef('OV') })
    await enter(page, request, 'overview')
    const total = await claimCount(request, seeder)
    const counts = page.getByLabel('Claims by evidence readiness')
    await expect(counts.getByText('Claims', { exact: true }).locator('xpath=following-sibling::dd[1]')).toHaveText(String(total))
    await expect(page.getByRole('heading', { name: 'Waiting on evidence' })).toBeVisible()
    await expect(page.getByText(/Readiness counts cover/)).toBeVisible()
  })

  test('an organization with no claims sees an invitation, not an empty table', async ({ page, request }) => {
    await enter(page, request, 'empty', EMPTY_ORGANIZATION)
    await page.goto('/app/claims')
    await expect(page.getByRole('heading', { name: 'No claims yet' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Create the first claim' })).toBeVisible()
    await expect(page.getByRole('table')).toHaveCount(0)
    await page.goto('/app')
    await expect(page.getByRole('heading', { name: 'No claims yet' })).toBeVisible()
  })

  test('a claim list request carries the bearer token and nothing identifying the organization', async ({ page, request }) => {
    await enter(page, request, 'headers')
    const req = page.waitForRequest((r) => r.url().includes('/api/v1/claims?') && r.method() === 'GET')
    await page.goto('/app/claims')
    const r = await req
    expect(r.headers().authorization).toMatch(/^Bearer /)
    expect(r.url()).not.toMatch(/organi[sz]ation/i)
    expect(Object.keys(r.headers()).join(' ')).not.toMatch(/organi[sz]ation|tenant/i)
  })
})

test.describe('tenants and sessions in the browser (real backend)', () => {
  test('another organization cannot see a claim, and a missing claim looks the same', async ({ page, request }) => {
    const seeder = await apiToken(request, ORGANIZATION, 'tenantseed')
    const secret = await createClaimViaApi(request, seeder, { internalId: uniqueRef('SECRET') })
    await enter(page, request, 'orgb', OTHER_ORGANIZATION)

    await page.goto('/app/claims')
    await expect(page.getByRole('heading', { level: 1, name: 'Claims' })).toBeVisible()
    // Let the list finish loading: leaving a page while its session renewal is in flight would strand the session, which is not what this test is about.
    await page.waitForLoadState('networkidle')
    await expect(page.getByText(secret.internalId)).toHaveCount(0)

    await page.goto(`/app/claims/${secret.id}`)
    await expect(page.getByRole('alert')).toContainText('We could not find that claim.')
    const foreign = await page.getByRole('alert').innerText()
    await page.goto('/app/claims/00000000-0000-4000-8000-000000000000')
    await expect(page.getByRole('alert')).toContainText('We could not find that claim.')
    expect(await page.getByRole('alert').innerText()).toBe(foreign)
    await expect(page.getByText(secret.internalId)).toHaveCount(0)
  })

  test('another person signing in on the same browser never sees the last person’s claims, even for an instant', async ({ page, request }) => {
    const seeder = await apiToken(request, ORGANIZATION, 'cacheseed')
    const mine = await createClaimViaApi(request, seeder, { internalId: uniqueRef('CACHE') })
    await enter(page, request, 'cacheA')
    await page.goto(`/app/claims`)
    // Wait for the list before loading the next page: leaving mid-way through a session renewal would strand the session.
    await expect(page.getByRole('heading', { level: 1, name: 'Claims' })).toBeVisible()
    await page.waitForLoadState('networkidle')
    await page.goto(`/app/claims/${mine.id}`)
    await expect(page.getByRole('heading', { level: 1, name: mine.internalId })).toBeVisible()
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page).toHaveURL(/\/login$/)

    // Hold the next person's claim requests back so any leftover cache would have time to show.
    await page.route('**/api/v1/claims**', async (route) => {
      await new Promise((r) => setTimeout(r, 800))
      await route.continue()
    })
    const emailB = await registerUser(request, 'cacheB', OTHER_ORGANIZATION)
    await signInThroughUi(page, emailB)
    await expect(page).toHaveURL(/\/app$/)
    await page.goto(`/app/claims/${mine.id}`)
    await expect(page.getByText(mine.internalId)).toHaveCount(0)
    await expect(page.getByText('We could not find that claim.')).toBeVisible()
  })

  test('an expired access token is renewed once, invisibly, and the list still loads', async ({ page, request }) => {
    await enter(page, request, 'expiry')
    // An expired token is refused until a renewal has really happened, however many list requests are in flight
    // (React StrictMode in development starts the first one twice and cancels one).
    const refreshes: string[] = []
    page.on('request', (r) => r.url().endsWith('/api/v1/auth/refresh') && refreshes.push(r.url()))
    let refused = 0
    await page.route('**/api/v1/claims?*size=20*', async (route) => {
      if (refreshes.length === 0 && route.request().method() === 'GET') {
        refused++
        await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'TOKEN_EXPIRED', status: 401, error: 'Unauthorized', message: 'Access token has expired' }) })
      } else {
        await route.continue()
      }
    })
    await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Claims' }).click()
    await expect(page.getByRole('heading', { level: 1, name: 'Claims' })).toBeVisible()
    await expect(page.getByRole('table')).toBeVisible()
    expect(refused).toBeGreaterThan(0)
    expect(refreshes).toHaveLength(1)
    await expect(page).toHaveURL(/\/app\/claims$/)
  })

  test('when the session cannot be renewed the person is sent to sign in, told why, and returned to the same view', async ({ page, request }) => {
    const email = await enter(page, request, 'lost')
    await page.route('**/api/v1/claims?*', (route) =>
      route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'TOKEN_EXPIRED', status: 401, error: 'Unauthorized', message: 'Access token has expired' }) }),
    )
    await page.route('**/api/v1/auth/refresh', (route) =>
      route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'REFRESH_TOKEN_EXPIRED', status: 401, error: 'Unauthorized', message: 'Please log in again' }) }),
    )
    await page.goto('/app/claims?stage=FNOL')
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText('Your session has ended.')).toBeVisible()
    await page.unroute('**/api/v1/claims?*')
    await page.unroute('**/api/v1/auth/refresh')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app\/claims\?stage=FNOL$/)
  })
})

test.describe('quality of the new screens', () => {
  const sizes = [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'mobile', width: 390, height: 844 },
  ]

  for (const size of sizes) {
    test(`overview, list, form, detail and states pass axe, have no console errors and keep to the screen at ${size.name}`, async ({ page, request }) => {
      test.setTimeout(120_000)
      const seeder = await apiToken(request, ORGANIZATION, 'axeseed')
      const claim = await createClaimViaApi(request, seeder, { internalId: uniqueRef('AXE') })
      const problems: string[] = []
      page.on('console', (m) => m.type() === 'error' && problems.push(m.text()))
      page.on('pageerror', (e) => problems.push(e.message))
      await page.setViewportSize({ width: size.width, height: size.height })
      await enter(page, request, `axe${size.name}`)

      const views: Array<[string, string, () => Promise<void>]> = [
        ['overview', '/app', async () => expect(page.getByRole('heading', { name: 'Waiting on evidence' })).toBeVisible()],
        ['list', '/app/claims', async () => expect(page.getByRole('table')).toBeVisible()],
        ['new', '/app/claims/new', async () => expect(page.getByRole('heading', { level: 1, name: 'Create claim' })).toBeVisible()],
        ['detail', `/app/claims/${claim.id}`, async () => expect(page.getByRole('heading', { level: 1, name: claim.internalId })).toBeVisible()],
      ]
      for (const [name, url, ready] of views) {
        await page.goto(url)
        await ready()
        expect(await axeViolations(page), `${name} at ${size.name}`).toEqual([])
        expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), `${name} sideways scroll`).toBeLessThanOrEqual(0)
        await page.screenshot({ path: `e2e/.artifacts/claims-${name}-${size.name}.png`, fullPage: true })
      }

      await page.goto('/app/claims/new')
      await page.getByRole('button', { name: 'Create claim' }).click()
      await expect(page.getByRole('alert').filter({ hasText: 'things need fixing' })).toBeVisible()
      expect(await axeViolations(page), `form errors at ${size.name}`).toEqual([])
      await page.screenshot({ path: `e2e/.artifacts/claims-new-errors-${size.name}.png`, fullPage: true })

      await page.goto('/app/claims/00000000-0000-4000-8000-000000000000')
      await expect(page.getByRole('alert')).toContainText('We could not find that claim.')
      expect(await axeViolations(page), `not found at ${size.name}`).toEqual([])

      // Browser-reported resource errors for the deliberate 404 above are expected; nothing else should be logged.
      expect(problems.filter((p) => !/status of 404|Failed to load resource/.test(p))).toEqual([])
    })
  }

  test('stays within the screen at 320px on every new page', async ({ page, request }) => {
    const seeder = await apiToken(request, ORGANIZATION, 'narrowseed')
    const claim = await createClaimViaApi(request, seeder, { internalId: uniqueRef('NARROW') })
    await page.setViewportSize({ width: 320, height: 800 })
    await enter(page, request, 'narrow')
    for (const url of ['/app', '/app/claims', '/app/claims/new', `/app/claims/${claim.id}`]) {
      await page.goto(url)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), url).toBeLessThanOrEqual(0)
    }
  })
})
