import type { StageIconName } from '@/components/illustrations/StageIcons'

/**
 * Landing-page facts. Each availability tag mirrors the backend contract (api-contract.md, section 4):
 * flip a tag only when the matching backend module ships and the matching screen exists.
 */
export type Availability = 'available' | 'planned'

export type Stage = {
  id: StageIconName
  title: string
  summary: string
  goesIn: string
  comesOut: string
  staysHuman: string
  availability: Availability
  note: string
}

export const stages: Stage[] = [
  {
    id: 'intake',
    title: 'Document intake',
    summary: 'Documents are filed under a claim and measured against a checklist that fits the claim stage.',
    goesIn: 'PDF, JPEG or PNG files up to 20 MB, each filed under a document type.',
    comesOut: 'A stored original with a checksum, and a checklist that says which checks cannot run because a document is missing.',
    staysHuman: 'The investigator decides what to upload and which earlier file a new one replaces.',
    availability: 'available',
    note: 'The checklist is a configurable product default the insurer still has to confirm.',
  },
  {
    id: 'extraction',
    title: 'Structured extraction',
    summary: 'A model reads a document into labelled fields. Each value cites its page and a quote. What it cannot read stays unknown.',
    goesIn: 'A final invoice or a garage estimate.',
    comesOut: 'Fields with a source page and quote, totals recalculated in code, and a status of verified or needs review.',
    staysHuman: 'A person reads anything marked needs review. Verified means the fields were readable and consistent, not that the document is true.',
    availability: 'available',
    note: 'Only invoices and estimates are read today. Synthetic documents only while the free model tier is in use.',
  },
  {
    id: 'checks',
    title: 'Deterministic checks',
    summary: 'Plain code, not a model, checks figures, dates and repeats. The same inputs always give the same results.',
    goesIn: 'The claim record, the extracted fields and the supplied history.',
    comesOut: 'One result per rule: pass, flag, incomplete or not applicable, with the values used, the evidence and the harmless explanations to rule out first.',
    staysHuman: 'A flag is something to check. The investigator decides whether it matters.',
    availability: 'available',
    note: 'Eight rules today. Their thresholds are defaults the insurer still has to confirm.',
  },
  {
    id: 'history',
    title: 'Historical comparison',
    summary: 'Earlier claims your organization supplies as CSV are compared with this claim by exact tokens, never by guesswork.',
    goesIn: 'Claim and garage history files that use pseudonymous tokens instead of names or registration numbers.',
    comesOut: 'Earlier claims that share a vehicle, policy or claimant token, each with the reason. "No match" and "no history supplied" are kept apart.',
    staysHuman: 'A shared garage or policy is context to interpret, never a finding on its own.',
    availability: 'available',
    note: 'Only the history you import is searched. It is not a complete record of any vehicle.',
  },
  {
    id: 'explanations',
    title: 'Competing explanations',
    summary: 'From a frozen set of evidence, the system proposes explanations and the strongest counter-evidence. Each must cite its sources.',
    goesIn: 'A frozen snapshot of the claim, the extractions, the rule results and the history.',
    comesOut: 'Hypotheses with supporting evidence, counter-evidence and unknowns. Anything without a verifiable source is rejected in code.',
    staysHuman: 'Nothing here is a verdict. The investigator weighs the explanations.',
    availability: 'available',
    note: 'A model proposes, a second step argues the other side, an independent review judges, and code rejects anything it cannot verify.',
  },
  {
    id: 'review',
    title: 'Human review',
    summary: 'The investigator reads the report and records what happens next, with a written reason.',
    goesIn: 'The report and the evidence behind it.',
    comesOut: 'A recorded action: request more evidence, escalate for further investigation, no further checks needed, or defer. Actions are append-only.',
    staysHuman: 'The decision is always a person’s. The tool cannot take an action.',
    availability: 'available',
    note: 'Decisions are permanent and carry the time, the account and the score the tool had given at that moment.',
  },
]

