import { useRef, useState } from 'react'
import type { DragEvent, FormEvent } from 'react'
import { Button } from '@/components/ui/Button'
import { Callout } from '@/components/ui/Callout'
import { SelectField, TextField } from '@/components/ui/Field'
import { formatTime } from '@/utils/format'
import styles from './Documents.module.css'
import { DOCUMENT_TYPES, documentTypeLabel, documentTypeLabels, isExtractable, requirementLabel } from './labels'
import type { DocumentDto, Requirements } from './schemas'
import { describeUploadFailure } from './uploadMessage'
import { ACCEPT_ATTRIBUTE, MAX_DOCUMENTS_PER_CLAIM, MAX_FILE_BYTES, checkFile, formatBytes } from './uploadRules'
import { percent, useUpload } from './useUpload'

type UploadPanelProps = {
  claimId: string
  requirements: Requirements | undefined
  currentDocuments: DocumentDto[]
  /** A document the next upload should replace. The earlier file is kept and marked replaced. */
  replacing: DocumentDto | null
  onStopReplacing: () => void
  onShowDocument: (documentId: string) => void
}

export function UploadPanel({ claimId, requirements, currentDocuments, replacing, onStopReplacing, onShowDocument }: UploadPanelProps) {
  const upload = useUpload()
  const { state } = upload
  const fileInput = useRef<HTMLInputElement>(null)
  const fileId = 'document-file'
  const [typeChoice, setTypeChoice] = useState<string | null>(null)
  const [file, setFile] = useState<File | null>(null)
  const [externalId, setExternalId] = useState('')
  const [fileProblem, setFileProblem] = useState<string | null>(null)
  const [typeProblem, setTypeProblem] = useState<string | null>(null)
  const [over, setOver] = useState(false)

  // Offer the one document the stage cannot do without, when it has not arrived, as a starting point the person can change.
  const missingMandatory = requirements?.items.find((i) => i.mandatory && i.evidenceState === 'NOT_RECEIVED')?.documentType
  const type = replacing ? replacing.documentType : (typeChoice ?? missingMandatory ?? '')
  const full = currentDocuments.length >= MAX_DOCUMENTS_PER_CLAIM
  const busy = upload.busy

  function pick(next: File | undefined) {
    if (!next) return
    const problem = checkFile(next)
    setFileProblem(problem?.message ?? null)
    setFile(problem ? null : next)
    if (problem && fileInput.current) fileInput.current.value = ''
    upload.reset()
  }

  function clearFile() {
    setFile(null)
    setFileProblem(null)
    if (fileInput.current) fileInput.current.value = ''
    upload.reset()
  }

  function onDrop(event: DragEvent) {
    event.preventDefault()
    setOver(false)
    if (!busy && !full) pick(event.dataTransfer.files[0])
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void send()
  }

  async function send() {
    if (busy || full) return
    setTypeProblem(null)
    if (!type) {
      setTypeProblem('Choose what kind of document this is.')
      document.getElementById('document-type')?.focus()
      return
    }
    if (!file) {
      setFileProblem(fileProblem ?? 'Choose a file to upload.')
      fileInput.current?.focus()
      return
    }
    await upload.submit({ claimId, file, documentType: type, externalDocumentId: externalId.trim() || undefined, supersedesDocumentId: replacing?.id })
  }

  const failure = state.phase === 'failed' ? describeUploadFailure(state.error) : null
  const done = state.phase === 'done' ? state.result : null

  function another() {
    setFile(null)
    setFileProblem(null)
    setExternalId('')
    if (fileInput.current) fileInput.current.value = ''
    onStopReplacing()
    upload.reset()
  }

  const requirement = requirements?.items.find((i) => i.documentType === type)

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {replacing ? (
        <div className={styles.replacing}>
          <span>
            This file will <strong>replace</strong> {replacing.fileName} ({documentTypeLabel(replacing.documentType)}). The earlier file is kept and marked as replaced.
          </span>
          <Button size="sm" onClick={onStopReplacing} disabled={busy}>
            Do not replace
          </Button>
        </div>
      ) : null}

      <SelectField
        id="document-type"
        name="documentType"
        label="Document type"
        value={type}
        onChange={(e) => {
          setTypeChoice(e.target.value)
          setTypeProblem(null)
          upload.reset()
        }}
        disabled={busy || Boolean(replacing) || full}
        error={typeProblem ?? undefined}
        hint={replacing ? 'A replacement has the same type as the file it replaces.' : requirement ? `${requirementLabel(requirement.requirement, requirement.mandatory)} at this claim stage.` : undefined}
      >
        <option value="">Choose a type</option>
        {DOCUMENT_TYPES.map((t) => (
          <option key={t} value={t}>
            {documentTypeLabels[t]}
          </option>
        ))}
      </SelectField>

      <div className={styles.drop} data-over={over} onDragOver={(e) => { e.preventDefault(); if (!busy && !full) setOver(true) }} onDragLeave={() => setOver(false)} onDrop={onDrop}>
        <label htmlFor={fileId} style={{ fontWeight: 600, fontSize: 'var(--text-sm)' }}>
          File
        </label>
        <input
          ref={fileInput}
          id={fileId}
          className={styles.fileInput}
          type="file"
          accept={ACCEPT_ATTRIBUTE}
          disabled={busy || full}
          aria-describedby={`${fileId}-help${fileProblem ? ` ${fileId}-problem` : ''}`}
          aria-invalid={fileProblem ? true : undefined}
          onChange={(e) => pick(e.target.files?.[0])}
        />
        <p id={`${fileId}-help`}>
          PDF, JPEG or PNG, up to {formatBytes(MAX_FILE_BYTES)} per file. You can also drop a file here. A claim holds up to {MAX_DOCUMENTS_PER_CLAIM} documents.
        </p>
        {fileProblem ? (
          <p id={`${fileId}-problem`} role="alert" style={{ color: 'var(--danger)', fontWeight: 500 }}>
            {fileProblem}
          </p>
        ) : null}
      </div>

      {file ? (
        <div className={styles.chosen}>
          <span>
            <strong>{file.name}</strong> <span className={styles.mono}>{formatBytes(file.size)}</span>
          </span>
          <Button size="sm" onClick={clearFile} disabled={busy}>
            Remove
          </Button>
        </div>
      ) : null}

      <TextField
        label="Insurer's document id"
        optional
        value={externalId}
        onChange={(e) => setExternalId(e.target.value)}
        maxLength={255}
        disabled={busy || full}
        hint="Your own reference for this document, if it has one."
        mono
        autoComplete="off"
      />

      {full ? (
        <Callout tone="caution" title="This claim holds the most documents it can.">
          A claim can hold {MAX_DOCUMENTS_PER_CLAIM} documents, so nothing more can be added.
        </Callout>
      ) : null}

      {state.phase === 'sending' || state.phase === 'confirming' ? (
        <div className={styles.progress}>
          {state.phase === 'sending' ? (
            <>
              <progress value={percent(state)} max={100} aria-label={`Sending ${state.fileName}`} />
              <p>
                Sending {state.fileName}: {percent(state)}% ({formatBytes(state.loaded)} of {formatBytes(state.total)}). This is the transfer only; the server has not confirmed it.
              </p>
            </>
          ) : (
            <>
              <progress aria-label={`Waiting for the server to confirm ${state.fileName}`} />
              <p>All of {state.fileName} has been sent. Waiting for the server to confirm it is stored. Keep this page open.</p>
            </>
          )}
        </div>
      ) : null}

      <div className={styles.row}>
        <Button type="submit" variant="primary" loading={busy} loadingLabel={state.phase === 'confirming' ? 'Waiting for the server' : 'Sending'} disabled={full}>
          {replacing ? 'Upload replacement' : 'Upload'}
        </Button>
        {busy ? <Button onClick={upload.cancel}>Cancel upload</Button> : null}
      </div>

      <div role="status" aria-live="polite" style={{ display: 'contents' }}>
        {done ? (
          <Callout tone="evidence" title={`${done.document.fileName} is stored.`} actions={<Button size="sm" onClick={another}>Upload another file</Button>}>
            <p>
              The server confirmed it at {formatTime(new Date(done.document.receivedAt))}. {done.replayed ? 'This was an upload the server had already received, so nothing was stored twice. ' : ''}
              It has been stored, not read: {isExtractable(done.document.documentType) ? 'use “Read document” in the list to have it read.' : 'this kind of document is kept for people to open.'}
            </p>
          </Callout>
        ) : null}
        {state.phase === 'cancelled' ? (
          <Callout tone="neutral" title="Upload cancelled.">
            If the server had already received the whole file before you cancelled, it will appear in the list below.
          </Callout>
        ) : null}
      </div>

      {failure ? (
        <Callout
          tone="danger"
          title={failure.title}
          actions={
            <>
              {failure.retryable && file ? (
                <Button size="sm" icon="refresh" onClick={() => void send()}>
                  Try again
                </Button>
              ) : null}
              {failure.existingDocumentId ? (
                <Button size="sm" onClick={() => onShowDocument(failure.existingDocumentId!)}>
                  Show the stored file
                </Button>
              ) : null}
            </>
          }
        >
          <p>{failure.message}</p>
        </Callout>
      ) : null}
    </form>
  )
}
