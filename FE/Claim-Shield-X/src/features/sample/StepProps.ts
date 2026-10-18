import type { Focus } from './refs'
import type { EvidenceRef } from './types'

export type StepProps = {
  focus: Focus
  setFocus: (focus: Focus) => void
  openRef: (ref: EvidenceRef) => void
}
