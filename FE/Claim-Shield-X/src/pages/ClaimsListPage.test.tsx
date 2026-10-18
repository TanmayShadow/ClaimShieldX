import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { Route } from 'react-router'
import { page, summary } from '@/test/claims'
import { renderPage, signInForTests, Where } from '@/test/render'
import { server } from '@/test/server'
import { ClaimsListPage } from './ClaimsListPage'

type Seen = URLSearchParams[]

function listHandler(respond: (params: URLSearchParams) => Response | Promise<Response>, seen: Seen = []) {
  server.use(
    http.get('*/api/v1/claims', ({ request }) => {
      const params = new URL(request.url).searchParams
      seen.push(params)
      return respond(params)
    }),
  )
  return seen
}

const setup = (entry = '/app/claims') =>
  renderPage(<ClaimsListPage />, { path: '/app/claims', entry, extraRoutes: [<Route key="d" path="/app/claims/:id" element={<Where />} />] })

beforeEach(async () => {
  await signInForTests()
})

describe('ClaimsListPage: data', () => {
  it('asks for the newest 20 claims by default and shows each with its evidence readiness in words', async () => {
    const a = summary({ internalId: 'CLM-A', level: 'NOT_READY', blockers: 1, warnings: 4 })
    const b = summary({ internalId: 'CLM-B', currentStage: 'FNOL', level: 'READY' , blockers: 0, warnings: 0 })
    const seen = listHandler(() => HttpResponse.json(page([a, b])))
    setup()
    expect(screen.getByRole('status', { name: '' })).toHaveTextContent('Loading claims')
    const link = await screen.findByRole('link', { name: 'CLM-A' })
    expect(link).toHaveAttribute('href', `/app/claims/${a.id}`)
    expect(Object.fromEntries(seen[0]!)).toEqual({ page: '0', size: '20', sort: 'createdAt,desc' })
    const row = link.closest('tr')!
    expect(within(row).getByText('Final invoice received')).toBeInTheDocument()
    expect(within(row).getByText('Not ready')).toBeInTheDocument()
    expect(within(row).getByText('1 required document issue, 4 warnings')).toBeInTheDocument()
    expect(within(screen.getByRole('link', { name: 'CLM-B' }).closest('tr')!).getByText('Ready')).toBeInTheDocument()
    expect(screen.getByText('Showing 1 to 2 of 2 claims.')).toBeInTheDocument()
  })

  it('shows the time in the viewer’s zone with the full zone on hover', async () => {
    listHandler(() => HttpResponse.json(page([summary({ createdAt: '2026-09-10T05:00:00Z' })])))
    setup()
    const time = await screen.findByText(/10 Sept?\.? 2026, 10:30/)
    expect(time.tagName).toBe('TIME')
    expect(time).toHaveAttribute('datetime', '2026-09-10T05:00:00Z')
    expect(time.getAttribute('title')).toMatch(/GMT\+5:30|IST/)
  })

  it('keeps the table semantic: a labelled table, column headers and a row header per claim', async () => {
    listHandler(() => HttpResponse.json(page([summary()])))
    setup()
    const table = await screen.findByRole('table', { name: 'Claims in your organization' })
    expect(within(table).getAllByRole('columnheader').map((h) => h.textContent)).toEqual(['Reference', 'Stage', 'Evidence readiness', 'Created'])
    expect(within(table).getAllByRole('rowheader')).toHaveLength(1)
  })

  it('offers no search box, because the API has no search', async () => {
    listHandler(() => HttpResponse.json(page([summary()])))
    setup()
    await screen.findByRole('table')
    expect(screen.queryByRole('searchbox')).toBeNull()
    expect(screen.queryByLabelText(/search/i)).toBeNull()
  })

  it('never uses fraud or risk wording', async () => {
    listHandler(() => HttpResponse.json(page([summary(), summary({ level: 'READY_WITH_WARNINGS', blockers: 0, warnings: 2 })])))
    const { container } = setup()
    await screen.findByRole('table')
    expect(container.textContent).not.toMatch(/fraud|suspicious|risk/i)
  })
})

