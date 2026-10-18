import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { delay, http, HttpResponse } from 'msw'
import { Route } from 'react-router'
import { detail } from '@/test/claims'
import { renderPage, signInForTests, Where } from '@/test/render'
import { server } from '@/test/server'
import { ClaimNewPage } from './ClaimNewPage'

const setup = () => renderPage(<ClaimNewPage />, { path: '/app/claims/new', extraRoutes: [<Route key="d" path="/app/claims/:id" element={<Where />} />] })

async function fillRequired() {
  await userEvent.type(screen.getByLabelText('Claim reference'), 'CLM-DEMO-1001')
  await userEvent.selectOptions(screen.getByLabelText('Claim stage'), 'FINAL_INVOICE_RECEIVED')
  await userEvent.type(screen.getByLabelText('Policy token'), 'POL-TOKEN-1001')
  await userEvent.type(screen.getByLabelText('Garage id'), 'GARAGE-DEMO-10')
  await userEvent.type(screen.getByLabelText('Vehicle token'), 'VEH-TOKEN-1001')
  await userEvent.type(screen.getByLabelText('Incident date and time'), '2026-09-10T10:30')
  await userEvent.selectOptions(screen.getByLabelText('Incident type'), 'COLLISION')
}

const submit = () => userEvent.click(screen.getByRole('button', { name: 'Create claim' }))

function capture(respond: () => Response | Promise<Response> = () => HttpResponse.json(detail, { status: 201 })) {
  const bodies: unknown[] = []
  const auth: Array<string | null> = []
  server.use(
    http.post('*/api/v1/claims', async ({ request }) => {
      bodies.push(await request.json())
      auth.push(request.headers.get('authorization'))
      return respond()
    }),
  )
  return { bodies, auth }
}

beforeEach(async () => {
  await signInForTests()
})

