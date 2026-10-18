import { useState } from 'react'
import { useOutletContext } from 'react-router'
import { EmptyFolder } from '@/components/illustrations/EmptyFolder'
import { Callout } from '@/components/ui/Callout'
import { EmptyState } from '@/components/ui/EmptyState'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { Skeleton } from '@/components/ui/Skeleton'
import { useRequirements } from '@/features/documents/hooks'
import { History } from '@/features/investigations/History'
import { useInvestigation, useInvestigations } from '@/features/investigations/hooks'
import styles from '@/features/investigations/Investigation.module.css'
import { InvestigationView } from '@/features/investigations/InvestigationView'
import { isActive } from '@/features/investigations/labels'
import { StartPanel } from '@/features/investigations/StartPanel'
import type { ClaimOutletContext } from './ClaimWorkspacePage'

export function ClaimInvestigationPage() {
  const { claim } = useOutletContext<ClaimOutletContext>()
  const list = useInvestigations(claim.id)
  const requirements = useRequirements(claim.id)
  const [chosenId, setChosenId] = useState<string | null>(null)
  const [refusedAsActive, setRefusedAsActive] = useState(false)

  const newest = list.data?.items[0]
  const currentId = chosenId && list.data?.items.some((i) => i.id === chosenId) ? chosenId : newest?.id
  const detail = useInvestigation(currentId)
  // Until the single-investigation poll answers, the list's copy of it is shown.
  const shown = detail.data ?? list.data?.items.find((i) => i.id === currentId)
  const willWait = requirements.data?.items.some((i) => i.mandatory && ['NOT_RECEIVED', 'FAILED'].includes(i.evidenceState)) ?? false
  const newestActive = newest ? isActive(newest.status) : false

  return (
    <div className={styles.stack}>
      {list.isPending ? <Skeleton variant="block" label="Loading investigations" /> : null}
      {list.isError ? <ErrorPanel error={list.error} subject="the investigations" onRetry={() => void list.refetch()} retrying={list.isFetching} /> : null}

      {list.data && list.data.items.length === 0 ? (
        <>
          <EmptyState title="No investigation yet" illustration={<EmptyFolder />} headingLevel={2}>
            Start one to prepare this claim&rsquo;s evidence and run the checks. You can watch each stage as it happens.
          </EmptyState>
          <StartPanel claimId={claim.id} willWait={willWait} onStarted={(i) => setChosenId(i.id)} onAlreadyActive={() => setRefusedAsActive(true)} />
        </>
      ) : null}

      {refusedAsActive && newestActive ? (
        <Callout tone="info" title="An investigation is already under way for this claim.">
          Only one can run at a time, so this is the one shown. You can start another when it has ended.
        </Callout>
      ) : null}

      {shown ? (
        <InvestigationView
          claimId={claim.id}
          investigation={shown}
          dataUpdatedAt={detail.dataUpdatedAt}
          fetching={detail.isFetching}
          refreshError={detail.isError ? detail.error : null}
          onRefresh={() => {
            void detail.refetch()
            void list.refetch()
          }}
        />
      ) : null}

      {list.data && list.data.items.length > 0 && !newestActive ? (
        <section aria-labelledby="start-title" className={styles.stack}>
          <h2 id="start-title" style={{ fontSize: 'var(--text-xl)' }}>
            Start another investigation
          </h2>
          <StartPanel claimId={claim.id} willWait={willWait} onStarted={(i) => setChosenId(i.id)} onAlreadyActive={() => setRefusedAsActive(true)} />
        </section>
      ) : null}

      {list.data && list.data.items.length > 1 && currentId ? (
        <section aria-labelledby="history-title" className={styles.stack}>
          <h2 id="history-title" style={{ fontSize: 'var(--text-xl)' }}>
            Earlier investigations
          </h2>
          <History items={list.data.items} currentId={currentId} onSelect={setChosenId} />
        </section>
      ) : null}
    </div>
  )
}
