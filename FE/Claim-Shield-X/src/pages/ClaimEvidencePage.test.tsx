import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Outlet, Route } from 'react-router'
import { detail } from '@/test/claims'
import { evidence, graph, INV_ID } from '@/test/report'
import { investigation, investigationPage } from '@/test/investigations'
import { renderPage, signInForTests } from '@/test/render'
import { server } from '@/test/server'
import { ClaimEvidencePage } from './ClaimEvidencePage'

const claimId = detail.id
const Shell = () => <Outlet context={{ claim: detail }} />
const setup = (hash = '') =>
  renderPage(<Shell />, { path: '/app/claims/:claimId', entry: `/app/claims/${claimId}/evidence${hash}`, nested: [<Route key="e" path="evidence" element={<ClaimEvidencePage />} />, <Route key="i" path="investigation" element={<p>investigation tab</p>} />] })

function mock(items: unknown[], graphBody: unknown = graph) {
  server.use(
    http.get('*/api/v1/claims/:id/investigations', () => HttpResponse.json(investigationPage(items as never[]))),
    http.get('*/api/v1/investigations/:id/evidence', () => HttpResponse.json(evidence)),
    http.get('*/api/v1/investigations/:id/graph', () => HttpResponse.json(graphBody as never)),
  )
}

const frozen = investigation({ status: 'COMPLETED', done: 3, snapshotId: 'snap-1', id: INV_ID })

beforeEach(async () => {
  await signInForTests()
})

it('says nothing is frozen until an investigation has run the checks', async () => {
  mock([investigation({ status: 'WAITING_FOR_EVIDENCE' })])
  setup()
  expect(await screen.findByRole('heading', { name: 'No evidence has been frozen yet' })).toBeInTheDocument()
  await userEvent.click(screen.getByRole('link', { name: 'Go to the investigation' }))
  expect(await screen.findByText('investigation tab')).toBeInTheDocument()
})

it('groups the frozen items by kind, each with what it is in words, and never calls a model reading true', async () => {
  mock([frozen])
  setup()
  await screen.findByRole('heading', { name: 'Evidence', level: 2 })
  expect(screen.getByText(/4 items, frozen/)).toBeInTheDocument()
  expect(screen.getByText(/A value read from a document is a model.s reading, never treated as true on its own/)).toBeInTheDocument()
  const titles = screen.getAllByRole('heading', { level: 3 }).map((h) => h.textContent?.replace(/\d+$/, '').trim())
  expect(titles).toEqual(['Relationships', 'Claim record', 'Read from documents', 'Rule results', 'Checks that could not run'])
  expect(screen.getByText('Recorded by the insurer')).toBeInTheDocument()
  expect(screen.getByText('Read by a model, needs review')).toBeInTheDocument()
  expect(screen.getByText('Result of a rule')).toBeInTheDocument()
  expect(screen.getByText('Not known')).toBeInTheDocument()
})

it('shows relationships with how they were matched, and says when there are none', async () => {
  mock([frozen])
  const first = setup()
  const list = await screen.findByRole('list', { name: 'Related claims' })
  expect(within(list).getByText(/This claim/)).toBeInTheDocument()
  expect(within(list).getByText('Same vehicle')).toBeInTheDocument()
  expect(within(list).getByText(/Matched on Vehicle token by exact token/)).toBeInTheDocument()
  first.unmount()
  mock([frozen], { snapshotId: 's', nodes: [{ id: 'CLAIM', type: 'CLAIM', label: 'This claim' }], edges: [] })
  setup()
  expect(await screen.findByText(/No other claim is related to this one\. A shared garage alone is context, not a link\./)).toBeInTheDocument()
})

it('lands on the cited item and focuses it', async () => {
  mock([frozen])
  setup('#ev-RULE.HISTORICAL_RELATIONSHIP')
  await screen.findByRole('heading', { name: 'Evidence', level: 2 })
  const item = document.getElementById('ev-RULE.HISTORICAL_RELATIONSHIP')!
  expect(item).toBeInTheDocument()
  expect(document.activeElement).toBe(item)
  expect(item).toHaveAttribute('data-target', 'true')
})

it('explains a failure to load the evidence without server text', async () => {
  server.use(
    http.get('*/api/v1/claims/:id/investigations', () => HttpResponse.json(investigationPage([frozen]))),
    http.get('*/api/v1/investigations/:id/evidence', () => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'server text' }, { status: 500 })),
    http.get('*/api/v1/investigations/:id/graph', () => HttpResponse.json(graph)),
  )
  setup()
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(document.body.textContent).not.toContain('server text')
})