describe('ClaimNewPage: the form', () => {
  it('groups the fields, labels every one, and says which may be blank', () => {
    setup()
    for (const legend of ['Claim', 'Policy', 'Claimant', 'Garage and vehicle', 'Incident', 'Money']) {
      expect(screen.getByRole('group', { name: legend })).toBeInTheDocument()
    }
    const required = ['Claim reference', 'Claim stage', 'Policy token', 'Garage id', 'Vehicle token', 'Incident date and time', 'Incident type', 'Currency']
    for (const label of required) expect(screen.getByLabelText(label)).toBeInTheDocument()
    expect(screen.getByLabelText(/^Claimant token/).closest('div')).toHaveTextContent('optional')
    expect(screen.getByText(/Fields marked optional can be left blank/)).toBeInTheDocument()
  })

  it('has no field for anything the server controls, or for the claim version', () => {
    const { container } = setup()
    const names = [...container.querySelectorAll('[name]')].map((e) => e.getAttribute('name'))
    for (const forbidden of ['claimVersion', 'organizationId', 'createdBy', 'id', 'status', 'isActive']) expect(names).not.toContain(forbidden)
  })

  it('asks for tokens, not registration numbers or names, and says matching is exact', () => {
    setup()
    expect(screen.getByText(/never the policy number or a name/)).toBeInTheDocument()
    expect(screen.getByText(/never a registration number/)).toBeInTheDocument()
    expect(screen.getByText(/exact and case-sensitive/)).toBeInTheDocument()
  })

  it('names the time zone times are entered in, and warns that a claim cannot be edited', () => {
    setup()
    expect(screen.getByText(/Times are in your browser's time zone \(Asia\/(Kolkata|Calcutta)\)/)).toBeInTheDocument()
    expect(screen.getByText(/cannot be edited after it is created/)).toBeInTheDocument()
  })

  it('writes the currency in capitals as it is typed', async () => {
    setup()
    const currency = screen.getByLabelText('Currency')
    await userEvent.clear(currency)
    await userEvent.type(currency, 'usd')
    expect(currency).toHaveValue('USD')
  })

  it('uses the right kind of input for dates, times and amounts', () => {
    setup()
    expect(screen.getByLabelText('Incident date and time')).toHaveAttribute('type', 'datetime-local')
    expect(screen.getByLabelText(/^Policy start date/)).toHaveAttribute('type', 'date')
    expect(screen.getByLabelText(/^Initial estimate/)).toHaveAttribute('inputmode', 'decimal')
  })
})

describe('ClaimNewPage: checking before sending', () => {
  it('lists every problem in a summary that takes focus, and sends nothing', async () => {
    const { bodies } = capture()
    setup()
    await submit()
    const summary = await screen.findByRole('alert')
    await waitFor(() => expect(summary).toHaveFocus())
    expect(summary).toHaveTextContent('7 things need fixing')
    expect(within(summary).getByRole('link', { name: 'Claim reference' })).toBeInTheDocument()
    expect(screen.getByLabelText('Claim reference')).toBeInvalid()
    expect(screen.getByLabelText('Claim reference')).toHaveAccessibleDescription(/For example CLM-DEMO-1001\. Enter the claim reference\./)
    expect(bodies).toHaveLength(0)
  })

  it('moves focus to a field from its entry in the summary', async () => {
    setup()
    await submit()
    await userEvent.click(within(await screen.findByRole('alert')).getByRole('link', { name: 'Vehicle token' }))
    expect(screen.getByLabelText('Vehicle token')).toHaveFocus()
  })

  it('refuses an amount with three decimals instead of rounding it', async () => {
    const { bodies } = capture()
    setup()
    await fillRequired()
    await userEvent.type(screen.getByLabelText(/^Initial estimate/), '45000.505')
    await submit()
    expect((await screen.findAllByText(/Enter digits with at most two decimals/)).length).toBe(2) // once in the summary, once beside the field
    expect(screen.getByLabelText(/^Initial estimate/)).toBeInvalid()
    expect(bodies).toHaveLength(0)
  })
})

describe('ClaimNewPage: creating', () => {
  it('sends exactly the contract body with the access token, then opens the new claim', async () => {
    const { bodies, auth } = capture()
    setup()
    await fillRequired()
    await userEvent.type(screen.getByLabelText(/^Claimant token/), 'CLT-TOKEN-1001')
    await userEvent.type(screen.getByLabelText(/^Initial estimate/), '45000.50')
    await userEvent.type(screen.getByLabelText(/^Deductible/), '1000')
    await submit()
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent(`/app/claims/${detail.id}`))
    expect(bodies).toEqual([
      {
        internalId: 'CLM-DEMO-1001',
        claimVersion: 1,
        currentStage: 'FINAL_INVOICE_RECEIVED',
        policy: { policyToken: 'POL-TOKEN-1001' },
        claimant: { claimantToken: 'CLT-TOKEN-1001' },
        garage: { garageId: 'GARAGE-DEMO-10' },
        vehicle: { vehicleToken: 'VEH-TOKEN-1001' },
        incident: { incidentAt: '2026-09-10T05:00:00.000Z', incidentType: 'COLLISION' },
        financial: { currency: 'INR', initialEstimateAmount: 45000.5, deductible: 1000 },
      },
    ])
    expect(auth[0]).toMatch(/^Bearer /)
    expect(JSON.parse(screen.getByTestId('state').textContent!)).toEqual({ created: 'CLM-DEMO-1001' })
  })

  it('sends one request however often the button is pressed while it works', async () => {
    const { bodies } = capture(async () => {
      await delay(120)
      return HttpResponse.json(detail, { status: 201 })
    })
    setup()
    await fillRequired()
    const button = screen.getByRole('button', { name: 'Create claim' })
    await userEvent.dblClick(button)
    await userEvent.click(screen.getByRole('button', { name: 'Creating the claim' }))
    await waitFor(() => expect(screen.getByTestId('where')).toBeInTheDocument())
    expect(bodies).toHaveLength(1)
  })
})

describe('ClaimNewPage: the server’s answers', () => {
  it('puts the server’s field errors on the fields they name, and anything else under Other', async () => {
    capture(() =>
      HttpResponse.json(
        {
          code: 'VALIDATION_FAILED',
          status: 400,
          message: 'Request validation failed',
          errors: [
            { field: 'policy.policyToken', message: 'Policy token cannot be blank' },
            { field: 'incident.incidentType', message: 'Incident type is required' },
            { field: 'workflow.status', message: 'not allowed' },
          ],
        },
        { status: 400 },
      ),
    )
    setup()
    await fillRequired()
    await submit()
    expect(await screen.findByText('Policy token cannot be blank')).toBeInTheDocument()
    expect(screen.getByLabelText('Policy token')).toBeInvalid()
    expect(screen.getByLabelText('Incident type')).toBeInvalid()
    const summary = screen.getByRole('alert')
    expect(summary).toHaveTextContent('3 things need fixing')
    expect(summary).toHaveTextContent('workflow.status: not allowed')
    await waitFor(() => expect(summary).toHaveFocus())
  })

  it('says a duplicate reference is a duplicate, on the reference field', async () => {
    capture(() => HttpResponse.json({ code: 'DUPLICATE_CLAIM', status: 409, message: 'A claim with the same internal id already exists' }, { status: 409 }))
    setup()
    await fillRequired()
    await submit()
    const field = await screen.findByLabelText('Claim reference')
    await waitFor(() => expect(field).toBeInvalid())
    expect(field).toHaveAccessibleDescription(/already exists in your organization\. References must be unique/)
    expect(screen.queryByText(/earlier attempt/)).toBeNull()
  })

  it('does not lose what was typed when the server cannot be reached, and warns the claim may exist', async () => {
    server.use(http.post('*/api/v1/claims', () => HttpResponse.error()))
    setup()
    await fillRequired()
    await submit()
    const alert = await screen.findByText('The server could not be reached.')
    expect(alert).toBeInTheDocument()
    expect(screen.getByText(/may already have been created/)).toBeInTheDocument()
    expect(screen.getByLabelText('Claim reference')).toHaveValue('CLM-DEMO-1001')
    expect(screen.getByLabelText('Garage id')).toHaveValue('GARAGE-DEMO-10')
  })

  it('after an unanswered attempt, explains that a duplicate may be the earlier attempt', async () => {
    let calls = 0
    server.use(
      http.post('*/api/v1/claims', () => {
        calls++
        return calls === 1 ? HttpResponse.error() : HttpResponse.json({ code: 'DUPLICATE_CLAIM', status: 409, message: 'x' }, { status: 409 })
      }),
    )
    setup()
    await fillRequired()
    await submit()
    await screen.findByText('The server could not be reached.')
    await submit()
    expect((await screen.findAllByText(/If your earlier attempt reached the server, it was created/)).length).toBeGreaterThan(0)
  })

  it('shows a server fault in words and never what the server said', async () => {
    capture(() => HttpResponse.json({ code: 'INTERNAL_ERROR', status: 500, message: 'org.postgresql.util.PSQLException: relation "claims"' }, { status: 500 }))
    setup()
    await fillRequired()
    await submit()
    expect(await screen.findByText('The service had a problem.')).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/PSQL|relation/)
  })

  it('explains a permission problem', async () => {
    capture(() => HttpResponse.json({ code: 'FORBIDDEN', status: 403, message: 'no' }, { status: 403 }))
    setup()
    await fillRequired()
    await submit()
    expect(await screen.findByText('You do not have permission to do this.')).toBeInTheDocument()
  })

  it('can be cancelled back to the list', () => {
    setup()
    expect(screen.getByRole('link', { name: 'Cancel' })).toHaveAttribute('href', '/app/claims')
  })
})
