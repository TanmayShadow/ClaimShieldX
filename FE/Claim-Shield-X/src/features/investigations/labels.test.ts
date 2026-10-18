import { STAGE_ORDER, investigationErrorMessage, investigationStatusMark, isActive, isTerminal, isWaiting, stageLabel, stageMark, stagePurpose } from './labels'

describe('stage wording', () => {
  it('names and explains all six stages in order', () => {
    expect(STAGE_ORDER).toEqual(['PREPARING_EVIDENCE', 'EXTRACTING', 'RUNNING_RULES', 'BUILDING_HYPOTHESES', 'VALIDATING_FINDINGS', 'GENERATING_REPORT'])
    for (const s of STAGE_ORDER) {
      expect(stageLabel(s).length).toBeGreaterThan(3)
      expect(stagePurpose[s].length).toBeGreaterThan(20)
    }
    expect(stageLabel('NEW_STAGE')).toBe('NEW_STAGE')
  })

  it('gives every stage state its own shape', () => {
    const states = ['PENDING', 'RUNNING', 'OK', 'DEGRADED', 'FAILED', 'SKIPPED'].map((s) => stageMark(s).state)
    expect(new Set(states).size).toBe(6)
  })

  it('never lets an unbuilt stage read as checked and skipped', () => {
    expect(stageMark('SKIPPED', 'NOT_AVAILABLE_YET')).toEqual({ state: 'unknown', label: 'Not available yet' })
    expect(stageMark('SKIPPED').label).toBe('Nothing to do')
    expect(stageMark('SKIPPED', 'NOT_AVAILABLE_YET').state).not.toBe(stageMark('SKIPPED').state)
  })

  it('says a partly finished stage is partly finished, not failed', () => {
    expect(stageMark('DEGRADED')).toEqual({ state: 'attention', label: 'Finished, partly' })
  })

  it('says a stage the model was deliberately not asked in is skipped for a reason, not unavailable', () => {
    expect(stageMark('SKIPPED', 'NO_CONCERNS_TO_ANALYZE')).toEqual({ state: 'skipped', label: 'Skipped: nothing flagged' })
    expect(stageMark('SKIPPED', 'NO_CONCERNS_TO_ANALYZE').label).not.toBe(stageMark('SKIPPED', 'NOT_AVAILABLE_YET').label)
  })

  it('describes every stage as work that is done, now that all six are built', () => {
    for (const s of STAGE_ORDER) expect(stagePurpose[s]).not.toMatch(/^Will /)
  })
})

describe('investigation status', () => {
  it('classifies statuses', () => {
    expect(['COMPLETED', 'FAILED', 'CANCELLED'].every(isTerminal)).toBe(true)
    expect(['QUEUED', 'EXTRACTING', 'WAITING_FOR_EVIDENCE', 'RETRY_WAIT'].some(isTerminal)).toBe(false)
    expect(isWaiting('WAITING_FOR_EVIDENCE') && isWaiting('RETRY_WAIT')).toBe(true)
    expect(isWaiting('EXTRACTING')).toBe(false)
    expect(isActive('QUEUED')).toBe(true)
    expect(isActive('COMPLETED')).toBe(false)
  })

  it('says waiting for a document is waiting, not failure', () => {
    expect(investigationStatusMark('WAITING_FOR_EVIDENCE')).toEqual({ state: 'pending', label: 'Waiting for a document' })
  })

  it('names the running stage in words', () => {
    expect(investigationStatusMark('EXTRACTING')).toEqual({ state: 'active', label: 'Running: documents read' })
  })

  it('shows a cancel in progress as stopping, until it is over', () => {
    expect(investigationStatusMark('EXTRACTING', true).label).toBe('Stopping at the next safe point')
    expect(investigationStatusMark('CANCELLED', true).label).toBe('Cancelled')
  })

  it('explains every failure code and never repeats an unknown one', () => {
    for (const code of ['EXTRACTION_FAILED', 'MODEL_UNAVAILABLE', 'MODEL_QUOTA_EXHAUSTED', 'INVALID_MODEL_OUTPUT', 'STORAGE_UNAVAILABLE', 'TIMEOUT', 'INTERNAL_ERROR']) {
      expect(investigationErrorMessage(code)).not.toContain(code)
    }
    expect(investigationErrorMessage('BRAND_NEW')).toBe('The investigation could not finish.')
    expect(investigationErrorMessage(null)).toBe('The investigation could not finish.')
  })

  it('never calls anything fraud or a verdict', () => {
    const all = JSON.stringify([STAGE_ORDER.map((s) => stagePurpose[s]), ['QUEUED', 'COMPLETED', 'FAILED', 'CANCELLED', 'RETRY_WAIT'].map((s) => investigationStatusMark(s))])
    expect(all).not.toMatch(/fraud|suspicious|verdict|guilty/i)
  })
})
