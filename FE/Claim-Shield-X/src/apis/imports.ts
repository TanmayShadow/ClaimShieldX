import { importPageSchema, importSchema, rowPageSchema } from '@/features/imports/schemas'
import { apiRaw, apiRequest } from './client'

const path = (id: string) => `/historical-imports/${encodeURIComponent(id)}`

export const listImports = (page: number, signal?: AbortSignal) => apiRequest('/historical-imports', { query: { page, size: 20, sort: 'createdAt,desc' }, schema: importPageSchema, signal })

export const getImport = (id: string, signal?: AbortSignal) => apiRequest(path(id), { schema: importSchema, signal })

export const listImportRows = (id: string, status: string | undefined, page: number, signal?: AbortSignal) =>
  apiRequest(`${path(id)}/rows`, { query: { page, size: 50, status }, schema: rowPageSchema, signal })

/** The server answers 202 even for a file it could not use: that is a FAILED import with a reason, not an HTTP error. */
export function uploadImport(args: { file: File; importType: string; source: string; idempotencyKey: string }) {
  const form = new FormData()
  form.append('file', args.file)
  form.append('importType', args.importType)
  form.append('templateVersion', '1')
  if (args.source.trim()) form.append('source', args.source.trim())
  return apiRequest('/historical-imports', { method: 'POST', headers: { 'Idempotency-Key': args.idempotencyKey }, body: form, schema: importSchema, timeoutMs: 120_000 })
}

/** The template is behind the session like everything else, so it is fetched with the token and saved from memory. */
export async function fetchTemplate(importType: string, signal?: AbortSignal): Promise<Blob> {
  const response = await apiRaw(`/historical-imports/templates/${encodeURIComponent(importType)}`, { query: { version: 1 }, headers: { Accept: 'text/csv' }, signal })
  return response.blob()
}
