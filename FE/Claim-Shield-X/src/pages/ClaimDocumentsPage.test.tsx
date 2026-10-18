import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { Outlet, Route } from 'react-router'
import { detail } from '@/test/claims'
import { FakeXhr } from '@/test/fakeXhr'
import { renderPage, signInForTests } from '@/test/render'
import { server } from '@/test/server'
import { ClaimDocumentsPage } from './ClaimDocumentsPage'

const claimId = detail.id
const url = `/app/claims/${claimId}/documents`

type Doc = {
  id: string
  claimId: string
  documentType: string
  fileName: string
  contentType: string
  sizeBytes: number
  sha256: string
  status: string
  externalDocumentId: string | null
  supersedesDocumentId: string | null
  superseded: boolean
  receivedAt: string
}

let n = 0
const doc = (patch: Partial<Doc> = {}): Doc => {
  n++
  return {
    id: `d0000000-0000-4000-8000-${String(n).padStart(12, '0')}`,
    claimId,
    documentType: 'FINAL_INVOICE',
    fileName: `invoice-${n}.pdf`,
    contentType: 'application/pdf',
    sizeBytes: 2048,
    sha256: 'a'.repeat(64),
    status: 'RECEIVED',
    externalDocumentId: null,
    supersedesDocumentId: null,
    superseded: false,
    receivedAt: '2026-10-18T05:42:33.827448Z',
    ...patch,
  }
}

const item = (documentType: string, requirement: string, evidenceState: string, extra: Partial<{ mandatory: boolean; documentIds: string[]; blocks: string[] }> = {}) => ({
  documentType,
  requirement,
  mandatory: false,
  evidenceState,
  documentIds: [],
  blocks: [],
  ...extra,
})

const checklist = (patch: Record<string, unknown> = {}) => ({
  claimId,
  stage: 'FINAL_INVOICE_RECEIVED',
  incidentType: 'COLLISION',
  readiness: 'NOT_READY',
  items: [
    item('CLAIM_FORM', 'REQUIRED', 'NOT_RECEIVED'),
    item('DAMAGE_PHOTOGRAPH', 'OPTIONAL', 'NOT_RECEIVED', { blocks: ['REPAIR_SCOPE_REVIEW'] }),
    item('FINAL_INVOICE', 'REQUIRED', 'NOT_RECEIVED', { mandatory: true, blocks: ['INVOICE_ARITHMETIC', 'ESTIMATE_INVOICE_VARIANCE'] }),
    item('THEFT_EVIDENCE', 'NOT_APPLICABLE', 'NOT_RECEIVED'),
  ],
  blockers: [{ code: 'DOCUMENT_MISSING', documentType: 'FINAL_INVOICE', requirement: 'REQUIRED', message: 'Final invoice has not been received. Checks that cannot run without it: Invoice arithmetic check.', blocks: ['INVOICE_ARITHMETIC'] }],
  warnings: [{ code: 'DOCUMENT_MISSING', documentType: 'CLAIM_FORM', requirement: 'REQUIRED', message: 'Claim form has not been received.', blocks: [] }],
  ...patch,
})

let docs: Doc[] = []
let requirements = checklist()
let listCalls = 0

function Shell() {
  return <Outlet context={{ claim: detail }} />
}

function setup() {
  return renderPage(<Shell />, { path: '/app/claims/:claimId', entry: url, nested: [<Route key="d" path="documents" element={<ClaimDocumentsPage />} />] })
}

const withFiles = userEvent.setup({ applyAccept: false })
const pdf = (name = 'invoice.pdf', size = 1234) => {
  const f = new File(['x'.repeat(Math.min(size, 64))], name, { type: 'application/pdf' })
  Object.defineProperty(f, 'size', { value: size })
  return f
}

