import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { isApiError } from '@/apis/errors'
import { getAudit, getEvidence, getGraph, getLatestReport, listActions, recordAction } from '@/apis/report'
import { newIdempotencyKey } from '@/features/documents/uploadRules'

export const reportKeys = {
  latest: (claimId: string) => ['claims', 'report', claimId] as const,
  actions: (investigationId: string) => ['investigation', 'actions', investigationId] as const,
  audit: (investigationId: string, page: number) => ['investigation', 'audit', investigationId, page] as const,
  evidence: (investigationId: string) => ['investigation', 'evidence', investigationId] as const,
  graph: (investigationId: string) => ['investigation', 'graph', investigationId] as const,
}

export const useLatestReport = (claimId: string) => useQuery({ queryKey: reportKeys.latest(claimId), queryFn: ({ signal }) => getLatestReport(claimId, signal), refetchOnMount: 'always' })

// A snapshot never changes once frozen, so evidence and graph are fetched once per investigation.
export const useEvidence = (investigationId: string | undefined) =>
  useQuery({ queryKey: reportKeys.evidence(investigationId ?? ''), queryFn: ({ signal }) => getEvidence(investigationId!, signal), enabled: Boolean(investigationId), staleTime: Infinity })
export const useGraph = (investigationId: string | undefined) =>
  useQuery({ queryKey: reportKeys.graph(investigationId ?? ''), queryFn: ({ signal }) => getGraph(investigationId!, signal), enabled: Boolean(investigationId), staleTime: Infinity })

export const useActions = (investigationId: string) => useQuery({ queryKey: reportKeys.actions(investigationId), queryFn: ({ signal }) => listActions(investigationId, signal), refetchOnMount: 'always' })

export const useAudit = (investigationId: string, page: number) =>
  useQuery({ queryKey: reportKeys.audit(investigationId, page), queryFn: ({ signal }) => getAudit(investigationId, page, signal), placeholderData: keepPreviousData, refetchOnMount: 'always' })

/** One key per decision. A repeat of the same click after a dropped connection reuses it, so the decision is recorded once. */
export function useRecordAction(investigationId: string, claimId: string, onDone?: () => void) {
  const queryClient = useQueryClient()
  const key = useRef<string | null>(null)
  return useMutation({
    mutationFn: (body: { action: string; rationale: string; reportVersion: number }) => {
      key.current ??= newIdempotencyKey()
      return recordAction(investigationId, key.current, body)
    },
    onSuccess: () => {
      key.current = null
      void queryClient.invalidateQueries({ queryKey: reportKeys.actions(investigationId), refetchType: 'all' })
      void queryClient.invalidateQueries({ queryKey: ['investigation', 'audit', investigationId], refetchType: 'all' })
      void queryClient.invalidateQueries({ queryKey: reportKeys.latest(claimId), refetchType: 'all' })
      onDone?.()
    },
    onError: (error) => {
      if (isApiError(error) && error.status >= 400) key.current = null
    },
  })
}
