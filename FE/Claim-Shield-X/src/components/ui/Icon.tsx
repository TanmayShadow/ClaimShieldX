import type { ReactNode, SVGProps } from 'react'

// One hand-drawn set on a 24px grid: 1.75 stroke, round caps and joins. Shapes, not colours, tell states apart.
const glyphs = {
  'arrow-right': <path d="M4 12h15M13 6l6 6-6 6" />,
  'arrow-left': <path d="M20 12H5M11 6l-6 6 6 6" />,
  check: <path d="M5 12.5l4.5 4.5L19 7.5" />,
  x: <path d="M6 6l12 12M18 6L6 18" />,
  plus: <path d="M12 5v14M5 12h14" />,
  minus: <path d="M5 12h14" />,
  'chevron-down': <path d="M6 9l6 6 6-6" />,
  'chevron-right': <path d="M9 6l6 6-6 6" />,
  alert: <path d="M12 4.5l9 15.5H3zM12 10v4.5M12 17.3v.1" />,
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 7.8v.1" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3.2 2" />
    </>
  ),
  ring: <circle cx="12" cy="12" r="7.5" />,
  'ring-half': (
    <>
      <circle cx="12" cy="12" r="7.5" />
      <path d="M12 4.5a7.5 7.5 0 0 1 0 15z" fill="currentColor" />
    </>
  ),
  'dash-circle': (
    <>
      <circle cx="12" cy="12" r="7.5" />
      <path d="M8.5 12h7" />
    </>
  ),
  question: (
    <>
      <circle cx="12" cy="12" r="9" strokeDasharray="2.2 2.6" />
      <path d="M9.7 9.6a2.4 2.4 0 1 1 3.3 2.2c-.7.3-1 .8-1 1.5M12 16.4v.1" />
    </>
  ),
  block: (
    <>
      <circle cx="12" cy="12" r="7.5" />
      <path d="M6.7 17.3L17.3 6.7" />
    </>
  ),
  'cross-octagon': <path d="M8.5 3.5h7l5 5v7l-5 5h-7l-5-5v-7zM9.5 9.5l5 5M14.5 9.5l-5 5" />,
  'check-square': (
    <>
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <path d="M8 12.4l3 3 5-6" />
    </>
  ),
  file: <path d="M7 3.5h7l4 4v13H7zM14 3.5v4h4" />,
  folder: <path d="M3.5 7V19.5h17V8.5H11L9.5 6.5H3.5z" />,
  upload: <path d="M12 16V5M7.5 9.5L12 5l4.5 4.5M5 19h14" />,
  download: <path d="M12 5v11M7.5 11.5L12 16l4.5-4.5M5 19h14" />,
  search: (
    <>
      <circle cx="10.5" cy="10.5" r="6" />
      <path d="M15.2 15.2L20 20" />
    </>
  ),
  eye: (
    <>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
      <circle cx="12" cy="12" r="2.8" />
    </>
  ),
  'eye-off': (
    <>
      <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" />
      <circle cx="12" cy="12" r="2.8" />
      <path d="M4 4l16 16" />
    </>
  ),
  refresh: <path d="M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4h-4" />,
  external: <path d="M14 4.5h5.5V10M19.5 4.5L11 13M17 14v5H5V7h5" />,
  menu: <path d="M4 7h16M4 12h16M4 17h16" />,
  logout: <path d="M10 4.5H5v15h5M14 8l4 4-4 4M18 12H9" />,
  lock: (
    <>
      <rect x="5" y="10.5" width="14" height="9.5" rx="1.5" />
      <path d="M8 10.5V8a4 4 0 0 1 8 0v2.5" />
    </>
  ),
  user: (
    <>
      <circle cx="12" cy="8.5" r="3.5" />
      <path d="M5 20c.8-4 3.5-6 7-6s6.2 2 7 6" />
    </>
  ),
  overview: (
    <>
      <rect x="4" y="4" width="7" height="7" />
      <rect x="13" y="4" width="7" height="7" />
      <rect x="4" y="13" width="7" height="7" />
      <rect x="13" y="13" width="7" height="7" />
    </>
  ),
  stack: (
    <>
      <path d="M4 7c0-1.4 3.6-2.5 8-2.5s8 1.1 8 2.5-3.6 2.5-8 2.5S4 8.4 4 7z" />
      <path d="M4 7v10c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5V7M4 12c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5" />
    </>
  ),
  route: (
    <>
      <circle cx="6" cy="6" r="2" />
      <circle cx="18" cy="18" r="2" />
      <path d="M8 6h5.5a3 3 0 0 1 0 6h-3a3 3 0 0 0 0 6H16" />
    </>
  ),
} satisfies Record<string, ReactNode>

export type IconName = keyof typeof glyphs

type IconProps = Omit<SVGProps<SVGSVGElement>, 'name'> & {
  name: IconName
  size?: number
  /** Provide only when the icon is the sole carrier of meaning; otherwise it is hidden from assistive technology. */
  title?: string
}

export function Icon({ name, size = 20, title, ...rest }: IconProps) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      focusable="false"
      data-icon={name}
      {...(title ? { role: 'img', 'aria-label': title } : { 'aria-hidden': true })}
      {...rest}
    >
      {glyphs[name]}
    </svg>
  )
}
