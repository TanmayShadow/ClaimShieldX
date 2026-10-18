import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { APIRequestContext, Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { OTHER_ORGANIZATION, ORGANIZATION, apiToken, backendUp, bigPdf, createClaimViaApi, fixture, fixtureBytes, listDocumentsViaApi, registerUser, sha256, signInThroughUi, uniqueRef, uploadFromRow } from './support'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy.')
})

type Setup = { token: string; claim: { id: string; internalId: string }; email: string }

/** A signed-in browser, and a fresh claim at the final-invoice stage with no documents. */
async function open(page: Page, request: APIRequestContext, label: string, organization = ORGANIZATION, stage = 'FINAL_INVOICE_RECEIVED'): Promise<Setup> {
  const token = await apiToken(request, organization, `${label}seed`)
  const claim = await createClaimViaApi(request, token, { internalId: uniqueRef(label.toUpperCase()), stage })
  const email = await registerUser(request, label, organization)
  await page.goto('/login')
  await signInThroughUi(page, email)
  await expect(page).toHaveURL(/\/app$/)
  await page.goto(`/app/claims/${claim.id}/documents`)
  await expect(page.getByRole('table', { name: 'Documents this claim stage expects' })).toBeVisible()
  return { token, claim, email }
}

const axeViolations = async (page: Page) =>
  (await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()).violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)

const upload = uploadFromRow

test.describe('checklist and upload (real backend)', () => {
  test('shows the checklist the backend computes, and an uploaded invoice moves readiness and the list', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'check')
    const checklist = page.getByRole('table', { name: 'Documents this claim stage expects' })
    const invoiceRow = checklist.getByRole('rowheader', { name: 'Final invoice' }).locator('xpath=..')
    await expect(invoiceRow).toContainText('Needed to proceed')
    await expect(invoiceRow).toContainText('Not received')
    await expect(invoiceRow).toContainText('Invoice arithmetic')
    await expect(page.getByText('Not ready').first()).toBeVisible()
    await expect(page.getByRole('heading', { name: 'No documents yet' })).toBeVisible()
    await expect(invoiceRow.getByRole('button', { name: 'Upload final invoice' })).toBeVisible()

    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    await expect(page.getByText(/It has been stored, not read/)).toBeVisible()

    const docs = page.getByRole('table', { name: 'Documents stored on this claim' })
    await expect(docs.getByRole('rowheader', { name: /synthetic-invoice\.pdf/ })).toBeVisible()
    await expect(docs).toContainText('Uploaded · not read yet')
    await expect(docs).toContainText('792 B')
    await expect(invoiceRow).toContainText('Received')
    await expect(page.getByText('Ready with warnings').first()).toBeVisible()

    const api = await listDocumentsViaApi(request, token, claim.id)
    expect(api.totalElements).toBe(1)
    expect(api.items[0]).toMatchObject({ fileName: 'synthetic-invoice.pdf', status: 'RECEIVED', documentType: 'FINAL_INVOICE', sha256: sha256(fixtureBytes('synthetic-invoice.pdf')) })
  })

  test('the claim list reflects the new readiness after an upload', async ({ page, request }) => {
    const { claim } = await open(page, request, 'listready')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    await page.getByRole('navigation', { name: 'Main' }).getByRole('link', { name: 'Claims' }).click()
    const row = page.getByRole('row').filter({ has: page.getByRole('link', { name: claim.internalId }) })
    await expect(row).toContainText('Ready with warnings')
  })

  test('a photograph is stored, not read, and previews as an image through the session', async ({ page, request }) => {
    await open(page, request, 'photo')
    await upload(page, 'DAMAGE_PHOTOGRAPH', fixture('synthetic-photo.png'))
    await expect(page.getByText(/synthetic-photo\.png is stored/)).toBeVisible()
    await expect(page.getByText(/this kind of document is kept for people to open/)).toBeVisible()
    await expect(page.getByRole('table', { name: 'Documents stored on this claim' })).toContainText('Stored · not read')
    await expect(page.getByRole('button', { name: /^Read/ })).toHaveCount(0)

    await page.getByRole('button', { name: 'Preview: synthetic-photo.png' }).click()
    const img = page.getByRole('img', { name: 'Preview of synthetic-photo.png' })
    await expect(img).toBeVisible()
    expect(await img.evaluate((el: HTMLImageElement) => [el.naturalWidth, el.naturalHeight, el.src.startsWith('blob:')])).toEqual([64, 48, true])
    await page.getByRole('button', { name: 'Hide preview: synthetic-photo.png' }).click()
    await expect(img).toHaveCount(0)
  })

  test('downloads the stored file byte for byte, under its own name', async ({ page, request }) => {
    await open(page, request, 'download')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    const downloading = page.waitForEvent('download')
    await page.getByRole('button', { name: 'Download: synthetic-invoice.pdf' }).click()
    const download = await downloading
    expect(download.suggestedFilename()).toBe('synthetic-invoice.pdf')
    const saved = readFileSync(await download.path())
    expect(sha256(saved)).toBe(sha256(fixtureBytes('synthetic-invoice.pdf')))
  })

  test('a PDF has no inline preview, only a download', async ({ page, request }) => {
    await open(page, request, 'nopdfprev')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    await expect(page.getByRole('button', { name: /preview/i })).toHaveCount(0)
  })
})

