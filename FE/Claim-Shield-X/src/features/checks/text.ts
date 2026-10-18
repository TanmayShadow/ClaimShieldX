/** "invoiceTotal" -> "Invoice total". */
export function humanizeKey(key: string): string {
  const spaced = key.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').trim().toLowerCase()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

/** "CLAIM_FORM:DOCUMENT_MISSING" -> "Claim form: document missing". Anything that is not an upper-case code is left as it is. */
export function humanizeCode(value: string): string {
  if (!/^[A-Z0-9_:./-]+$/.test(value) || !/[A-Z]{2}/.test(value)) return value
  return value
    .split(':')
    .map((part, i) => (i === 0 ? humanizeKey(part) : humanizeKey(part).toLowerCase()))
    .join(': ')
}

/** A short list read as one sentence: "Vehicle token, garage id". Accepts codes and camelCase names. */
export function sentenceList(values: string[], separator = ', '): string {
  return values.map((v, i) => (i === 0 ? humanizeKey(v) : humanizeKey(v).toLowerCase())).join(separator)
}
