import type { IconName } from '@/components/ui/Icon'

export type NavEntry = { label: string; to: string; icon: IconName; end?: boolean }

/**
 * Only destinations that exist and work. A task adds its entry when it ships its screen: Claims in F6, Data imports in F12.
 * Activity and Settings are absent because the backend has no audit or settings API to back them.
 */
export const navEntries: NavEntry[] = [
  { label: 'Overview', to: '/app', icon: 'overview', end: true },
  { label: 'Claims', to: '/app/claims', icon: 'folder' },
]