describe('ClaimsListPage: filters, sort and paging live in the address', () => {
  it('filters by stage, goes back to page 1, and asks the API for exactly that stage', async () => {
    const seen = listHandler(() => HttpResponse.json(page([summary()])))
    setup('/app/claims?page=2')
    await screen.findByRole('table')
    await userEvent.selectOptions(screen.getByLabelText('Claim stage'), 'FNOL')
    await waitFor(() => expect(screen.getByLabelText('Claim stage')).toHaveValue('FNOL'))
    await waitFor(() => expect(seen.at(-1)!.get('stage')).toBe('FNOL'))
    expect(seen.at(-1)!.get('page')).toBe('0')
  })

  it('sorts, reports the sort on the column, and goes back to page 1', async () => {
    const seen = listHandler(() => HttpResponse.json(page([summary()])))
    setup()
    await screen.findByRole('table')
    expect(screen.getByRole('columnheader', { name: 'Created' })).toHaveAttribute('aria-sort', 'descending')
    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'internalId,asc')
    await waitFor(() => expect(seen.at(-1)!.get('sort')).toBe('internalId,asc'))
    expect(screen.getByRole('columnheader', { name: 'Reference' })).toHaveAttribute('aria-sort', 'ascending')
    expect(screen.getByRole('columnheader', { name: 'Created' })).toHaveAttribute('aria-sort', 'none')
  })

  it('changes the page size', async () => {
    const seen = listHandler(() => HttpResponse.json(page([summary()])))
    setup()
    await screen.findByRole('table')
    await userEvent.selectOptions(screen.getByLabelText('Claims per page'), '50')
    await waitFor(() => expect(seen.at(-1)!.get('size')).toBe('50'))
  })

  it('pages forwards and back, and disables the buttons at either end', async () => {
    const seen = listHandler((p) => HttpResponse.json(page([summary()], { page: Number(p.get('page')), totalElements: 45, size: 20 })))
    setup()
    await screen.findByText('Page 1 of 3')
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('Page 2 of 3')
    expect(seen.at(-1)!.get('page')).toBe('1')
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('Page 3 of 3')
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Previous' }))
    await screen.findByText('Page 2 of 3')
  })

  it('restores the view from the address, as after a refresh or a shared link', async () => {
    const seen = listHandler(() => HttpResponse.json(page([summary()], { page: 1, totalElements: 30 })))
    setup('/app/claims?stage=ESTIMATE_RECEIVED&sort=internalId,desc&size=10&page=1')
    await screen.findByRole('table')
    expect(Object.fromEntries(seen[0]!)).toEqual({ page: '1', size: '10', sort: 'internalId,desc', stage: 'ESTIMATE_RECEIVED' })
    expect(screen.getByLabelText('Claim stage')).toHaveValue('ESTIMATE_RECEIVED')
    expect(screen.getByLabelText('Sort by')).toHaveValue('internalId,desc')
    expect(screen.getByLabelText('Claims per page')).toHaveValue('10')
  })

  it('sanitises a hostile address instead of passing it to the API', async () => {
    const seen = listHandler(() => HttpResponse.json(page([summary()])))
    setup(`/app/claims?page=-1&size=9999&sort=${encodeURIComponent('password,asc')}&stage=${encodeURIComponent('<script>')}`)
    await screen.findByRole('table')
    expect(Object.fromEntries(seen[0]!)).toEqual({ page: '0', size: '20', sort: 'createdAt,desc' })
  })

  it('hands the list’s own address to a claim, so going back returns to the same view', async () => {
    const claim = summary({ internalId: 'CLM-GO' })
    listHandler(() => HttpResponse.json(page([claim])))
    setup('/app/claims?stage=FINAL_INVOICE_RECEIVED&sort=internalId,asc')
    await userEvent.click(await screen.findByRole('link', { name: 'CLM-GO' }))
    expect(screen.getByTestId('where')).toHaveTextContent(`/app/claims/${claim.id}`)
    expect(JSON.parse(screen.getByTestId('state').textContent!)).toEqual({ listSearch: '?stage=FINAL_INVOICE_RECEIVED&sort=internalId%2Casc' })
  })
})

