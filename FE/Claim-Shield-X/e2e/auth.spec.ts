import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'
import { backendUp, PASSWORD, registerUser, signInThroughUi } from './support'

const AXE_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

test.beforeEach(async ({ request }) => {
  test.skip(!(await backendUp(request)), 'BLOCKED / NOT RUN: the backend is not answering through the dev proxy (start it and set E2E_API_TARGET).')
})

test.describe('sign in (real backend)', () => {
  test('signs in, shows who is signed in, and stores no token anywhere readable by the page', async ({ page, request, context }) => {
    const email = await registerUser(request, 'login')
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    await expect(page.getByRole('heading', { level: 1, name: 'Overview' })).toBeVisible()
    await expect(page.getByText(email)).toBeVisible()
    await expect(page.getByText('Org A', { exact: true })).toBeVisible()

    const storage = await page.evaluate(() => ({ local: { ...localStorage }, session: { ...sessionStorage }, cookie: document.cookie }))
    expect(storage.local).toEqual({ 'claimshield.session-hint': '1' })
    expect(storage.session).toEqual({})
    expect(storage.cookie).toBe('')

    const cookies = await context.cookies()
    const refresh = cookies.find((c) => c.name === 'refreshToken')
    expect(refresh, 'the refresh cookie was set through the proxy').toBeDefined()
    expect(refresh).toMatchObject({ httpOnly: true, sameSite: 'Strict', path: '/api/v1/auth' })
  })

  test('says only that the email or password is wrong, identically for a wrong password and an unknown email', async ({ page, request }) => {
    const email = await registerUser(request, 'wrong')
    await page.goto('/login')
    await signInThroughUi(page, email, 'not-the-password')
    const first = await page.getByRole('alert').textContent()
    expect(first).toContain('The email or password is not correct.')
    await expect(page).toHaveURL(/\/login$/)

    await page.getByLabel('Email address').fill('nobody-here@org-a.test')
    await page.getByLabel('Password', { exact: true }).fill('not-the-password')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByRole('alert')).toHaveText(first!)
  })

  test('checks the form before asking the server', async ({ page }) => {
    const requests: string[] = []
    page.on('request', (r) => requests.push(new URL(r.url()).pathname))
    await page.goto('/login')
    await page.getByRole('button', { name: 'Sign in' }).click()
    await expect(page.getByText('Enter your email address.')).toBeVisible()
    await expect(page.getByLabel('Email address')).toBeFocused()
    expect(requests.filter((p) => p.startsWith('/api'))).toEqual([])
  })

  test('sends one login request however many times the button is pressed', async ({ page, request }) => {
    const email = await registerUser(request, 'double')
    let logins = 0
    page.on('request', (r) => {
      if (r.url().endsWith('/api/v1/auth/login')) logins++
    })
    await page.goto('/login')
    await page.getByLabel('Email address').fill(email)
    await page.getByLabel('Password', { exact: true }).fill(PASSWORD)
    const button = page.getByRole('button', { name: 'Sign in' })
    await button.dblclick()
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/app$/)
    expect(logins).toBe(1)
  })

  test('reports an unreachable server and keeps what was typed', async ({ page, request }) => {
    const email = await registerUser(request, 'offline')
    await page.route('**/api/v1/auth/login', (route) => route.abort('connectionrefused'))
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page.getByRole('alert')).toContainText('The server could not be reached.')
    await expect(page.getByLabel('Password', { exact: true })).toHaveValue(PASSWORD)
    await expect(page).toHaveURL(/\/login$/)
  })

  test('shows and hides the password', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('Password', { exact: true }).fill('visible-secret')
    await page.getByRole('button', { name: 'Show password' }).click()
    await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'text')
    await page.getByRole('button', { name: 'Hide password' }).click()
    await expect(page.getByLabel('Password', { exact: true })).toHaveAttribute('type', 'password')
  })

  test('works from the keyboard in a sensible order', async ({ page, request }) => {
    const email = await registerUser(request, 'keys')
    await page.goto('/login')
    await expect(page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible()
    await page.keyboard.press('Tab') // skip link
    await page.keyboard.press('Tab') // brand link
    await page.keyboard.press('Tab')
    await expect(page.getByLabel('Email address')).toBeFocused()
    await page.keyboard.type(email)
    await page.keyboard.press('Tab')
    await expect(page.getByLabel('Password', { exact: true })).toBeFocused()
    await page.keyboard.type(PASSWORD)
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: 'Show password' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: 'Sign in' })).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(page).toHaveURL(/\/app$/)
  })
})

