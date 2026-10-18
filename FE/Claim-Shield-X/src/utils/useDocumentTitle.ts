import { useEffect } from 'react'

const BRAND = 'ClaimShield X'

export function useDocumentTitle(title: string | undefined): void {
  useEffect(() => {
    document.title = title ? `${title} · ${BRAND}` : BRAND
  }, [title])
}
