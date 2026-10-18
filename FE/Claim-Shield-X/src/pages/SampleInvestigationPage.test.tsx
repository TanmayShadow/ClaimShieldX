import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { SampleInvestigationPage } from './SampleInvestigationPage'

function Where() {
  const { pathname, search } = useLocation()
  return <output data-testid="where">{pathname + search}</output>
}

function setup(entry = '/sample-investigation') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SampleInvestigationPage />
      <Where />
    </MemoryRouter>,
  )
}

const stepButton = (name: RegExp) => within(screen.getByRole('navigation', { name: 'Walkthrough steps' })).getByRole('button', { name })

describe('SampleInvestigationPage', () => {
  it('says on every view that the case is fictional and read-only', () => {
    const { container } = setup()
    expect(container.querySelector('[data-synthetic]')).toHaveTextContent('This case is fictional')
    expect(screen.getByText('Synthetic demo data')).toBeInTheDocument()
    expect(screen.getByText(/Every name, number and document in it is SYNTHETIC DEMO DATA/)).toBeInTheDocument()
  })

  it('starts at the documents step and marks the current step', () => {
    setup()
    expect(stepButton(/Documents/)).toHaveAttribute('aria-current', 'step')
    expect(stepButton(/Checks/)).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('heading', { level: 2, name: /Step 1 of 5/ })).toBeInTheDocument()
  })

  it('opens the step named in the address and falls back safely for nonsense', () => {
    const { unmount } = setup('/sample-investigation?step=history')
    expect(stepButton(/History/)).toHaveAttribute('aria-current', 'step')
    unmount()
    setup('/sample-investigation?step=%3Cscript%3E')
    expect(stepButton(/Documents/)).toHaveAttribute('aria-current', 'step')
  })

  it('moves with Next and Back and finishes on a real link', async () => {
    setup()
    expect(screen.queryByRole('button', { name: 'Back' })).toBeNull()
    for (const next of ['Checks', 'History', 'Explanations', 'Review']) {
      await userEvent.click(screen.getByRole('button', { name: new RegExp(`^Next: ${next}`) }))
      expect(stepButton(new RegExp(next))).toHaveAttribute('aria-current', 'step')
    }
    expect(screen.queryByRole('button', { name: /^Next/ })).toBeNull()
    expect(screen.getByRole('link', { name: /Finish and return/ })).toHaveAttribute('href', '/')
    await userEvent.click(screen.getByRole('button', { name: 'Back' }))
    expect(stepButton(/Explanations/)).toHaveAttribute('aria-current', 'step')
  })

  it('writes the step into the address so back and forward work', async () => {
    setup()
    await userEvent.click(stepButton(/Checks/))
    expect(screen.getByTestId('where')).toHaveTextContent('/sample-investigation?step=checks')
  })
})

describe('Documents step', () => {
  it('marks the printed value a field came from and shows its source quote as text', async () => {
    setup()
    await userEvent.click(screen.getByRole('button', { name: 'grandTotal' }))
    const printed = screen.getByRole('group', { name: /Final invoice as printed/ })
    expect(within(printed).getAllByText('62,510.00').some((el) => el.tagName === 'MARK')).toBe(true)
    expect(screen.getByText(/Source of grandTotal: page 1/)).toBeInTheDocument()
    expect(screen.getByText(/Grand total 62,510.00/)).toBeInTheDocument()
  })

  it('shows what could not be read as unknown and says nothing was guessed', async () => {
    setup()
    await userEvent.click(screen.getByRole('button', { name: 'incidentDate' }))
    expect(screen.getByText('incidentDate is unknown')).toBeInTheDocument()
    expect(screen.getByText(/Nothing was guessed or filled in/)).toBeInTheDocument()
  })

  it('switches documents and is honest that a photograph is stored but not read', async () => {
    setup()
    await userEvent.click(screen.getByRole('button', { name: /Garage estimate/ }))
    expect(screen.getByRole('heading', { level: 3, name: 'Garage estimate' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: /Garage estimate as printed/ })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Damage photograph/ }))
    expect(screen.getByText(/Stored, but not read/)).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: /Fields read/ })).toBeNull()
  })
})