test.describe('sessions (real backend)', () => {
  test('a visitor reaching the signed-in area is sent to sign in and returned afterwards', async ({ page, request }) => {
    const email = await registerUser(request, 'guard')
    await page.goto('/app?welcome=1')
    await expect(page).toHaveURL(/\/login$/)
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app\?welcome=1$/)
  })

  test('survives reloads: the page asks the server through the cookie, once, and stays signed in', async ({ page, request }) => {
    const email = await registerUser(request, 'reload')
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    for (let i = 0; i < 3; i++) {
      const refreshes: string[] = []
      const listener = (r: { url: () => string }) => {
        if (r.url().endsWith('/api/v1/auth/refresh')) refreshes.push(r.url())
      }
      page.on('request', listener)
      await page.reload()
      await expect(page.getByText(email)).toBeVisible()
      page.off('request', listener)
      // The refresh token works only once: more than one call per load would sign the person out.
      expect(refreshes, `reload ${i + 1}`).toHaveLength(1)
    }
  })

  test('signing out ends the session on the server too', async ({ page, request, context }) => {
    const email = await registerUser(request, 'logout')
    await page.goto('/login')
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    const refreshCookieBefore = (await context.cookies()).find((c) => c.name === 'refreshToken')!
    await page.getByRole('button', { name: 'Sign out' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText('You have signed out.')).toBeVisible()
    expect(await page.evaluate(() => localStorage.length)).toBe(0)

    // The old refresh token must now be dead even for someone who kept a copy.
    const replay = await request.post('/api/v1/auth/refresh', { data: { refreshToken: refreshCookieBefore.value }, failOnStatusCode: false })
    expect(replay.status()).toBe(401)
    await page.goto('/app')
    await expect(page).toHaveURL(/\/login$/)
  })

  test('a second sign-in as the same user ends the first one (one session per user)', async ({ browser, request }) => {
    const email = await registerUser(request, 'single')
    const first = await browser.newContext()
    const second = await browser.newContext()
    const a = await first.newPage()
    const b = await second.newPage()
    await a.goto('/login')
    await signInThroughUi(a, email)
    await expect(a).toHaveURL(/\/app$/)
    await b.goto('/login')
    await signInThroughUi(b, email)
    await expect(b).toHaveURL(/\/app$/)
    await a.reload()
    await expect(a).toHaveURL(/\/login$/)
    await first.close()
    await second.close()
  })

  test('refuses to follow an address that leaves the site', async ({ page, request }) => {
    const email = await registerUser(request, 'redirect')
    await page.goto(`/login?returnTo=${encodeURIComponent('//evil.example/app')}`)
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/localhost:5173\/app$/)
  })

  test('the landing page offers Sign in to a visitor without calling the server, and Open workspace afterwards', async ({ page, request }) => {
    const requests: string[] = []
    page.on('request', (r) => requests.push(new URL(r.url()).pathname))
    await page.goto('/')
    await expect(page.getByRole('link', { name: 'Sign in' }).first()).toHaveAttribute('href', '/login')
    expect(requests.filter((p) => p.startsWith('/api'))).toEqual([])

    const email = await registerUser(request, 'landing')
    await page.getByRole('link', { name: 'Sign in' }).first().click()
    await signInThroughUi(page, email)
    await expect(page).toHaveURL(/\/app$/)
    await page.goto('/')
    await expect(page.getByRole('link', { name: 'Open workspace' }).first()).toHaveAttribute('href', '/app')
  })
})

test.describe('sign-in page quality', () => {
  for (const size of [
    { name: 'desktop', width: 1440, height: 900 },
    { name: 'mobile', width: 390, height: 844 },
  ]) {
    test(`has no axe violations and no sideways scroll at ${size.name}`, async ({ page }) => {
      await page.setViewportSize({ width: size.width, height: size.height })
      await page.goto('/login')
      await expect(page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible()
      const results = await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()
      expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([])
      expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0)
      await page.screenshot({ path: `e2e/.artifacts/login-${size.name}.png`, fullPage: true })
    })

    test(`shows an error state that passes axe at ${size.name}`, async ({ page }) => {
      await page.setViewportSize({ width: size.width, height: size.height })
      await page.goto('/login')
      await page.getByRole('button', { name: 'Sign in' }).click()
      await expect(page.getByText('Enter your email address.')).toBeVisible()
      const results = await new AxeBuilder({ page }).withTags(AXE_TAGS).analyze()
      expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`)).toEqual([])
      await page.screenshot({ path: `e2e/.artifacts/login-errors-${size.name}.png`, fullPage: true })
    })
  }

  test('contacts no third-party origin and logs no console errors', async ({ page }) => {
    const origins = new Set<string>()
    const problems: string[] = []
    page.on('request', (r) => origins.add(new URL(r.url()).origin))
    page.on('console', (m) => m.type() === 'error' && problems.push(m.text()))
    page.on('pageerror', (e) => problems.push(e.message))
    await page.goto('/login')
    await expect(page.getByRole('heading', { level: 1, name: 'Sign in' })).toBeVisible()
    expect([...origins]).toEqual(['http://localhost:5173'])
    expect(problems).toEqual([])
  })
})