describe('ClaimsListPage: every state', () => {
  it('invites the first claim when there are none, with an illustration and a next step', async () => {
    listHandler(() => HttpResponse.json(page([])))
    setup()
    expect(await screen.findByRole('heading', { name: 'No claims yet' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Create the first claim' })).toHaveAttribute('href', '/app/claims/new')
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('tells an empty filter apart from an empty organization, and offers to clear it', async () => {
    listHandler((p) => HttpResponse.json(page(p.get('stage') ? [] : [summary()])))
    setup('/app/claims?stage=POST_SETTLEMENT')
    expect(await screen.findByRole('heading', { name: 'Nothing at this stage' })).toBeInTheDocument()
    expect(screen.getByText('No claims at the stage "Post settlement".')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Show all stages' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('says so when the address points past the last page, and offers the first', async () => {
    listHandler((p) => HttpResponse.json(Number(p.get('page')) > 0 ? page([], { page: 5, totalElements: 3, totalPages: 1 }) : page([summary()], { totalElements: 3 })))
    setup('/app/claims?page=5')
    expect(await screen.findByRole('heading', { name: 'There is nothing on this page' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Go to the first page' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('shows a retryable error, in words, and recovers', async () => {
    let fail = true
    listHandler(() => (fail ? HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'java.lang.NullPointerException at Secret.java:1' }, { status: 500 }) : HttpResponse.json(page([summary()]))))
    setup()
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The service had a problem.')
    expect(alert).not.toHaveTextContent(/NullPointer|Secret\.java/)
    fail = false
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('table')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('explains a permission problem without offering a pointless retry', async () => {
    listHandler(() => HttpResponse.json({ code: 'FORBIDDEN', status: 403, message: 'no' }, { status: 403 }))
    setup()
    expect(await screen.findByRole('alert')).toHaveTextContent('You do not have permission to do this.')
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull()
  })

  it('keeps the old page on screen, marked busy, while the next one loads', async () => {
    listHandler(async (p) => {
      if (p.get('page') === '1') await delay(120)
      return HttpResponse.json(page([summary({ internalId: p.get('page') === '1' ? 'CLM-NEXT' : 'CLM-FIRST' })], { page: Number(p.get('page')), totalElements: 40 }))
    })
    setup()
    await screen.findByRole('link', { name: 'CLM-FIRST' })
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    expect(screen.getByRole('link', { name: 'CLM-FIRST' })).toBeInTheDocument()
    expect(screen.getByRole('table').parentElement).toHaveAttribute('aria-busy', 'true')
    expect(await screen.findByRole('link', { name: 'CLM-NEXT' })).toBeInTheDocument()
  })

  it('refreshes on request and says when it last updated', async () => {
    let calls = 0
    listHandler(() => {
      calls++
      return HttpResponse.json(page([summary()]))
    })
    setup()
    await screen.findByRole('table')
    expect(screen.getByText(/^Updated \d{2}:\d{2}:\d{2}$/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(calls).toBe(2))
  })

  it('rejects a response that does not match the contract, rather than showing something wrong', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    listHandler(() => HttpResponse.json({ items: [{ id: 5 }], page: 0, size: 20, totalElements: 1, totalPages: 1 }))
    setup()
    expect(await screen.findByRole('alert')).toHaveTextContent('could not read')
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('sends the access token', async () => {
    let auth: string | null = null
    server.use(
      http.get('*/api/v1/claims', ({ request }) => {
        auth = request.headers.get('authorization')
        return HttpResponse.json(page([summary()]))
      }),
    )
    setup()
    await screen.findByRole('table')
    expect(auth).toMatch(/^Bearer .+/)
  })
})
