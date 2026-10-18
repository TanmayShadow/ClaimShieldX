import { ruleOutcomeMark, ruleOutcomeMeaning } from './outcome'
import type { RuleOutcome } from './outcome'

const outcomes: RuleOutcome[] = ['PASS', 'FLAG', 'INCOMPLETE', 'NOT_APPLICABLE', 'ERROR']

describe('ruleOutcomeMark', () => {
  it('gives each outcome its own visual state and a text label', () => {
    const marks = outcomes.map((o) => ruleOutcomeMark(o, o === 'FLAG' ? 'REVIEW' : undefined))
    expect(new Set(marks.map((m) => m.state)).size).toBe(outcomes.length)
    marks.forEach((m) => expect(m.label.length).toBeGreaterThan(0))
  })

  it('never calls an incomplete rule a failure or a flag', () => {
    expect(ruleOutcomeMark('INCOMPLETE')).toEqual({ state: 'unknown', label: 'Incomplete' })
    expect(ruleOutcomeMark('INCOMPLETE').state).not.toBe('attention')
    expect(ruleOutcomeMark('INCOMPLETE').state).not.toBe('failed')
  })

  it('distinguishes flag severities in words', () => {
    expect(ruleOutcomeMark('FLAG', 'REVIEW').label).toBe('Flag · review')
    expect(ruleOutcomeMark('FLAG', 'INFO').label).toBe('Flag · information')
  })

  it('explains every outcome and never uses the word fraud', () => {
    for (const o of outcomes) {
      expect(ruleOutcomeMeaning[o].length).toBeGreaterThan(10)
      expect(ruleOutcomeMeaning[o]).not.toMatch(/fraud/i)
    }
  })
})
