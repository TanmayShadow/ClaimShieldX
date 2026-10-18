import { scrollToDetail } from './scrollToDetail'

function media(matches: Record<string, boolean>) {
  window.matchMedia = ((query: string) => ({ matches: matches[query] ?? false, media: query }) as MediaQueryList) as typeof window.matchMedia
}

function target() {
  const el = document.createElement('div')
  el.scrollIntoView = vi.fn()
  return el
}

describe('scrollToDetail', () => {
  it('scrolls smoothly when the layout is stacked', () => {
    media({ '(max-width: 56rem)': true })
    const el = target()
    scrollToDetail(el)
    expect(el.scrollIntoView).toHaveBeenCalledWith({ block: 'start', behavior: 'smooth' })
  })

  it('jumps without animation when reduced motion is requested', () => {
    media({ '(max-width: 56rem)': true, '(prefers-reduced-motion: reduce)': true })
    const el = target()
    scrollToDetail(el)
    expect(el.scrollIntoView).toHaveBeenCalledWith({ block: 'start', behavior: 'auto' })
  })

  it('leaves the page alone when the detail already sits beside the list', () => {
    media({ '(max-width: 56rem)': false })
    const el = target()
    scrollToDetail(el)
    expect(el.scrollIntoView).not.toHaveBeenCalled()
  })

  it('tolerates a missing element', () => {
    expect(() => scrollToDetail(null)).not.toThrow()
  })
})
