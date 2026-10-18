import { reviewActions, sampleDocuments, sampleHistory, sampleHypotheses, sampleRules } from './data'
import { focusFor, resolveRef } from './refs'
import type { EvidenceRef } from './types'

const money = (text: string) => Math.round(Number(text.replace(/,/g, '')) * 100)
const doc = (id: string) => sampleDocuments.find((d) => d.id === id)!

describe('sample case data', () => {
  it('has estimate lines that add up exactly to the printed total', () => {
    const s = doc('doc-estimate').sheet!
    expect(s.lines.reduce((sum, l) => sum + money(l.amount), 0)).toBe(money(s.totals[0]!.amount))
    expect(money(s.totals[0]!.amount)).toBe(4500050)
  })

  it('has invoice lines that add up exactly to the printed total', () => {
    const s = doc('doc-invoice').sheet!
    expect(s.lines.reduce((sum, l) => sum + money(l.amount), 0)).toBe(money(s.totals[0]!.amount))
    expect(money(s.totals[0]!.amount)).toBe(6251000)
  })

  it('makes the variance rule match the two documents', () => {
    const invoice = money(doc('doc-invoice').sheet!.totals[0]!.amount)
    const estimate = money(doc('doc-estimate').sheet!.totals[0]!.amount)
    const rule = sampleRules.find((r) => r.ruleId === 'ESTIMATE_INVOICE_VARIANCE')!
    expect(rule.explanation).toContain('17,509.50')
    expect(invoice - estimate).toBe(1750950)
    const percent = ((invoice - estimate) / estimate) * 100
    expect(percent.toFixed(2)).toBe('38.91')
    expect(rule.explanation).toContain('38.91%')
    expect(percent).toBeGreaterThan(10)
  })

  it('has the extra invoice lines explain the whole difference', () => {
    const estimateLines = doc('doc-estimate').sheet!.lines.map((l) => l.description)
    const extra = doc('doc-invoice').sheet!.lines.filter((l) => !estimateLines.includes(l.description))
    expect(extra.map((l) => l.description)).toEqual(['Radiator support repair', 'Coolant hose and refill', 'Wheel alignment'])
    expect(extra.reduce((sum, l) => sum + money(l.amount), 0)).toBe(1750950)
  })

  it('covers all eight rules of the backend, once each', () => {
    expect(sampleRules.map((r) => r.ruleId).sort()).toEqual(
      [
        'DATE_CONSISTENCY',
        'DOCUMENT_FILE_DUPLICATE',
        'ESTIMATE_INVOICE_VARIANCE',
        'HISTORICAL_RELATIONSHIP',
        'INVOICE_ARITHMETIC',
        'INVOICE_IDENTITY_DUPLICATE',
        'REPEATED_LINE_ITEMS',
        'REQUIRED_EVIDENCE',
      ].sort(),
    )
  })

  it('gives severity to flags and to nothing else, as the backend does', () => {
    for (const r of sampleRules) {
      expect(Boolean(r.severity), r.ruleId).toBe(r.outcome === 'FLAG')
      if (r.outcome === 'FLAG') expect(r.considerations?.length, r.ruleId).toBeGreaterThan(0)
    }
  })

  it('shows a mix of outcomes, including a rule that could not run', () => {
    const outcomes = sampleRules.map((r) => r.outcome)
    expect(outcomes.filter((o) => o === 'PASS')).toHaveLength(5)
    expect(outcomes.filter((o) => o === 'FLAG')).toHaveLength(2)
    expect(outcomes.filter((o) => o === 'INCOMPLETE')).toHaveLength(1)
  })

  it('anchors every extracted field to something printed on its document', () => {
    for (const d of sampleDocuments.filter((x) => x.sheet)) {
      const s = d.sheet!
      const anchors = new Set([...s.meta.map((m) => m.anchor), ...s.lines.map((l) => l.id), ...s.totals.map((t) => t.id)])
      for (const f of d.fields.filter((x) => x.state === 'EXTRACTED' && x.anchor)) {
        expect(anchors.has(f.anchor!), `${d.id}.${f.name}`).toBe(true)
        expect(f.quote, `${d.id}.${f.name} has a source quote`).toBeTruthy()
      }
      for (const f of d.fields.filter((x) => x.state === 'UNKNOWN')) {
        expect(f.unknownReason).toBeTruthy()
        expect(f.value).toBeUndefined()
      }
    }
  })

  it('has history timing that agrees with the dates', () => {
    const incident = Date.UTC(2026, 8, 10)
    const dates: Record<string, number> = { 'HC-0231': Date.UTC(2026, 6, 27), 'HC-0012': Date.UTC(2026, 1, 12) }
    for (const m of sampleHistory.matches) {
      expect(Math.round((incident - dates[m.source.sourceRecordId]!) / 86_400_000), m.externalClaimId).toBe(m.daysBetween)
    }
  })

  it('keeps a shared policy as context and a shared vehicle as exact, as the backend does', () => {
    for (const m of sampleHistory.matches) {
      expect(m.strength).toBe(m.relation === 'SAME_VEHICLE' ? 'EXACT' : 'CONTEXT')
    }
  })

  it('gives each explanation support, counter-evidence and unknowns', () => {
    expect(sampleHypotheses).toHaveLength(2)
    for (const h of sampleHypotheses) {
      expect(h.supports.length, h.id).toBeGreaterThan(0)
      expect(h.weakens.length, h.id).toBeGreaterThan(0)
      expect(h.unknown.length, h.id).toBeGreaterThan(0)
    }
  })

  it('resolves every citation to a real source, or marks it as an explicit unknown', () => {
    const refs: EvidenceRef[] = [
      ...sampleRules.flatMap((r) => r.evidence),
      ...sampleHypotheses.flatMap((h) => [...h.supports, ...h.weakens, ...h.unknown].map((e) => e.ref)),
    ]
    expect(refs.length).toBeGreaterThan(15)
    for (const ref of refs) {
      const resolved = resolveRef(ref)
      expect(resolved.label, JSON.stringify(ref)).not.toBe('Source not found')
      expect(resolved.openable, JSON.stringify(ref)).toBe(ref.kind !== 'MISSING')
      expect(focusFor(ref) === null, JSON.stringify(ref)).toBe(ref.kind === 'MISSING')
    }
  })

  it('cites the front-bumper line when it says the invoice replaces a front bumper', () => {
    const h2 = sampleHypotheses.find((h) => h.id === 'H2')!
    const claim = h2.supports.find((s) => /front bumper again/.test(s.text))!
    expect(claim.ref).toEqual({ kind: 'DOCUMENT_FIELD', documentId: 'doc-invoice', field: 'lineItems[0]' })
    const field = doc('doc-invoice').fields.find((f) => f.name === 'lineItems[0]')!
    expect(field.value).toMatch(/Front bumper/)
  })

  it('never says a claim is fraudulent', () => {
    const all = JSON.stringify([sampleRules, sampleHypotheses, sampleHistory, reviewActions])
    for (const sentence of all.split(/(?<=[.!?"])\s+/)) {
      if (/fraud/i.test(sentence)) expect(sentence).toMatch(/never|not|none/i)
    }
  })

  it('offers exactly the four review actions the contract defines', () => {
    expect(reviewActions.map((a) => a.value)).toEqual(['REQUEST_MORE_EVIDENCE', 'ESCALATE_FOR_FURTHER_INVESTIGATION', 'NO_FURTHER_CHECKS_NEEDED', 'DEFER_REVIEW'])
  })
})