beforeEach(async () => {
  FakeXhr.reset()
  vi.stubGlobal('XMLHttpRequest', FakeXhr)
  await signInForTests()
  n = 0
  listCalls = 0
  docs = []
  requirements = checklist()
  server.use(
    http.get('*/api/v1/claims/:id/documents', () => {
      listCalls++
      return HttpResponse.json({ items: docs, page: 0, size: 100, totalElements: docs.length, totalPages: 1 })
    }),
    http.get('*/api/v1/claims/:id/document-requirements', () => HttpResponse.json(requirements)),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('checklist', () => {
  it('shows what the stage expects, what has arrived and which checks each gap stops', async () => {
    setup()
    const table = await screen.findByRole('table', { name: 'Documents this claim stage expects' })
    const invoice = within(table).getByRole('rowheader', { name: 'Final invoice' }).closest('tr')!
    expect(invoice).toHaveTextContent('Needed to proceed')
    expect(invoice).toHaveTextContent('Not received')
    expect(invoice).toHaveTextContent('Invoice arithmetic')
    expect(invoice).toHaveTextContent('Estimate-to-invoice variance')
    expect(within(table).getByRole('rowheader', { name: 'Claim form' }).closest('tr')).toHaveTextContent('Expected')
    expect(within(table).getByRole('rowheader', { name: 'Damage photograph' }).closest('tr')).toHaveTextContent('Repair scope review')
  })

  it('says a not-applicable document is not needed, rather than missing', async () => {
    setup()
    const row = (await screen.findByRole('rowheader', { name: 'Theft evidence' })).closest('tr')!
    expect(row).toHaveTextContent('Not needed at this stage')
    expect(row).not.toHaveTextContent('Not received')
  })

  it('explains readiness as being about evidence and shows what blocks it', async () => {
    setup()
    expect(await screen.findByText('Not ready')).toBeInTheDocument()
    expect(screen.getByText(/says nothing about the claim itself/)).toBeInTheDocument()
    expect(screen.getByText(/Final invoice has not been received\. Checks that cannot run without it/)).toBeInTheDocument()
  })

  it('says a missing document means a check could not run, never a finding', async () => {
    setup()
    expect(await screen.findByText(/A missing document means a check could not run\. It is never a finding\./)).toBeInTheDocument()
  })

  it('shows a stage with no checklist without inventing rows', async () => {
    requirements = checklist({ items: [], blockers: [], readiness: 'READY_WITH_WARNINGS', warnings: [{ code: 'NO_CHECKLIST_FOR_STAGE', message: 'No documents are expected at this stage.', blocks: [] }] })
    setup()
    expect(await screen.findByText('No documents are expected at this stage.')).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: /expects/ })).toBeNull()
  })

  it('keeps going when the checklist fails, with a retry', async () => {
    let fail = true
    server.use(http.get('*/api/v1/claims/:id/document-requirements', () => (fail ? HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'x' }, { status: 500 }) : HttpResponse.json(requirements))))
    setup()
    expect(await screen.findByText('The service had a problem.')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Kinds of document you can upload' })).toBeInTheDocument()
    fail = false
    await userEvent.click(screen.getAllByRole('button', { name: 'Try again' })[0]!)
    expect(await screen.findByRole('table', { name: 'Documents this claim stage expects' })).toBeInTheDocument()
  })
})

describe('document list', () => {
  it('invites the first upload when there are no documents', async () => {
    setup()
    expect(await screen.findByRole('heading', { name: 'No documents yet' })).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Documents stored on this claim' })).toBeNull()
  })

  it('shows each file with its kind, name, size, time and state, and marks a replaced file', async () => {
    const old = doc({ fileName: 'old-invoice.pdf', superseded: true, sizeBytes: 1536 * 1024 })
    const current = doc({ fileName: 'new-invoice.pdf', supersedesDocumentId: old.id, externalDocumentId: 'INS-77', status: 'VERIFIED' })
    docs = [current, old]
    setup()
    const table = await screen.findByRole('table', { name: 'Documents stored on this claim' })
    const rows = within(table).getAllByRole('rowheader')
    expect(rows.map((r) => within(r).getByText(/invoice\.pdf/).textContent)).toEqual(['new-invoice.pdf', 'old-invoice.pdf'])
    const newRow = rows[0]!.closest('tr')!
    expect(newRow).toHaveTextContent('Read and consistent')
    expect(newRow).toHaveTextContent('Replaces an earlier file')
    expect(newRow).toHaveTextContent('Your id: INS-77')
    const oldRow = rows[1]!.closest('tr')!
    expect(oldRow).toHaveTextContent('Replaced by a newer file')
    expect(oldRow).toHaveTextContent('1.5 MB')
    expect(within(oldRow).queryByRole('button', { name: /Replace/ })).toBeNull()
  })

  it('says stored is not read: upload and reading are separate states', async () => {
    docs = [doc({ fileName: 'a.pdf' }), doc({ fileName: 'p.png', documentType: 'DAMAGE_PHOTOGRAPH', contentType: 'image/png' })]
    setup()
    expect(await screen.findByText('Uploaded · not read yet')).toBeInTheDocument()
    expect(screen.getByText('Stored · not read')).toBeInTheDocument()
  })

  it('shows a file name as text even when it looks like markup', async () => {
    docs = [doc({ fileName: '<img src=x onerror=alert(1)>.pdf' })]
    const { container } = setup()
    expect(await screen.findByText('<img src=x onerror=alert(1)>.pdf')).toBeInTheDocument()
    expect(container.querySelector('img')).toBeNull()
  })

  it('shows a retryable error', async () => {
    let fail = true
    server.use(http.get('*/api/v1/claims/:id/documents', () => (fail ? HttpResponse.json({ code: 'SERVICE_UNAVAILABLE', status: 503, message: 'x' }, { status: 503 }) : HttpResponse.json({ items: [doc()], page: 0, size: 100, totalElements: 1, totalPages: 1 }))))
    setup()
    expect(await screen.findByText('A service this needs is unavailable.')).toBeInTheDocument()
    fail = false
    await userEvent.click(screen.getAllByRole('button', { name: 'Try again' }).at(-1)!)
    expect(await screen.findByRole('table', { name: 'Documents stored on this claim' })).toBeInTheDocument()
  })

  it('gives every action a name that says which file it is for', async () => {
    docs = [doc({ fileName: 'invoice-1.pdf' }), doc({ fileName: 'photo.png', documentType: 'DAMAGE_PHOTOGRAPH', contentType: 'image/png' })]
    setup()
    await screen.findByRole('table', { name: 'Documents stored on this claim' })
    expect(screen.getByRole('button', { name: 'Download: invoice-1.pdf' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download: photo.png' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Preview: photo.png' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /preview.*invoice-1\.pdf/ })).toBeNull()
    expect(screen.getByRole('button', { name: 'Replace: invoice-1.pdf' })).toBeInTheDocument()
  })
})