test.describe('what is refused (real backend)', () => {
  test('refuses unsuitable files before any request is made', async ({ page, request }) => {
    await open(page, request, 'client')
    const uploads: string[] = []
    page.on('request', (r) => r.method() === 'POST' && r.url().includes('/documents') && uploads.push(r.url()))
    const refused = (text: string) => page.getByRole('alert').filter({ hasText: text })

    await upload(page, 'FINAL_INVOICE', fixture('not-a-document.txt'))
    await expect(refused('Only PDF, JPEG and PNG')).toBeVisible()
    await upload(page, 'FINAL_INVOICE', fixture('empty.pdf'))
    await expect(refused('This file is empty')).toBeVisible()
    await upload(page, 'FINAL_INVOICE', { name: 'IMG_0001.HEIC', mimeType: 'image/heic', buffer: Buffer.from('heic') })
    await expect(refused('Convert the photo to JPEG')).toBeVisible()
    await upload(page, 'FINAL_INVOICE', { name: 'huge.pdf', mimeType: 'application/pdf', buffer: bigPdf(21) })
    await expect(refused('Files can be at most 20.0 MB')).toBeVisible()
    expect(uploads).toEqual([])

    // Closing the chooser without choosing anything sends nothing and says nothing.
    await page.getByRole('button', { name: 'Dismiss' }).click()
    const [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.getByRole('button', { name: 'Upload final invoice' }).click()])
    await chooser.setFiles([])
    await expect(page.getByRole('alert')).toHaveCount(0)
    expect(uploads).toEqual([])
  })

  test('the server refuses a text file dressed up as a PDF, by its content', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'disguised')
    await upload(page, 'FINAL_INVOICE', fixture('disguised.pdf'))
    await expect(page.getByText('That file type is not accepted.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Try again' })).toHaveCount(0)
    expect((await listDocumentsViaApi(request, token, claim.id)).totalElements).toBe(0)
  })

  test('a repeat of the same file is refused as a duplicate and points at the stored one', async ({ page, request }) => {
    await open(page, request, 'dup')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText('This exact file is already stored.')).toBeVisible()
    await page.getByRole('button', { name: 'Show the stored file' }).click()
    const row = page.getByRole('row').filter({ has: page.getByRole('rowheader', { name: /synthetic-invoice\.pdf/ }) })
    await expect(row).toHaveAttribute('data-highlight', 'true')
    await expect(row).toBeFocused()
  })

  test('the same file under another document type is allowed, as the contract says', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'othertype')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    await upload(page, 'JOB_CARD', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    expect((await listDocumentsViaApi(request, token, claim.id)).totalElements).toBe(2)
  })

  test('explains a 413 from the server in words', async ({ page, request }) => {
    await open(page, request, 'toolarge')
    await page.route('**/api/v1/claims/*/documents', (route) =>
      route.request().method() === 'POST' ? route.fulfill({ status: 413, contentType: 'application/json', body: JSON.stringify({ code: 'PAYLOAD_TOO_LARGE', status: 413, message: 'x' }) }) : route.continue(),
    )
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText('That file is too large.')).toBeVisible()
  })
})

