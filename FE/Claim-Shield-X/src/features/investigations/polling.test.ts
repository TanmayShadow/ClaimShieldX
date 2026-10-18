import { pollInterval } from './polling'

describe('pollInterval', () => {
  it.each(['COMPLETED', 'FAILED', 'CANCELLED'])('stops once %s', (status) => {
    expect(pollInterval(status, 0)).toBe(false)
    expect(pollInterval(status, 10 * 60_000)).toBe(false)
  })

  it.each(['QUEUED', 'PREPARING_EVIDENCE', 'EXTRACTING', 'RUNNING_RULES', 'BUILDING_HYPOTHESES', 'VALIDATING_FINDINGS', 'GENERATING_REPORT'])('asks quickly while %s moves', (status) => {
    expect(pollInterval(status, 1000)).toBe(2000)
  })

  it('slows down as a run gets older, and never goes below two seconds or above eight', () => {
    const intervals = [0, 29_999, 30_000, 119_999, 120_000, 3_600_000].map((age) => pollInterval('EXTRACTING', age))
    expect(intervals).toEqual([2000, 2000, 4000, 4000, 8000, 8000])
  })

  it('asks every five seconds while waiting, however old', () => {
    expect(pollInterval('WAITING_FOR_EVIDENCE', 0)).toBe(5000)
    expect(pollInterval('RETRY_WAIT', 3_600_000)).toBe(5000)
  })

  it('treats a status it has never seen as still moving, so it keeps looking', () => {
    expect(pollInterval('SOMETHING_NEW', 0)).toBe(2000)
  })
})
