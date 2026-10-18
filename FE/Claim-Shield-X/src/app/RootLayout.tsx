import { useEffect, useRef } from 'react'
import { Outlet, ScrollRestoration, useLocation } from 'react-router'

/** After a client-side navigation, move focus to the new page so keyboard and screen-reader users start at its top. */
function useFocusOnNavigate() {
  const { pathname } = useLocation()
  const previous = useRef(pathname)

  useEffect(() => {
    if (previous.current === pathname) return
    previous.current = pathname
    const target = document.querySelector<HTMLElement>('[data-page-title]') ?? document.getElementById('main')
    target?.focus({ preventScroll: true })
  }, [pathname])
}

export function RootLayout() {
  useFocusOnNavigate()
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to main content
      </a>
      <Outlet />
      <ScrollRestoration />
    </>
  )
}