const chooser = () => document.getElementById('document-file-chooser') as HTMLInputElement

/** The whole upload as a person does it: press the row's button (the chooser opens), then pick a file. */
async function uploadFor(label: string, file: File) {
  await userEvent.click(await screen.findByRole('button', { name: `Upload ${label}` }))
  await withFiles.upload(chooser(), file)
}

describe('upload: from a row', () => {
  it('gives every row of the checklist its own Upload button, named for that document', async () => {
    setup()
    const table = await screen.findByRole('table', { name: 'Documents this claim stage expects' })
    for (const label of ['claim form', 'damage photograph', 'final invoice', 'theft evidence', 'other document']) {
      expect(within(table).getByRole('button', { name: `Upload ${label}` })).toBeEnabled()
    }
    // The button sits in the row of the document it is for.
    expect(within(within(table).getByRole('rowheader', { name: 'Final invoice' }).closest('tr')!).getByRole('button', { name: 'Upload final invoice' })).toBeInTheDocument()
  })

  it('makes the button of the one document the stage cannot do without stand out, and no other', async () => {
    setup()
    expect(await screen.findByRole('button', { name: 'Upload final invoice' })).toHaveClass(/primary/)
    expect(screen.getByRole('button', { name: 'Upload claim form' })).not.toHaveClass(/primary/)
    expect(screen.getByRole('button', { name: 'Upload damage photograph' })).not.toHaveClass(/primary/)
  })

  it('offers "Add another" on a row that already has a file', async () => {
    const stored = doc()
    docs = [stored]
    requirements = checklist({ items: [item('FINAL_INVOICE', 'REQUIRED', 'RECEIVED', { mandatory: true, documentIds: [stored.id] }), item('CLAIM_FORM', 'REQUIRED', 'NOT_RECEIVED')], blockers: [] })
    setup()
    expect(await screen.findByRole('button', { name: 'Add another final invoice' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Upload claim form' })).toBeInTheDocument()
  })

  it('has no document-type selector and no separate submit step', async () => {
    setup()
    await screen.findByRole('table', { name: 'Documents this claim stage expects' })
    expect(screen.queryByLabelText('Document type')).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Add a document' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Upload' })).toBeNull()
    expect(screen.getByText(/Press/, { selector: 'p' })).toHaveTextContent(/the file is sent as soon as you choose it/)
  })

  it('uses one hidden chooser that accepts only the kinds of file the server takes', async () => {
    setup()
    await screen.findByRole('table', { name: 'Documents this claim stage expects' })
    expect(chooser()).toHaveAttribute('type', 'file')
    expect(chooser()).toHaveAttribute('accept', expect.stringContaining('.pdf'))
    expect(chooser()).toHaveAttribute('hidden')
  })

  it('opens the chooser when a row button is pressed, and sends nothing if no file is chosen', async () => {
    setup()
    const spy = vi.spyOn(HTMLInputElement.prototype, 'click')
    await userEvent.click(await screen.findByRole('button', { name: 'Upload claim form' }))
    expect(spy.mock.instances.some((i) => (i as HTMLInputElement).id === 'document-file-chooser')).toBe(true)
    expect(FakeXhr.all).toHaveLength(0)
    spy.mockRestore()
  })

  it.each([
    ['notes.txt', 1000, 'text/plain', /Only PDF, JPEG and PNG/],
    ['empty.pdf', 0, 'application/pdf', /This file is empty/],
    ['huge.pdf', 21 * 1024 * 1024, 'application/pdf', /Files can be at most 20\.0 MB/],
    ['IMG_1.heic', 1000, 'image/heic', /Convert the photo to JPEG/],
  ])('rejects %s before sending, with a specific reason', async (name, size, type, message) => {
    setup()
    const f = new File(['x'], name, { type })
    Object.defineProperty(f, 'size', { value: size })
    await uploadFor('final invoice', f)
    const alert = await screen.findByText('That file cannot be uploaded.')
    expect(alert.closest('[role="alert"]')).toHaveTextContent(message)
    expect(FakeXhr.all).toHaveLength(0)
    await userEvent.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByText('That file cannot be uploaded.')).toBeNull()
  })

  it('uploads a file dropped on a row as that row’s kind of document', async () => {
    setup()
    const row = (await screen.findByRole('rowheader', { name: 'Damage photograph' })).closest('tr')!
    const dataTransfer = { files: [pdf('dropped.png', 3000)], types: ['Files'] }
    fireEvent.dragOver(row, { dataTransfer })
    expect(row).toHaveAttribute('data-over', 'true')
    fireEvent.drop(row, { dataTransfer })
    await waitFor(() => expect(FakeXhr.all).toHaveLength(1))
    expect(FakeXhr.last().body!.get('documentType')).toBe('DAMAGE_PHOTOGRAPH')
    expect((FakeXhr.last().body!.get('file') as File).name).toBe('dropped.png')
    expect(row).not.toHaveAttribute('data-over')
  })

  it('stops accepting files once the claim holds fifty', async () => {
    docs = Array.from({ length: 50 }, () => doc())
    setup()
    expect(await screen.findByText('This claim holds the most documents it can.')).toBeInTheDocument()
    for (const button of screen.getAllByRole('button', { name: /^Upload / })) expect(button).toBeDisabled()
  })

  it('ignores a dropped file once the claim holds fifty', async () => {
    docs = Array.from({ length: 50 }, () => doc())
    setup()
    const row = (await screen.findByRole('rowheader', { name: 'Claim form' })).closest('tr')!
    fireEvent.drop(row, { dataTransfer: { files: [pdf('late.pdf', 2000)], types: ['Files'] } })
    await new Promise((r) => setTimeout(r, 50))
    expect(FakeXhr.all).toHaveLength(0)
  })

  it('still lets a document be added when the checklist cannot be loaded', async () => {
    server.use(http.get('*/api/v1/claims/:id/document-requirements', () => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'x' }, { status: 500 })))
    setup()
    const list = await screen.findByRole('list', { name: 'Kinds of document you can upload' })
    expect(within(list).getAllByRole('button')).toHaveLength(14)
    await userEvent.click(within(list).getByRole('button', { name: 'Upload job card' }))
    await withFiles.upload(chooser(), pdf('jc.pdf', 2000))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(1))
    expect(FakeXhr.last().body!.get('documentType')).toBe('JOB_CARD')
  })

  it('keeps the insurer’s document id for the next upload only', async () => {
    setup()
    await screen.findByRole('table', { name: 'Documents this claim stage expects' })
    await userEvent.click(screen.getByText('Options for the next upload'))
    await userEvent.type(screen.getByLabelText(/Insurer.s document id/), 'INS-42')
    await uploadFor('claim form', pdf('cf.pdf', 2000))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(1))
    expect(FakeXhr.last().body!.get('externalDocumentId')).toBe('INS-42')
    expect(screen.getByLabelText(/Insurer.s document id/)).toHaveValue('')
  })
})

