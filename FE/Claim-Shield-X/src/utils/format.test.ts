import { formatDate, formatDateTime, formatDateTimeWithZone, formatMoney, formatTime, pluralize, viewerTimeZone } from './format'

describe('formatting', () => {
  it('shows UTC instants in the viewer’s zone (these tests run as Asia/Kolkata)', () => {
    expect(viewerTimeZone()).toBeTruthy()
    expect(formatDateTime('2026-09-10T05:00:00Z')).toMatch(/10 Sept?\.? 2026,? 10:30/)
    expect(formatDate('2026-09-10T05:00:00Z')).toMatch(/10 Sept?\.? 2026/)
  })

  it('names the zone in the long form', () => {
    expect(formatDateTimeWithZone('2026-09-10T05:00:00Z')).toMatch(/GMT\+5:30|IST/)
  })

  it.each([null, undefined, '', 'not a date', '2026-13-45'])('says not available for %j instead of throwing', (value) => {
    expect(formatDateTime(value)).toBe('Not available')
    expect(formatDate(value)).toBe('Not available')
    expect(formatDateTimeWithZone(value)).toBe('Not available')
  })

  it('formats money in rupees with Indian grouping and exactly two decimals', () => {
    expect(formatMoney(45000.5, 'INR')).toBe('₹45,000.50')
    expect(formatMoney(1234567.1, 'INR')).toBe('₹12,34,567.10')
    expect(formatMoney(0, 'INR')).toBe('₹0.00')
  })

  it('falls back to a plain code and amount for an unknown currency, never throwing', () => {
    expect(formatMoney(12.5, 'ZZZ')).toMatch(/12\.50/)
    expect(formatMoney(12.5, 'not a code')).toBe('not a code 12.50')
    expect(formatMoney(12.5, null)).toBe('12.50')
  })

  it.each([null, undefined, Number.NaN, Number.POSITIVE_INFINITY])('says not available for the amount %j', (amount) => {
    expect(formatMoney(amount, 'INR')).toBe('Not available')
  })

  it('formats a clock time with seconds', () => {
    expect(formatTime(new Date('2026-09-10T05:00:07Z'))).toBe('10:30:07')
  })

  it('pluralizes', () => {
    expect(pluralize(1, 'claim')).toBe('1 claim')
    expect(pluralize(0, 'claim')).toBe('0 claims')
    expect(pluralize(2, 'warning')).toBe('2 warnings')
    expect(pluralize(2, 'entry', 'entries')).toBe('2 entries')
  })
})
