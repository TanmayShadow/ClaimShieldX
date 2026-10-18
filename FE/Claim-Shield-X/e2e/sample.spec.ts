import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']
const steps = ['documents', 'checks', 'history', 'explanations', 'review'] as const
const sizes = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'mobile', width: 390, height: 844 },
]

test.describe('sample investigation', () => {
  for (const size of sizes) {
    for (const step of steps) {
      test(`${step} has no axe violations at ${size.name}`, async ({ page }) => {
        await page.setViewportSize({ width: size.width, height: size.height })
        await page.goto(`/sample-investigation?step=${step}`)
        await expect(page.getByRole('heading', { level: 1, name: 'Sample investigation' })).toBeVisible()
        const results = await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()
        expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([])
        expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
        await page.screenshot({ path: `e2e/.artifacts/sample-${step}-${size.name}.png`, fullPage: true })
      })
    }
  }

  test('stays readable at 320px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 800 })
    for (const step of steps) {
      await page.goto(`/sample-investigation?step=${step}`)
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth), step).toBeLessThanOrEqual(0)
    }
  })

  test('makes no API request and stores nothing while it is used', async ({ page }) => {
    const requests: string[] = []
    page.on('request', (r) => requests.push(new URL(r.url()).pathname))
    await page.goto('/sample-investigation')
    for (const next of ['Checks', 'History', 'Explanations', 'Review']) {
      await page.getByRole('button', { name: new RegExp(`^Next: ${next}`) }).click()
    }
    await page.getByRole('radio', { name: /Request more evidence/ }).check()
    await page.getByLabel('Rationale').fill('Ask the garage for the job card.')
    await page.getByRole('button', { name: 'Preview the entry' }).click()
    await expect(page.getByText('Entry preview · not saved')).toBeVisible()
    expect(requests.filter((p) => p.startsWith('/api'))).toEqual([])
    const stored = await page.evaluate(() => localStorage.length + sessionStorage.length + document.cookie.length)
    expect(stored).toBe(0)
  })

  test('a citation can be followed by keyboard and lands on its source', async ({ page }) => {
    await page.goto('/sample-investigation?step=checks')
    const citation = page.getByRole('button', { name: /^Document Final invoice, grand total/ })
    await citation.focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('button', { name: /^01\s*Documents/ })).toHaveAttribute('aria-current', 'step')
    await expect(page.locator('mark', { hasText: '62,510.00' }).first()).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.activeElement?.hasAttribute('data-focus-target'))).toBe(true)
  })

  test('on a phone, choosing a rule brings its detail into view', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/sample-investigation?step=checks')
    await page.getByRole('button', { name: /Repeated line items/ }).click()
    await expect(page.getByRole('heading', { level: 3, name: 'Repeated line items' })).toBeInViewport()
  })

  test('browser back returns to the previous step', async ({ page }) => {
    await page.goto('/sample-investigation')
    await page.getByRole('button', { name: /^02\s*Checks/ }).click()
    await expect(page).toHaveURL(/step=checks/)
    await page.goBack()
    await expect(page.getByRole('button', { name: /^01\s*Documents/ })).toHaveAttribute('aria-current', 'step')
  })

  test('the field list and the document stay in step with the keyboard', async ({ page }) => {
    await page.goto('/sample-investigation')
    await page.getByRole('button', { name: 'lineItems[6]' }).focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('mark', { hasText: 'Wheel alignment' }).first()).toBeVisible()
    await expect(page.getByText(/Source of lineItems\[6\]: page 1/)).toBeVisible()
  })

  test('the landing page reaches the sample and the sample returns home', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: 'Explore the sample investigation' }).first().click()
    await expect(page).toHaveURL(/\/sample-investigation$/)
    await expect(page.getByText('This case is fictional.')).toBeVisible()
    await page.getByRole('link', { name: 'Home', exact: true }).first().click()
    await expect(page).toHaveURL(/localhost:5173\/$/)
  })

  test('the current page is announced in the header navigation', async ({ page }) => {
    await page.goto('/sample-investigation')
    await expect(page.getByRole('navigation', { name: 'Sections' }).getByRole('link', { name: 'Sample investigation' })).toHaveAttribute('aria-current', 'page')
  })
})
