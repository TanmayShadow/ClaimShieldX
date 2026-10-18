import { capabilities, neverDoes, securityControls, stages, syntheticFinding } from './content'

describe('landing content', () => {
  it('keeps the worked example arithmetically consistent with its own text', () => {
    const numbers = [...syntheticFinding.summary.matchAll(/INR ([\d,]+\.\d{2})/g)].map((m) => Number(m[1]!.replace(/,/g, '')))
    const [invoice, estimate, difference] = numbers
    expect(invoice).toBe(62510)
    expect(estimate).toBe(45000.5)
    expect(difference).toBeCloseTo(invoice! - estimate!, 2)
    const percent = Number(/(\d+\.\d{2})%/.exec(syntheticFinding.summary)![1])
    expect(percent).toBeCloseTo(((invoice! - estimate!) / estimate!) * 100, 2)
    // The rule flags only above 10% and at least 1,000.00, so the example must clear both.
    expect(percent).toBeGreaterThan(10)
    expect(difference).toBeGreaterThanOrEqual(1000)
  })

  it('gives the finding all three evidence columns', () => {
    for (const column of [syntheticFinding.supports, syntheticFinding.weakens, syntheticFinding.unknown]) {
      expect(column.length).toBeGreaterThan(0)
      for (const entry of column) {
        expect(entry.label).not.toBe('')
        expect(entry.source).not.toBe('')
      }
    }
  })

  it('marks every stage as in the prototype, because the backend has built all six', () => {
    expect(stages.filter((s) => s.availability === 'planned')).toEqual([])
    expect(stages).toHaveLength(6)
  })

  it('lists anything still planned after what is available', () => {
    const order = capabilities.map((c) => c.availability)
    const firstPlanned = order.indexOf('planned')
    if (firstPlanned !== -1) expect(order.slice(firstPlanned).every((a) => a === 'planned')).toBe(true)
    expect(capabilities.filter((c) => c.availability === 'available')).toHaveLength(7)
  })

  it('never promises what the product refuses to do', () => {
    expect(neverDoes.join(' ')).toMatch(/fraudulent/)
    expect(neverDoes.join(' ')).toMatch(/Approve, reject or settle/)
  })

  it('claims no certification in the security controls', () => {
    const text = securityControls.map((c) => c.text).join(' ')
    expect(text).not.toMatch(/certif|complian|SOC|ISO|HIPAA|GDPR|encrypt/i)
  })
})
