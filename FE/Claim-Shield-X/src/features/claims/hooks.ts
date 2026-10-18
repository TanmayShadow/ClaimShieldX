import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createClaim, getClaim, listClaims } from '@/apis/claims'
import type { ClaimListParams } from './listParams'

export const claimKeys = {
  all: ['claims'] as const,
  list: (params: ClaimListParams) => ['claims', 'list', params] as const,
  detail: (id: string) => ['claims', 'detail', id] as const,
}

export function useClaimsList(params: ClaimListParams) {
  return useQuery({
    queryKey: claimKeys.list(params),
    queryFn: ({ signal }) => listClaims(params, signal),
    // Keep the old page on screen, marked busy, while the next one loads: no flash of emptiness when paging or sorting.
    placeholderData: keepPreviousData,
  })
}

export function useClaim(claimId: string | undefined) {
  return useQuery({
    queryKey: claimKeys.detail(claimId ?? ''),
    queryFn: ({ signal }) => getClaim(claimId!, signal),
    enabled: Boolean(claimId),
  })
}

export function useCreateClaim() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createClaim,
    onSuccess: (claim) => {
      queryClient.setQueryData(claimKeys.detail(claim.id), claim)
      void queryClient.invalidateQueries({ queryKey: claimKeys.all, refetchType: 'all' })
    },
  })
}
