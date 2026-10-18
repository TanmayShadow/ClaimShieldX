import { useSyncExternalStore } from 'react'

let online = typeof navigator === 'undefined' ? true : navigator.onLine

function subscribe(listener: () => void): () => void {
  // Trust the event, not a re-read of navigator.onLine, which can briefly lag the event that announced it.
  const goOnline = () => {
    online = true
    listener()
  }
  const goOffline = () => {
    online = false
    listener()
  }
  online = navigator.onLine
  window.addEventListener('online', goOnline)
  window.addEventListener('offline', goOffline)
  return () => {
    window.removeEventListener('online', goOnline)
    window.removeEventListener('offline', goOffline)
  }
}

/** The browser's own idea of connectivity. It can say "offline" reliably; "online" only means a network exists. */
export function useOnlineStatus(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => online,
    () => true,
  )
}
