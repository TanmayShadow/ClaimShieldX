/**
 * What a file must look like before it is worth sending. These are conveniences for the person (a quick, specific answer);
 * the server checks the real content, size and type again and remains the authority (api-contract 7.4).
 */
export const MAX_FILE_BYTES = 20 * 1024 * 1024
export const MAX_DOCUMENTS_PER_CLAIM = 50
export const ACCEPTED_EXTENSIONS = ['.pdf', '.jpg', '.jpeg', '.png'] as const
export const ACCEPTED_MIME_TYPES = ['application/pdf', 'image/jpeg', 'image/png'] as const
export const ACCEPT_ATTRIBUTE = [...ACCEPTED_EXTENSIONS, ...ACCEPTED_MIME_TYPES].join(',')

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(bytes < 10 * 1024 ? 1 : 0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const extensionOf = (name: string) => {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot).toLowerCase()
}

export type FileProblem = { code: 'EMPTY' | 'TOO_LARGE' | 'UNSUPPORTED_TYPE' | 'HEIC'; message: string }

export function checkFile(file: { name: string; size: number; type: string }): FileProblem | null {
  const ext = extensionOf(file.name)
  if (ext === '.heic' || ext === '.heif' || file.type === 'image/heic' || file.type === 'image/heif') {
    return { code: 'HEIC', message: 'HEIC photos are not supported. Convert the photo to JPEG and choose it again.' }
  }
  if (file.size === 0) return { code: 'EMPTY', message: 'This file is empty. Choose a file that has content.' }
  if (file.size > MAX_FILE_BYTES) {
    return { code: 'TOO_LARGE', message: `This file is ${formatBytes(file.size)}. Files can be at most ${formatBytes(MAX_FILE_BYTES)}. Upload a smaller copy.` }
  }
  const okExtension = (ACCEPTED_EXTENSIONS as readonly string[]).includes(ext)
  const okMime = (ACCEPTED_MIME_TYPES as readonly string[]).includes(file.type)
  // The browser's idea of the type can be empty or wrong, so either signal is enough to let the server decide.
  if (!okExtension && !okMime) return { code: 'UNSUPPORTED_TYPE', message: 'Only PDF, JPEG and PNG files are accepted.' }
  return null
}

/** Idempotency-Key: 8 to 128 characters from A-Za-z0-9._:- (api-contract 6). */
export function newIdempotencyKey(): string {
  return `upload-${crypto.randomUUID()}`
}
