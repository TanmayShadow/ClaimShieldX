import { useId, useRef, useState } from 'react'
import { Button } from '@/components/ui/Button'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { SelectField, TextField } from '@/components/ui/Field'
import { formatBytes } from '@/features/documents/uploadRules'
import { saveBlob } from './download'
import { useTemplateDownload, useUploadImport } from './hooks'
import styles from './Imports.module.css'
import { IMPORT_TYPES, MAX_IMPORT_BYTES, importTypeHints, importTypeLabels, templateFileNames } from './labels'
import type { ImportType } from './labels'
import { ImportResult } from './ImportResult'
import { checkCsv, checkSource } from './rules'

export function ImportForm() {
  const fileId = useId()
  const fileInput = useRef<HTMLInputElement>(null)
  const [type, setType] = useState<ImportType>('HISTORICAL_CLAIMS')
  const [file, setFile] = useState<File | null>(null)
  const [source, setSource] = useState('')
  const [touched, setTouched] = useState(false)
  const upload = useUploadImport()
  const template = useTemplateDownload()

  const fileProblem = file ? checkCsv(file) : touched ? 'Choose a CSV file to send.' : null
  const sourceProblem = checkSource(source)
  const busy = upload.isPending

  function submit(e: React.FormEvent) {
    e.preventDefault()
    setTouched(true)
    if (!file || checkCsv(file) || sourceProblem || busy) return
    upload.mutate({ file, importType: type, source })
  }

  function another() {
    upload.reset()
    setFile(null)
    setSource('')
    setTouched(false)
    if (fileInput.current) fileInput.current.value = ''
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <SelectField
        label="What the file holds"
        value={type}
        onChange={(e) => {
          setType(e.target.value as ImportType)
          upload.reset()
        }}
        disabled={busy}
        hint={importTypeHints[type]}
      >
        {IMPORT_TYPES.map((t) => (
          <option key={t} value={t}>
            {importTypeLabels[t]}
          </option>
        ))}
      </SelectField>

      <div className={styles.templateRow}>
        <Button
          size="sm"
          icon="download"
          onClick={() => template.mutate(type, { onSuccess: (blob) => saveBlob(blob, templateFileNames[type]) })}
          loading={template.isPending}
          loadingLabel="Fetching the template"
        >
          Download the {importTypeLabels[type].toLowerCase()} template
        </Button>
        <span>It holds the exact column names and one made-up example row.</span>
      </div>
      {template.isError ? <ErrorPanel error={template.error} subject="the template" /> : null}

      <div className={styles.fileBox}>
        <label htmlFor={fileId}>File</label>
        <input
          ref={fileInput}
          id={fileId}
          type="file"
          accept=".csv,text/csv"
          disabled={busy}
          aria-describedby={`${fileId}-help${fileProblem ? ` ${fileId}-problem` : ''}`}
          aria-invalid={fileProblem ? true : undefined}
          onChange={(e) => {
            setFile(e.target.files?.[0] ?? null)
            upload.reset()
          }}
        />
        <p id={`${fileId}-help`}>A .csv file saved as UTF-8, up to {formatBytes(MAX_IMPORT_BYTES)} and 10,000 rows. Use pseudonymous tokens, never names or registration numbers.</p>
        {fileProblem ? (
          <p id={`${fileId}-problem`} role="alert" className={styles.problem}>
            {fileProblem}
          </p>
        ) : null}
      </div>
      {file && !checkCsv(file) ? (
        <p className={styles.chosen}>
          <strong>{file.name}</strong> <span className={styles.mono}>{formatBytes(file.size)}</span>
        </p>
      ) : null}

      <TextField
        label="Source label"
        optional
        value={source}
        onChange={(e) => setSource(e.target.value)}
        maxLength={100}
        disabled={busy}
        error={sourceProblem ?? undefined}
        hint="A short name for where this file came from, for example INSURER_DEMO."
        mono
        autoComplete="off"
      />

      {busy ? (
        <div className={styles.progress}>
          <progress aria-label="Waiting for the server to process the file" />
          <p>Sending the file and waiting for the server to check every row. Keep this page open.</p>
        </div>
      ) : null}

      {upload.isError ? <ErrorPanel error={upload.error} subject="that import" /> : null}

      <div>
        <Button type="submit" variant="primary" loading={busy} loadingLabel="Importing" icon="upload">
          Import the file
        </Button>
      </div>

      <div role="status" aria-live="polite">
        {upload.data ? <ImportResult result={upload.data} onAnother={another} /> : null}
      </div>
    </form>
  )
}
