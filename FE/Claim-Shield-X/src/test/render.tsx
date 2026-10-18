import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { resetSessionForTests, signIn } from '@/features/auth/session'
import { makeJwt } from './jwt'
import { server } from './server'

/** Signs in through the real session code so every request carries a bearer token. */
export async function signInForTests(claims: Record<string, unknown> = {}): Promise<string> {
  resetSessionForTests()
  window.localStorage.clear()
  const token = makeJwt(claims)
  server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json({ accessToken: token })))
  await signIn('inv@org-a.test', 'password-123')
  return token
}

export function Where() {
  const { pathname, search, state } = useLocation()
  return (
    <>
      <output data-testid="where">{pathname + search}</output>
      <output data-testid="state">{JSON.stringify(state)}</output>
    </>
  )
}

type Options = { path?: string; entry?: string | { pathname: string; state?: unknown }; extraRoutes?: ReactElement[]; nested?: ReactElement[] }

/** Renders a page at a route, with a fresh query cache and a fallback route that shows where navigation ended up. */
export function renderPage(element: ReactElement, { path = '/app/page', entry = path, extraRoutes = [], nested = [] }: Options = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0, gcTime: 0 }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path={path} element={element}>
            {nested}
          </Route>
          {extraRoutes}
          <Route path="*" element={<Where />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}
