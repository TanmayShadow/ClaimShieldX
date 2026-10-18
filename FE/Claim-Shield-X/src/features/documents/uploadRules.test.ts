import { ACCEPT_ATTRIBUTE, MAX_FILE_BYTES, checkFile, formatBytes, newIdempotencyKey } from './uploadRules'

const f = (name: string, size = 1000, type = '') => ({ name, size, type })

describe('checkFile', () => {
  it.each([
    ['invoice.pdf', 'application/pdf'],
    ['photo.JPG', 'image/jpeg'],
    ['photo.jpeg', ''],
    ['scan.PNG', 'image/png'],
    ['no-extension', 'application/pdf'],
    ['weird.dat', 'image/png'],
  ])('accepts %s (%s)', (name, type) => {
    expect(checkFile(f(name, 1000, type))).toBeNull()
  })

  it.each([['notes.txt', 'text/plain'], ['sheet.xlsx', ''], ['page.html', 'text/html'], ['image.svg', 'image/svg+xml'], ['archive.zip', 'application/zip'], ['pdf', '']])('refuses %s (%s)', (name, type) => {
    expect(checkFile(f(name, 1000, type))?.code).toBe('UNSUPPORTED_TYPE')
  })

  it('refuses an empty file with its own message', () => {
    expect(checkFile(f('a.pdf', 0, 'application/pdf'))).toMatchObject({ code: 'EMPTY' })
  })

  it('accepts exactly the limit and refuses one byte over, saying how large the file is', () => {
    expect(checkFile(f('a.pdf', MAX_FILE_BYTES, 'application/pdf'))).toBeNull()
    const over = checkFile(f('a.pdf', MAX_FILE_BYTES + 1, 'application/pdf'))
    expect(over?.code).toBe('TOO_LARGE')
    expect(over?.message).toMatch(/20\.0 MB/)
  })

  it.each([['IMG_0001.HEIC', ''], ['photo.heif', ''], ['x', 'image/heic']])('tells the person to convert %s to JPEG', (name, type) => {
    const p = checkFile(f(name, 1000, type))
    expect(p?.code).toBe('HEIC')
    expect(p?.message).toMatch(/Convert the photo to JPEG/)
  })

  it('checks size before type so a huge unsupported file hears about the more fixable problem first', () => {
    expect(checkFile(f('huge.txt', MAX_FILE_BYTES + 5, 'text/plain'))?.code).toBe('TOO_LARGE')
  })

  it('lists what the file picker should offer', () => {
    expect(ACCEPT_ATTRIBUTE).toContain('.pdf')
    expect(ACCEPT_ATTRIBUTE).toContain('image/png')
    expect(ACCEPT_ATTRIBUTE).not.toContain('svg')
  })
})

describe('formatBytes', () => {
  it.each([[0, '0 B'], [999, '999 B'], [1024, '1.0 KB'], [5 * 1024, '5.0 KB'], [200 * 1024, '200 KB'], [1536 * 1024, '1.5 MB'], [20 * 1024 * 1024, '20.0 MB']])('formats %i', (n, text) => {
    expect(formatBytes(n)).toBe(text)
  })
})

describe('newIdempotencyKey', () => {
  it('meets the backend format, and differs each time', () => {
    const a = newIdempotencyKey()
    const b = newIdempotencyKey()
    for (const key of [a, b]) {
      expect(key).toMatch(/^[A-Za-z0-9._:-]{8,128}$/)
    }
    expect(a).not.toBe(b)
  })
})
