import type { HistoricalImport, ImportRow } from '@/features/imports/schemas'

export const IMPORT_ID = 'dddddddd-1111-4000-8000-000000000001'

export function historicalImport(patch: Partial<HistoricalImport> = {}): HistoricalImport {
  return {
    id: IMPORT_ID,
    importType: 'HISTORICAL_CLAIMS',
    templateVersion: '1',
    source: 'INSURER_DEMO',
    status: 'COMPLETED',
    failureCode: null,
    failureDetail: null,
    sha256: 'b8b00aeb66afe744462af6909f1e02f7a0803efd348a69d8dc2484f42443a682',
    sizeBytes: 695,
    counts: { totalRows: 3, accepted: 3, rejected: 0, skippedDuplicate: 0 },
    createdAt: '2026-10-18T12:15:31Z',
    startedAt: '2026-10-18T12:15:31Z',
    finishedAt: '2026-10-18T12:15:32Z',
    ...patch,
  }
}

export const mixedRows: ImportRow[] = [
  { rowNumber: 1, status: 'ACCEPTED', sourceRecordId: 'MIX-1' },
  { rowNumber: 2, status: 'REJECTED', sourceRecordId: 'MIX-2', errors: [{ field: 'vehicle_token', code: 'MISSING_REQUIRED', message: 'is required' }, { field: 'incident_at', code: 'INVALID_DATE', message: 'must be an ISO-8601 date' }] },
  { rowNumber: 3, status: 'SKIPPED_DUPLICATE', sourceRecordId: 'MIX-3' },
]

export const importPage = (items: HistoricalImport[], extra: Partial<{ page: number; totalPages: number }> = {}) => ({ items, page: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0, ...extra })
