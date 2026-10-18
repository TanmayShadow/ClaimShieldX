import { QueryClient } from '@tanstack/react-query'
import { isApiError } from '@/apis/errors'

/**
 * Server state lives here and nowhere else. It is cleared whenever a session ends, so one person's data can never
 * be shown to the next person who signs in on the same browser.
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (failureCount, error) => failureCount < 2 && isApiError(error) && error.retryable,
    },
    mutations: { retry: false },
  },
})
