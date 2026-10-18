import { useState } from 'react'
import type { ChangeEvent } from 'react'
import type { DocumentDto } from './schemas'
import { MAX_DOCUMENTS_PER_CLAIM, checkFile } from './uploadRules'
import { useUpload } from './useUpload'
import type { UploadParams } from './useUpload'

/** The id of the page's single, hidden file input. A row's button opens it; the hook never holds the element itself. */
export const FILE_CHOOSER_ID = 'document-file-chooser'

/** What one upload is for: a document type, and optionally the earlier file it replaces (which is kept and marked replaced). */
export type UploadTarget = { documentType: string; replaces?: DocumentDto }

/**
 * One upload at a time, started from a row: the row's button opens the file chooser for that document type and the upload begins
 * the moment a file is chosen, so there is no separate type selector or submit step. The retry, cancel and duplicate handling
 * of `useUpload` are unchanged.
 */
export function useDocumentUploader(claimId: string, storedCount: number) {
  const upload = useUpload()
  const [chosenFor, setChosenFor] = useState<UploadTarget | null>(null)
  const [target, setTarget] = useState<UploadTarget | null>(null)
  const [last, setLast] = useState<UploadParams | null>(null)
  const [problem, setProblem] = useState<string | null>(null)
  const [externalId, setExternalId] = useState('')
  const full = storedCount >= MAX_DOCUMENTS_PER_CLAIM
  const busy = upload.busy

  function start(file: File, to: UploadTarget) {
    if (busy || full) return
    const issue = checkFile(file)
    upload.reset()
    setTarget(to)
    if (issue) {
      setProblem(issue.message)
      setLast(null)
      return
    }
    setProblem(null)
    const params: UploadParams = { claimId, file, documentType: to.documentType, externalDocumentId: externalId.trim() || undefined, supersedesDocumentId: to.replaces?.id }
    // The insurer's id belongs to this one upload; the next one starts without it.
    setExternalId('')
    setLast(params)
    void upload.submit(params)
  }

  /** Opens the file chooser for a row. */
  function choose(to: UploadTarget) {
    if (busy || full) return
    setChosenFor(to)
    const input = document.getElementById(FILE_CHOOSER_ID) as HTMLInputElement | null
    if (!input) return
    input.value = '' // choosing the same file again must still raise a change
    input.click()
  }

  function onInputChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    const to = chosenFor
    setChosenFor(null)
    if (file && to) start(file, to)
  }

  /** A file dropped on a row is uploaded as that row's document type. */
  function dropOn(to: UploadTarget, file: File | undefined) {
    if (file) start(file, to)
  }

  function dismiss() {
    upload.reset()
    setProblem(null)
    setTarget(null)
    setLast(null)
  }

  function retry() {
    if (last) void upload.submit(last)
  }

  return {
    onInputChange,
    choose,
    dropOn,
    dismiss,
    retry,
    cancel: upload.cancel,
    state: upload.state,
    busy,
    full,
    target,
    problem,
    canRetry: Boolean(last),
    externalId,
    setExternalId,
  }
}

export type DocumentUploader = ReturnType<typeof useDocumentUploader>
