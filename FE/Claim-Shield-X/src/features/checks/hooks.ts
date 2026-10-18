import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getHistoryMatches, getLatestRuleRun, runRules } from '@/apis/rules'

export const checkKeys = {
  latest: (claimId: string) => ['claims', 'rule-run', claimId] as const,
  history: (claimId: string) => ['claims', 'history-matches', claimId] as const,
}

export const useLatestRuleRun = (claimId: string) => useQuery({ queryKey: checkKeys.latest(claimId), queryFn: ({ signal }) => getLatestRuleRun(claimId, signal), refetchOnMount: 'always' })

export const useHistoryMatches = (claimId: string) => useQuery({ queryKey: checkKeys.history(claimId), queryFn: ({ signal }) => getHistoryMatches(claimId, signal), refetchOnMount: 'always' })

export function useRunRules(claimId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => runRules(claimId),
    onSuccess: (run) => queryClient.setQueryData(checkKeys.latest(claimId), run),
  })
}
