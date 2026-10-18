/** Words for the backend's enum values. An unknown value (a newer backend) is shown as sent rather than hidden or crashed on. */

export const CLAIM_STAGES = ['FNOL', 'ESTIMATE_RECEIVED', 'FINAL_INVOICE_RECEIVED', 'POST_SETTLEMENT'] as const
export type ClaimStage = (typeof CLAIM_STAGES)[number]

export const stageLabels: Record<ClaimStage, string> = {
  FNOL: 'First notice of loss',
  ESTIMATE_RECEIVED: 'Estimate received',
  FINAL_INVOICE_RECEIVED: 'Final invoice received',
  POST_SETTLEMENT: 'Post settlement',
}

export const INCIDENT_TYPES = ['COLLISION', 'THEFT', 'OTHER'] as const
export type IncidentType = (typeof INCIDENT_TYPES)[number]

export const incidentTypeLabels: Record<IncidentType, string> = { COLLISION: 'Collision', THEFT: 'Theft', OTHER: 'Other' }

export function stageLabel(stage: string): string {
  return (stageLabels as Record<string, string>)[stage] ?? stage
}

export function incidentTypeLabel(type: string): string {
  return (incidentTypeLabels as Record<string, string>)[type] ?? type
}
