import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { isApiError } from '@/apis/errors'
import { cancelInvestigation, getInvestigation, listInvestigations, retryInvestigation, startInvestigation } from '@/apis/investigations'
import { claimKeys } from '@/features/claims/hooks'
import { newIdempotencyKey } from '@/features/documents/uploadRules'
import { pollInterval } from './polling'
import type { Investigation } from './schemas'

export const investigationKeys = {
  list: (claimId: string) => ['claims', 'investigations', claimId] as const,
  one: (id: string) => ['claims', 'investigation', id] as const,
}

export function useInvestigations(claimId: string) {
  return useQuery({
    queryKey: investigationKeys.list(claimId),
    queryFn: ({ signal }) => listInvestigations(claimId, signal),
    // The newest investigation is the one being watched, so while it is unfinished the list is re-read too.
    refetchInterval: (query) => {
      const latest = query.state.data?.items[0]
      return latest ? pollInterval(latest.status, Date.now() - new Date(latest.createdAt).getTime()) : false
    },
  })
}

/** One investigation, polled while it is unfinished and left alone once it ends. Polling pauses while the tab is hidden. */
export function useInvestigation(id: string | undefined) {
  return useQuery({
    queryKey: investigationKeys.one(id ?? ''),
    queryFn: ({ signal }) => getInvestigation(id!, signal),
    enabled: Boolean(id),
    placeholderData: keepPreviousData,
    refetchInterval: (query) => (query.state.data ? pollInterval(query.state.data.status, Date.now() - new Date(query.state.data.createdAt).getTime()) : false),
    refetchIntervalInBackground: false,
  })
}

/**
 * Start, retry and cancel each need an Idempotency-Key. One key per intention: a repeat of the same click after a dropped
 * connection reuses it (so the server cannot do it twice), and a success starts a new intention.
 */
function useIdempotentMutation<TArg>(run: (arg: TArg, key: string) => Promise<Investigation>, claimId: string, onDone?: (result: Investigation) => void) {
  const queryClient = useQueryClient()
  const key = useRef<string | null>(null)
  const mutation = useMutation({
    mutationFn: async (arg: TArg) => {
      key.current ??= newIdempotencyKey()
      return run(arg, key.current)
    },
    onSuccess: (result) => {
      key.current = null
      queryClient.setQueryData(investigationKeys.one(result.id), result)
      onDone?.(result)
    },
    onError: (error) => {
      // An answer from the server (even a refusal) ends the intention; no answer at all (status 0) keeps the key so a retry is safe.
      if (isApiError(error) && error.status >= 400) key.current = null
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: investigationKeys.list(claimId), refetchType: 'all' })
      void queryClient.invalidateQueries({ queryKey: claimKeys.all, refetchType: 'all' })
    },
  })
  return mutation
}

export const useStartInvestigation = (claimId: string, onDone?: (i: Investigation) => void) =>
  useIdempotentMutation<string | undefined>((note, key) => startInvestigation(claimId, key, note), claimId, onDone)

export const useRetryInvestigation = (claimId: string, id: string) => useIdempotentMutation<void>((_, key) => retryInvestigation(id, key), claimId)

export const useCancelInvestigation = (claimId: string, id: string) => useIdempotentMutation<void>((_, key) => cancelInvestigation(id, key), claimId)
