import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getExtraction, getRequirements, listDocuments, startExtraction } from '@/apis/documents'
import { claimKeys } from '@/features/claims/hooks'

export const documentKeys = {
  documents: (claimId: string) => ['claims', 'documents', claimId] as const,
  requirements: (claimId: string) => ['claims', 'requirements', claimId] as const,
  extraction: (claimId: string, documentId: string) => ['claims', 'extraction', claimId, documentId] as const,
}

export const useDocuments = (claimId: string) => useQuery({ queryKey: documentKeys.documents(claimId), queryFn: ({ signal }) => listDocuments(claimId, signal) })

export const useRequirements = (claimId: string) => useQuery({ queryKey: documentKeys.requirements(claimId), queryFn: ({ signal }) => getRequirements(claimId, signal) })

export const useExtraction = (claimId: string, documentId: string, enabled: boolean) =>
  useQuery({ queryKey: documentKeys.extraction(claimId, documentId), queryFn: ({ signal }) => getExtraction(claimId, documentId, signal), enabled, retry: false })

/** After anything that changes what a claim holds, the list, the checklist and every claim's readiness are stale. */
export function useRefreshClaimEvidence() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: claimKeys.all, refetchType: 'all' })
}

export function useStartExtraction(claimId: string) {
  const refresh = useRefreshClaimEvidence()
  return useMutation({
    mutationFn: (documentId: string) => startExtraction(claimId, documentId),
    // Success or failure, the document may have moved on (read, needs review, failed), so look again either way.
    onSettled: () => refresh(),
  })
}
