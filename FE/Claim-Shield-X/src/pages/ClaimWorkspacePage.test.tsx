import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { detail } from '@/test/claims'
import { renderPage, signInForTests } from '@/test/render'
import { Route } from 'react-router'
import { server } from '@/test/server'
import { ClaimRecordPage } from './ClaimRecordPage'
import { ClaimWorkspacePage } from './ClaimWorkspacePage'

const path = '/app/claims/:claimId'
const url = `/app/claims/${detail.id}`

const checklist = {
  claimId: detail.id,
  stage: 'FINAL_INVOICE_RECEIVED',
  incidentType: 'COLLISION',
  readiness: 'NOT_READY',
  items: [],
  blockers: [{ code: 'DOCUMENT_MISSING', documentType: 'FINAL_INVOICE', requirement: 'REQUIRED', message: 'Final invoice has not been received.', blocks: [] }],
  warnings: [{ code: 'DOCUMENT_MISSING', documentType: 'CLAIM_FORM', requirement: 'REQUIRED', message: 'Claim form has not been received.', blocks: [] }],
}

function setup(entry: string | { pathname: string; state?: unknown } = url) {
  return renderPage(<ClaimWorkspacePage />, {
    path,
    entry,
    nested: [<Route key="i" index element={<ClaimRecordPage />} />, <Route key="d" path="documents" element={<p>documents tab content</p>} />],
  })
}

beforeEach(async () => {
  await signInForTests()
  server.use(http.get('*/api/v1/claims/*/document-requirements', () => HttpResponse.json(checklist)))
})

describe('ClaimWorkspacePage', () => {
  it('shows the stored claim, grouped, with money formatted and absent optional values marked as not provided', async () => {
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)))
    setup()
    expect(await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })).toBeInTheDocument()
    const strip = screen.getByLabelText('Claim at a glance')
    expect(strip).toHaveTextContent('Final invoice received')
    expect(strip).toHaveTextContent('Collision')
    expect(strip).toHaveTextContent('1')
    const money = screen.getByRole('heading', { level: 3, name: 'Money' }).closest('section')!
    expect(money).toHaveTextContent('₹45,000.50')
    expect(money).toHaveTextContent('₹1,000.00')
    expect(within(money).getByText('Approved estimate').nextElementSibling).toHaveTextContent('Not provided')
    expect(screen.getByText('GARAGE-DEMO-10')).toBeInTheDocument()
    expect(screen.getByText('VEH-TOKEN-1001')).toBeInTheDocument()
  })

  it('shows incident times in the viewer’s zone', async () => {
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)))
    setup()
    await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })
    expect(screen.getByText(/10 Sept?\.? 2026, 10:30/)).toBeInTheDocument()
  })

  it('tolerates a claim with every optional block missing', async () => {
    server.use(
      http.get(`*/api/v1/claims/${detail.id}`, () =>
        HttpResponse.json({ ...detail, claimant: null, policy: { policyToken: 'P' }, vehicle: { vehicleToken: 'V' }, incident: { incidentAt: detail.incident.incidentAt, incidentType: 'OTHER' }, financial: { currency: 'INR' } }),
      ),
    )
    setup()
    await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })
    expect(screen.getAllByText('Not provided').length).toBeGreaterThan(5)
  })

  it('treats a claim that is not there, or belongs to another organization, as one and the same', async () => {
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json({ code: 'NOT_FOUND', status: 404, message: 'Claim not found' }, { status: 404 })))
    setup()
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('We could not find that claim.')
    expect(alert).toHaveTextContent('may not exist, or it may belong to another organization')
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
    expect(screen.getByRole('link', { name: 'Back to the claims list' })).toHaveAttribute('href', '/app/claims')
  })

  it('does not even ask the server about an address that is not a claim id', async () => {
    const asked = vi.fn()
    server.use(
      http.get('*/api/v1/claims/*', () => {
        asked()
        return HttpResponse.json(detail)
      }),
    )
    setup('/app/claims/not-an-id')
    expect(await screen.findByRole('heading', { level: 1, name: 'Claim not found' })).toBeInTheDocument()
    expect(asked).not.toHaveBeenCalled()
  })

  it('offers a retry for a server fault and recovers', async () => {
    let fail = true
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => (fail ? HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'x' }, { status: 500 }) : HttpResponse.json(detail))))
    setup()
    expect(await screen.findByText('The service had a problem.')).toBeInTheDocument()
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })).toBeInTheDocument()
  })

  it('links back to the claims list and says documents come next', async () => {
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)))
    setup()
    await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })
    expect(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link', { name: 'Claims' })).toHaveAttribute('href', '/app/claims')
  })

  it('sends the breadcrumb back to the list view the list handed over', async () => {
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)))
    setup({ pathname: url, state: { listSearch: '?stage=FNOL&sort=internalId%2Casc&page=2' } })
    await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })
    expect(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link', { name: 'Claims' })).toHaveAttribute('href', '/app/claims?stage=FNOL&sort=internalId%2Casc&page=2')
  })

  it.each(['//evil.example', 'https://evil.example/x', '?next=//evil.example/<script>', '/app/claims', 'javascript:alert(1)', '?a b', 42, { x: 1 }])(
    'ignores a back-to-list value it did not expect (%j)',
    async (bad) => {
      server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)))
      setup({ pathname: url, state: { listSearch: bad } })
      await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })
      expect(within(screen.getByRole('navigation', { name: 'Breadcrumb' })).getByRole('link', { name: 'Claims' })).toHaveAttribute('href', '/app/claims')
    },
  )

  it('offers a tab for each section that exists, with the current one marked', async () => {
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)))
    setup()
    await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })
    const tabs = within(screen.getByRole('navigation', { name: 'Claim sections' }))
    expect(tabs.getAllByRole('link').map((a) => a.textContent)).toEqual(['Record', 'Documents', 'Investigation', 'Report', 'Evidence', 'Checks', 'Earlier claims'])
    expect(tabs.getByRole('link', { name: 'Record' })).toHaveAttribute('aria-current', 'page')
    await userEvent.click(tabs.getByRole('link', { name: 'Documents' }))
    expect(await screen.findByText('documents tab content')).toBeInTheDocument()
    expect(tabs.getByRole('link', { name: 'Documents' })).toHaveAttribute('aria-current', 'page')
  })

  it('shows the claim\u2019s evidence readiness from the checklist, and links to it', async () => {
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)))
    setup()
    expect(await screen.findByText('Not ready')).toBeInTheDocument()
    expect(screen.getByText('1 required document issue, 1 warning')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'See the checklist' })).toHaveAttribute('href', '/app/claims/' + detail.id + '/documents')
  })

  it('still shows the claim when the checklist cannot load, and offers to try again', async () => {
    server.use(
      http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)),
      http.get('*/api/v1/claims/*/document-requirements', () => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'x' }, { status: 500 })),
    )
    setup()
    expect(await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })).toBeInTheDocument()
    expect(await screen.findByText('Evidence readiness is not available right now.')).toBeInTheDocument()
    expect(screen.getByText('GARAGE-DEMO-10')).toBeInTheDocument()
  })

  it('never uses fraud or risk wording', async () => {
    server.use(http.get(`*/api/v1/claims/${detail.id}`, () => HttpResponse.json(detail)))
    const { container } = setup()
    await screen.findByRole('heading', { level: 1, name: 'CLM-DEMO-1001' })
    expect(container.textContent).not.toMatch(/fraud|suspicious|risk/i)
  })
})
