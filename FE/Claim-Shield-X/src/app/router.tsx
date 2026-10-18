import { createBrowserRouter } from 'react-router'
import type { RouteObject } from 'react-router'
import { Skeleton } from '@/components/ui/Skeleton'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { AppShell } from '@/features/shell/AppShell'
import { RouteError } from '@/features/shell/RouteError'
import { RootLayout } from './RootLayout'

const routes: RouteObject[] = [
  {
    path: '/',
    Component: RootLayout,
    errorElement: <RouteError homeTo="/" />,
    HydrateFallback: () => (
      <div className="container" style={{ paddingBlock: 'var(--space-7)' }}>
        <Skeleton label="Loading the page" />
      </div>
    ),
    children: [
      { index: true, lazy: async () => ({ Component: (await import('@/pages/LandingPage')).LandingPage }) },
      { path: 'login', lazy: async () => ({ Component: (await import('@/pages/LoginPage')).LoginPage }) },
      {
        path: 'app',
        Component: RequireAuth,
        children: [
          {
            Component: AppShell,
            children: [
              {
                // Errors inside a page render here, inside the shell, so navigation and sign-out stay usable.
                errorElement: <RouteError />,
                children: [
                  { index: true, lazy: async () => ({ Component: (await import('@/pages/AppHomePage')).AppHomePage }) },
                  { path: 'claims', lazy: async () => ({ Component: (await import('@/pages/ClaimsListPage')).ClaimsListPage }) },
                  { path: 'claims/new', lazy: async () => ({ Component: (await import('@/pages/ClaimNewPage')).ClaimNewPage }) },
                  {
                    path: 'claims/:claimId',
                    lazy: async () => ({ Component: (await import('@/pages/ClaimWorkspacePage')).ClaimWorkspacePage }),
                    children: [
                      { index: true, lazy: async () => ({ Component: (await import('@/pages/ClaimRecordPage')).ClaimRecordPage }) },
                      { path: 'documents', lazy: async () => ({ Component: (await import('@/pages/ClaimDocumentsPage')).ClaimDocumentsPage }) },
                      { path: 'investigation', lazy: async () => ({ Component: (await import('@/pages/ClaimInvestigationPage')).ClaimInvestigationPage }) },
                      { path: 'evidence', lazy: async () => ({ Component: (await import('@/pages/ClaimEvidencePage')).ClaimEvidencePage }) },
                      { path: 'report', lazy: async () => ({ Component: (await import('@/pages/ClaimReportPage')).ClaimReportPage }) },
                      { path: 'checks', lazy: async () => ({ Component: (await import('@/pages/ClaimChecksPage')).ClaimChecksPage }) },
                      { path: 'history', lazy: async () => ({ Component: (await import('@/pages/ClaimHistoryPage')).ClaimHistoryPage }) },
                    ],
                  },
                  { path: '*', lazy: async () => ({ Component: (await import('@/pages/AppNotFoundPage')).AppNotFoundPage }) },
                ],
              },
            ],
          },
        ],
      },
      { path: 'sample-investigation', lazy: async () => ({ Component: (await import('@/pages/SampleInvestigationPage')).SampleInvestigationPage }) },
      ...(import.meta.env.DEV
        ? [{ path: 'design', lazy: async () => ({ Component: (await import('@/pages/DesignSandboxPage')).DesignSandboxPage }) }]
        : []),
      { path: '*', lazy: async () => ({ Component: (await import('@/pages/NotFoundPage')).NotFoundPage }) },
    ],
  },
]

export const router = createBrowserRouter(routes)
