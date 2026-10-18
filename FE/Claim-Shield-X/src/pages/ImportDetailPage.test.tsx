import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { Route } from 'react-router'
import { historicalImport, IMPORT_ID, mixedRows } from '@/test/imports'
import { renderPage, signInForTests, Where } from '@/test/render'
import { server } from '@/test/server'
import { ImportDetailPage } from './ImportDetailPage'

const setup = (entry = `/app/imports/${IMPORT_ID}`) => renderPage(<ImportDetailPage />, { path: '/app/imports/:importId', entry, extraRoutes: [<Route key="l" path="/app/imports" element={<Where />} />] })

const rowsPage = (items: unknown[], extra = {}) => ({ items, page: 0, size: 50, totalElements: items.length, totalPages: items.length ? 1 : 0, ...extra })
let rowRequests: URLSearchParams[]

function mock(record = historicalImport({ status: 'COMPLETED_WITH_ERRORS', counts: { totalRows: 3, accepted: 1, rejected: 1, skippedDuplicate: 1 } }), rows: (p: URLSearchParams) => unknown = () => rowsPage(mixedRows)) {
  rowRequests = []
  server.use(
    http.get('*/api/v1/historical-imports/:id', () => HttpResponse.json(record)),
    http.get('*/api/v1/historical-imports/:id/rows', ({ request }) => {
      const p = new URL(request.url).searchParams
      rowRequests.push(p)
      return HttpResponse.json(rows(p) as never)
    }),
  )
}

beforeEach(async () => {
  await signInForTests()
})

