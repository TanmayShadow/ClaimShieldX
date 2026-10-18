import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { Outlet, Route } from 'react-router'
import { detail } from '@/test/claims'
import { renderPage, signInForTests } from '@/test/render'
import { server } from '@/test/server'
import { ClaimHistoryPage } from './ClaimHistoryPage'

const claimId = detail.id
const Shell = () => <Outlet context={{ claim: detail }} />
const setup = () => renderPage(<Shell />, { path: '/app/claims/:claimId', entry: `/app/claims/${claimId}/history`, nested: [<Route key="h" path="history" element={<ClaimHistoryPage />} />] })

const base = { claimId, status: 'NO_HISTORICAL_DATA_AVAILABLE', dataAvailability: { historicalClaimRows: 0, garageHistoryRows: 0, latestClaimsImportFailed: false }, totalPriorClaims: 0, truncated: false, matches: [], garage: { garageId: 'G1', importedHistoricalClaims: 0, caution: 'A garage volume is context only.' }, notes: ['Nothing was compared.'] }
const serve = (body: unknown) => server.use(http.get('*/api/v1/claims/:id/history-matches', () => HttpResponse.json(body as never)))

const match = (patch = {}) => ({
  externalClaimId: 'CLM-SYN-REPEAT-B',
  claimVersion: 1,
  versionsMatched: 1,
  relation: 'SAME_VEHICLE',
  strength: 'EXACT',
  matchedFields: ['vehicleToken', 'garageId'],
  timing: 'EARLIER',
  daysBetween: 45,
  claimType: 'COLLISION',
  claimStatus: 'CLOSED_PAID',
  garageId: 'GARAGE-SYN-03',
  incidentAt: '2026-08-01T00:00:00Z',
  ambiguities: ['OUTCOME_NOT_RECORDED'],
  explanation: 'Another claim has the same vehicle token and is dated 45 days before this one.',
  ...patch,
})

beforeEach(async () => {
  await signInForTests()
})

it('says that no history was imported is not the same as no match', async () => {
  serve(base)
  setup()
  expect(await screen.findByText('No historical claims have been imported, so nothing could be compared.')).toBeInTheDocument()
  expect(screen.getByText('This is not the same as finding no match.')).toBeInTheDocument()
  expect(screen.getByText(/A garage volume is context only\./)).toBeInTheDocument()
  // No data must never look like a clean result: it carries the caution shape, not the check mark.
  const callout = screen.getByText('No historical claims have been imported, so nothing could be compared.').closest('[role="status"]')!
  expect(callout.querySelector('[data-icon]')).toHaveAttribute('data-icon', 'alert')
})

it.each([
  ['NO_MATCH_FOUND', 'No earlier claim in the imported history relates to this one.'],
  ['HISTORY_IMPORT_FAILED', 'The latest history import failed and no history is stored.'],
  ['SOMETHING_NEW', 'The history lookup returned a state this page does not know.'],
])('keeps the %s state apart in words', async (status, title) => {
  serve({ ...base, status })
  setup()
  expect(await screen.findByText(title)).toBeInTheDocument()
})

it('lists each earlier claim once with the reason, timing and what to keep in mind, and never calls it a finding', async () => {
  serve({ ...base, status: 'MATCHES_FOUND', totalPriorClaims: 2, matches: [match(), match({ externalClaimId: 'CLM-OLD', relation: 'SAME_POLICY', strength: 'CONTEXT', timing: 'SAME_DAY', daysBetween: 0, ambiguities: ['WEAK_TOKEN_ONLY', 'ORDER_UNKNOWN_SAME_DAY'] })] })
  setup()
  const list = await screen.findByRole('list', { name: 'Earlier claims that relate to this one' })
  const first = within(list).getByRole('heading', { name: 'CLM-SYN-REPEAT-B' }).closest('li')!
  expect(within(first).getByText('Exact link')).toBeInTheDocument()
  expect(within(first).getByText('Same vehicle')).toBeInTheDocument()
  expect(within(first).getByText('45 days earlier')).toBeInTheDocument()
  expect(within(first).getByText(/To keep in mind: Outcome not recorded\./)).toBeInTheDocument()
  expect(within(first).getByText('Vehicle token, garage id')).toBeInTheDocument()
  const second = within(list).getByRole('heading', { name: 'CLM-OLD' }).closest('li')!
  expect(within(second).getByText('Context only')).toBeInTheDocument()
  expect(within(second).getByText('The same day')).toBeInTheDocument()
  expect(document.body.textContent).not.toMatch(/fraud|suspicious/i)
})

it('says when the list is cut short and when matches may be out of date', async () => {
  serve({ ...base, status: 'MATCHES_FOUND', truncated: true, totalPriorClaims: 80, dataAvailability: { latestClaimsImportFailed: true }, matches: [match()] })
  setup()
  expect(await screen.findByText('The list is incomplete.')).toBeInTheDocument()
  expect(screen.getByText('The latest import failed.')).toBeInTheDocument()
})

it('explains a failure without server text', async () => {
  server.use(http.get('*/api/v1/claims/:id/history-matches', () => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'server text' }, { status: 500 })))
  setup()
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(document.body.textContent).not.toContain('server text')
})
