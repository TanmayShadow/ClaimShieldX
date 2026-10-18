import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Outlet, Route } from 'react-router'
import { detail } from '@/test/claims'
import { action, evidence, finding, gap, hypothesis, INV_ID, report } from '@/test/report'
import { renderPage, signInForTests } from '@/test/render'
import { server } from '@/test/server'
import { ClaimReportPage } from './ClaimReportPage'

const claimId = detail.id
const Shell = () => <Outlet context={{ claim: detail }} />
const setup = () =>
  renderPage(<Shell />, { path: '/app/claims/:claimId', entry: `/app/claims/${claimId}/report`, nested: [<Route key="r" path="report" element={<ClaimReportPage />} />, <Route key="e" path="evidence" element={<p>evidence tab</p>} />, <Route key="i" path="investigation" element={<p>investigation tab</p>} />] })

let posts: Array<{ key: string | null; body: unknown }>
let actions: unknown[]

function mock(r: unknown, status = 200) {
  posts = []
  actions = []
  server.use(
    http.get('*/api/v1/claims/:id/reports/latest', () => HttpResponse.json(r as never, { status })),
    http.get('*/api/v1/investigations/:id/evidence', () => HttpResponse.json(evidence)),
    http.get('*/api/v1/investigations/:id/actions', () => HttpResponse.json(actions as never)),
    http.get('*/api/v1/investigations/:id/audit', () =>
      HttpResponse.json({
        items: [
          { id: 'e1', createdAt: '2026-10-18T09:10:00Z', eventType: 'INVESTIGATION_CREATED', actorUserId: 'u-1', detail: {} },
          { id: 'e2', createdAt: '2026-10-18T09:12:44Z', eventType: 'REPORT_GENERATED', actorUserId: null, detail: {} },
        ],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      }),
    ),
  )
}

beforeEach(async () => {
  await signInForTests()
})

