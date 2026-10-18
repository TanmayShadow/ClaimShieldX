import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef } from 'react'
import { fetchTemplate, getImport, listImportRows, listImports, uploadImport } from '@/apis/imports'
import { isApiError } from '@/apis/errors'
import { newIdempotencyKey } from '@/features/documents/uploadRules'
import { isImportInFlight } from './labels'

export const importKeys = {
  all: ['historical-imports'] as const,
  list: (page: number) => ['historical-imports', 'list', page] as const,
  one: (id: string) => ['historical-imports', 'one', id] as const,
  rows: (id: string, status: string | undefined, page: number) => ['historical-imports', 'rows', id, status ?? 'ALL', page] as const,
}

export const useImports = (page: number) => useQuery({ queryKey: importKeys.list(page), queryFn: ({ signal }) => listImports(page, signal), placeholderData: keepPreviousData, refetchOnMount: 'always', retry: false })

/** An import is final almost at once; if the server ever answers while it is still working, it is polled until it ends. */
export const useImport = (id: string) =>
  useQuery({
    queryKey: importKeys.one(id),
    queryFn: ({ signal }) => getImport(id, signal),
    refetchInterval: (query) => (query.state.data && isImportInFlight(query.state.data.status) ? 2000 : false),
    retry: false,
  })

/** `enabled` is false for a file that held no rows (a refused file), so nothing is asked for. */
export const useImportRows = (id: string, status: string | undefined, page: number, enabled = true) =>
  useQuery({ queryKey: importKeys.rows(id, status, page), queryFn: ({ signal }) => listImportRows(id, status, page, signal), placeholderData: keepPreviousData, retry: false, enabled })

type UploadArgs = { file: File; importType: string; source: string }

/**
 * One key per intention. A repeat of the same upload after a dropped connection reuses it (the server then cannot store the file twice);
 * a different file, type or source gets a new key, because the same key with a different request is refused.
 */
export function useUploadImport() {
  const queryClient = useQueryClient()
  const key = useRef<{ fingerprint: string; value: string } | null>(null)
  return useMutation({
    mutationFn: (args: UploadArgs) => {
      const fingerprint = [args.importType, args.source.trim(), args.file.name, args.file.size, args.file.lastModified].join('|')
      if (key.current?.fingerprint !== fingerprint) key.current = { fingerprint, value: newIdempotencyKey() }
      return uploadImport({ ...args, idempotencyKey: key.current.value })
    },
    onSuccess: (result) => {
      key.current = null
      queryClient.setQueryData(importKeys.one(result.id), result)
    },
    onError: (error) => {
      // An answer from the server ends the intention; no answer at all keeps the key so repeating is safe.
      if (isApiError(error) && error.status >= 400) key.current = null
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: importKeys.all, refetchType: 'all' })
      // New history can change every claim's earlier-claim matches and rule results.
      void queryClient.invalidateQueries({ queryKey: ['claims', 'history-matches'], refetchType: 'all' })
      void queryClient.invalidateQueries({ queryKey: ['claims', 'rule-run'], refetchType: 'all' })
    },
  })
}

export const useTemplateDownload = () => useMutation({ mutationFn: (importType: string) => fetchTemplate(importType) })
