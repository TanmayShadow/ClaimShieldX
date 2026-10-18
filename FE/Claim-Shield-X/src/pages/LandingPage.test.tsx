import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { capabilities, stages } from '@/features/landing/content'
import { LandingPage } from './LandingPage'

function setup() {
  return render(
    <MemoryRouter>
      <LandingPage />
    </MemoryRouter>,
  )
}

describe('LandingPage', () => {
  it('has the page landmarks and a single h1', () => {
    setup()
    expect(screen.getByRole('banner')).toBeInTheDocument()
    expect(screen.getByRole('main')).toBeInTheDocument()
    expect(screen.getByRole('contentinfo')).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Every claim has a story. Follow the evidence.')
  })

  it('has an h2 for every section and no skipped heading levels', () => {
    const { container } = setup()
    const levels = [...container.querySelectorAll('h1,h2,h3,h4')].map((h) => Number(h.tagName[1]))
    levels.forEach((level, i) => {
      if (i > 0) expect(level - levels[i - 1]!).toBeLessThanOrEqual(1)
    })
    expect(screen.getAllByRole('heading', { level: 2 }).length).toBeGreaterThanOrEqual(5)
  })

  it('links only to sections that exist, or to routes that exist today', () => {
    const { container } = setup()
    const hrefs = [...container.querySelectorAll('a[href]')].map((a) => a.getAttribute('href')!)
    expect(hrefs.length).toBeGreaterThan(5)
    for (const href of hrefs) {
      if (href.startsWith('#')) {
        expect(container.querySelector(href), `${href} has a target`).not.toBeNull()
      } else {
        expect(['/', '/sample-investigation', '/login', '/app']).toContain(href)
      }
    }
  })

  it('labels every example as synthetic', () => {
    setup()
    expect(screen.getAllByText(/synthetic/i).length).toBeGreaterThanOrEqual(3)
    const example = screen.getByRole('heading', { name: /final invoice total is higher/i }).closest('section')!
    expect(within(example).getByText('Synthetic demo data')).toBeInTheDocument()
  })

  it('separates what supports, weakens and is unknown in the example', () => {
    setup()
    expect(screen.getByRole('heading', { level: 4, name: 'Supports' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 4, name: 'Weakens' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 4, name: 'Not known' })).toBeInTheDocument()
  })

  it('never uses hype, certifications or fraud verdicts', () => {
    const { container } = setup()
    const text = container.textContent ?? ''
    expect(text).not.toMatch(/revolution|game-chang|100% accurate|instant fraud|fraud detected|AI-powered magic|SOC ?2|ISO ?27001|HIPAA|GDPR|testimonial/i)
    // "fraud" may appear only in sentences that deny it.
    for (const sentence of text.split(/(?<=[.!?])\s+/)) {
      if (/fraud/i.test(sentence)) {
        expect(sentence, sentence).toMatch(/never|not|will not|none of|nothing|proof|probability|reason to look|means approve, reject, settle or fraud/i)
      }
    }
  })

  it('shows no invented numbers about adoption, accuracy or savings', () => {
    const { container } = setup()
    expect(container.textContent).not.toMatch(/\d+\s*%\s*(faster|accurate|fewer|reduction|savings)|customers|trusted by/i)
  })
})

describe('Capabilities table', () => {
  it('states in words, row by row, what is in the prototype and what is only planned', () => {
    setup()
    const table = screen.getByRole('table', { name: /Capabilities and whether they exist/ })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(capabilities.length)
    rows.forEach((row, i) => {
      const available = capabilities[i]!.availability === 'available'
      expect(within(row).getByRole('rowheader')).toHaveTextContent(capabilities[i]!.name)
      expect(within(row).queryByText('In the prototype') !== null).toBe(available)
      expect(within(row).queryByText('Planned') !== null).toBe(!available)
      expect(row.querySelector('[data-state]')).toHaveAttribute('data-state', available ? 'done' : 'pending')
    })
  })
})

describe('Anatomy of an investigation', () => {
  it('starts on the first stage with a labelled, connected tab and panel', () => {
    setup()
    const tablist = screen.getByRole('tablist', { name: 'Stages of an investigation' })
    const tabs = within(tablist).getAllByRole('tab')
    expect(tabs).toHaveLength(stages.length)
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true')
    const panel = screen.getByRole('tabpanel')
    expect(panel).toHaveAttribute('aria-labelledby', tabs[0]!.id)
    expect(tabs[0]).toHaveAttribute('aria-controls', panel.id)
    expect(within(panel).getByRole('heading', { level: 3, name: 'Document intake' })).toBeInTheDocument()
  })

  it('uses a roving tab stop so the whole list is one tab stop', () => {
    setup()
    const tabs = screen.getAllByRole('tab')
    expect(tabs.filter((t) => t.tabIndex === 0)).toHaveLength(1)
    expect(tabs.filter((t) => t.tabIndex === -1)).toHaveLength(stages.length - 1)
  })

  it('shows the chosen stage when clicked', async () => {
    setup()
    await userEvent.click(screen.getByRole('tab', { name: /Historical comparison/ }))
    const panel = screen.getByRole('tabpanel')
    expect(within(panel).getByRole('heading', { level: 3, name: 'Historical comparison' })).toBeInTheDocument()
    expect(within(panel).getByText(/never a finding on its own/)).toBeInTheDocument()
  })

  it('moves with the arrow keys, wraps, and supports Home and End', async () => {
    setup()
    const first = screen.getAllByRole('tab')[0]!
    first.focus()
    await userEvent.keyboard('{ArrowRight}')
    expect(screen.getAllByRole('tab')[1]).toHaveFocus()
    expect(screen.getAllByRole('tab')[1]).toHaveAttribute('aria-selected', 'true')
    await userEvent.keyboard('{End}')
    expect(screen.getAllByRole('tab')[stages.length - 1]).toHaveFocus()
    await userEvent.keyboard('{ArrowRight}')
    expect(screen.getAllByRole('tab')[0]).toHaveFocus()
    await userEvent.keyboard('{ArrowLeft}')
    expect(screen.getAllByRole('tab')[stages.length - 1]).toHaveFocus()
    await userEvent.keyboard('{Home}')
    expect(screen.getAllByRole('tab')[0]).toHaveFocus()
  })

  it('says in text, not only by colour or shape, that a stage is in the prototype', async () => {
    setup()
    await userEvent.click(screen.getByRole('tab', { name: /Competing explanations/ }))
    const panel = screen.getByRole('tabpanel')
    expect(within(panel).getAllByText('In the prototype').length).toBeGreaterThan(0)
    expect(within(panel).queryByText(/Not built yet/)).not.toBeInTheDocument()
  })

  it('states for every stage what stays with a person', async () => {
    setup()
    for (const stage of stages) {
      await userEvent.click(screen.getByRole('tab', { name: new RegExp(stage.title) }))
      expect(within(screen.getByRole('tabpanel')).getByText('Stays with a person')).toBeInTheDocument()
      expect(within(screen.getByRole('tabpanel')).getByText(stage.staysHuman)).toBeInTheDocument()
    }
  })
})
