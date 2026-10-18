import { readinessMark, readinessMeaning } from './readiness'

describe('readinessMark', () => {
  it('shows a blocked claim as not ready, in words, with what is missing', () => {
    expect(readinessMark({ level: 'NOT_READY', blockerCount: 1, warningCount: 4 })).toEqual({
      state: 'blocked',
      label: 'Not ready',
      detail: '1 required document issue, 4 warnings',
    })
  })

  it('omits a warning count of zero and pluralizes', () => {
    expect(readinessMark({ level: 'NOT_READY', blockerCount: 2, warningCount: 0 }).detail).toBe('2 required document issues')
  })

  it('shows warnings as warnings, not as a problem with the claim', () => {
    expect(readinessMark({ level: 'READY_WITH_WARNINGS', blockerCount: 0, warningCount: 1 })).toEqual({ state: 'attention', label: 'Ready with warnings', detail: '1 warning' })
  })

  it('shows a ready claim as ready', () => {
    expect(readinessMark({ level: 'READY', blockerCount: 0, warningCount: 0 }).state).toBe('done')
  })

  it('does not guess for a level it does not know', () => {
    expect(readinessMark({ level: 'SOMETHING_NEW', blockerCount: 0, warningCount: 0 })).toMatchObject({ state: 'unknown', label: 'Readiness not available' })
  })

  it('never calls a claim fraudulent or risky in any wording', () => {
    const all = JSON.stringify([readinessMeaning, ['NOT_READY', 'READY_WITH_WARNINGS', 'READY'].map((level) => readinessMark({ level, blockerCount: 1, warningCount: 1 }))])
    expect(all).not.toMatch(/fraud|suspic|risk|danger/i)
  })

  it('says that not-ready is about evidence, not about the claim', () => {
    expect(readinessMeaning.NOT_READY).toMatch(/says nothing about the claim itself/)
  })
})
