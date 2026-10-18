import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { backendUp, registerUser, signInThroughUi } from './support'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy.')
})

async function enter(page: Page, request: Parameters<typeof registerUser>[0], label: string) {
  const email = await registerUser(request, label)
  await page.goto('/login')
  await signInThroughUi(page, email)
  await expect(page).toHaveURL(/\/app$/)
  return email
}

const violations = async (page: Page) =>
  (await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()).violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)

test.describe('application shell (real backend)', () => {
  test('shows the workspace, the signed-in account and only the destinations that exist', async ({ page, request }) => {
    const email = await enter(page, request, 'shell')
    await expect(page.getByRole('navigation', { name: 'Main' }).getByRole('link')).toHaveText(['Overview', 'Claims', 'History imports'])
    await expect(page.getByRole('link', { name: 'Overview' })).toHaveAttribute('aria-current', 'page')
    await expect(page.getByText('Org A', { exact: true })).toBeVisible()
    await expect(page.getByText(email)).toBeVisible()
    await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeVisible()
    await expect(page.getByText(/notification|unread|recent activity/i)).toHaveCount(0)
  })

  test('the skip link jumps over the navigation to the main content', async ({ page, request }) => {
    await enter(page, request, 'skip')
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeVisible()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page.locator('#main')).toBeFocused()
  })

  test('signs out from the shell', async ({ page, request }) => {
    await enter(page, request, 'out')
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText('You have signed out.')).toBeVisible()
  })

  test('an unknown address inside the workspace is a not-found page with the shell intact, and it survives a reload', async ({ page, request }) => {
    await enter(page, request, 'nf')
    await page.goto('/app/does-not-exist')
    await expect(page.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible()
    await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'Page not found' })).toBeVisible()
    await page.getByRole('link', { name: 'Go to the overview' }).click()
    await expect(page).toHaveURL(/\/app$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeFocused()
  })

  test('says so while the browser is offline and stops when it is back', async ({ page, request, context }) => {
    await enter(page, request, 'offline')
    await context.setOffline(true)
    await expect(page.getByRole('status').filter({ hasText: 'You are offline.' })).toBeVisible()
    await context.setOffline(false)
    await expect(page.getByText('You are offline.')).toHaveCount(0)
  })
})

test.describe('shell layout', () => {
  test('desktop: the navigation is always visible and there is no menu button', async ({ page, request }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await enter(page, request, 'desk')
    await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Menu' })).toBeHidden()
    expect(await violations(page)).toEqual([])
    await page.screenshot({ path: 'e2e/.artifacts/shell-desktop.png', fullPage: true })
  })

  for (const width of [820, 390]) {
    test(`${width}px: the menu is closed until asked for, opens by keyboard, closes on Escape and passes axe either way`, async ({ page, request }) => {
      await page.setViewportSize({ width, height: 800 })
      await enter(page, request, `m${width}`)
      const menu = page.getByRole('button', { name: 'Menu' })
      await expect(menu).toBeVisible()
      await expect(page.getByRole('navigation', { name: 'Main' })).toBeHidden()
      expect(await violations(page)).toEqual([])

      await menu.focus()
      await page.keyboard.press('Enter')
      await expect(menu).toHaveAttribute('aria-expanded', 'true')
      await expect(page.getByRole('navigation', { name: 'Main' })).toBeVisible()
      expect(await violations(page)).toEqual([])
      await page.screenshot({ path: `e2e/.artifacts/shell-menu-${width}.png`, fullPage: true })

      await page.getByRole('link', { name: 'Overview' }).focus()
      await page.keyboard.press('Escape')
      await expect(menu).toHaveAttribute('aria-expanded', 'false')
      await expect(menu).toBeFocused()
      await expect(page.getByRole('navigation', { name: 'Main' })).toBeHidden()
    })
  }

  for (const width of [1440, 820, 390, 320]) {
    test(`does not scroll sideways at ${width}px`, async ({ page, request }) => {
      await page.setViewportSize({ width, height: 800 })
      await enter(page, request, `w${width}`)
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
    })
  }

  test('keeps the navigation reachable at 200% zoom of a laptop screen (a 640px window)', async ({ page, request }) => {
    await page.setViewportSize({ width: 640, height: 450 })
    await enter(page, request, 'zoom')
    await page.getByRole('button', { name: 'Menu' }).click()
    await expect(page.getByRole('link', { name: 'Overview' })).toBeInViewport()
    await expect(page.getByRole('button', { name: 'Sign out' })).toBeVisible()
  })
})

test('the brand name never wraps and its mark keeps its size', async ({ browser, request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy.')
  for (const width of [1440, 390, 320]) {
    const context = await browser.newContext({ viewport: { width, height: 800 } })
    const page = await context.newPage()
    const email = await registerUser(request, `brand${width}`)
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    const brand = page.locator('a', { hasText: 'ClaimShield X' }).locator('visible=true').first()
    const [box, mark] = await Promise.all([brand.boundingBox(), brand.locator('svg').boundingBox()])
    expect(box!.height, `${width}px: one line`).toBeLessThan(48)
    expect(mark!.width, `${width}px: mark size`).toBeGreaterThanOrEqual(27)
    await context.close()
  }
})
