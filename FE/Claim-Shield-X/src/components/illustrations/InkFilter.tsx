import { useId } from 'react'
import type { ReactNode } from 'react'

/**
 * Wraps line art in a very slight displacement so contours read as drawn in ink rather than vector-perfect.
 * The filter id is unique per instance so several illustrations can share a page.
 */
export function Inked({ children, scale = 2 }: { children: ReactNode; scale?: number }) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, '')
  return (
    <>
      <defs>
        <filter id={`ink-${id}`} x="-3%" y="-3%" width="106%" height="106%">
          <feTurbulence type="fractalNoise" baseFrequency="0.016" numOctaves="1" seed="11" result="noise" />
          <feDisplacementMap in="SourceGraphic" in2="noise" scale={scale} xChannelSelector="R" yChannelSelector="G" />
        </filter>
      </defs>
      <g filter={`url(#ink-${id})`}>{children}</g>
    </>
  )
}