describe('upload: sending', () => {
  async function start(file = pdf('inv.pdf', 10_000), label = 'final invoice') {
    setup()
    await uploadFor(label, file)
    await waitFor(() => expect(FakeXhr.all).toHaveLength(1))
    return FakeXhr.last()
  }

  it('sends the file and its fields as the contract says, with the token and an idempotency key', async () => {
    const x = await start()
    expect(x.method).toBe('POST')
    expect(x.url).toBe(`/api/v1/claims/${claimId}/documents`)
    expect(x.headers.Authorization).toMatch(/^Bearer /)
    expect(x.headers['Idempotency-Key']).toMatch(/^upload-[0-9a-f-]{36}$/)
    expect(x.body!.get('documentType')).toBe('FINAL_INVOICE')
    expect((x.body!.get('file') as File).name).toBe('inv.pdf')
  })

  it('sends the kind of the row whose button was pressed', async () => {
    const x = await start(pdf('cf.pdf', 4000), 'claim form')
    expect(x.body!.get('documentType')).toBe('CLAIM_FORM')
  })

  it('shows real progress, and never calls it done until the server confirms', async () => {
    const x = await start()
    act(() => x.progress(2500, 10_000))
    expect(await screen.findByText(/Sending inv\.pdf as Final invoice: 25%/)).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Sending inv.pdf' })).toHaveValue(25)
    expect(screen.getByText(/the server has not confirmed it/)).toBeInTheDocument()

    act(() => x.progress(10_000, 10_000))
    expect(await screen.findByText(/All of inv\.pdf has been sent\. Waiting for the server to confirm/)).toBeInTheDocument()
    expect(screen.queryByText(/100%/)).toBeNull()
    expect(screen.queryByText(/is stored as/)).toBeNull()
    expect(screen.getByRole('button', { name: 'Waiting for the server' })).toHaveAttribute('aria-busy', 'true')
  })

  it('confirms only when the server answers, and then refreshes the list and the checklist', async () => {
    const x = await start()
    const before = listCalls
    const stored = doc({ fileName: 'inv.pdf' })
    docs = [stored]
    requirements = checklist({ readiness: 'READY_WITH_WARNINGS', blockers: [], items: [item('FINAL_INVOICE', 'REQUIRED', 'RECEIVED', { mandatory: true, documentIds: [stored.id] })] })
    act(() => x.reply(201, stored))
    expect(await screen.findByText('inv.pdf is stored as final invoice.')).toBeInTheDocument()
    expect(screen.getByText(/It has been stored, not read: use “Read document”/)).toBeInTheDocument()
    await waitFor(() => expect(listCalls).toBeGreaterThan(before))
    expect(await screen.findByText('Ready with warnings')).toBeInTheDocument()
    expect(await screen.findByRole('rowheader', { name: /inv\.pdf/ })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Add another final invoice' })).toBeInTheDocument()
  })

  it('does not tell someone a photograph will be read', async () => {
    await start(pdf('p.png', 3000), 'damage photograph')
    act(() => FakeXhr.last().reply(201, doc({ fileName: 'p.png', documentType: 'DAMAGE_PHOTOGRAPH', contentType: 'image/png' })))
    expect(await screen.findByText(/this kind of document is kept for people to open/)).toBeInTheDocument()
  })

  it('can be cancelled, says so, and does not pretend nothing arrived', async () => {
    const x = await start()
    act(() => x.progress(500, 10_000))
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel upload' }))
    expect(x.aborted).toBe(true)
    expect(await screen.findByText('Upload cancelled.')).toBeInTheDocument()
    expect(screen.getByText(/If the server had already received the whole file/)).toBeInTheDocument()
  })

  it('disables every other row while one upload runs, so nothing starts twice', async () => {
    await start()
    expect(screen.getByRole('button', { name: 'Upload claim form' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Upload damage photograph' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Sending' })).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByRole('button', { name: 'Cancel upload' })).toBeEnabled()
  })

  it('ignores a file dropped on a row while another upload is running', async () => {
    await start()
    const row = screen.getByRole('rowheader', { name: 'Claim form' }).closest('tr')!
    const dataTransfer = { files: [pdf('second.pdf', 2000)], types: ['Files'] }
    fireEvent.dragOver(row, { dataTransfer })
    expect(row).not.toHaveAttribute('data-over')
    fireEvent.drop(row, { dataTransfer })
    await new Promise((r) => setTimeout(r, 50))
    expect(FakeXhr.all).toHaveLength(1)
  })

  it('clears the confirmation when dismissed, and every row is ready again', async () => {
    const x = await start()
    act(() => x.reply(201, doc()))
    await userEvent.click(await screen.findByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByText(/is stored as/)).toBeNull()
    expect(screen.getByRole('button', { name: 'Upload claim form' })).toBeEnabled()
  })

  it('lets a second file go up straight after the first, without any reset', async () => {
    const x = await start()
    act(() => x.reply(201, doc()))
    await screen.findByText(/is stored as/)
    await uploadFor('claim form', pdf('cf.pdf', 2000))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(2))
    expect(FakeXhr.last().body!.get('documentType')).toBe('CLAIM_FORM')
    expect(FakeXhr.all[1]!.headers['Idempotency-Key']).not.toBe(FakeXhr.all[0]!.headers['Idempotency-Key'])
  })
})