export type CapabilityRow = { name: string; detail: string; availability: Availability }

export const capabilities: CapabilityRow[] = [
  { name: 'Document intake and readiness', detail: 'Upload, checksum, stage-aware checklist, and which checks a missing document stops.', availability: 'available' },
  { name: 'Reading invoices and estimates', detail: 'Fields with page and quote, unknown kept unknown, totals recalculated in code.', availability: 'available' },
  { name: 'Eight deterministic checks', detail: 'Invoice arithmetic, estimate-to-invoice variance, date consistency, repeated files, repeated invoice numbers, repeated lines, history links, required evidence.', availability: 'available' },
  { name: 'History import and comparison', detail: 'Earlier claims matched by exact token, with the reason shown. Administrators import the CSV files on the History imports page, with a reason shown for every rejected row.', availability: 'available' },
  { name: 'Investigation runs with live stages', detail: 'Queue a run and watch each stage report its own state, wait for a missing document, retry or cancel, through to a finished report.', availability: 'available' },
  { name: 'Evidence-linked explanations', detail: 'Hypotheses with counter-evidence and unknowns, every citation verified in code.', availability: 'available' },
  { name: 'Reviewable report and recorded actions', detail: 'A versioned report, a review-priority score with its definition, and append-only human actions with an audit trail.', availability: 'available' },
]

export const neverDoes: string[] = [
  'Label a claim fraudulent. An inconsistency is a reason to look, not proof.',
  'Approve, reject or settle a claim, or take any action on its own.',
  'Show a score as a probability of fraud. A review-priority score, when it exists, will be named and defined as that.',
  'Treat missing information as evidence. A missing document means a check could not run.',
  'Send real claim data to a model while the free model tier is in use.',
]

export const securityControls: Array<{ id: string; text: string }> = [
  { id: 'S1', text: 'Every record belongs to one organization. A request for another organization’s record gets "not found", so its existence is not revealed.' },
  { id: 'S2', text: 'Signing in gives a short-lived access token (30 minutes). The longer-lived refresh token sits in a cookie that page scripts cannot read, and each refresh token works only once.' },
  { id: 'S3', text: 'Uploads are identified by their content, not by file name or declared type. Originals come back only as attachments through the server; the browser never receives a storage address.' },
  { id: 'S4', text: 'Model calls run on the server. The browser holds no key, and the claim record is not sent to the model when a document is read.' },
  { id: 'S5', text: 'Error messages do not repeat what you typed, and by rule logs hold no document content or credentials.' },
]

export const notClaimed: string[] = [
  'No security certification or regulatory compliance is claimed.',
  'No insurer system is integrated.',
  'Retention and deletion periods are not decided yet.',
]

export const syntheticFinding = {
  id: 'F-03',
  title: 'The final invoice total is higher than the garage estimate',
  rule: 'Estimate-to-invoice variance',
  summary:
    'The invoice total is INR 62,510.00 against an estimate of INR 45,000.50: INR 17,509.50 more, or 38.91%. The check flags an increase of more than 10% and at least INR 1,000.00.',
  supports: [
    { label: 'Invoice grand total INR 62,510.00', source: 'Final invoice, page 1: "Grand total 62,510.00"' },
    { label: 'Estimate INR 45,000.50', source: 'Claim record: initial estimate' },
  ],
  weakens: [
    { label: 'The invoice lines add up to its printed total', source: 'Invoice arithmetic check: pass' },
    { label: 'Repairs are often extended after the first estimate', source: 'Benign explanation to rule out' },
  ],
  unknown: [
    { label: 'Whether a supplementary estimate was issued', source: 'None received: not checked' },
    { label: 'Whether the garage found more damage', source: 'Not in the supplied documents' },
  ],
  next: 'Ask the garage for the supplementary estimate or the job card, then run the checks again.',
}
