import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Outlet, Route } from 'react-router'
import { detail } from '@/test/claims'
import { DOC_ID, result, ruleRun } from '@/test/checks'
import { renderPage, signInForTests } from '@/test/render'
import { server } from '@/test/server'
import { ClaimChecksPage } from './ClaimChecksPage'

const claimId = detail.id

function Shell() {
  return <Outlet context={{ claim: detail }} />
}

const setup = () =>
  renderPage(<Shell />, { path: '/app/claims/:claimId', entry: `/app/claims/${claimId}/checks`, nested: [<Route key="c" path="checks" element={<ClaimChecksPage />} />, <Route key="d" path="documents" element={<p>documents tab</p>} />, <Route key="r" index element={<p>record tab</p>} />] })

const documents = () =>
  http.get('*/api/v1/claims/:id/documents', () =>
    HttpResponse.json({ items: [{ id: DOC_ID, claimId, documentType: 'FINAL_INVOICE', fileName: 'invoice-0412.pdf', contentType: 'application/pdf', sizeBytes: 100, sha256: 'x', status: 'UPLOADED', superseded: false, receivedAt: '2026-10-18T09:00:00Z' }], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
  )
const latest = (body: unknown, status = 200) => http.get('*/api/v1/claims/:id/rule-runs/latest', () => HttpResponse.json(body as never, { status }))

beforeEach(async () => {
  await signInForTests()
})

describe('before any run', () => {
  it('invites the first run and shows the results once it is stored', async () => {
    let posted = 0
    server.use(
      documents(),
      latest({ code: 'NOT_FOUND', status: 404, message: 'x' }, 404),
      http.post('*/api/v1/claims/:id/rule-runs', () => {
        posted++
        return HttpResponse.json(ruleRun(), { status: 201 })
      }),
    )
    setup()
    expect(await screen.findByRole('heading', { name: 'The checks have not been run yet' })).toBeInTheDocument()
    expect(screen.getByText(/No model is involved, and nothing is decided about the claim/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Run the checks' }))
    expect(await screen.findByRole('heading', { name: 'Checks on this claim' })).toBeInTheDocument()
    expect(posted).toBe(1)
  })

  it('explains a failed first run and offers to try again', async () => {
    server.use(documents(), latest({ code: 'NOT_FOUND', status: 404, message: 'x' }, 404), http.post('*/api/v1/claims/:id/rule-runs', () => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'server text' }, { status: 500 })))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Run the checks' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('server text')
  })
})

describe('a stored run', () => {
  it('leads with what to look at, in words and shapes, and counts every outcome', async () => {
    server.use(documents(), latest(ruleRun()))
    setup()
    const list = await screen.findByRole('list', { name: 'Rule results' })
    const titles = within(list).getAllByRole('heading', { level: 3 }).map((h) => h.textContent)
    expect(titles).toEqual(['Estimate-to-invoice variance', 'Required evidence', 'Date consistency', 'Invoice arithmetic'])
    const counts = screen.getByRole('list', { name: 'Results by outcome' })
    for (const text of ['1 flagged', '1 incomplete', '1 passed', '1 not applicable']) expect(within(counts).getByText(text)).toBeInTheDocument()
    expect(screen.getByText('Flag · review')).toBeInTheDocument()
    expect(screen.getByText('Incomplete')).toBeInTheDocument()
    expect(screen.getByText(/It is not a conclusion about the claim/)).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/fraud/i)
  })

  it('opens a flag with the other explanations, the cited file by name, the quote as plain text, and humanized values', async () => {
    server.use(documents(), latest(ruleRun()))
    setup()
    const card = (await screen.findByRole('heading', { name: 'Estimate-to-invoice variance' })).closest('li')!
    expect(within(card).getByText('Other explanations to rule out')).toBeInTheDocument()
    expect(within(card).getByText('Supplementary damage or repairs found after the estimate was made.')).toBeInTheDocument()
    expect(within(card).getByRole('button', { name: /invoice-0412\.pdf · Grand total/ })).toBeInTheDocument()
    const quote = within(card).getByText(/Grand total 62,510\.00/)
    expect(quote.textContent).toContain('<b>bold</b>')
    expect(quote.querySelector('b')).toBeNull()
    expect(within(card).getByText('Invoice total')).toBeInTheDocument()
    expect(within(card).getByText('62510.00')).toBeInTheDocument()
    expect(within(card).getByText('Garage estimate document')).toBeInTheDocument()
    expect(within(card).getByText('Not recorded')).toBeInTheDocument()
    expect(within(card).getByText('Yes')).toBeInTheDocument()
  })

  it('keeps routine results folded and states what a missing document means', async () => {
    server.use(documents(), latest(ruleRun()))
    setup()
    const card = (await screen.findByRole('heading', { name: 'Required evidence' })).closest('li')!
    expect(card.querySelector('details')).not.toHaveAttribute('open')
    expect(within(card).getByText(/never a negative finding/)).toBeInTheDocument()
    await userEvent.click(within(card).getByText(/Evidence, values and what else could explain it/))
    expect(within(card).getByText('Claim form: document missing')).toBeInTheDocument()
  })

  it('shows an outcome it does not know as unknown, not as a pass', async () => {
    server.use(documents(), latest(ruleRun({ results: [result({ id: 'r-new', label: 'A new rule', outcome: 'SOMETHING_NEW', explanation: 'x' })] })))
    setup()
    await screen.findByRole('heading', { name: 'A new rule' })
    expect(screen.getByText('Not recognised')).toBeInTheDocument()
  })

  it('flags a doubtful reading in words', async () => {
    server.use(documents(), latest(ruleRun({ results: [result({ id: 'r-d', label: 'Doubtful one', reliability: 'DOUBTFUL', reliabilityReasons: ['NO_SOURCE_QUOTE'] })] })))
    setup()
    await screen.findByRole('heading', { name: 'Doubtful one' })
    expect(screen.getByText('Based on a doubtful reading')).toBeInTheDocument()
    expect(screen.getByText(/The reading behind this was doubtful: No source quote\./)).toBeInTheDocument()
  })

  it('says nothing changed when the server returns the stored run, and that a new run was stored otherwise', async () => {
    let reused = true
    server.use(
      documents(),
      latest(ruleRun()),
      http.post('*/api/v1/claims/:id/rule-runs', () => HttpResponse.json(ruleRun({ reusedExistingRun: reused }), { status: reused ? 200 : 201 })),
    )
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Check again' }))
    expect(await screen.findByText('Nothing has changed since this run.')).toBeInTheDocument()
    reused = false
    await userEvent.click(screen.getByRole('button', { name: 'Check again' }))
    expect(await screen.findByText('The claim changed, so these checks were run again.')).toBeInTheDocument()
    expect(screen.queryByText('Nothing has changed since this run.')).not.toBeInTheDocument()
  })

  it('cites a document as plain text with no link when the file is unknown', async () => {
    server.use(http.get('*/api/v1/claims/:id/documents', () => HttpResponse.json({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 })), latest(ruleRun()))
    setup()
    const card = (await screen.findByRole('heading', { name: 'Estimate-to-invoice variance' })).closest('li')!
    expect(within(card).getByRole('button', { name: /Document · Grand total/ })).toBeInTheDocument()
  })

  it('goes to the Documents tab from a document citation', async () => {
    server.use(documents(), latest(ruleRun()))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: /invoice-0412\.pdf · Grand total/ }))
    expect(await screen.findByText('documents tab')).toBeInTheDocument()
  })
})

it('opens the claim record from a claim-field citation and names the field plainly', async () => {
  server.use(documents(), latest(ruleRun()))
  setup()
  await userEvent.click(await screen.findByRole('button', { name: /Initial estimate amount/ }))
  expect(await screen.findByText('record tab')).toBeInTheDocument()
})

it('explains a failure to load the checks, without server text', async () => {
  server.use(documents(), http.get('*/api/v1/claims/:id/rule-runs/latest', () => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'server text' }, { status: 500 })))
  setup()
  expect(await screen.findByRole('alert')).toBeInTheDocument()
  expect(document.body.textContent).not.toContain('server text')
})
