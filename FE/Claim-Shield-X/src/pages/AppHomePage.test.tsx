import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { page, summary } from '@/test/claims'
import { renderPage, signInForTests } from '@/test/render'
import { server } from '@/test/server'
import { AppHomePage } from './AppHomePage'

const setup = () => renderPage(<AppHomePage />, { path: '/app' })

function claims(body: ReturnType<typeof page>) {
  const seen: URLSearchParams[] = []
  server.use(
    http.get('*/api/v1/claims', ({ request }) => {
      seen.push(new URL(request.url).searchParams)
      return HttpResponse.json(body)
    }),
  )
  return seen
}

beforeEach(async () => {
  await signInForTests()
})

describe('Overview', () => {
  it('counts claims by evidence readiness and says exactly which claims the counts cover', async () => {
    const items = [
      summary({ level: 'NOT_READY' }),
      summary({ level: 'NOT_READY' }),
      summary({ level: 'READY_WITH_WARNINGS', blockers: 0, warnings: 2 }),
      summary({ level: 'READY', blockers: 0, warnings: 0 }),
    ]
    const seen = claims(page(items, { size: 100 }))
    setup()
    const counts = await screen.findByLabelText('Claims by evidence readiness')
    expect(within(counts).getByText('Claims').nextElementSibling).toHaveTextContent('4')
    expect(within(counts).getByText('Not ready').nextElementSibling).toHaveTextContent('2')
    expect(within(counts).getByText('Ready with warnings').nextElementSibling).toHaveTextContent('1')
    expect(within(counts).getByText('Ready').nextElementSibling).toHaveTextContent('1')
    expect(screen.getByText(/Readiness counts cover all 4 claims/)).toBeInTheDocument()
    expect(Object.fromEntries(seen[0]!)).toEqual({ page: '0', size: '100', sort: 'createdAt,desc' })
  })

  it('does not pretend to count claims it did not fetch', async () => {
    claims(page([summary({ level: 'NOT_READY' })], { size: 100, totalElements: 350, totalPages: 4 }))
    setup()
    expect(await screen.findByText(/Readiness counts cover your 1 newest of 350 claims/)).toBeInTheDocument()
    expect(screen.getByLabelText('Claims by evidence readiness')).toHaveTextContent('350')
  })

  it('lists claims waiting on evidence, not-ready ones first, at most eight', async () => {
    const warnings = Array.from({ length: 5 }, (_, i) => summary({ internalId: `W-${i}`, level: 'READY_WITH_WARNINGS', blockers: 0, warnings: 1 }))
    const blocked = Array.from({ length: 5 }, (_, i) => summary({ internalId: `B-${i}`, level: 'NOT_READY' }))
    claims(page([...warnings, ...blocked], { size: 100 }))
    setup()
    const list = (await screen.findByRole('heading', { name: 'Waiting on evidence' })).parentElement!.nextElementSibling as HTMLElement
    const refs = within(list).getAllByRole('link').map((a) => a.textContent)
    expect(refs).toHaveLength(8)
    expect(refs.slice(0, 5)).toEqual(['B-0', 'B-1', 'B-2', 'B-3', 'B-4'])
    expect(refs.slice(5)).toEqual(['W-0', 'W-1', 'W-2'])
    expect(screen.getByRole('link', { name: 'View all claims' })).toHaveAttribute('href', '/app/claims')
  })

  it('says so when nothing is waiting', async () => {
    claims(page([summary({ level: 'READY', blockers: 0, warnings: 0 })], { size: 100 }))
    setup()
    expect(await screen.findByText(/No claim is waiting on evidence/)).toBeInTheDocument()
  })

  it('invites the first claim when there are none', async () => {
    claims(page([], { size: 100 }))
    setup()
    expect(await screen.findByRole('heading', { name: 'No claims yet' })).toBeInTheDocument()
    expect(screen.getAllByRole('link', { name: /Create (the first )?claim/ }).length).toBeGreaterThan(0)
    expect(screen.queryByLabelText('Claims by evidence readiness')).toBeNull()
  })

  it('explains the readiness levels, and that they describe evidence and not the claim', async () => {
    claims(page([summary()], { size: 100 }))
    setup()
    await userEvent.click(await screen.findByText('What these readiness levels mean'))
    expect(screen.getByText(/says nothing about the claim itself/)).toBeInTheDocument()
  })

  it('shows a retryable error and recovers', async () => {
    let fail = true
    server.use(http.get('*/api/v1/claims', () => (fail ? HttpResponse.json({ code: 'SERVICE_UNAVAILABLE', status: 503, message: 'x' }, { status: 503 }) : HttpResponse.json(page([summary()], { size: 100 })))))
    setup()
    expect(await screen.findByRole('alert')).toHaveTextContent('A service this needs is unavailable.')
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByLabelText('Claims by evidence readiness')).toBeInTheDocument()
  })

  it('invents no metrics: no investigations, activity or scores', async () => {
    claims(page([summary()], { size: 100 }))
    const { container } = setup()
    await screen.findByLabelText('Claims by evidence readiness')
    expect(container.textContent).not.toMatch(/investigation|activity|score|fraud|risk|trend|%/i)
  })
})