test.describe('replacing a file (real backend)', () => {
  test('keeps the old file, marks it replaced, and counts only the new one', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'replace')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()

    const [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.getByRole('button', { name: 'Replace: synthetic-invoice.pdf' }).click()])
    await chooser.setFiles(fixture('synthetic-estimate.pdf'))
    await expect(page.getByText(/synthetic-estimate\.pdf is stored/)).toBeVisible()

    const docs = page.getByRole('table', { name: 'Documents stored on this claim' })
    const oldRow = docs.getByRole('row').filter({ has: page.getByRole('rowheader', { name: /synthetic-invoice\.pdf/ }) })
    await expect(oldRow).toContainText('Replaced by a newer file')
    await expect(oldRow.getByRole('button', { name: /^Replace/ })).toHaveCount(0)
    const newRow = docs.getByRole('row').filter({ has: page.getByRole('rowheader', { name: /synthetic-estimate\.pdf/ }) })
    await expect(newRow).toContainText('Replaces an earlier file')

    const checklistRow = page.getByRole('table', { name: 'Documents this claim stage expects' }).getByRole('rowheader', { name: 'Final invoice' }).locator('xpath=..')
    await expect(checklistRow.getByRole('cell').nth(3)).toHaveText('1')
    const api = await listDocumentsViaApi(request, token, claim.id)
    expect(api.items.map((i) => [i.fileName, i.superseded]).sort()).toEqual([['synthetic-estimate.pdf', false], ['synthetic-invoice.pdf', true]])
  })
})

test.describe('transfer behaviour (real backend)', () => {
  test('shows real progress for a large file, and is not "stored" until the server answers', async ({ page, request, context }) => {
    const { token, claim } = await open(page, request, 'progress')
    const cdp = await context.newCDPSession(page)
    await cdp.send('Network.enable')
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 20, downloadThroughput: -1, uploadThroughput: 3 * 1024 * 1024 })

    await upload(page, 'FINAL_INVOICE', { name: 'big.pdf', mimeType: 'application/pdf', buffer: bigPdf(12) })

    const bar = page.getByRole('progressbar', { name: 'Sending big.pdf' })
    await expect(bar).toBeVisible()
    const seen: number[] = []
    await expect
      .poll(
        async () => {
          const v = await bar.getAttribute('value').catch(() => null)
          if (v !== null) seen.push(Number(v))
          return Math.max(0, ...seen)
        },
        { intervals: [150], timeout: 20_000 },
      )
      .toBeGreaterThan(20)
    expect(Math.min(...seen)).toBeLessThan(99)
    await expect(page.getByText(/^big\.pdf is stored/)).toHaveCount(0) // not stored before the server answers
    await expect(page.getByText(/big\.pdf is stored/)).toBeVisible({ timeout: 60_000 })
    expect(new Set(seen).size).toBeGreaterThan(2)

    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 })
    expect((await listDocumentsViaApi(request, token, claim.id)).items.map((i) => i.fileName)).toEqual(['big.pdf'])
  })

  test('cancelling mid-transfer stops it, says so, and stores nothing', async ({ page, request, context }) => {
    const { token, claim } = await open(page, request, 'cancel')
    const cdp = await context.newCDPSession(page)
    await cdp.send('Network.enable')
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 20, downloadThroughput: -1, uploadThroughput: 1 * 1024 * 1024 })
    await upload(page, 'FINAL_INVOICE', { name: 'cancelme.pdf', mimeType: 'application/pdf', buffer: bigPdf(10) })
    await expect(page.getByRole('progressbar', { name: 'Sending cancelme.pdf' })).toBeVisible()
    await expect.poll(async () => Number((await page.getByRole('progressbar', { name: 'Sending cancelme.pdf' }).getAttribute('value').catch(() => '0')) ?? 0)).toBeGreaterThan(3)
    await page.getByRole('button', { name: 'Cancel upload' }).click()
    await expect(page.getByText('Upload cancelled.')).toBeVisible()
    await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 })
    await page.getByRole('button', { name: 'Refresh' }).click()
    await expect(page.getByRole('heading', { name: 'No documents yet' })).toBeVisible()
    expect((await listDocumentsViaApi(request, token, claim.id)).totalElements).toBe(0)
    await expect(page.getByRole('button', { name: 'Upload final invoice' })).toBeEnabled()
  })

  test('retries a dropped upload with the same idempotency key, and the file is stored exactly once', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'retry')
    const keys: string[] = []
    let dropped = false
    await page.route('**/api/v1/claims/*/documents', async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      keys.push((await route.request().headerValue('idempotency-key')) ?? '')
      if (!dropped) {
        dropped = true
        return route.abort('connectionrefused')
      }
      return route.continue()
    })
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText('The server could not be reached.')).toBeVisible()
    await expect(page.getByText(/Trying again is safe/)).toBeVisible()
    await page.getByRole('button', { name: 'Try again' }).click()
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    expect(keys).toHaveLength(2)
    expect(keys[0]).toBe(keys[1])
    expect(keys[0]).toMatch(/^upload-[0-9a-f-]{36}$/)
    expect((await listDocumentsViaApi(request, token, claim.id)).totalElements).toBe(1)
  })

  test('an access token that expires during the upload is renewed and the file is stored once', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'expiredup')
    const refreshes: string[] = []
    page.on('request', (r) => r.url().endsWith('/api/v1/auth/refresh') && refreshes.push(r.url()))
    let refused = false
    await page.route('**/api/v1/claims/*/documents', (route) => {
      if (route.request().method() === 'POST' && !refused) {
        refused = true
        return route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'TOKEN_EXPIRED', status: 401, message: 'expired' }) })
      }
      return route.continue()
    })
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    expect(refreshes).toHaveLength(1)
    expect((await listDocumentsViaApi(request, token, claim.id)).totalElements).toBe(1)
  })
})

