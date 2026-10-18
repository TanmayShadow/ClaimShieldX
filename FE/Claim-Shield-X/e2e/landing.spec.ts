import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const sizes = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'tablet', width: 820, height: 1100 },
  { name: 'mobile', width: 390, height: 844 },
]

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

test.describe('landing page', () => {
  for (const size of sizes) {
    test(`has no axe violations at ${size.name}`, async ({ page }) => {
      await page.setViewportSize({ width: size.width, height: size.height })
      await page.goto('/')
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      const results = await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()
      expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([])
    })

    test(`does not scroll sideways at ${size.name}`, async ({ page }) => {
      await page.setViewportSize({ width: size.width, height: size.height })
      await page.goto('/')
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
      await page.screenshot({ path: `e2e/.artifacts/landing-${size.name}.png`, fullPage: true })
    })
  }

  for (const width of [640, 320]) {
    test(`stays readable at ${width}px (200% zoom of a 1280px window, and the smallest phones)`, async ({ page }) => {
      await page.setViewportSize({ width, height: 800 })
      await page.goto('/')
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
    })
  }

  test('contacts no third-party origin and logs no console errors', async ({ page }) => {
    const origins = new Set<string>()
    const problems: string[] = []
    page.on('request', (r) => origins.add(new URL(r.url()).origin))
    page.on('console', (m) => {
      if (m.type() === 'error') problems.push(m.text())
    })
    page.on('pageerror', (e) => problems.push(e.message))
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await page.evaluate(() => document.fonts.ready)
    expect([...origins]).toEqual(['http://localhost:5173'])
    expect(problems).toEqual([])
  })

  test('serves the self-hosted display font', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    const loaded = await page.evaluate(async () => {
      await document.fonts.ready
      return ['Syne Variable', 'DM Sans Variable', 'IBM Plex Mono'].map((f) => [f, document.fonts.check(`16px "${f}"`)])
    })
    expect(loaded).toEqual([
      ['Syne Variable', true],
      ['DM Sans Variable', true],
      ['IBM Plex Mono', true],
    ])
  })

  test('skip link, then section links, work from the keyboard', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: 'Skip to main content' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: 'ClaimShield X' })).toBeFocused()
    await page.getByRole('link', { name: 'Boundaries', exact: true }).focus()
    await page.keyboard.press('Enter')
    await expect(page.getByRole('heading', { name: 'What exists today, and what does not' })).toBeInViewport()
  })

  test('the hero call to action jumps to the stage route', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('link', { name: 'See how an investigation works' }).click()
    await expect(page.getByRole('heading', { name: 'Anatomy of an investigation' })).toBeInViewport()
  })

  test('the stage route can be driven with the keyboard', async ({ page }) => {
    await page.goto('/')
    const tabs = page.getByRole('tab')
    await tabs.first().focus()
    await page.keyboard.press('ArrowRight')
    await expect(tabs.nth(1)).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByRole('tabpanel').getByRole('heading', { level: 3 })).toHaveText('Structured extraction')
    await page.keyboard.press('End')
    await expect(page.getByRole('tabpanel').getByRole('heading', { level: 3 })).toHaveText('Human review')
    await page.keyboard.press('Tab')
    await expect(page.getByRole('tabpanel')).toBeFocused()
  })

  test('the stage route works by pointer and reports what each stage is in words', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('tab', { name: /Competing explanations/ }).click()
    const panel = page.getByRole('tabpanel')
    await expect(panel.getByRole('heading', { level: 3 })).toHaveText('Competing explanations')
    await expect(panel.getByText('In the prototype').first()).toBeVisible()
  })

  test('reduced motion stops the hero entrance', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/')
    const duration = await page.locator('figure svg').first().evaluate((el) => getComputedStyle(el).animationDuration)
    expect(Number.parseFloat(duration)).toBeLessThan(0.001)
  })

  test('forced-colors mode keeps the stage tabs and the call to action visible', async ({ page }) => {
    await page.emulateMedia({ forcedColors: 'active' })
    await page.goto('/')
    await expect(page.getByRole('link', { name: 'See how an investigation works' })).toBeVisible()
    await expect(page.getByRole('tab').first()).toBeVisible()
  })

  test('a visitor can read the whole page without any control doing nothing', async ({ page }) => {
    await page.goto('/')
    // every in-page link points at an element that exists
    const dead = await page.evaluate(() =>
      [...document.querySelectorAll('a[href^="#"]')]
        .map((a) => a.getAttribute('href')!)
        .filter((h) => h.length > 1 && !document.querySelector(h)),
    )
    expect(dead).toEqual([])
  })
})
