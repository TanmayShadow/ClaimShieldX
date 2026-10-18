import { DOCUMENT_TYPES, checkLabel, documentStatusMark, documentTypeLabel, documentTypeLabels, evidenceStateMark, failureMessage, isExtractable, requirementLabel } from './labels'

describe('document labels', () => {
  it('labels all fourteen document types and shows an unknown one as sent', () => {
    expect(DOCUMENT_TYPES).toHaveLength(14)
    for (const type of DOCUMENT_TYPES) expect(documentTypeLabels[type].length).toBeGreaterThan(2)
    expect(documentTypeLabel('SOMETHING_NEW')).toBe('SOMETHING_NEW')
  })

  it('reads only invoices and estimates', () => {
    expect(DOCUMENT_TYPES.filter(isExtractable)).toEqual(['GARAGE_ESTIMATE', 'FINAL_INVOICE'].sort((a, b) => DOCUMENT_TYPES.indexOf(a as never) - DOCUMENT_TYPES.indexOf(b as never)))
  })

  it('keeps upload and reading apart: stored is not read', () => {
    expect(documentStatusMark('RECEIVED', 'FINAL_INVOICE').label).toBe('Uploaded · not read yet')
    expect(documentStatusMark('RECEIVED', 'DAMAGE_PHOTOGRAPH').label).toBe('Stored · not read')
    expect(documentStatusMark('VERIFIED', 'FINAL_INVOICE').label).toBe('Read and consistent')
    expect(documentStatusMark('VERIFIED', 'FINAL_INVOICE').label).not.toMatch(/true|genuine|valid/i)
  })

  it('gives each status its own shape', () => {
    const states = ['RECEIVED', 'PROCESSING', 'VERIFIED', 'NEEDS_REVIEW', 'FAILED'].map((s) => documentStatusMark(s, 'FINAL_INVOICE').state)
    expect(new Set(states).size).toBe(5)
  })

  it('never calls the evidence wrong, only unread or in need of review', () => {
    const all = [...['RECEIVED', 'PROCESSING', 'VERIFIED', 'NEEDS_REVIEW', 'FAILED'].map((s) => documentStatusMark(s, 'FINAL_INVOICE').label), ...['NOT_RECEIVED', 'RECEIVED', 'VERIFIED', 'NEEDS_REVIEW', 'FAILED'].map((s) => evidenceStateMark(s).label)]
    expect(all.join(' ')).not.toMatch(/fraud|fake|forged|suspicious|invalid/i)
  })

  it('separates a mandatory document from an expected one', () => {
    expect(requirementLabel('REQUIRED', true)).toBe('Needed to proceed')
    expect(requirementLabel('REQUIRED', false)).toBe('Expected')
    expect(requirementLabel('OPTIONAL', false)).toBe('Optional')
    expect(requirementLabel('NOT_APPLICABLE', false)).toBe('Not applicable')
  })

  it('names the checks a missing document stops', () => {
    expect(checkLabel('INVOICE_ARITHMETIC')).toBe('Invoice arithmetic')
    expect(checkLabel('REPAIR_SCOPE_REVIEW')).toBe('Repair scope review')
    expect(checkLabel('NEW_RULE')).toBe('NEW_RULE')
  })

  it('explains every failure code in words and never repeats an unknown one raw', () => {
    for (const code of ['DOCUMENT_TOO_LARGE', 'UNREADABLE_FILE', 'STORAGE_UNAVAILABLE', 'STORED_FILE_MISMATCH', 'MODEL_REJECTED_INPUT', 'MODEL_BLOCKED', 'MODEL_NOT_CONFIGURED', 'MODEL_RATE_LIMITED', 'MODEL_UNAVAILABLE', 'MODEL_OUTPUT_INVALID', 'INTERNAL_ERROR']) {
      expect(failureMessage(code)).not.toContain(code)
    }
    expect(failureMessage('BRAND_NEW_CODE')).toBe('The document could not be read.')
    expect(failureMessage(null)).toBe('The document could not be read.')
  })
})
