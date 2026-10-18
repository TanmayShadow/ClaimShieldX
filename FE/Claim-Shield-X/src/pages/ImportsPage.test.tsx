import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route } from 'react-router'
import { vi } from 'vitest'
import { uploadImport } from '@/apis/imports'
import { networkError, normalizeErrorBody } from '@/apis/errors'
import type { HistoricalImport } from '@/features/imports/schemas'
import { historicalImport, importPage } from '@/test/imports'
import { renderPage, signInForTests, Where } from '@/test/render'
import { server } from '@/test/server'
import { ImportsPage } from './ImportsPage'

// jsdom's FormData and File are not the ones Node's fetch understands, so the multipart request itself is covered by the browser tests
// against the real backend. Here the API function is replaced, and what the form hands it (file, kind, source, key) is what is checked.
vi.mock('@/apis/imports', async (importOriginal) => ({ ...(await importOriginal<Record<string, unknown>>()), uploadImport: vi.fn() }))
const upload = vi.mocked(uploadImport)

const setup = () => renderPage(<ImportsPage />, { path: '/app/imports', extraRoutes: [<Route key="d" path="/app/imports/:id" element={<Where />} />] })

type Post = { key: string; importType: string; source: string; fileName: string }
let posts: Post[]

function mockList(items = [historicalImport()]) {
  server.use(http.get('*/api/v1/historical-imports', () => HttpResponse.json(importPage(items))))
}

type Answer = HistoricalImport | { error: 'network' } | { error: number; code: string }

function mockPost(respond: (n: number) => Answer) {
  posts = []
  let n = 0
  upload.mockReset()
  upload.mockImplementation(async (args) => {
    n++
    posts.push({ key: args.idempotencyKey, importType: args.importType, source: args.source, fileName: args.file.name })
    const answer = respond(n)
    if ('error' in answer) throw answer.error === 'network' ? networkError() : normalizeErrorBody(answer.error, { code: answer.code, status: answer.error, message: 'server text' }, null)
    return answer
  })
}

const ok = (patch: Partial<HistoricalImport> = {}): Answer => historicalImport(patch)

const csvFile = (name = 'history.csv', content = 'a,b\n1,2\n') => new File([content], name, { type: 'text/csv' })

beforeEach(async () => {
  await signInForTests()
})

describe('the page', () => {
  it('lists earlier imports with their result in words, and says what happens with no history', async () => {
    mockList([historicalImport(), historicalImport({ id: 'i2', status: 'FAILED', failureCode: 'EMPTY_FILE', counts: { totalRows: 0, accepted: 0, rejected: 0, skippedDuplicate: 0 }, source: null })])
    setup()
    const table = await screen.findByRole('table', { name: 'Imports of your organization, newest first' })
    const rows = within(table).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('Earlier claims')
    expect(rows[1]).toHaveTextContent('INSURER_DEMO')
    expect(rows[1]).toHaveTextContent('Stored')
    expect(rows[1]).toHaveTextContent('3 stored · 0 rejected · 0 already stored')
    expect(rows[2]).toHaveTextContent('Nothing stored')
    expect(rows[2]).toHaveTextContent('Not given')
    expect(within(rows[1]!).getByRole('link')).toHaveAttribute('href', '/app/imports/dddddddd-1111-4000-8000-000000000001')
  })

  it('explains that no history is not the same as no match when nothing was imported', async () => {
    mockList([])
    setup()
    expect(await screen.findByText(/Until history is imported, a claim.s earlier-claims check says it had nothing to compare with\. That is not the same as finding no match\./)).toBeInTheDocument()
  })

  it('tells an account that is not an administrator so, and shows no form', async () => {
    server.use(http.get('*/api/v1/historical-imports', () => HttpResponse.json({ code: 'FORBIDDEN', status: 403, message: 'server text' }, { status: 403 })))
    setup()
    expect(await screen.findByRole('heading', { name: 'Only an organization administrator manages history imports' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Import the file' })).not.toBeInTheDocument()
    expect(document.body.textContent).not.toContain('server text')
    expect(document.body.textContent).not.toContain('FORBIDDEN')
  })

  it('explains any other failure to list, with a retry', async () => {
    server.use(http.get('*/api/v1/historical-imports', () => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'server text' }, { status: 500 })))
    setup()
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /try again|retry/i })).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('server text')
  })

  it('pages through the list', async () => {
    const seen: string[] = []
    server.use(
      http.get('*/api/v1/historical-imports', ({ request }) => {
        const requested = new URL(request.url).searchParams.get('page') ?? '0'
        seen.push(requested)
        return HttpResponse.json(importPage([historicalImport()], { page: Number(requested), totalPages: 2 }))
      }),
    )
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Next' }))
    await waitFor(() => expect(seen).toContain('1'))
    expect(await screen.findByText('Page 2 of 2')).toBeInTheDocument()
  })
})

