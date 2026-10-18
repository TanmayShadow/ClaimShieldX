import { useEffect, useState } from 'react'
import { fetchDocumentBlob } from '@/apis/documents'

type BlobState = { status: 'idle' | 'loading' | 'ready' | 'error'; url: string | null; error: unknown }
type Settled = { key: string; status: 'ready' | 'error'; url: string | null; error: unknown }

/**
 * Fetches a stored file with the session's token and exposes it as a short-lived object URL, revoked when it is no longer needed.
 * The file never has a public address; this is the only way the browser sees its bytes.
 */
export function useBlobUrl(claimId: string, documentId: string, enabled: boolean): BlobState {
  const key = `${claimId}/${documentId}`
  const [settled, setSettled] = useState<Settled | null>(null)

  useEffect(() => {
    if (!enabled) return
    const controller = new AbortController()
    let created: string | null = null
    fetchDocumentBlob(claimId, documentId, controller.signal)
      .then((blob) => {
        created = URL.createObjectURL(blob)
        setSettled({ key, status: 'ready', url: created, error: null })
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setSettled({ key, status: 'error', url: null, error })
      })
    return () => {
      controller.abort()
      if (created) URL.revokeObjectURL(created)
      setSettled((current) => (current?.key === key ? null : current))
    }
  }, [claimId, documentId, enabled, key])

  if (!enabled) return { status: 'idle', url: null, error: null }
  if (settled && settled.key === key) return { status: settled.status, url: settled.url, error: settled.error }
  return { status: 'loading', url: null, error: null }
}

/** Saves a stored file to disk under its (server-sanitised) name. */
export async function saveDocument(claimId: string, documentId: string, fileName: string): Promise<void> {
  const blob = await fetchDocumentBlob(claimId, documentId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
}
