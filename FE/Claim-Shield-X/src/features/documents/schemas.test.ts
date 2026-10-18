import { documentPageSchema, documentSchema, extractionSchema, extractionStartedSchema, requirementsSchema } from './schemas'

const doc = {
  id: '168cf265-8f9d-4bc8-aaa7-213695dda9c0',
  claimId: '38fa77fd-7a4f-46ba-baec-096a52730816',
  documentType: 'FINAL_INVOICE',
  fileName: 'synthetic-invoice.pdf',
  contentType: 'application/pdf',
  sizeBytes: 792,
  sha256: '25c16a1bcfbb5e4f37947b0bb608a356d291e272b11da784f833217a854c8032',
  status: 'RECEIVED',
  externalDocumentId: null,
  supersedesDocumentId: null,
  superseded: false,
  receivedAt: '2026-10-18T05:42:33.827448Z',
}

describe('documentSchema', () => {
  it('reads the real upload response', () => {
    expect(documentSchema.parse(doc)).toMatchObject({ fileName: 'synthetic-invoice.pdf', superseded: false })
  })
  it('never carries the storage location, because the API never sends it', () => {
    expect(documentSchema.parse({ ...doc, objectKey: 'documents/org/claim/uuid' })).not.toHaveProperty('objectKey')
  })
  it('reads a page and tolerates a newer status', () => {
    const page = { items: [{ ...doc, status: 'ARCHIVED' }], page: 0, size: 100, totalElements: 1, totalPages: 1 }
    expect(documentPageSchema.parse(page).items[0]?.status).toBe('ARCHIVED')
  })
  it.each([
    ['a negative size', { ...doc, sizeBytes: -1 }],
    ['no file name', { ...doc, fileName: undefined }],
    ['a text flag', { ...doc, superseded: 'false' }],
  ])('rejects %s', (_l, body) => {
    expect(documentSchema.safeParse(body).success).toBe(false)
  })
})

describe('requirementsSchema', () => {
  const body = {
    claimId: 'c',
    stage: 'FINAL_INVOICE_RECEIVED',
    incidentType: 'COLLISION',
    readiness: 'NOT_READY',
    items: [{ documentType: 'FINAL_INVOICE', requirement: 'REQUIRED', mandatory: true, evidenceState: 'NOT_RECEIVED', documentIds: [], blocks: ['INVOICE_ARITHMETIC'] }],
    blockers: [{ code: 'DOCUMENT_MISSING', documentType: 'FINAL_INVOICE', requirement: 'REQUIRED', message: 'Final invoice has not been received.', blocks: ['INVOICE_ARITHMETIC'] }],
    warnings: [{ code: 'NO_CHECKLIST_FOR_STAGE', message: 'No checklist at this stage.' }],
  }
  it('reads the real checklist, including a warning with no document type', () => {
    const parsed = requirementsSchema.parse(body)
    expect(parsed.items[0]?.mandatory).toBe(true)
    expect(parsed.warnings[0]?.blocks).toEqual([])
  })
  it('reads a stage with no checklist', () => {
    expect(requirementsSchema.safeParse({ ...body, items: [], blockers: [] }).success).toBe(true)
  })
  it('rejects a checklist item without its state', () => {
    expect(requirementsSchema.safeParse({ ...body, items: [{ documentType: 'X', requirement: 'REQUIRED', mandatory: false }] }).success).toBe(false)
  })
})

describe('extraction schemas', () => {
  it('reads the 202 body', () => {
    expect(extractionStartedSchema.parse({ documentId: 'd', documentStatus: 'VERIFIED', extractionId: 'e', attempt: 1 }).documentStatus).toBe('VERIFIED')
  })
  it('reads a successful result summary and ignores the field values', () => {
    const parsed = extractionSchema.parse({
      documentId: 'd',
      documentType: 'FINAL_INVOICE',
      extractionId: 'e',
      attempt: 1,
      status: 'SUCCEEDED',
      documentStatus: 'NEEDS_REVIEW',
      modelId: 'gemini-3.5-flash-lite',
      pageCount: 1,
      createdAt: '2026-10-18T09:12:44Z',
      fields: [{ name: 'grandTotal', state: 'EXTRACTED', value: '24,780.00', source: { page: 1, quote: 'x' } }, { name: 'incidentDate', state: 'UNKNOWN', unknownReason: 'NOT_PRESENT' }],
      issues: [{ code: 'IDENTIFIER_MISMATCH', severity: 'REVIEW', field: 'garageIdentifier', message: 'The garage identifier on the document does not match the claim record.' }],
    })
    expect(parsed.fields.map((f) => f.state)).toEqual(['EXTRACTED', 'UNKNOWN'])
    expect(JSON.stringify(parsed)).not.toContain('24,780.00')
  })
  it('reads a failed attempt with no fields', () => {
    const parsed = extractionSchema.parse({ documentId: 'd', documentType: 'FINAL_INVOICE', extractionId: 'e', attempt: 2, status: 'FAILED', documentStatus: 'FAILED', createdAt: 't', failureCode: 'MODEL_RATE_LIMITED', retryable: true })
    expect(parsed).toMatchObject({ fields: [], issues: [], retryable: true })
  })
})