describe('no report yet', () => {
  it('says what a report is and where to get one', async () => {
    mock({ code: 'NOT_FOUND', status: 404, message: 'x' }, 404)
    setup()
    expect(await screen.findByRole('heading', { name: 'There is no report yet' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Go to the investigation' }))
    expect(await screen.findByText('investigation tab')).toBeInTheDocument()
  })

  it('explains a failure to load without server text', async () => {
    mock({ code: 'INTERNAL_ERROR', status: 500, message: 'server text' }, 500)
    setup()
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('server text')
  })
})

describe('a report', () => {
  it('shows the score as a rank with its sources, never as a verdict, and keeps the backend notice', async () => {
    mock(report())
    setup()
    const panel = await screen.findByRole('region', { name: 'Review priority' })
    expect(within(panel).getAllByText('20')).toHaveLength(2) // the score, and the points of the one check behind it
    expect(within(panel).getByText('of 100')).toBeInTheDocument()
    expect(within(panel).getByText('Medium review priority')).toBeInTheDocument()
    expect(within(panel).getByText('Review it by hand')).toBeInTheDocument()
    expect(within(panel).getByRole('table', { name: 'Where the points come from' })).toHaveTextContent('Historical relationship')
    expect(within(panel).getByText(/not a probability, not a finding about the claim and not a decision/)).toBeInTheDocument()
    expect(within(panel).getByRole('img')).toHaveAccessibleName(/Score 20 of 100, medium band/)
    expect(document.body.textContent).not.toMatch(/fraud|suspicious|guilty/i)
  })

  it('says no points were added when nothing was flagged', async () => {
    mock(report({ reviewPriority: { score: 0, band: 'LOW', recommendedAction: 'NO_ADDITIONAL_CHECK_INDICATED', components: [] }, findings: [gap] }))
    setup()
    expect(await screen.findByText('No check flagged anything, so no points were added.')).toBeInTheDocument()
    expect(screen.getByText('The tool indicates no further check')).toBeInTheDocument()
    expect(screen.getByText('Low review priority')).toBeInTheDocument()
  })

  it('groups findings by what they are, and labels a model hypothesis as one', async () => {
    mock(report())
    setup()
    await screen.findByRole('heading', { name: 'Earlier claims relate to this one' })
    const groups = screen.getAllByRole('heading', { level: 4 }).map((h) => h.textContent?.replace(/\d+$/, '').trim())
    expect(groups).toEqual(['To review', 'Gaps in the evidence', 'Hypotheses to verify', 'Decisions recorded'])
    const card = screen.getByRole('heading', { name: 'Recent prior claims on the same vehicle' }).closest('li')!
    expect(within(card).getByText('Hypothesis to verify')).toBeInTheDocument()
    expect(within(card).getByText(/Proposed by a model\. Independent review: supported by the evidence\. A hypothesis to verify, not a conclusion\./)).toBeInTheDocument()
    expect(within(card).getByText('Legitimate explanations and counter-evidence')).toBeInTheDocument()
    expect(within(card).getByText('The vehicle may belong to a fleet.')).toBeInTheDocument()
  })

  it('renders model text as plain text', async () => {
    mock(report())
    setup()
    const card = (await screen.findByRole('heading', { name: 'Recent prior claims on the same vehicle' })).closest('li')!
    const summary = within(card).getByText(/shares a vehicle token/)
    expect(summary.textContent).toContain('<i>quickly</i>')
    expect(summary.querySelector('i')).toBeNull()
  })

  it('opens facts to review by default and leaves the rest folded; a gap reads as an evidence gap', async () => {
    mock(report())
    setup()
    const review = (await screen.findByRole('heading', { name: 'Earlier claims relate to this one' })).closest('li')!
    expect(review.querySelector('details')).toHaveAttribute('open')
    const gapCard = screen.getByRole('heading', { name: gap.title }).closest('li')!
    expect(gapCard.querySelector('details')).not.toHaveAttribute('open')
    expect(within(gapCard).getByText('Could not be checked')).toBeInTheDocument()
    await userEvent.click(within(gapCard).getByText(/Other explanations, what to check next and the evidence/))
    expect(within(gapCard).getByText('Policy record: document missing')).toBeInTheDocument()
  })

  it('names each cited item by its label from the frozen evidence, and links to it', async () => {
    mock(report())
    setup()
    const card = (await screen.findByRole('heading', { name: 'Earlier claims relate to this one' })).closest('li')!
    const link = await within(card).findByRole('link', { name: /HISTORICAL_RELATIONSHIP: FLAG/ })
    expect(link).toHaveAttribute('href', expect.stringContaining('/evidence#ev-RULE.HISTORICAL_RELATIONSHIP'))
    // A reference that is not in the snapshot is still shown, as its code.
    expect(within(card).getByRole('link', { name: /REL\.1/ })).toBeInTheDocument()
  })

  it('says when the report is out of date and when it is partial, listing what could not be done', async () => {
    mock(report({ reportStatus: 'STALE', completeness: 'PARTIAL', unavailableSections: ['EVIDENCE_REVIEW', 'DOCUMENT_READING'], findings: [finding()] }))
    setup()
    expect(await screen.findByText('Newer evidence exists than this report was written from.')).toBeInTheDocument()
    expect(screen.getByText('This report is partial.')).toBeInTheDocument()
    expect(screen.getByText('The independent review could not answer, so no explanation was published.')).toBeInTheDocument()
    expect(screen.getByText('A document could not be read.')).toBeInTheDocument()
    expect(screen.getByText('Out of date')).toBeInTheDocument()
    expect(screen.getByText('This report is out of date.')).toBeInTheDocument()
  })

  it('shows the history of the investigation, and who did what in words', async () => {
    mock(report())
    setup()
    const list = await screen.findByRole('list', { name: 'Investigation history, oldest first' })
    expect(within(list).getByText('Investigation started')).toBeInTheDocument()
    expect(within(list).getByText('Report written')).toBeInTheDocument()
    expect(within(list).getByText('a person')).toBeInTheDocument()
    expect(within(list).getByText('the system')).toBeInTheDocument()
  })
})

describe('recording a decision', () => {
  async function fill(actionName = 'Request more evidence', why = 'The policy record is missing, so I am asking for it.') {
    await userEvent.click(await screen.findByLabelText(actionName))
    await userEvent.type(screen.getByLabelText(/^Why/), why)
  }

  it('says there is no approve or reject option, and that a decision is permanent', async () => {
    mock(report())
    setup()
    await screen.findByRole('region', { name: 'Your decision' })
    expect(screen.getByText(/None of these options approves, rejects or settles a claim/)).toBeInTheDocument()
    expect(screen.getAllByRole('radio').map((r) => r.parentElement?.textContent)).toEqual(['Request more evidence', 'Escalate for further investigation', 'No further checks needed', 'Defer the review'])
  })

  it('refuses a missing choice or a short reason before any request', async () => {
    mock(report())
    server.use(http.post('*/api/v1/investigations/:id/actions', ({ request }) => (posts.push({ key: request.headers.get('idempotency-key'), body: null }), HttpResponse.json(action(), { status: 201 }))))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Record decision' }))
    expect(screen.getByText('Choose what you decided.')).toBeInTheDocument()
    expect(screen.getByText(/Write at least 10 characters/)).toBeInTheDocument()
    await userEvent.click(screen.getByLabelText('Defer the review'))
    await userEvent.type(screen.getByLabelText(/^Why/), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Record decision' }))
    expect(screen.getByText(/Write at least 10 characters/)).toBeInTheDocument()
    expect(posts).toEqual([])
  })

  it('refuses a reason over 2000 characters', async () => {
    mock(report())
    setup()
    await userEvent.click(await screen.findByLabelText('Defer the review'))
    await userEvent.click(screen.getByLabelText(/^Why/))
    await userEvent.paste('x'.repeat(2001))
    await userEvent.click(screen.getByRole('button', { name: 'Record decision' }))
    expect(screen.getByText('Use at most 2000 characters.')).toBeInTheDocument()
  })

  it('confirms before recording, sends the action, trimmed reason and report version with an idempotency key, then lists it and clears the form', async () => {
    mock(report({ reportVersion: 3 }))
    server.use(
      http.post('*/api/v1/investigations/:id/actions', async ({ request }) => {
        posts.push({ key: request.headers.get('idempotency-key'), body: await request.json() })
        actions = [action({ reportVersion: 3 })]
        return HttpResponse.json(action({ reportVersion: 3 }), { status: 201 })
      }),
    )
    setup()
    await fill('Escalate for further investigation', '  Two earlier claims need a closer look.  ')
    await userEvent.click(screen.getByRole('button', { name: 'Record decision' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('cannot be changed or removed')
    expect(dialog).toHaveTextContent('Escalate for further investigation')
    expect(dialog).toHaveTextContent('report version 3')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Go back' }))
    expect(posts).toEqual([])
    await userEvent.click(screen.getByRole('button', { name: 'Record decision' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Record decision' }))
    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]!.body).toEqual({ action: 'ESCALATE_FOR_FURTHER_INVESTIGATION', rationale: 'Two earlier claims need a closer look.', reportVersion: 3 })
    expect(posts[0]!.key).toMatch(/^upload-[0-9a-f-]{36}$/)
    const list = await screen.findByRole('list', { name: 'Decisions recorded, oldest first' })
    expect(within(list).getByText('Request more evidence')).toBeInTheDocument()
    expect(within(list).getByText(/At that moment the tool had scored it 20 of 100 and suggested: Review it by hand/)).toBeInTheDocument()
    expect(screen.getByLabelText(/^Why/)).toHaveValue('')
  })

  it('keeps line breaks of a recorded reason and shows it as text', async () => {
    mock(report())
    actions = [action({ rationale: 'line one\n<b>line two</b>' })]
    server.use(http.get('*/api/v1/investigations/:id/actions', () => HttpResponse.json(actions as never)))
    setup()
    const text = await screen.findByText(/line one/)
    expect(text.textContent).toBe('line one\n<b>line two</b>')
    expect(text.querySelector('b')).toBeNull()
  })

  it('reuses the key after a dropped connection and takes a new one after the server answers', async () => {
    mock(report())
    let n = 0
    server.use(
      http.post('*/api/v1/investigations/:id/actions', ({ request }) => {
        n++
        posts.push({ key: request.headers.get('idempotency-key'), body: null })
        if (n === 1) return HttpResponse.error()
        if (n === 2) return HttpResponse.json({ code: 'REPORT_VERSION_MISMATCH', status: 409, message: 'x' }, { status: 409 })
        return HttpResponse.json(action(), { status: 201 })
      }),
    )
    setup()
    await fill()
    const submit = async () => {
      await userEvent.click(screen.getByRole('button', { name: 'Record decision' }))
      await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Record decision' }))
    }
    await submit()
    expect(await within(screen.getByRole('dialog')).findByText(/could not be reached/)).toBeInTheDocument()
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Record decision' }))
    expect(await within(screen.getByRole('dialog')).findByText(/A newer report exists than the one you were reading/)).toBeInTheDocument()
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Record decision' }))
    await waitFor(() => expect(posts).toHaveLength(3))
    expect(posts[1]!.key).toBe(posts[0]!.key)
    expect(posts[2]!.key).not.toBe(posts[0]!.key)
  })

  it('warns that a decision on an out-of-date report is marked as such', async () => {
    mock(report({ reportStatus: 'STALE' }))
    setup()
    expect(await screen.findByText(/Your decision will be recorded and marked as made on an out-of-date report/)).toBeInTheDocument()
  })
})

it('a decision shows that it was made on an out-of-date report', async () => {
  mock(report())
  actions = [action({ reportWasStale: true })]
  server.use(http.get('*/api/v1/investigations/:id/actions', () => HttpResponse.json(actions as never)))
  setup()
  expect(await screen.findByText(/which was already out of date/)).toBeInTheDocument()
  void INV_ID
  void hypothesis
})
