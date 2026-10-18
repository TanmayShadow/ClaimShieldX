import type { EvidenceKind } from '@/components/evidence/EvidenceReference'
import { sampleDocuments, sampleRules } from './data'
import type { EvidenceRef } from './types'

export type Focus = { documentId?: string; field?: string; ruleId?: string; historyId?: string; claimField?: string }

export type ResolvedRef = { kind: EvidenceKind; label: string; detail?: string; openable: boolean }

const fieldLabel = (name: string) => name.replace(/\[(\d+)\]/, (_, i: string) => ` ${Number(i) + 1}`).replace(/([A-Z])/g, ' $1').toLowerCase()

/** Turns a citation into text and says whether there is a real item behind it to open. */
export function resolveRef(ref: EvidenceRef): ResolvedRef {
  switch (ref.kind) {
    case 'DOCUMENT_FIELD': {
      const doc = sampleDocuments.find((d) => d.id === ref.documentId)
      const field = doc?.fields.find((f) => f.name === ref.field)
      if (!doc || !field) return { kind: 'missing', label: 'Source not found', openable: false }
      return { kind: 'document', label: `${doc.typeLabel}, ${fieldLabel(field.name)}`, detail: field.page ? `page ${field.page}` : undefined, openable: true }
    }
    case 'RULE_RESULT': {
      const rule = sampleRules.find((r) => r.ruleId === ref.ruleId)
      return rule ? { kind: 'rule', label: rule.label, openable: true } : { kind: 'missing', label: 'Source not found', openable: false }
    }
    case 'HISTORICAL_RECORD':
      return { kind: 'history', label: `Earlier claim ${ref.sourceRecordId}`, openable: true }
    case 'CLAIM_FIELD':
      return { kind: 'claim', label: `${ref.label}: ${ref.value}`, openable: true }
    case 'MISSING':
      return { kind: 'missing', label: ref.label, openable: false }
  }
}

export function focusFor(ref: EvidenceRef): { step: StepId; focus: Focus } | null {
  switch (ref.kind) {
    case 'DOCUMENT_FIELD':
      return { step: 'documents', focus: { documentId: ref.documentId, field: ref.field } }
    case 'RULE_RESULT':
      return { step: 'checks', focus: { ruleId: ref.ruleId } }
    case 'HISTORICAL_RECORD':
      return { step: 'history', focus: { historyId: ref.sourceRecordId } }
    case 'CLAIM_FIELD':
      return { step: 'documents', focus: { claimField: ref.field } }
    case 'MISSING':
      return null
  }
}

export type StepId = 'documents' | 'checks' | 'history' | 'explanations' | 'review'
export const stepIds: StepId[] = ['documents', 'checks', 'history', 'explanations', 'review']