test.describe('reading documents (the model is stubbed here; a live run is separate)', () => {
  test('shows honest elapsed time while a reading is in progress, never a percentage', async ({ page, request }) => {
    await open(page, request, 'reading')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    await page.route('**/documents/*/extraction', async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      await new Promise((r) => setTimeout(r, 2600))
      return route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({ documentId: 'x', documentStatus: 'VERIFIED', extractionId: 'e', attempt: 1 }) })
    })
    await page.getByRole('button', { name: 'Read document: synthetic-invoice.pdf' }).click()
    await expect(page.getByText(/Reading for \d+ s\. This can take a few minutes/)).toBeVisible()
    await expect(page.getByText(/Reading for [12] s/)).toBeVisible({ timeout: 4000 })
    expect(await page.locator('main').innerText()).not.toMatch(/Reading[^.\n]*\d+\s*%/)
    await expect(page.getByText(/Finished reading:/)).toBeVisible({ timeout: 8000 })
  })

  test('says a photograph is not read, using the backend’s own refusal', async ({ page, request }) => {
    const { token, claim } = await open(page, request, 'unsupported')
    await upload(page, 'DAMAGE_PHOTOGRAPH', fixture('synthetic-photo.png'))
    await expect(page.getByText(/synthetic-photo\.png is stored/)).toBeVisible()
    const doc = (await listDocumentsViaApi(request, token, claim.id)).items[0]!
    const response = await request.post(`/api/v1/claims/${claim.id}/documents/${doc.id}/extraction`, { headers: { Authorization: `Bearer ${token}` }, failOnStatusCode: false })
    expect(response.status()).toBe(422)
    expect((await response.json()).code).toBe('EXTRACTION_NOT_SUPPORTED')
    await expect(page.getByRole('button', { name: /^Read/ })).toHaveCount(0)
  })

  test('LIVE: reads a synthetic invoice with the real model', async ({ page, request }) => {
    test.skip(!process.env.E2E_LIVE_MODEL, 'Set E2E_LIVE_MODEL=1 to call the real model (synthetic document only; it uses free-tier quota).')
    test.setTimeout(5 * 60_000)
    await open(page, request, 'live')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    await page.getByRole('button', { name: 'Read document: synthetic-invoice.pdf' }).click()
    await expect(page.getByText(/Finished reading:/)).toBeVisible({ timeout: 4 * 60_000 })
    await expect(page.getByRole('table', { name: 'Documents stored on this claim' })).toContainText(/Read and consistent|Read · needs review|Could not be read/)
    await page.getByRole('button', { name: /Reading result: synthetic-invoice\.pdf/ }).click()
    await expect(page.getByText(/Attempt 1/)).toBeVisible()
    await expect(page.getByText(/does not show that the document is true/)).toBeVisible()
  })
})

test.describe('tenants (real backend)', () => {
  test('another organization cannot open a claim’s documents', async ({ page, request }) => {
    const owner = await apiToken(request, ORGANIZATION, 'docowner')
    const claim = await createClaimViaApi(request, owner, { internalId: uniqueRef('DOCSEC') })
    const email = await registerUser(request, 'docintruder', OTHER_ORGANIZATION)
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    await page.goto(`/app/claims/${claim.id}/documents`)
    await expect(page.getByRole('alert')).toContainText('We could not find that claim.')
    await expect(page.getByRole('button', { name: /^Upload / })).toHaveCount(0)
  })
})

