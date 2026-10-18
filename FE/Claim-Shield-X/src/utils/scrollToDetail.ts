/**
 * In a list-and-detail layout that stacks on narrow screens, bring the detail into view after a choice.
 * Does nothing where the detail is already beside the list, and never animates for people who asked for less motion.
 */
export function scrollToDetail(element: HTMLElement | null): void {
  if (!element || typeof element.scrollIntoView !== 'function') return
  if (!window.matchMedia('(max-width: 56rem)').matches) return
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  element.scrollIntoView({ block: 'start', behavior: reduced ? 'auto' : 'smooth' })
}
