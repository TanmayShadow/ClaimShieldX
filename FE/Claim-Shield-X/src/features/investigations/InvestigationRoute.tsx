import { StageIcon } from '@/components/illustrations/StageIcons'
import type { StageIconName } from '@/components/illustrations/StageIcons'
import { StatusMark } from '@/components/ui/StatusMark'
import { formatDateTime, formatDateTimeWithZone } from '@/utils/format'
import { STAGE_ORDER, stageDisplay, stageLabel } from './labels'
import type { StageId } from './labels'
import styles from './Route.module.css'
import type { Investigation } from './schemas'

const icons: Record<StageId, StageIconName> = {
  PREPARING_EVIDENCE: 'intake',
  EXTRACTING: 'extraction',
  RUNNING_RULES: 'checks',
  BUILDING_HYPOTHESES: 'explanations',
  VALIDATING_FINDINGS: 'validation',
  GENERATING_REPORT: 'report',
}

type RouteProps = { investigation: Investigation; selected: StageId; onSelect: (stage: StageId) => void }

/**
 * The investigation as a route through its real stages. Every state shown is the backend's: nothing advances on a timer,
 * and the flowing segment appears only while a stage is reported RUNNING.
 */
export function InvestigationRoute({ investigation, selected, onSelect }: RouteProps) {
  const byId = new Map(investigation.stages.map((s) => [s.stage, s]))
  const finished = STAGE_ORDER.filter((id) => ['OK', 'DEGRADED'].includes(byId.get(id)?.status ?? '')).length
  const running = STAGE_ORDER.some((id) => byId.get(id)?.status === 'RUNNING')
  // Progress is the count of consecutive finished stages from the start, so the solid track never jumps over unfinished work.
  let consecutive = 0
  for (const id of STAGE_ORDER) {
    if (['OK', 'DEGRADED'].includes(byId.get(id)?.status ?? '')) consecutive++
    else break
  }
  const progress = STAGE_ORDER.length > 1 ? consecutive / (STAGE_ORDER.length - 1) : 0

  return (
    <div className={styles.route} role="group" aria-label={`Investigation route, ${finished} of ${STAGE_ORDER.length} stages finished`} style={{ ['--progress' as string]: Math.min(progress, 1) }}>
      <span className={styles.done} aria-hidden="true" />
      {running && consecutive < STAGE_ORDER.length - 1 ? <span className={styles.flow} aria-hidden="true" data-flow="true" /> : null}
      <ol className={styles.list}>
        {STAGE_ORDER.map((id, index) => {
          const stage = byId.get(id)
          const status = stage?.status ?? 'PENDING'
          const mark = stageDisplay(id, stage, investigation.status)
          const when = stage?.finishedAt ?? stage?.startedAt
          return (
            <li key={id}>
              <button type="button" className={styles.node} data-status={status} data-stage={id} aria-pressed={id === selected} onClick={() => onSelect(id)}>
                <span className={styles.icon}>
                  <StageIcon name={icons[id]} size={44} />
                </span>
                <span className={styles.text}>
                  <span className={styles.step}>{String(index + 1).padStart(2, '0')}</span>
                  <span className={styles.name}>{stageLabel(id)}</span>
                  <StatusMark state={mark.state}>{mark.label}</StatusMark>
                  {when ? (
                    <time className={styles.when} dateTime={when} title={formatDateTimeWithZone(when)}>
                      {stage?.finishedAt ? 'finished ' : 'started '}
                      {formatDateTime(when)}
                    </time>
                  ) : null}
                </span>
              </button>
            </li>
          )
        })}
      </ol>
    </div>
  )
}