describe('Checks step', () => {
  it('opens on the first flagged result and explains what a flag means', async () => {
    setup('/sample-investigation?step=checks')
    expect(screen.getByRole('heading', { level: 3, name: 'Estimate-to-invoice variance' })).toBeInTheDocument()
    expect(screen.getByText(/Something to check, not a conclusion/)).toBeInTheDocument()
    expect(screen.getByText('Harmless explanations to rule out first')).toBeInTheDocument()
  })

  it('treats a rule that could not run as missing evidence, never a negative finding', async () => {
    setup('/sample-investigation?step=checks')
    await userEvent.click(screen.getByRole('button', { name: /Required evidence/ }))
    expect(screen.getByText(/never a negative finding/)).toBeInTheDocument()
    expect(screen.getByText(/says nothing against the claim/)).toBeInTheDocument()
    expect(screen.queryByText('Harmless explanations to rule out first')).toBeNull()
  })

  it('summarises results by outcome in words', () => {
    setup('/sample-investigation?step=checks')
    const counts = screen.getByRole('list', { name: 'Results by outcome' })
    expect(counts).toHaveTextContent('5 pass')
    expect(counts).toHaveTextContent('2 flag · review')
    expect(counts).toHaveTextContent('1 incomplete')
  })

  it('opens the document a citation names, marks the value and moves focus there', async () => {
    setup('/sample-investigation?step=checks')
    await userEvent.click(screen.getByRole('button', { name: /^Document Final invoice, grand total/ }))
    expect(stepButton(/Documents/)).toHaveAttribute('aria-current', 'step')
    const printed = screen.getByRole('group', { name: /Final invoice as printed/ })
    expect(within(printed).getAllByText('62,510.00').some((el) => el.tagName === 'MARK')).toBe(true)
    await waitFor(() => expect(document.activeElement).toHaveAttribute('data-focus-target'))
  })

  it('opens the claim record for a claim-field citation', async () => {
    setup('/sample-investigation?step=checks')
    await userEvent.click(screen.getByRole('button', { name: /^Claim record Initial estimate on the claim/ }))
    expect(stepButton(/Documents/)).toHaveAttribute('aria-current', 'step')
    expect(screen.getByText('INR 45,000.50').className).toContain('hit')
  })
})

describe('History step', () => {
  it('separates an exact vehicle match from context-only matches and shows the reason and limits', () => {
    setup('/sample-investigation?step=history')
    expect(screen.getByText('Exact match')).toBeInTheDocument()
    expect(screen.getByText('Context only')).toBeInTheDocument()
    expect(screen.getAllByText(/Outcome not recorded: not known, never cleared/)).toHaveLength(2)
    expect(screen.getByText(/Weak token only/)).toBeInTheDocument()
    expect(screen.getAllByText(/the insurer.s label, not interpreted/)).toHaveLength(2)
  })

  it('treats the garage as context and never as evidence', () => {
    setup('/sample-investigation?step=history')
    expect(screen.getByText(/context only and never evidence of wrongdoing/)).toBeInTheDocument()
  })

  it('keeps "no data" and "no match" apart', () => {
    setup('/sample-investigation?step=history')
    expect(screen.getByText('no historical data available')).toBeInTheDocument()
    expect(screen.getByText('no match found')).toBeInTheDocument()
  })
})