describe('an import', () => {
  it('shows what the file was, how it ended, and the counts that add up', async () => {
    mock()
    setup()
    expect(await screen.findByRole('heading', { level: 1, name: 'Earlier claims' })).toBeInTheDocument()
    expect(screen.getByText('INSURER_DEMO')).toBeInTheDocument()
    expect(screen.getAllByText('Stored, some rows rejected').length).toBeGreaterThan(0)
    const counts = screen.getByLabelText('Row counts')
    expect(counts).toHaveTextContent('Rows in the file3')
    expect(counts).toHaveTextContent('Stored1')
    expect(counts).toHaveTextContent('Rejected1')
    expect(counts).toHaveTextContent('Already stored1')
    expect(screen.getByText('b8b00aeb66afe744…')).toBeInTheDocument()
  })

  it('lists each row with its result, and for a rejected row each problem in words, the column and the server message', async () => {
    mock()
    setup()
    const table = await screen.findByRole('table', { name: 'Rows of this import' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(4)
    expect(rows[1]).toHaveTextContent('Stored')
    expect(rows[3]).toHaveTextContent('Already stored')
    const rejected = rows[2]!
    expect(rejected).toHaveTextContent('Rejected')
    // A row that needs fixing carries the attention shape, not the done one.
    expect(within(rejected).getByText('Rejected').closest('[data-state]')).toHaveAttribute('data-state', 'attention')
    expect(within(rows[1]!).getByText('Stored').closest('[data-state]')).toHaveAttribute('data-state', 'done')
    expect(rejected).toHaveTextContent('vehicle_token')
    expect(rejected).toHaveTextContent('Required value missing')
    expect(rejected).toHaveTextContent('is required')
    expect(rejected).toHaveTextContent('incident_at')
    expect(rejected).toHaveTextContent('Not a valid date')
    expect(within(rows[1]!).getByText('Nothing')).toBeInTheDocument()
  })

  it('renders row text as plain text', async () => {
    mock(undefined, () => rowsPage([{ rowNumber: 1, status: 'REJECTED', sourceRecordId: '<b>X</b>', errors: [{ field: 'a', code: 'INVALID_VALUE', message: 'must be <i>one</i> of these' }] }]))
    setup()
    const table = await screen.findByRole('table', { name: 'Rows of this import' })
    expect(table.querySelector('b')).toBeNull()
    expect(table.querySelector('i')).toBeNull()
    expect(table).toHaveTextContent('<b>X</b>')
    expect(table).toHaveTextContent('must be <i>one</i> of these')
  })

  it('filters rows by result, in the address, and asks the server for that result', async () => {
    mock(undefined, (p) => rowsPage(p.get('status') === 'REJECTED' ? [mixedRows[1]] : mixedRows))
    const { container } = setup()
    await screen.findByRole('table', { name: 'Rows of this import' })
    await userEvent.click(screen.getByRole('button', { name: 'Rejected' }))
    await waitFor(() => expect(rowRequests.some((p) => p.get('status') === 'REJECTED')).toBe(true))
    await waitFor(() => expect(within(screen.getByRole('table', { name: 'Rows of this import' })).getAllByRole('row')).toHaveLength(2))
    expect(screen.getByRole('button', { name: 'Rejected' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-pressed', 'false')
    await userEvent.click(screen.getByRole('button', { name: 'All' }))
    await waitFor(() => expect(within(screen.getByRole('table', { name: 'Rows of this import' })).getAllByRole('row')).toHaveLength(4))
    void container
  })

  it('opens already filtered from an address, and ignores a filter it does not know', async () => {
    mock()
    setup(`/app/imports/${IMPORT_ID}?status=REJECTED`)
    await screen.findByRole('table', { name: 'Rows of this import' })
    expect(rowRequests[0]!.get('status')).toBe('REJECTED')
    expect(screen.getByRole('button', { name: 'Rejected' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('ignores an unknown filter in the address', async () => {
    mock()
    setup(`/app/imports/${IMPORT_ID}?status=EVIL&page=-3`)
    await screen.findByRole('table', { name: 'Rows of this import' })
    expect(rowRequests[0]!.get('status')).toBeNull()
    expect(rowRequests[0]!.get('page')).toBe('0')
  })

  it('says when no row has the chosen result', async () => {
    mock(undefined, (p) => rowsPage(p.get('status') ? [] : mixedRows))
    setup()
    await screen.findByRole('table', { name: 'Rows of this import' })
    await userEvent.click(screen.getByRole('button', { name: 'Already stored' }))
    expect(await screen.findByRole('heading', { name: 'No rows match' })).toBeInTheDocument()
  })

  it('pages through many rows', async () => {
    mock(historicalImport({ counts: { totalRows: 120, accepted: 120, rejected: 0, skippedDuplicate: 0 } }), (p) => rowsPage(mixedRows, { page: Number(p.get('page') ?? 0), totalPages: 3, totalElements: 120 }))
    setup()
    expect(await screen.findByText('Page 1 of 3')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText('Page 2 of 3')).toBeInTheDocument()
    expect(rowRequests.at(-1)!.get('page')).toBe('1')
  })

  it('shows a refused file as a reason, with no rows to list', async () => {
    mock(historicalImport({ status: 'FAILED', failureCode: 'INVALID_ENCODING', failureDetail: null, counts: { totalRows: 0, accepted: 0, rejected: 0, skippedDuplicate: 0 } }))
    setup()
    expect(await screen.findByText('Nothing was stored.')).toBeInTheDocument()
    expect(screen.getByText(/The file is not UTF-8 text/)).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: 'Rows of this import' })).not.toBeInTheDocument()
    expect(rowRequests).toEqual([])
  })

  it('keeps asking while the server is still working on it', async () => {
    let n = 0
    server.use(
      http.get('*/api/v1/historical-imports/:id', () => HttpResponse.json(n++ === 0 ? historicalImport({ status: 'PROCESSING' }) : historicalImport())),
      http.get('*/api/v1/historical-imports/:id/rows', () => HttpResponse.json(rowsPage(mixedRows))),
    )
    setup()
    expect(await screen.findByText('The file is being processed.')).toBeInTheDocument()
    expect(await screen.findByText('3 rows stored.', undefined, { timeout: 6000 })).toBeInTheDocument()
  }, 15_000)

  it('says it cannot find an import that is missing or belongs to another organization, the same way', async () => {
    server.use(http.get('*/api/v1/historical-imports/:id', () => HttpResponse.json({ code: 'NOT_FOUND', status: 404, message: 'x' }, { status: 404 })))
    setup()
    expect(await screen.findByText('We could not find that import.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Back to the imports' })).toHaveAttribute('href', '/app/imports')
  })

  it('does not ask the server about an address that is not an import id', async () => {
    mock()
    setup('/app/imports/not-an-id')
    expect(await screen.findByRole('heading', { level: 1, name: 'Import not found' })).toBeInTheDocument()
    expect(rowRequests).toEqual([])
  })
})