test.describe('quality of the documents tab', () => {
  for (const size of [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'mobile', width: 390, height: 844 },
  ]) {
    test(`passes axe in every state and keeps to the screen at ${size.name}`, async ({ page, request, context }) => {
      test.setTimeout(120_000)
      await page.setViewportSize({ width: size.width, height: size.height })
      const { claim } = await open(page, request, `axe${size.name}`)
      const problems: string[] = []
      page.on('console', (m) => m.type() === 'error' && problems.push(m.text()))
      page.on('pageerror', (e) => problems.push(e.message))
      const shot = (n: string) => page.screenshot({ path: `e2e/.artifacts/docs-${n}-${size.name}.png`, fullPage: true })
      const check = async (state: string) => {
        expect(await axeViolations(page), `${state} at ${size.name}`).toEqual([])
        expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), `${state} sideways`).toBeLessThanOrEqual(0)
      }

      await check('empty')
      await shot('empty')

      await upload(page, 'FINAL_INVOICE', fixture('not-a-document.txt'))
      await expect(page.getByRole('alert').filter({ hasText: 'Only PDF' })).toBeVisible()
      await check('file problem')
      await page.getByRole('button', { name: 'Dismiss' }).click()

      // the upload in flight, held on a slow link so the progress state can be inspected
      const cdp = await context.newCDPSession(page)
      await cdp.send('Network.enable')
      await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 20, downloadThroughput: -1, uploadThroughput: 1 * 1024 * 1024 })
      await upload(page, 'FINAL_INVOICE', { name: 'slow.pdf', mimeType: 'application/pdf', buffer: bigPdf(6) })
      await expect(page.getByRole('progressbar', { name: 'Sending slow.pdf' })).toBeVisible()
      await check('sending')
      await shot('sending')
      await page.getByRole('button', { name: 'Cancel upload' }).click()
      await expect(page.getByText('Upload cancelled.')).toBeVisible()
      await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 })
      await check('cancelled')

      await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
      await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
        await upload(page, 'DAMAGE_PHOTOGRAPH', fixture('synthetic-photo.png'))
      await expect(page.getByText(/synthetic-photo\.png is stored/)).toBeVisible()
      await page.getByRole('button', { name: 'Preview: synthetic-photo.png' }).click()
      await expect(page.getByRole('img', { name: 'Preview of synthetic-photo.png' })).toBeVisible()
      await check('with documents and preview')
      await shot('documents')

        await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
      await expect(page.getByText('This exact file is already stored.')).toBeVisible()
      await check('duplicate error')

      expect(problems.filter((p) => !/status of (409|415)|Failed to load resource/.test(p))).toEqual([])
      void claim
    })
  }

  test('the whole upload works from the keyboard alone', async ({ page, request }) => {
    await open(page, request, 'keys')
    const button = page.getByRole('button', { name: 'Upload final invoice' })
    await button.focus()
    await expect(button).toBeFocused()
    const [chooser] = await Promise.all([page.waitForEvent('filechooser'), page.keyboard.press('Enter')])
    await chooser.setFiles(fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    // Focus is not moved away by the upload: the row's button is still where the person left it.
    await expect(page.getByRole('button', { name: 'Add another final invoice' })).toBeVisible()
  })

  test('stays within the screen at 320px with documents listed', async ({ page, request }) => {
    await page.setViewportSize({ width: 320, height: 800 })
    await open(page, request, 'narrowdocs')
    await upload(page, 'FINAL_INVOICE', fixture('synthetic-invoice.pdf'))
    await expect(page.getByText(/synthetic-invoice\.pdf is stored/)).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
  })

  test('shows a hostile-looking file name as plain text', async ({ page, request }) => {
    await open(page, request, 'hostile')
    await upload(page, 'JOB_CARD', { name: '<img src=x onerror=alert(1)>.pdf', mimeType: 'application/pdf', buffer: bigPdf(0.01) })
    await expect(page.getByText(/is stored/).first()).toBeVisible()
    await expect(page.locator('img[src="x"]')).toHaveCount(0)
    expect(await page.evaluate(() => (window as unknown as { __xss?: boolean }).__xss)).toBeUndefined()
  })
})
