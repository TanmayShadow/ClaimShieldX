import { DEFAULT_SIZE, DEFAULT_SORT, parseListParams, toSearch } from './listParams'

const parse = (query: string) => parseListParams(new URLSearchParams(query))

describe('parseListParams', () => {
  it('uses the defaults for an empty address', () => {
    expect(parse('')).toEqual({ page: 0, size: DEFAULT_SIZE, sort: DEFAULT_SORT })
  })

  it('reads every valid parameter', () => {
    expect(parse('page=3&size=50&sort=internalId,asc&stage=FNOL')).toEqual({ page: 3, size: 50, sort: 'internalId,asc', stage: 'FNOL' })
  })

  it.each([
    ['page=-1', 0],
    ['page=abc', 0],
    ['page=1.5', 0],
    ['page=99999999999', 0],
    ['page=1e3', 0],
    ['page=%20', 0],
    ['page=', 0],
  ])('falls back to page 0 for %s', (query, page) => {
    expect(parse(query).page).toBe(page)
  })

  it.each(['size=0', 'size=1000', 'size=-5', 'size=abc', 'size=20.5', 'size='])('falls back to the default size for %s', (query) => {
    expect(parse(query).size).toBe(DEFAULT_SIZE)
  })

  it.each(['sort=password,asc', 'sort=createdAt', 'sort=createdAt,sideways', 'sort=;drop table', 'sort=updatedAt,desc', 'sort='])('falls back to the default sort for %s', (query) => {
    expect(parse(query).sort).toBe(DEFAULT_SORT)
  })

  it.each(['stage=fnol', 'stage=ARCHIVED', 'stage=<script>', 'stage=', 'stage=FNOL,ESTIMATE_RECEIVED'])('ignores the stage %s', (query) => {
    expect(parse(query)).not.toHaveProperty('stage')
  })

  it('never lets an unexpected value reach the API', () => {
    const p = parse('page=../../x&size=9999&sort=x&stage=y&extra=1')
    expect(p).toEqual({ page: 0, size: DEFAULT_SIZE, sort: DEFAULT_SORT })
  })
})

describe('toSearch', () => {
  it('writes nothing for the defaults', () => {
    expect(toSearch({ page: 0, size: DEFAULT_SIZE, sort: DEFAULT_SORT }).toString()).toBe('')
  })

  it('writes only what differs', () => {
    expect(toSearch({ page: 2, size: 50, sort: 'internalId,asc', stage: 'POST_SETTLEMENT' }).toString()).toBe('stage=POST_SETTLEMENT&sort=internalId%2Casc&size=50&page=2')
  })

  it('round-trips through parseListParams', () => {
    const p = { page: 4, size: 10 as const, sort: 'createdAt,asc' as const, stage: 'ESTIMATE_RECEIVED' as const }
    expect(parseListParams(toSearch(p))).toEqual(p)
  })
})
