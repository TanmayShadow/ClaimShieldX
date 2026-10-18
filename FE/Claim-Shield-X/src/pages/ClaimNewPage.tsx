import { useNavigate } from 'react-router'
import { ClaimForm } from '@/features/claims/ClaimForm'
import { PageHeader } from '@/components/ui/PageHeader'
import { useDocumentTitle } from '@/utils/useDocumentTitle'

export function ClaimNewPage() {
  useDocumentTitle('Create claim')
  const navigate = useNavigate()
  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: 'Claims', to: '/app/claims' }, { label: 'Create claim' }]}
        title="Create claim"
        description="Record a claim so its documents can be filed and checked. The details are shown back to you exactly as the server stored them."
      />
      <ClaimForm onCreated={(id, internalId) => navigate(`/app/claims/${id}`, { state: { created: internalId } })} />
    </>
  )
}
