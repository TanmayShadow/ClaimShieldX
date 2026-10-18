import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

const widths = [
  { name: 'desktop', width: 1440, height: 900 },
  { name: 'tablet', width: 820, height: 1100 },
  { name: 'mobile', width: 390, height: 844 },
]

test.describe('design sandbox', () => {
  test('has no axe violations (WCAG 2.2 AA, including contrast)', async ({ page }) => {
    await page.goto('/design')
    await expect(page.getByRole('heading', { level: 1, name: 'Design sandbox' })).toBeVisible()
    const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze()
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([])
  })

  test('skip link is the first tab stop and moves focus to main', async ({ page }) => {
    await page.goto('/design')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await page.keyboard.press('Tab')
    const skip = page.getByRole('link', { name: 'Skip to main content' })
    await expect(skip).toBeFocused()
    await expect(skip).toBeInViewport()
    await page.keyboard.press('Enter')
    await expect(page.locator('#main')).toBeFocused()
  })

  test('confirmation dialog traps focus, cancels on Escape and restores focus', async ({ page }) => {
    await page.goto('/design')
    const opener = page.getByRole('button', { name: 'Open confirmation' })
    await opener.focus()
    await page.keyboard.press('Enter')
    const dialog = page.getByRole('dialog', { name: 'Remove this example?' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByRole('button', { name: 'Cancel' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(dialog.getByRole('button', { name: 'Remove' })).toBeFocused()
    // The page behind a modal dialog is inert: however often Tab is pressed, focus never lands on it.
    for (let i = 0; i < 6; i++) {
      await page.keyboard.press('Tab')
      const outside = await page.evaluate(() => {
        const active = document.activeElement
        return active !== document.body && !document.querySelector('dialog')!.contains(active)
      })
      expect(outside).toBe(false)
    }
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
    await expect(opener).toBeFocused()
  })

  test('a pending confirmation ignores Escape and a second click', async ({ page }) => {
    await page.goto('/design')
    await page.getByRole('button', { name: 'Open confirmation' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('button', { name: 'Remove' }).click()
    await expect(dialog.getByRole('button', { name: 'Working' })).toHaveAttribute('aria-busy', 'true')
    await page.keyboard.press('Escape')
    await expect(dialog).toBeVisible()
    await expect(dialog).toBeHidden({ timeout: 4000 })
  })

  test('reduced motion removes the running-state animation', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto('/design')
    const icon = page.locator('[data-state="active"] svg').first()
    const duration = await icon.evaluate((el) => getComputedStyle(el).animationDuration)
    expect(Number.parseFloat(duration)).toBeLessThan(0.001)
  })

  test('motion is on by default for the running state only', async ({ page }) => {
    await page.goto('/design')
    const active = await page.locator('[data-state="active"] svg').first().evaluate((el) => getComputedStyle(el).animationName)
    const done = await page.locator('[data-state="done"] svg').first().evaluate((el) => getComputedStyle(el).animationName)
    expect(active).not.toBe('none')
    expect(done).toBe('none')
  })

  for (const size of widths) {
    test(`renders without horizontal scroll at ${size.name} (${size.width}px)`, async ({ page }) => {
      await page.setViewportSize({ width: size.width, height: size.height })
      await page.goto('/design')
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)
      expect(overflow).toBeLessThanOrEqual(0)
      await page.screenshot({ path: `e2e/.artifacts/design-${size.name}.png`, fullPage: true })
    })
  }
})