describe('Explanations step', () => {
  it('says it is hand-written for the example, not a verdict', () => {
    setup('/sample-investigation?step=explanations')
    expect(screen.getByText('Written by hand for this example')).toBeInTheDocument()
    expect(screen.getAllByText('Hypothesis · not a finding')).toHaveLength(2)
  })

  it('shows supports, counter-evidence and unknowns side by side for each explanation', () => {
    setup('/sample-investigation?step=explanations')
    expect(screen.getAllByRole('heading', { level: 5, name: 'Supports' })).toHaveLength(2)
    expect(screen.getAllByRole('heading', { level: 5, name: /Weakens or counts against/ })).toHaveLength(2)
    expect(screen.getAllByRole('heading', { level: 5, name: 'Not known' })).toHaveLength(2)
  })

  it('lets an unavailable source stand as plain text and opens every real one', async () => {
    setup('/sample-investigation?step=explanations')
    expect(screen.getAllByText('Supplementary estimate: not received')[0]!.closest('button')).toBeNull()
    await userEvent.click(screen.getAllByRole('button', { name: /^History record Earlier claim HC-0231/ })[0]!)
    expect(stepButton(/History/)).toHaveAttribute('aria-current', 'step')
    await waitFor(() => expect(document.activeElement).toHaveTextContent('CLM-SYN-REPEAT-B'))
  })

  it('opens a rule result from a citation and selects that rule', async () => {
    setup('/sample-investigation?step=explanations')
    await userEvent.click(screen.getAllByRole('button', { name: /^Rule result Date consistency/ })[0]!)
    expect(stepButton(/Checks/)).toHaveAttribute('aria-current', 'step')
    expect(screen.getByRole('heading', { level: 3, name: 'Date consistency' })).toBeInTheDocument()
  })
})

describe('Review step', () => {
  const rationale = () => screen.getByLabelText('Rationale')

  it('needs an action before it will preview', async () => {
    setup('/sample-investigation?step=review')
    await userEvent.type(rationale(), 'Waiting for the supplementary estimate.')
    await userEvent.click(screen.getByRole('button', { name: 'Preview the entry' }))
    expect(screen.getByRole('alert')).toHaveTextContent('Choose one of the four actions')
  })

  it('needs a rationale of at least ten characters', async () => {
    setup('/sample-investigation?step=review')
    await userEvent.click(screen.getByRole('radio', { name: /Request more evidence/ }))
    await userEvent.type(rationale(), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Preview the entry' }))
    expect(screen.getByRole('alert')).toHaveTextContent(/between 10 and 2000/i)
  })

  it('previews the entry, says it is not saved, and can start again', async () => {
    setup('/sample-investigation?step=review')
    await userEvent.click(screen.getByRole('radio', { name: /Defer review/ }))
    await userEvent.type(rationale(), 'Wait for the next history import.')
    await userEvent.click(screen.getByRole('button', { name: 'Preview the entry' }))
    expect(screen.queryByRole('alert')).toBeNull()
    const preview = within(screen.getByText(/Entry preview · not saved/).parentElement!)
    expect(preview.getByText('DEFER_REVIEW')).toBeInTheDocument()
    expect(preview.getByText('Wait for the next history import.')).toBeInTheDocument()
    expect(preview.getByText(/None of\s+the four actions means approve, reject, settle or fraud/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Start again' }))
    expect(screen.queryByText('DEFER_REVIEW')).toBeNull()
    expect(rationale()).toHaveValue('')
  })

  it('offers only the four actions the backend defines', () => {
    setup('/sample-investigation?step=review')
    expect(screen.getAllByRole('radio')).toHaveLength(4)
  })
})

describe('the whole walkthrough', () => {
  it('never touches the network', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    const xhrSpy = vi.spyOn(XMLHttpRequest.prototype, 'open')
    setup()
    for (const next of ['Checks', 'History', 'Explanations', 'Review']) {
      await userEvent.click(screen.getByRole('button', { name: new RegExp(`^Next: ${next}`) }))
    }
    await userEvent.click(screen.getByRole('radio', { name: /Request more evidence/ }))
    await userEvent.type(screen.getByLabelText('Rationale'), 'Ask the garage for the job card.')
    await userEvent.click(screen.getByRole('button', { name: 'Preview the entry' }))
    expect(fetchSpy).not.toHaveBeenCalled()
    expect(xhrSpy).not.toHaveBeenCalled()
  })

  it('never uses a persistent store', async () => {
    const set = vi.spyOn(Storage.prototype, 'setItem')
    setup('/sample-investigation?step=review')
    await userEvent.click(screen.getByRole('radio', { name: /Defer review/ }))
    await userEvent.type(screen.getByLabelText('Rationale'), 'Come back after the import.')
    await userEvent.click(screen.getByRole('button', { name: 'Preview the entry' }))
    expect(set).not.toHaveBeenCalled()
  })
})