describe('the form', () => {
  it('refuses what can be refused before sending', async () => {
    mockList([])
    mockPost(() => ok())
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'Import the file' }))
    expect(screen.getByText('Choose a CSV file to send.')).toBeInTheDocument()
    const input = screen.getByLabelText('File', { selector: 'input' })
    await userEvent.upload(input, new File(['x'], 'notes.txt', { type: 'text/plain' }), { applyAccept: false })
    expect(screen.getByText(/Choose a \.csv file/)).toBeInTheDocument()
    await userEvent.upload(input, csvFile('empty.csv', ''), { applyAccept: false })
    expect(screen.getByText('That file is empty.')).toBeInTheDocument()
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.csv', { type: 'text/csv' })
    await userEvent.upload(input, big, { applyAccept: false })
    expect(screen.getByText(/Imports can be at most 5\.0 MB/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await userEvent.upload(input, csvFile())
    await userEvent.type(screen.getByLabelText(/^Source label/), 'has spaces')
    expect(screen.getByText(/Use 1 to 100 letters, digits or/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    expect(posts).toEqual([])
  })

  it('hands over the file, the kind, the source and an idempotency key', async () => {
    mockList([])
    mockPost(() => ok({ source: 'INSURER_DEMO' }))
    setup()
    await userEvent.selectOptions(await screen.findByLabelText('What the file holds'), 'GARAGE_HISTORY')
    await userEvent.upload(screen.getByLabelText('File', { selector: 'input' }), csvFile('garages.csv'))
    await userEvent.type(screen.getByLabelText(/^Source label/), 'INSURER_DEMO')
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]).toMatchObject({ fileName: 'garages.csv', importType: 'GARAGE_HISTORY', source: 'INSURER_DEMO' })
    expect(posts[0]!.key).toMatch(/^upload-[0-9a-f-]{36}$/)
  })

  it('omits the source when none is given', async () => {
    mockList([])
    mockPost(() => ok({ source: null }))
    setup()
    await userEvent.upload(await screen.findByLabelText('File', { selector: 'input' }), csvFile())
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await waitFor(() => expect(posts).toHaveLength(1))
    expect(posts[0]!.source).toBe('')
  })

  it.each([
    ['COMPLETED', { counts: { totalRows: 2, accepted: 2, rejected: 0, skippedDuplicate: 0 } }, '2 rows stored.', /Claims you open from now on are compared with this history/],
    ['COMPLETED', { counts: { totalRows: 1, accepted: 0, rejected: 0, skippedDuplicate: 1 } }, 'Nothing new was stored.', /1 row had already been stored and was left as it is, so sending the same file twice changes nothing/],
    ['COMPLETED_WITH_ERRORS', { counts: { totalRows: 3, accepted: 1, rejected: 2, skippedDuplicate: 0 } }, '1 row stored, 2 rows rejected.', /rows that were valid are kept/],
    ['FAILED', { failureCode: 'INVALID_HEADER', failureDetail: 'Missing required columns: source_record_id.', counts: { totalRows: 0, accepted: 0, rejected: 0, skippedDuplicate: 0 } }, 'Nothing was stored.', /The first row does not match the template/],
    ['PROCESSING', { counts: { totalRows: 0, accepted: 0, rejected: 0, skippedDuplicate: 0 } }, 'The file is being processed.', /looks again every few seconds/],
  ])('says what a %s import did, in words', async (status, patch, title, body) => {
    mockList([])
    mockPost(() => ok({ status, ...patch }))
    setup()
    await userEvent.upload(await screen.findByLabelText('File', { selector: 'input' }), csvFile())
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    expect(await screen.findByText(title)).toBeInTheDocument()
    expect(screen.getByText(body)).toBeInTheDocument()
    if (status === 'FAILED') {
      expect(screen.getByText('Missing required columns: source_record_id.')).toBeInTheDocument()
      // A refused file is announced as a problem, never as a success.
      expect(screen.getByRole('alert')).toHaveTextContent('Nothing was stored.')
    } else {
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    }
  })

  it('links a partly rejected import to its rejected rows', async () => {
    mockList([])
    mockPost(() => ok({ status: 'COMPLETED_WITH_ERRORS', counts: { totalRows: 3, accepted: 1, rejected: 2, skippedDuplicate: 0 } }))
    setup()
    await userEvent.upload(await screen.findByLabelText('File', { selector: 'input' }), csvFile())
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    const link = await screen.findByRole('link', { name: 'See the rejected rows' })
    expect(link).toHaveAttribute('href', expect.stringMatching(/\/app\/imports\/dddddddd-1111-4000-8000-000000000001\?status=REJECTED$/))
  })

  it('lets another file be added after a result', async () => {
    mockList([])
    mockPost(() => ok())
    setup()
    await userEvent.upload(await screen.findByLabelText('File', { selector: 'input' }), csvFile())
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Add another file' }))
    expect(screen.queryByText('3 rows stored.')).not.toBeInTheDocument()
    expect(screen.getByLabelText('File', { selector: 'input' })).toHaveValue('')
  })

  it.each([
    [413, 'PAYLOAD_TOO_LARGE', /too large/],
    [422, 'IDEMPOTENCY_KEY_REUSED', /could not be repeated/],
    [400, 'VALIDATION_FAILED', /need fixing/],
    [403, 'FORBIDDEN', /do not have permission/],
  ])('explains a %i %s in words, with no server text', async (status, code, message) => {
    mockList([])
    mockPost(() => ({ error: status, code }))
    setup()
    await userEvent.upload(await screen.findByLabelText('File', { selector: 'input' }), csvFile())
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    expect(await screen.findByText(message)).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('server text')
  })

  it('reuses the key after a dropped connection, takes a new one after a refusal, and a new one for another file', async () => {
    mockList([])
    mockPost((n) => (n === 1 ? { error: 'network' } : n === 2 ? { error: 400, code: 'VALIDATION_FAILED' } : ok()))
    setup()
    const input = await screen.findByLabelText('File', { selector: 'input' })
    await userEvent.upload(input, csvFile('one.csv'))
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await screen.findByText('The server could not be reached.')
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await screen.findByText(/need fixing/)
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await waitFor(() => expect(posts).toHaveLength(3))
    expect(posts[1]!.key).toBe(posts[0]!.key)
    expect(posts[2]!.key).not.toBe(posts[0]!.key)
  })

  it('uses a new key when the file changes after a dropped connection, because the same key with another file is refused', async () => {
    mockList([])
    mockPost((n) => (n === 1 ? { error: 'network' } : ok()))
    setup()
    const input = await screen.findByLabelText('File', { selector: 'input' })
    await userEvent.upload(input, csvFile('one.csv', 'a\n'))
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await screen.findByText('The server could not be reached.')
    await userEvent.upload(input, csvFile('two.csv', 'bb\n'))
    await userEvent.click(screen.getByRole('button', { name: 'Import the file' }))
    await waitFor(() => expect(posts).toHaveLength(2))
    expect(posts[1]!.key).not.toBe(posts[0]!.key)
  })

  it('downloads the template for the chosen kind, with the session, and says if it cannot', async () => {
    mockList([])
    const asked: string[] = []
    server.use(
      http.get('*/api/v1/historical-imports/templates/:type', ({ params, request }) => {
        asked.push(`${String(params.type)}?${new URL(request.url).searchParams.get('version')}|${request.headers.get('accept')}|${request.headers.get('authorization')?.slice(0, 7)}`)
        return new HttpResponse('source_record_id,garage_id\n', { headers: { 'Content-Type': 'text/csv' } })
      }),
    )
    const created: Blob[] = []
    const realCreate = URL.createObjectURL
    URL.createObjectURL = (b: Blob | MediaSource) => (created.push(b as Blob), 'blob:test')
    URL.revokeObjectURL = () => {}
    const clicks: string[] = []
    const realClick = HTMLAnchorElement.prototype.click
    HTMLAnchorElement.prototype.click = function (this: HTMLAnchorElement) {
      clicks.push(this.download)
    }
    try {
      setup()
      await userEvent.selectOptions(await screen.findByLabelText('What the file holds'), 'GARAGE_HISTORY')
      await userEvent.click(screen.getByRole('button', { name: 'Download the garage history template' }))
      await waitFor(() => expect(clicks).toEqual(['garage-history-template-v1.csv']))
      expect(asked).toEqual(['GARAGE_HISTORY?1|text/csv|Bearer '])
      expect(await created[0]!.text()).toBe('source_record_id,garage_id\n')
    } finally {
      URL.createObjectURL = realCreate
      HTMLAnchorElement.prototype.click = realClick
    }
  })
})
