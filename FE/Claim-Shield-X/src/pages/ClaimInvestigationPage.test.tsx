import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { Outlet, Route } from 'react-router'
import { detail } from '@/test/claims'
import { investigation, investigationPage, stage, withStages } from '@/test/investigations'
import { renderPage, signInForTests } from '@/test/render'
import { server } from '@/test/server'
import { ClaimInvestigationPage } from './ClaimInvestigationPage'

const claimId = detail.id
const url = `/app/claims/${claimId}/investigation`

const requirements = (mandatoryState = 'RECEIVED') => ({
  claimId,
  stage: 'FINAL_INVOICE_RECEIVED',
  incidentType: 'COLLISION',
  readiness: mandatoryState === 'RECEIVED' ? 'READY_WITH_WARNINGS' : 'NOT_READY',
  items: [{ documentType: 'FINAL_INVOICE', requirement: 'REQUIRED', mandatory: true, evidenceState: mandatoryState, documentIds: [], blocks: [] }],
  blockers: [],
  warnings: [],
})

type Handlers = {
  list?: () => unknown[]
  one?: (id: string) => unknown
}

let calls: { list: number; one: number; post: Array<{ path: string; key: string | null; body: unknown }> }

function mock({ list = () => [], one }: Handlers = {}, mandatory = 'RECEIVED') {
  calls = { list: 0, one: 0, post: [] }
  server.use(
    http.get('*/api/v1/claims/:id/document-requirements', () => HttpResponse.json(requirements(mandatory))),
    http.get('*/api/v1/claims/:id/investigations', () => {
      calls.list++
      return HttpResponse.json(investigationPage(list() as never[]))
    }),
    http.get('*/api/v1/investigations/:id', ({ params }) => {
      calls.one++
      const items = list() as Array<{ id: string }>
      return HttpResponse.json((one ? one(String(params.id)) : items.find((i) => i.id === params.id)) as never)
    }),
  )
}

function post(pathPattern: string, respond: (body: unknown) => Response | Promise<Response>) {
  server.use(
    http.post(pathPattern, async ({ request }) => {
      const text = await request.text()
      const body = text ? JSON.parse(text) : undefined
      calls.post.push({ path: new URL(request.url).pathname, key: request.headers.get('idempotency-key'), body })
      return respond(body)
    }),
  )
}

function Shell() {
  return <Outlet context={{ claim: detail }} />
}

const setup = () =>
  renderPage(<Shell />, { path: '/app/claims/:claimId', entry: url, nested: [<Route key="i" path="investigation" element={<ClaimInvestigationPage />} />, <Route key="d" path="documents" element={<p>documents tab</p>} />] })

const route = () => screen.getByRole('group', { name: /Investigation route/ })
const node = (label: RegExp) => within(route()).getByRole('button', { name: label })

beforeEach(async () => {
  await signInForTests()
})

