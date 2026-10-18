import { CLAIM_STAGES } from './labels'
import type { ClaimStage } from './labels'

/** What the claims list is showing, kept in the address so a refresh, a shared link and the back button all keep it. */
export const PAGE_SIZES = [10, 20, 50, 100] as const
export const SORTS = ['createdAt,desc', 'createdAt,asc', 'internalId,asc', 'internalId,desc'] as const
export type ClaimSort = (typeof SORTS)[number]

export type ClaimListParams = { page: number; size: number; sort: ClaimSort; stage?: ClaimStage }

export const DEFAULT_SORT: ClaimSort = 'createdAt,desc'
export const DEFAULT_SIZE = 20

/** The address bar is untrusted input: anything unexpected falls back to a safe default rather than reaching the API. */
export function parseListParams(search: URLSearchParams): ClaimListParams {
  const pageRaw = search.get('page')
  const page = pageRaw !== null && /^\d{1,6}$/.test(pageRaw) ? Number(pageRaw) : 0
  const sizeRaw = Number(search.get('size'))
  const size = (PAGE_SIZES as readonly number[]).includes(sizeRaw) ? sizeRaw : DEFAULT_SIZE
  const sortRaw = search.get('sort')
  const sort = (SORTS as readonly string[]).includes(sortRaw ?? '') ? (sortRaw as ClaimSort) : DEFAULT_SORT
  const stageRaw = search.get('stage')
  const stage = (CLAIM_STAGES as readonly string[]).includes(stageRaw ?? '') ? (stageRaw as ClaimStage) : undefined
  return { page, size, sort, ...(stage ? { stage } : {}) }
}

/** Only values that differ from the defaults are written, so the address stays short. */
export function toSearch(params: ClaimListParams): URLSearchParams {
  const search = new URLSearchParams()
  if (params.stage) search.set('stage', params.stage)
  if (params.sort !== DEFAULT_SORT) search.set('sort', params.sort)
  if (params.size !== DEFAULT_SIZE) search.set('size', String(params.size))
  if (params.page > 0) search.set('page', String(params.page))
  return search
}