describe('upload: failing', () => {
  async function failWith(status: number, body: unknown) {
    setup()
    await uploadFor('final invoice', pdf('inv.pdf', 4000))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(1))
    act(() => FakeXhr.last().reply(status, body))
  }

  it('points a duplicate at the file already stored, and finds it in the list', async () => {
    const existing = doc({ fileName: 'already-there.pdf' })
    docs = [existing]
    await failWith(409, { status: 409, code: 'DUPLICATE_DOCUMENT', message: 'x', details: { existingDocumentId: existing.id } })
    expect(await screen.findByText('This exact file is already stored.')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Show the stored file' }))
    const row = await screen.findByRole('rowheader', { name: /already-there\.pdf/ })
    await waitFor(() => expect(row.closest('tr')).toHaveAttribute('data-highlight', 'true'))
    await waitFor(() => expect(row.closest('tr')).toHaveFocus())
  })

  it.each([
    [415, 'UNSUPPORTED_MEDIA_TYPE', 'That file type is not accepted.'],
    [413, 'PAYLOAD_TOO_LARGE', 'That file is too large.'],
    [400, 'EMPTY_FILE', 'That file is empty.'],
    [409, 'DOCUMENT_LIMIT_REACHED', 'This claim holds the most documents it can.'],
    [403, 'FORBIDDEN', 'You do not have permission to do this.'],
  ])('explains a %i %s answer in words, with no pointless retry', async (status, code, title) => {
    await failWith(status, { status, code, message: 'server text' })
    expect(await screen.findByText(title)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
    expect(document.body.textContent).not.toContain('server text')
  })

  it('retries after a dropped connection with the same idempotency key, and says that is safe', async () => {
    setup()
    await uploadFor('final invoice', pdf('inv.pdf', 4000))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(1))
    act(() => FakeXhr.all[0]!.onerror?.())
    expect(await screen.findByText('The server could not be reached.')).toBeInTheDocument()
    expect(screen.getByText(/Trying again is safe: it cannot store the file twice/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(2))
    expect(FakeXhr.all[1]!.headers['Idempotency-Key']).toBe(FakeXhr.all[0]!.headers['Idempotency-Key'])
    expect(FakeXhr.all[1]!.body!.get('documentType')).toBe('FINAL_INVOICE')
    act(() => FakeXhr.all[1]!.reply(201, doc({ fileName: 'inv.pdf' }), { 'Idempotent-Replay': 'true' }))
    expect(await screen.findByText(/nothing was stored twice/)).toBeInTheDocument()
  })

  it('keeps the file and kind for a retry, so nothing has to be chosen again, and can be dismissed', async () => {
    await failWith(503, { status: 503, code: 'SERVICE_UNAVAILABLE', message: 'x' })
    await screen.findByText('A service this needs is unavailable.')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(2))
    expect((FakeXhr.all[1]!.body!.get('file') as File).name).toBe('inv.pdf')
    act(() => FakeXhr.all[1]!.reply(503, { status: 503, code: 'SERVICE_UNAVAILABLE', message: 'x' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByText('A service this needs is unavailable.')).toBeNull()
    expect(screen.getByRole('button', { name: 'Upload claim form' })).toBeEnabled()
  })
})

describe('replacing a file', () => {
  it('opens the chooser from the file’s own button, keeps its kind, and sends supersedesDocumentId', async () => {
    const old = doc({ fileName: 'old.pdf', documentType: 'FINAL_INVOICE' })
    docs = [old]
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Replace: old.pdf' }))
    await withFiles.upload(chooser(), pdf('new.pdf', 3000))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(1))
    expect(FakeXhr.last().body!.get('supersedesDocumentId')).toBe(old.id)
    expect(FakeXhr.last().body!.get('documentType')).toBe('FINAL_INVOICE')
    expect(await screen.findByText(/Sending new\.pdf as Final invoice, replacing old\.pdf/)).toBeInTheDocument()
  })

  it('sends no replacement marker for an ordinary upload of the same kind', async () => {
    docs = [doc({ fileName: 'old.pdf' })]
    setup()
    await uploadFor('final invoice', pdf('other.pdf', 3000))
    await waitFor(() => expect(FakeXhr.all).toHaveLength(1))
    expect(FakeXhr.last().body!.get('supersedesDocumentId')).toBeNull()
  })

  it('does nothing if no file is chosen after pressing Replace', async () => {
    docs = [doc({ fileName: 'old.pdf' })]
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Replace: old.pdf' }))
    expect(FakeXhr.all).toHaveLength(0)
  })
})

describe('download and preview', () => {
  let created: Blob[] = []
  let revoked: string[] = []
  let clicked: Array<{ href: string; download: string }> = []

  beforeEach(() => {
    created = []
    revoked = []
    clicked = []
    URL.createObjectURL = vi.fn((b: Blob) => {
      created.push(b)
      return `blob:test/${created.length}`
    })
    URL.revokeObjectURL = vi.fn((u: string) => {
      revoked.push(u)
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      clicked.push({ href: this.href, download: this.download })
    })
  })

  it('downloads through the session, saving under the stored name, and releases the temporary address', async () => {
    const d = doc({ fileName: 'invoice.pdf' })
    docs = [d]
    let auth: string | null = null
    server.use(
      http.get(`*/api/v1/claims/${claimId}/documents/${d.id}/content`, ({ request }) => {
        auth = request.headers.get('authorization')
        return new HttpResponse('%PDF-1.4 bytes', { headers: { 'Content-Type': 'application/pdf' } })
      }),
    )
    vi.useFakeTimers({ toFake: ['setTimeout'] })
    setup()
    vi.useRealTimers()
    await userEvent.click(await screen.findByRole('button', { name: 'Download: invoice.pdf' }))
    await waitFor(() => expect(clicked).toHaveLength(1))
    expect(clicked[0]).toEqual({ href: 'blob:test/1', download: 'invoice.pdf' })
    expect(auth).toMatch(/^Bearer /)
    expect(await created[0]!.text()).toBe('%PDF-1.4 bytes')
  })

  it('explains a download that fails', async () => {
    const d = doc()
    docs = [d]
    server.use(http.get(`*/api/v1/claims/${claimId}/documents/${d.id}/content`, () => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'stored file missing at /var/data/x' }, { status: 500 })))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: /^Download:/ }))
    expect(await screen.findByText('The service had a problem.')).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('/var/data')
  })

  it('previews an image through the session and releases it when hidden', async () => {
    const p = doc({ fileName: 'photo.png', documentType: 'DAMAGE_PHOTOGRAPH', contentType: 'image/png' })
    docs = [p]
    server.use(http.get(`*/api/v1/claims/${claimId}/documents/${p.id}/content`, () => new HttpResponse('PNGBYTES', { headers: { 'Content-Type': 'image/png' } })))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Preview: photo.png' }))
    const img = await screen.findByRole('img', { name: 'Preview of photo.png' })
    expect(img).toHaveAttribute('src', 'blob:test/1')
    await userEvent.click(screen.getByRole('button', { name: 'Hide preview: photo.png' }))
    expect(screen.queryByRole('img', { name: 'Preview of photo.png' })).toBeNull()
    await waitFor(() => expect(revoked).toContain('blob:test/1'))
  })

  it('offers no inline preview for a PDF, only a download', async () => {
    docs = [doc({ fileName: 'a.pdf' })]
    setup()
    await screen.findByRole('button', { name: 'Download: a.pdf' })
    expect(screen.queryByRole('button', { name: /preview/i })).toBeNull()
  })
})