describe('starting', () => {
  it('invites the first investigation, says what it does and does not do, and starts one with a note and an idempotency key', async () => {
    mock()
    const queued = investigation({ status: 'QUEUED', note: 'Check before month end' })
    post('*/api/v1/claims/:id/investigations', () => HttpResponse.json(queued, { status: 202, headers: { Location: `/api/v1/investigations/${queued.id}` } }))
    setup()
    expect(await screen.findByRole('heading', { name: 'No investigation yet' })).toBeInTheDocument()
    expect(screen.getByText(/It does not decide anything about the claim/)).toBeInTheDocument()
    expect(screen.getByText(/never sent to a model/)).toBeInTheDocument()
    await userEvent.type(screen.getByLabelText(/^Note/), 'Check before month end')
    mock({ list: () => [queued] })
    post('*/api/v1/claims/:id/investigations', () => HttpResponse.json(queued, { status: 202 }))
    await userEvent.click(screen.getByRole('button', { name: 'Start investigation' }))
    await waitFor(() => expect(calls.post).toHaveLength(1))
    expect(calls.post[0]).toMatchObject({ path: `/api/v1/claims/${claimId}/investigations`, body: { note: 'Check before month end' } })
    expect(calls.post[0]!.key).toMatch(/^upload-[0-9a-f-]{36}$/)
    expect(await screen.findByText('Note: Check before month end')).toBeInTheDocument()
    expect(screen.getAllByText('Queued').length).toBeGreaterThan(0)
  })

  it('sends no body at all when there is no note', async () => {
    mock()
    const queued = investigation({ status: 'QUEUED' })
    post('*/api/v1/claims/:id/investigations', () => HttpResponse.json(queued, { status: 202 }))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Start investigation' }))
    await waitFor(() => expect(calls.post).toHaveLength(1))
    expect(calls.post[0]!.body).toBeUndefined()
  })

  it('warns that it will wait when the one document the stage cannot do without is missing', async () => {
    mock({}, 'NOT_RECEIVED')
    setup()
    expect(await screen.findByText(/will wait for it and carry on as soon as it is uploaded/)).toBeInTheDocument()
  })

  it('refuses a note over 500 characters before sending', async () => {
    mock()
    setup()
    await userEvent.click(await screen.findByLabelText(/^Note/))
    await userEvent.paste('x'.repeat(501))
    expect(await screen.findByText('Use at most 500 characters.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Start investigation' })).toBeDisabled()
  })

  it.each([
    [409, 'INVESTIGATION_ALREADY_ACTIVE', /already under way/],
    [429, 'INVESTIGATION_QUOTA_EXCEEDED', /daily limit of investigations/],
    [404, 'NOT_FOUND', /could not find/],
  ])('explains a %i %s in words', async (status, code, message) => {
    mock()
    post('*/api/v1/claims/:id/investigations', () => HttpResponse.json({ code, status, message: 'server text' }, { status }))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Start investigation' }))
    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('server text')
  })

  it('keeps saying why when the refusal swaps the start form for the investigation that is already running', async () => {
    const running = investigation({ status: 'EXTRACTING', done: 1, startedAt: '2026-10-18T09:10:05Z' })
    let someoneStartedOne = false
    mock({ list: () => (someoneStartedOne ? [running] : []) })
    post('*/api/v1/claims/:id/investigations', () => {
      someoneStartedOne = true
      return HttpResponse.json({ code: 'INVESTIGATION_ALREADY_ACTIVE', status: 409, message: 'x' }, { status: 409 })
    })
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Start investigation' }))
    expect(await screen.findByRole('heading', { name: 'Investigation in progress' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Start investigation' })).not.toBeInTheDocument()
    expect(screen.getByText('An investigation is already under way for this claim.')).toBeInTheDocument()
    expect(screen.getByText(/Only one can run at a time, so this is the one shown/)).toBeInTheDocument()
  })

  it('reuses the idempotency key after a dropped connection, and takes a new one after the server answers', async () => {
    mock()
    let n = 0
    server.use(
      http.post('*/api/v1/claims/:id/investigations', ({ request }) => {
        n++
        calls.post.push({ path: '', key: request.headers.get('idempotency-key'), body: undefined })
        if (n === 1) return HttpResponse.error()
        if (n === 2) return HttpResponse.json({ code: 'INVESTIGATION_ALREADY_ACTIVE', status: 409, message: 'x' }, { status: 409 })
        return HttpResponse.json(investigation(), { status: 202 })
      }),
    )
    setup()
    const start = await screen.findByRole('button', { name: 'Start investigation' })
    await userEvent.click(start)
    await screen.findByText('The server could not be reached.')
    await userEvent.click(screen.getByRole('button', { name: 'Start investigation' }))
    await screen.findByText(/already under way/)
    await userEvent.click(screen.getByRole('button', { name: 'Start investigation' }))
    await waitFor(() => expect(calls.post).toHaveLength(3))
    expect(calls.post[1]!.key).toBe(calls.post[0]!.key)
    expect(calls.post[2]!.key).not.toBe(calls.post[0]!.key)
  })
})

describe('the route', () => {
  it('shows all six stages in order, each with its own state in words', async () => {
    mock({ list: () => [investigation({ status: 'EXTRACTING', done: 1, startedAt: '2026-10-18T09:10:05Z' })] })
    setup()
    expect(await screen.findByRole('group', { name: 'Investigation route, 1 of 6 stages finished' })).toBeInTheDocument()
    const nodes = within(route()).getAllByRole('button')
    expect(nodes.map((n) => n.getAttribute('data-stage'))).toEqual(['PREPARING_EVIDENCE', 'EXTRACTING', 'RUNNING_RULES', 'BUILDING_HYPOTHESES', 'VALIDATING_FINDINGS', 'GENERATING_REPORT'])
    expect(node(/Evidence prepared/)).toHaveTextContent('Finished')
    expect(node(/Documents read/)).toHaveTextContent('Running now')
    expect(node(/Checks run/)).toHaveTextContent('Waiting its turn')
    for (const label of [/Explanations/, /Evidence validated/, /Report assembled/]) expect(node(label)).toHaveTextContent('Waiting its turn')
  })

  it('animates the track only while a stage is really running', async () => {
    mock({ list: () => [investigation({ status: 'EXTRACTING', done: 1 })] })
    const { container, unmount } = setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(container.querySelector('[data-flow="true"]')).not.toBeNull()
    unmount()
    mock({ list: () => [investigation({ status: 'COMPLETED', done: 3, finishedAt: '2026-10-18T09:12:00Z' })] })
    const second = setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(second.container.querySelector('[data-flow="true"]')).toBeNull()
  })

  it('does not animate a queued investigation, because nothing is running yet', async () => {
    mock({ list: () => [investigation({ status: 'QUEUED' })] })
    const { container } = setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(container.querySelector('[data-flow="true"]')).toBeNull()
    expect(screen.getAllByText('Queued').length).toBeGreaterThan(0)
  })

  it('opens on the stage that matters and lets any stage be inspected, with the current one marked', async () => {
    mock({ list: () => [investigation({ status: 'EXTRACTING', done: 1 })] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(node(/Documents read/)).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('heading', { level: 3, name: 'Documents read' })).toBeInTheDocument()
    await userEvent.click(node(/Checks run/))
    expect(node(/Checks run/)).toHaveAttribute('aria-pressed', 'true')
    expect(node(/Documents read/)).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('heading', { level: 3, name: 'Checks run' })).toBeInTheDocument()
  })

  it('says the model was deliberately not asked when nothing was flagged, instead of showing a stage that found nothing', async () => {
    const inv = withStages(investigation({ status: 'COMPLETED', done: 3, reportId: 'r-1' }), {
      BUILDING_HYPOTHESES: stage('BUILDING_HYPOTHESES', 'SKIPPED', { summary: { reason: 'NO_CONCERNS_TO_ANALYZE' } }),
    })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(node(/Explanations/)).toHaveTextContent('Skipped: nothing flagged')
    await userEvent.click(node(/Explanations/))
    expect(screen.getByText(/No rule flagged anything, so the model was not asked/)).toBeInTheDocument()
  })

  it('shows what the explanation, validation and report stages did, in counts', async () => {
    const inv = withStages(investigation({ status: 'COMPLETED', done: 3, reportId: 'r-1' }), {
      BUILDING_HYPOTHESES: stage('BUILDING_HYPOTHESES', 'OK', { summary: { hypotheses: 2, h1Rejected: 1, h2: 'OK' } }),
      VALIDATING_FINDINGS: stage('VALIDATING_FINDINGS', 'OK', { summary: { findings: 4, published: 3, rejectedHypotheses: 1 } }),
      GENERATING_REPORT: stage('GENERATING_REPORT', 'OK', { summary: { band: 'MEDIUM', score: 20, completeness: 'COMPLETE', recommendedAction: 'MANUAL_REVIEW' } }),
    })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    await userEvent.click(node(/Explanations/))
    const h = screen.getByRole('region', { name: 'Details of Explanations' })
    expect(h).toHaveTextContent('Possible explanations proposed2')
    expect(h).toHaveTextContent('Dropped before review1')
    expect(h).toHaveTextContent('Counter-evidence stepAnswered')
    await userEvent.click(node(/Evidence validated/))
    expect(screen.getByRole('region', { name: 'Details of Evidence validated' })).toHaveTextContent('Published3')
    await userEvent.click(node(/Report assembled/))
    const r = screen.getByRole('region', { name: 'Details of Report assembled' })
    expect(r).toHaveTextContent('20 of 100 (medium)')
    expect(r).toHaveTextContent('Review it by hand')
  })
})

describe('inspecting a stage', () => {
  it('shows what evidence preparation found, in the checklist’s own terms', async () => {
    const inv = withStages(investigation({ status: 'EXTRACTING', done: 1 }), {
      PREPARING_EVIDENCE: stage('PREPARING_EVIDENCE', 'OK', { summary: { readiness: 'READY_WITH_WARNINGS', blockers: [], warnings: 4, documents: { FINAL_INVOICE: 1 }, claimVersion: 1 } }),
    })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    await userEvent.click(node(/Evidence prepared/))
    const inspector = screen.getByRole('region', { name: 'Details of Evidence prepared' })
    expect(inspector).toHaveTextContent('Ready with warnings')
    expect(inspector).toHaveTextContent('4 warnings')
    expect(inspector).toHaveTextContent('Final invoice: 1')
    expect(within(inspector).getByRole('link', { name: /Open the Documents tab/ })).toBeInTheDocument()
  })

  it('lists the documents the reading stage looked at and what happened to each, as text', async () => {
    const inv = withStages(investigation({ status: 'RUNNING_RULES', done: 2 }), {
      EXTRACTING: stage('EXTRACTING', 'DEGRADED', {
        summary: { read: 1, alreadyRead: 0, needsReview: 1, failed: 1, willRetry: 0, busy: 0, documents: [{ documentId: 'd1', documentType: 'FINAL_INVOICE', outcome: 'READ', code: null }, { documentId: 'd2', documentType: 'GARAGE_ESTIMATE', outcome: 'NEEDS_REVIEW', code: 'IDENTIFIER_MISMATCH' }, { documentId: 'd3', documentType: 'JOB_CARD', outcome: 'FAILED_FOR_GOOD', code: 'UNREADABLE_FILE' }] },
      }),
    })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(node(/Documents read/)).toHaveTextContent('Finished, partly')
    await userEvent.click(node(/Documents read/))
    const inspector = screen.getByRole('region', { name: 'Details of Documents read' })
    expect(inspector).toHaveTextContent('Final invoice')
    expect(inspector).toHaveTextContent('Read now1')
    expect(inspector).toHaveTextContent('Read, needs review')
    expect(inspector).toHaveTextContent('Could not be read')
    expect(inspector).toHaveTextContent(/does not show that the document is true/)
  })

  it('shows the rule run and the counts, and says a flag is not a conclusion', async () => {
    const inv = withStages(investigation({ status: 'COMPLETED', done: 3, ruleRunId: 'rrrrrrrr-0000-4000-8000-000000000009' }), {
      RUNNING_RULES: stage('RUNNING_RULES', 'OK', { summary: { ruleRunId: 'rrrrrrrr-0000-4000-8000-000000000009', reusedExistingRun: false, pass: 5, flag: 1, incomplete: 2, notApplicable: 0, error: 0 } }),
    })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    await userEvent.click(node(/Checks run/))
    const inspector = screen.getByRole('region', { name: 'Details of Checks run' })
    expect(inspector).toHaveTextContent('rrrrrrrr-0000-4000-8000-000000000009')
    expect(inspector).toHaveTextContent('5 pass · 1 flag · 2 incomplete')
    expect(inspector).toHaveTextContent(/A flag is something to check, not a conclusion/)
    expect(inspector).toHaveTextContent(/never a negative finding/)
  })

  it('shows a stage’s problem in words, never the raw code', async () => {
    const inv = withStages(investigation({ status: 'RETRY_WAIT', done: 1, error: { code: 'MODEL_QUOTA_EXHAUSTED', message: 'x' } }), {
      EXTRACTING: stage('EXTRACTING', 'FAILED', { errorCode: 'MODEL_QUOTA_EXHAUSTED' }),
    })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    const inspector = screen.getByRole('region', { name: 'Details of Documents read' })
    expect(inspector).toHaveTextContent('The reading service reached its usage limit.')
    expect(inspector).not.toHaveTextContent('MODEL_QUOTA_EXHAUSTED')
  })

  it('shows nothing from a summary it does not understand, rather than failing', async () => {
    const inv = withStages(investigation({ status: 'RUNNING_RULES', done: 2 }), { EXTRACTING: stage('EXTRACTING', 'OK', { summary: { documents: 'not a list', surprise: { deep: true } } }) })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    await userEvent.click(node(/Documents read/))
    expect(screen.getByRole('heading', { level: 3, name: 'Documents read' })).toBeInTheDocument()
  })
})

describe('stage states that need context', () => {
  it('shows the first stage as waiting for a document while the investigation waits, not as waiting its turn', async () => {
    const inv = withStages(investigation({ status: 'WAITING_FOR_EVIDENCE', waitingFor: [{ documentType: 'CLAIM_FORM', code: 'DOCUMENT_MISSING' }] }), {
      PREPARING_EVIDENCE: stage('PREPARING_EVIDENCE', 'PENDING', { attempts: 3, startedAt: '2026-10-18T09:10:05Z', summary: { blockers: [{ code: 'DOCUMENT_MISSING', documentType: 'CLAIM_FORM' }], warnings: 1, documents: {}, readiness: 'NOT_READY' } }),
    })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(node(/Evidence prepared/)).toHaveTextContent('Waiting for a document')
    expect(node(/Evidence prepared/)).not.toHaveTextContent('Waiting its turn')
    const inspector = screen.getByRole('region', { name: 'Details of Evidence prepared' })
    expect(inspector).toHaveTextContent('Times checked')
    expect(inspector).toHaveTextContent('Not ready')
    expect(inspector).toHaveTextContent('Waiting for: Claim form (not received)')
  })

  it('shows the later stages as waiting their turn while an earlier one runs', async () => {
    mock({ list: () => [investigation({ status: 'EXTRACTING', done: 1 })] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    for (const label of [/Explanations/, /Evidence validated/, /Report assembled/]) {
      expect(node(label)).toHaveTextContent('Waiting its turn')
      expect(node(label)).not.toHaveTextContent('Not available yet')
    }
  })

  it('shows a reading stage with nothing to read as having nothing to do', async () => {
    const inv = withStages(investigation({ status: 'RUNNING_RULES', done: 1 }), {
      EXTRACTING: stage('EXTRACTING', 'SKIPPED', { summary: { busy: 0, read: 0, failed: 0, documents: [], willRetry: 0, alreadyRead: 0, needsReview: 0 } }),
    })
    mock({ list: () => [inv] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(node(/Documents read/)).toHaveTextContent('Nothing to do')
    await userEvent.click(node(/Documents read/))
    expect(screen.getByText(/No invoice or estimate needed reading, so this stage had nothing to do\./)).toBeInTheDocument()
  })
})

describe('what an investigation can be doing', () => {
  it('says waiting for a document is waiting, not failure, names the document, and links to where to add it', async () => {
    mock({ list: () => [investigation({ status: 'WAITING_FOR_EVIDENCE', waitingFor: [{ documentType: 'FINAL_INVOICE', code: 'DOCUMENT_MISSING' }] })] })
    setup()
    expect(await screen.findByText('Waiting for a document. This is not a failure.')).toBeInTheDocument()
    expect(screen.getAllByText(/Final invoice \(not received\)/).length).toBeGreaterThan(0)
    expect(screen.getByText(/resumes at once/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Cancel investigation' })).toBeInTheDocument()
  })

  it('explains a retry that is scheduled, with the time and the attempt, and says finished work is kept', async () => {
    mock({ list: () => [investigation({ status: 'RETRY_WAIT', attempt: 2, nextRetryAt: '2026-10-18T09:20:30Z', error: { code: 'MODEL_UNAVAILABLE', message: 'x' } })] })
    setup()
    expect(await screen.findByText('A temporary problem. It will try again.')).toBeInTheDocument()
    expect(screen.getByText(/The reading service was unavailable\./)).toBeInTheDocument()
    expect(screen.getByText(/Attempt 2 of 3 will start/)).toBeInTheDocument()
    expect(screen.getByText(/Work already finished is kept/)).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('MODEL_UNAVAILABLE')
  })

  it('shows a failure, in words, with a way to try again that keeps finished stages', async () => {
    const inv = withStages(investigation({ status: 'FAILED', done: 1, error: { code: 'STORAGE_UNAVAILABLE', message: 'x' }, finishedAt: '2026-10-18T09:15:00Z' }), { EXTRACTING: stage('EXTRACTING', 'FAILED', { errorCode: 'STORAGE_UNAVAILABLE' }) })
    mock({ list: () => [inv] })
    setup()
    expect(await screen.findByText('The investigation could not finish.')).toBeInTheDocument()
    expect(screen.getAllByText(/The stored files could not be reached\./).length).toBeGreaterThan(0)
    expect(screen.getByText(/It stopped at "documents read"/)).toBeInTheDocument()
    expect(screen.getByText(/keeps the stages that finished and runs the one that failed/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel investigation' })).toBeNull()
  })

  it('retries with an idempotency key and shows the investigation queued again', async () => {
    let current = withStages(investigation({ status: 'FAILED', done: 1, error: { code: 'STORAGE_UNAVAILABLE', message: 'x' } }), { EXTRACTING: stage('EXTRACTING', 'FAILED', { errorCode: 'STORAGE_UNAVAILABLE' }) })
    mock({ list: () => [current], one: () => current })
    post('*/api/v1/investigations/:id/retry', () => {
      current = investigation({ status: 'QUEUED', done: 1 })
      return HttpResponse.json(current, { status: 202 })
    })
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(calls.post).toHaveLength(1))
    expect(calls.post[0]).toMatchObject({ path: `/api/v1/investigations/${current.id}/retry` })
    expect(calls.post[0]!.key).toMatch(/^upload-/)
    await waitFor(() => expect(screen.getAllByText('Queued').length).toBeGreaterThan(0))
  })

  it('points a finished investigation at its report, and says the score is not a finding', async () => {
    mock({ list: () => [investigation({ status: 'COMPLETED', done: 3, finishedAt: '2026-10-18T09:12:00Z', ruleRunId: 'r1', reportId: 'r-1', reportStatus: 'CURRENT' })] })
    setup()
    expect(await screen.findByText('The investigation finished.')).toBeInTheDocument()
    expect(screen.getByText(/A report was written\. Its review-priority score ranks where a person should look first\. It is not a finding about the claim\./)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Open the report' })).toHaveAttribute('href', expect.stringMatching(/\/report$/))
    expect(screen.getByText('6 of 6')).toBeInTheDocument()
  })

  it('does not offer a report that was never written', async () => {
    mock({ list: () => [investigation({ status: 'COMPLETED', done: 3, finishedAt: '2026-10-18T09:12:00Z' })] })
    setup()
    expect(await screen.findByText(/No report was written for this investigation\./)).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Open the report' })).not.toBeInTheDocument()
  })

  it('says when part of a stage could not be done', async () => {
    const inv = withStages(investigation({ status: 'COMPLETED', done: 3, finishedAt: '2026-10-18T09:12:00Z' }), { EXTRACTING: stage('EXTRACTING', 'DEGRADED') })
    mock({ list: () => [inv] })
    setup()
    expect(await screen.findByText(/part of one stage could not be done/)).toBeInTheDocument()
  })

  it('shows a cancelled investigation as cancelled, and offers a new one', async () => {
    mock({ list: () => [investigation({ status: 'CANCELLED', finishedAt: '2026-10-18T09:12:00Z' })] })
    setup()
    expect(await screen.findByText('This investigation was cancelled.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Start another investigation' })).toBeInTheDocument()
  })

  it('offers no new investigation while one is unfinished', async () => {
    mock({ list: () => [investigation({ status: 'EXTRACTING', done: 1 })] })
    setup()
    await screen.findByRole('group', { name: /Investigation route/ })
    expect(screen.queryByRole('heading', { name: 'Start another investigation' })).toBeNull()
  })
})

describe('cancelling', () => {
  it('asks first, says what it means, and sends nothing if the person keeps it running', async () => {
    mock({ list: () => [investigation({ status: 'EXTRACTING', done: 1 })] })
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel investigation' }))
    const dialog = screen.getByRole('dialog', { name: 'Cancel this investigation?' })
    expect(dialog).toHaveTextContent('Work already finished is kept')
    expect(dialog).toHaveTextContent('next safe point')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Keep it running' }))
    expect(calls.post).toHaveLength(0)
  })

  it('cancels with an idempotency key and shows the result', async () => {
    let current = investigation({ status: 'EXTRACTING', done: 1 })
    mock({ list: () => [current], one: () => current })
    post('*/api/v1/investigations/:id/cancel', () => {
      current = investigation({ status: 'CANCELLED', finishedAt: '2026-10-18T09:12:00Z' })
      return HttpResponse.json(current)
    })
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel investigation' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel investigation' }))
    await waitFor(() => expect(calls.post).toHaveLength(1))
    expect(calls.post[0]).toMatchObject({ path: `/api/v1/investigations/${current.id}/cancel` })
    expect(calls.post[0]!.key).toMatch(/^upload-/)
    expect(await screen.findByText('This investigation was cancelled.')).toBeInTheDocument()
  })

  it('shows a cancel in progress as stopping, and offers no second cancel', async () => {
    mock({ list: () => [investigation({ status: 'EXTRACTING', done: 1, cancelRequested: true })] })
    setup()
    expect(await screen.findByText('Cancel requested.')).toBeInTheDocument()
    expect(screen.getAllByText('Stopping at the next safe point').length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: 'Cancel investigation' })).toBeNull()
  })

  it('explains a cancel that is too late', async () => {
    mock({ list: () => [investigation({ status: 'RUNNING_RULES', done: 2 })] })
    post('*/api/v1/investigations/:id/cancel', () => HttpResponse.json({ code: 'INVALID_STATE_TRANSITION', status: 409, message: 'x' }, { status: 409 }))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel investigation' }))
    await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel investigation' }))
    expect(await screen.findByText(/not possible in the document.s current state|not possible/)).toBeInTheDocument()
  })
})

describe('polling', () => {
  it('follows an investigation as it moves and stops asking once it ends', async () => {
    let step = 0
    const steps = [investigation({ status: 'EXTRACTING', done: 1 }), investigation({ status: 'RUNNING_RULES', done: 2 }), investigation({ status: 'COMPLETED', done: 3, finishedAt: '2026-10-18T09:12:00Z' })]
    mock({ list: () => [steps[Math.min(step, 2)]!], one: () => steps[Math.min(step++, 2)] })
    setup()
    expect(await screen.findByText('Running: documents read')).toBeInTheDocument()
    expect(await screen.findByText('Running: checks run', undefined, { timeout: 9000 })).toBeInTheDocument()
    expect(await screen.findByText('The investigation finished.', undefined, { timeout: 9000 })).toBeInTheDocument()
    const settled = calls.one
    await delay(2600)
    expect(calls.one).toBe(settled)
  }, 30_000)

  it('says when it last looked and can be refreshed by hand', async () => {
    mock({ list: () => [investigation({ status: 'COMPLETED', done: 3, finishedAt: '2026-10-18T09:12:00Z' })] })
    setup()
    expect(await screen.findByText(/^Last updated \d{2}:\d{2}:\d{2}$/)).toBeInTheDocument()
    const before = calls.one
    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(calls.one).toBeGreaterThan(before))
  })

  it('keeps what it last received and says it may be out of date when a check fails', async () => {
    let fail = false
    const inv = investigation({ status: 'WAITING_FOR_EVIDENCE', waitingFor: [{ documentType: 'FINAL_INVOICE', code: 'DOCUMENT_MISSING' }] })
    mock({ list: () => [inv] })
    server.use(http.get('*/api/v1/investigations/:id', () => (fail ? HttpResponse.error() : HttpResponse.json(inv))))
    setup()
    await screen.findByText('Waiting for a document. This is not a failure.')
    const refresh = await screen.findByRole('button', { name: 'Refresh' })
    fail = true
    await userEvent.click(refresh)
    expect(await screen.findByText('This view may be out of date.')).toBeInTheDocument()
    expect(screen.getByText('Waiting for a document. This is not a failure.')).toBeInTheDocument()
  })

  it('announces the overall state politely, as text, without announcing every poll', async () => {
    mock({ list: () => [investigation({ status: 'EXTRACTING', done: 1 })] })
    setup()
    const live = (await screen.findByText('Investigation: Running: documents read. 1 of 6 stages finished.')).closest('[aria-live]')
    expect(live).toHaveAttribute('aria-live', 'polite')
  })
})

describe('history', () => {
  it('lists earlier investigations and shows one when asked', async () => {
    const newer = investigation({ id: 'bbbbbbbb-0000-4000-8000-000000000002', status: 'COMPLETED', done: 3, createdAt: '2026-10-18T10:00:00Z', finishedAt: '2026-10-18T10:02:00Z' })
    const older = investigation({ id: 'bbbbbbbb-0000-4000-8000-000000000001', status: 'CANCELLED', createdAt: '2026-10-18T08:00:00Z', finishedAt: '2026-10-18T08:01:00Z' })
    mock({ list: () => [newer, older] })
    setup()
    const table = await screen.findByRole('table', { name: 'Investigations of this claim, newest first' })
    expect(within(table).getAllByRole('rowheader')).toHaveLength(2)
    expect(screen.getByText('The investigation finished.')).toBeInTheDocument()
    await userEvent.click(within(table).getByRole('button', { name: /Show the investigation queued/ }))
    expect(await screen.findByText('This investigation was cancelled.')).toBeInTheDocument()
  })
})

describe('wording', () => {
  it('never uses fraud, verdict or percentage language', async () => {
    mock({ list: () => [investigation({ status: 'COMPLETED', done: 3, finishedAt: '2026-10-18T09:12:00Z' })] })
    const { container } = setup()
    await screen.findByText('The investigation finished.')
    expect(container.textContent).not.toMatch(/fraud|suspicious|verdict|guilty|risk score|\d+\s*%/i)
  })
})
