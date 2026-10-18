import { formatBytes } from '@/features/documents/uploadRules'
import { MAX_IMPORT_BYTES, SOURCE_PATTERN } from './labels'

/** What can be refused before sending. The server still decides: an unusable file there becomes a FAILED import with a reason. */
export function checkCsv(file: { name: string; size: number }): string | null {
  if (!file.name.toLowerCase().endsWith('.csv')) return 'Choose a .csv file. Save the sheet as "CSV (UTF-8)".'
  if (file.size === 0) return 'That file is empty.'
  if (file.size > MAX_IMPORT_BYTES) return `This file is ${formatBytes(file.size)}. Imports can be at most ${formatBytes(MAX_IMPORT_BYTES)}. Split it into smaller files.`
  return null
}

export function checkSource(value: string): string | null {
  const v = value.trim()
  if (v === '') return null
  return SOURCE_PATTERN.test(v) ? null : 'Use 1 to 100 letters, digits or . _ : - with no spaces, for example INSURER_DEMO.'
}
