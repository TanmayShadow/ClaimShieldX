import { apiRaw, apiRequest } from './client'
import { documentPageSchema, extractionSchema, extractionStartedSchema, requirementsSchema } from '@/features/documents/schemas'

const claimPath = (claimId: string) => `/claims/${encodeURIComponent(claimId)}`

export const listDocuments = (claimId: string, signal?: AbortSignal) =>
  apiRequest(`${claimPath(claimId)}/documents`, { query: { size: 100, sort: 'receivedAt,desc' }, schema: documentPageSchema, signal })

export const getRequirements = (claimId: string, signal?: AbortSignal) =>
  apiRequest(`${claimPath(claimId)}/document-requirements`, { schema: requirementsSchema, signal })

export const getExtraction = (claimId: string, documentId: string, signal?: AbortSignal) =>
  apiRequest(`${claimPath(claimId)}/documents/${encodeURIComponent(documentId)}/extraction`, { schema: extractionSchema, signal })

/** Runs the model on one document. The server answers only when it has finished, which can take minutes. */
export const startExtraction = (claimId: string, documentId: string, signal?: AbortSignal) =>
  apiRequest(`${claimPath(claimId)}/documents/${encodeURIComponent(documentId)}/extraction`, {
    method: 'POST',
    schema: extractionStartedSchema,
    signal,
    timeoutMs: 4 * 60_000,
  })

/** The original file, fetched with the session's token. There is no public or signed address for it. */
export async function fetchDocumentBlob(claimId: string, documentId: string, signal?: AbortSignal): Promise<Blob> {
  const response = await apiRaw(`${claimPath(claimId)}/documents/${encodeURIComponent(documentId)}/content`, { signal, timeoutMs: 120_000 })
  return response.blob()
}
