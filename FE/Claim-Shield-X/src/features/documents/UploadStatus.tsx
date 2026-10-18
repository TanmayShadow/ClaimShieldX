import { Button } from '@/components/ui/Button'
import { Callout } from '@/components/ui/Callout'
import { formatTime } from '@/utils/format'
import styles from './Documents.module.css'
import { documentTypeLabel, isExtractable } from './labels'
import { describeUploadFailure } from './uploadMessage'
import { MAX_DOCUMENTS_PER_CLAIM, formatBytes } from './uploadRules'
import { percent } from './useUpload'
import type { DocumentUploader } from './useDocumentUploader'

/** Where an upload stands. Always rendered, so a screen reader hears each change; it says nothing while nothing is happening. */
export function UploadStatus({ uploader, onShowDocument }: { uploader: DocumentUploader; onShowDocument: (documentId: string) => void }) {
  const { state, target, problem, busy, full } = uploader
  const failure = state.phase === 'failed' ? describeUploadFailure(state.error) : null
  const done = state.phase === 'done' ? state.result : null
  const kind = target ? documentTypeLabel(target.documentType) : ''
  const what = target?.replaces ? `${kind}, replacing ${target.replaces.fileName}` : kind

  return (
    <div className={styles.status} role="status" aria-live="polite">
      {full ? (
        <Callout tone="caution" title="This claim holds the most documents it can.">
          A claim can hold {MAX_DOCUMENTS_PER_CLAIM} documents, so nothing more can be added.
        </Callout>
      ) : null}

      {problem ? (
        <Callout tone="danger" title="That file cannot be uploaded." actions={<Button size="sm" onClick={uploader.dismiss}>Dismiss</Button>}>
          <p>{problem}</p>
        </Callout>
      ) : null}

      {busy ? (
        <div className={styles.progress}>
          {state.phase === 'sending' ? (
            <>
              <progress value={percent(state)} max={100} aria-label={`Sending ${state.fileName}`} />
              <p>
                Sending {state.fileName} as {what}: {percent(state)}% ({formatBytes(state.loaded)} of {formatBytes(state.total)}). This is the transfer only; the server has not confirmed it.
              </p>
            </>
          ) : (
            <>
              <progress aria-label={`Waiting for the server to confirm ${state.fileName}`} />
              <p>All of {state.fileName} has been sent. Waiting for the server to confirm it is stored. Keep this page open.</p>
            </>
          )}
          <div>
            <Button size="sm" onClick={uploader.cancel}>
              Cancel upload
            </Button>
          </div>
        </div>
      ) : null}

      {done ? (
        <Callout tone="evidence" title={`${done.document.fileName} is stored as ${kind.toLowerCase()}.`} actions={<Button size="sm" onClick={uploader.dismiss}>Dismiss</Button>}>
          <p>
            The server confirmed it at {formatTime(new Date(done.document.receivedAt))}. {done.replayed ? 'This was an upload the server had already received, so nothing was stored twice. ' : ''}
            It has been stored, not read: {isExtractable(done.document.documentType) ? 'use “Read document” in the list to have it read.' : 'this kind of document is kept for people to open.'}
          </p>
        </Callout>
      ) : null}

      {state.phase === 'cancelled' ? (
        <Callout tone="neutral" title="Upload cancelled." actions={<Button size="sm" onClick={uploader.dismiss}>Dismiss</Button>}>
          If the server had already received the whole file before you cancelled, it will appear in the list below.
        </Callout>
      ) : null}

      {failure ? (
        <Callout
          tone="danger"
          title={failure.title}
          actions={
            <>
              {failure.retryable && uploader.canRetry ? (
                <Button size="sm" icon="refresh" onClick={uploader.retry}>
                  Try again
                </Button>
              ) : null}
              {failure.existingDocumentId ? (
                <Button size="sm" onClick={() => onShowDocument(failure.existingDocumentId!)}>
                  Show the stored file
                </Button>
              ) : null}
              <Button size="sm" onClick={uploader.dismiss}>
                Dismiss
              </Button>
            </>
          }
        >
          <p>{failure.message}</p>
        </Callout>
      ) : null}
    </div>
  )
}