describe('reading a document', () => {
  it('offers it for an uploaded invoice or estimate only', async () => {
    docs = [
      doc({ fileName: 'invoice.pdf', documentType: 'FINAL_INVOICE', status: 'RECEIVED' }),
      doc({ fileName: 'estimate.pdf', documentType: 'GARAGE_ESTIMATE', status: 'NEEDS_REVIEW' }),
      doc({ fileName: 'verified.pdf', documentType: 'FINAL_INVOICE', status: 'VERIFIED' }),
      doc({ fileName: 'photo.png', documentType: 'DAMAGE_PHOTOGRAPH', contentType: 'image/png' }),
      doc({ fileName: 'old.pdf', documentType: 'FINAL_INVOICE', superseded: true }),
    ]
    setup()
    await screen.findByRole('table', { name: 'Documents stored on this claim' })
    expect(screen.getByRole('button', { name: 'Read document: invoice.pdf' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Read again: estimate.pdf' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Read (document|again): verified\.pdf/ })).toBeNull() // a verified document is not read again
    expect(screen.queryByRole('button', { name: /^Read (document|again): photo\.png/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Read (document|again): old\.pdf/ })).toBeNull()
    expect(screen.getByRole('button', { name: 'Reading result: verified.pdf' })).toBeInTheDocument() // but its result can be looked at
  })

  it('says it is reading and for how long, never with a percentage, then shows where the document ended up', async () => {
    const d = doc({ fileName: 'invoice.pdf' })
    docs = [d]
    server.use(
      http.post(`*/api/v1/claims/${claimId}/documents/${d.id}/extraction`, async () => {
        await delay(2500)
        docs = [{ ...d, status: 'NEEDS_REVIEW' }]
        return HttpResponse.json({ documentId: d.id, documentStatus: 'NEEDS_REVIEW', extractionId: 'e1', attempt: 1 }, { status: 202 })
      }),
    )
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Read document: invoice.pdf' }))
    expect(await screen.findByText(/Reading for \d+ s\. This can take a few minutes/)).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/Reading[^.]*\d+\s*%/)
    await waitFor(() => expect(screen.getByText(/Reading for [1-9] s/)).toBeInTheDocument(), { timeout: 8000 })
    expect(await screen.findByText('Finished reading: Read · needs review.', undefined, { timeout: 9000 })).toBeInTheDocument()
    expect(await screen.findByText('Read · needs review')).toBeInTheDocument()
  }, 30_000)

  it('explains a refusal in words', async () => {
    const d = doc({ fileName: 'invoice.pdf' })
    docs = [d]
    server.use(http.post(`*/api/v1/claims/${claimId}/documents/${d.id}/extraction`, () => HttpResponse.json({ code: 'INVALID_STATE_TRANSITION', status: 409, message: 'internal state machine text' }, { status: 409 })))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Read document: invoice.pdf' }))
    expect(await screen.findByText(/not possible in the document.s current state/)).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('state machine')
  })

  it('shows what happened when a document was read, including what needs a look, without values', async () => {
    const d = doc({ fileName: 'invoice.pdf', status: 'NEEDS_REVIEW' })
    docs = [d]
    server.use(
      http.get(`*/api/v1/claims/${claimId}/documents/${d.id}/extraction`, () =>
        HttpResponse.json({
          documentId: d.id,
          documentType: 'FINAL_INVOICE',
          extractionId: 'e1',
          attempt: 2,
          status: 'SUCCEEDED',
          documentStatus: 'NEEDS_REVIEW',
          modelId: 'gemini-3.5-flash-lite',
          pageCount: 1,
          createdAt: '2026-10-18T09:12:44Z',
          fields: [{ name: 'grandTotal', state: 'EXTRACTED', value: '24,780.00' }, { name: 'incidentDate', state: 'UNKNOWN' }],
          issues: [{ code: 'IDENTIFIER_MISMATCH', severity: 'REVIEW', field: 'garageIdentifier', message: 'The garage identifier on the document does not match the claim record.' }],
        }),
      ),
    )
    setup()
    await userEvent.click(await screen.findByRole('button', { name: /Reading result: invoice\.pdf/ }))
    expect(await screen.findByText(/1 read, 1 unknown/)).toBeInTheDocument()
    expect(screen.getByText(/Unknown means the document does not show it: nothing was guessed/)).toBeInTheDocument()
    expect(screen.getByText('The garage identifier on the document does not match the claim record.')).toBeInTheDocument()
    expect(screen.getByText('A person should look')).toBeInTheDocument()
    expect(screen.getByText('gemini-3.5-flash-lite')).toBeInTheDocument()
    expect(screen.getByText(/does not show that the document is true/)).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('24,780.00')
  })

  it('explains a failed reading and whether trying again can help', async () => {
    const d = doc({ fileName: 'scan.pdf', status: 'FAILED' })
    docs = [d]
    server.use(http.get(`*/api/v1/claims/${claimId}/documents/${d.id}/extraction`, () => HttpResponse.json({ documentId: d.id, documentType: 'FINAL_INVOICE', extractionId: 'e', attempt: 1, status: 'FAILED', documentStatus: 'FAILED', createdAt: '2026-10-18T09:12:44Z', failureCode: 'UNREADABLE_FILE', retryable: false })))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: /Reading result: scan\.pdf/ }))
    expect(await screen.findByText(/The file is damaged, password-protected, or has more than 40 pages\./)).toBeInTheDocument()
    expect(screen.getByText(/Reading it again will not help/)).toBeInTheDocument()
  })

  it('says a document that was never read has not been read', async () => {
    const d = doc({ fileName: 'x.pdf', status: 'FAILED' })
    docs = [d]
    server.use(http.get(`*/api/v1/claims/${claimId}/documents/${d.id}/extraction`, () => HttpResponse.json({ code: 'NOT_FOUND', status: 404, message: 'x' }, { status: 404 })))
    setup()
    await userEvent.click(await screen.findByRole('button', { name: /Reading result: x\.pdf/ }))
    expect(await screen.findByText('This document has not been read yet.')).toBeInTheDocument()
  })
})

describe('wording', () => {
  it('never uses fraud, risk or verdict language anywhere on the tab', async () => {
    docs = [doc({ status: 'NEEDS_REVIEW' }), doc({ status: 'FAILED' }), doc({ status: 'VERIFIED' })]
    const { container } = setup()
    await screen.findByRole('table', { name: 'Documents stored on this claim' })
    expect(container.textContent).not.toMatch(/fraud|suspicious|fake|forged|risk|genuine/i)
  })
})
