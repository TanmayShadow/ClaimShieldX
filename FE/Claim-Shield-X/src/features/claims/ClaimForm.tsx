import { useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link } from 'react-router'
import { isApiError } from '@/apis/errors'
import { Button } from '@/components/ui/Button'
import { buttonClass } from '@/components/ui/buttonClass'
import { ErrorPanel } from '@/components/ui/ErrorPanel'
import { ErrorSummary } from '@/components/ui/ErrorSummary'
import type { ErrorSummaryItem } from '@/components/ui/ErrorSummary'
import { SelectField, TextField } from '@/components/ui/Field'
import { viewerTimeZone } from '@/utils/format'
import { FIELD_LABELS, FIELD_ORDER, emptyClaimForm, mapServerFieldErrors, validateClaimForm } from './claimForm'
import type { ClaimFormValues, FieldErrors, FieldPath } from './claimForm'
import styles from './ClaimForm.module.css'
import { useCreateClaim } from './hooks'
import { CLAIM_STAGES, INCIDENT_TYPES, incidentTypeLabels, stageLabels } from './labels'

type ClaimFormProps = { onCreated: (claimId: string, internalId: string) => void }

function Group({ title, hint, children }: { title: string; hint?: string; children: ReactNode }) {
  return (
    <fieldset className={styles.group}>
      <legend>{title}</legend>
      {hint ? <p className={styles.groupHint}>{hint}</p> : null}
      <div className={styles.grid}>{children}</div>
    </fieldset>
  )
}

export function ClaimForm({ onCreated }: ClaimFormProps) {
  const [values, setValues] = useState<ClaimFormValues>(emptyClaimForm)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [otherErrors, setOtherErrors] = useState<string[]>([])
  const [failure, setFailure] = useState<unknown>(null)
  // A request that failed without an answer may still have created the claim; a later "already exists" is then expected.
  const [unansweredAttempt, setUnansweredAttempt] = useState(false)
  const summary = useRef<HTMLDivElement>(null)
  const create = useCreateClaim()
  const zone = viewerTimeZone()

  const set = (path: FieldPath) => (event: { target: { value: string } }) => setValues((v) => ({ ...v, [path]: event.target.value }))

  function showErrors(next: FieldErrors, other: string[] = []) {
    setErrors(next)
    setOtherErrors(other)
    // Wait for the summary to render, then put focus on it so the first problem is the next thing read.
    window.requestAnimationFrame(() => summary.current?.focus())
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (create.isPending) return
    setFailure(null)

    const checked = validateClaimForm(values)
    if (!checked.ok) {
      showErrors(checked.errors)
      return
    }
    setErrors({})
    setOtherErrors([])

    try {
      const claim = await create.mutateAsync(checked.body)
      onCreated(claim.id, claim.internalId)
    } catch (error) {
      if (isApiError(error) && error.code === 'VALIDATION_FAILED' && error.fieldErrors.length > 0) {
        const mapped = mapServerFieldErrors(error.fieldErrors)
        showErrors(mapped.fieldErrors, mapped.other)
      } else if (isApiError(error) && error.code === 'DUPLICATE_CLAIM') {
        showErrors({
          internalId: unansweredAttempt
            ? 'A claim with this reference already exists. If your earlier attempt reached the server, it was created: look for it in the claims list.'
            : 'A claim with this reference already exists in your organization. References must be unique.',
        })
      } else {
        if (isApiError(error) && error.status === 0) setUnansweredAttempt(true)
        setFailure(error)
      }
    }
  }

  const summaryItems: ErrorSummaryItem[] = [
    ...FIELD_ORDER.filter((f) => errors[f]).map((f) => ({ id: f, label: FIELD_LABELS[f], message: errors[f]! })),
    ...otherErrors.map((message, i) => ({ id: `other-${i}`, label: 'Other', message })),
  ]

  const text = (path: FieldPath, label: string, extra: { hint?: string; optional?: boolean; mono?: boolean; maxLength?: number } = {}) => (
    <TextField
      id={path}
      name={path}
      label={label}
      value={values[path]}
      onChange={set(path)}
      error={errors[path]}
      optional={extra.optional}
      hint={extra.hint}
      mono={extra.mono}
      maxLength={extra.maxLength}
      autoComplete="off"
      spellCheck={false}
    />
  )

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      {summaryItems.length > 0 ? (
        <ErrorSummary ref={summary} title={summaryItems.length === 1 ? '1 thing needs fixing' : `${summaryItems.length} things need fixing`} items={summaryItems} />
      ) : null}

      {failure ? (
        <ErrorPanel
          error={failure}
          subject="the claims service"
          onRetry={() => document.getElementById('create-claim')?.click()}
          retrying={create.isPending}
          extra={unansweredAttempt ? <span>The claim may already have been created. Check the claims list before trying again.</span> : null}
        />
      ) : null}

      <p className={styles.fine}>Fields marked optional can be left blank. Every other field is required.</p>

      <Group title="Claim" hint="The reference is your own id for the claim and must be unique in your organization. The stage decides which documents the checklist expects.">
        <div className={styles.wide}>{text('internalId', 'Claim reference', { mono: true, hint: 'For example CLM-DEMO-1001.' })}</div>
        <div className={styles.wide}>
          <SelectField id="currentStage" name="currentStage" label="Claim stage" value={values.currentStage} onChange={set('currentStage')} error={errors.currentStage}>
            <option value="">Choose a stage</option>
            {CLAIM_STAGES.map((stage) => (
              <option key={stage} value={stage}>
                {stageLabels[stage]}
              </option>
            ))}
          </SelectField>
        </div>
      </Group>

      <Group title="Policy" hint="Use the pseudonymous token for the policy, never the policy number or a name.">
        {text('policy.policyToken', 'Policy token', { mono: true })}
        {text('policy.policyType', 'Policy type', { optional: true, hint: 'For example COMPREHENSIVE.' })}
        <TextField id="policy.policyStartDate" name="policy.policyStartDate" label="Policy start date" type="date" optional value={values['policy.policyStartDate']} onChange={set('policy.policyStartDate')} error={errors['policy.policyStartDate']} />
        <TextField id="policy.policyEndDate" name="policy.policyEndDate" label="Policy end date" type="date" optional value={values['policy.policyEndDate']} onChange={set('policy.policyEndDate')} error={errors['policy.policyEndDate']} />
      </Group>

      <Group title="Claimant" hint="Optional. Matching people is privacy-sensitive, so add this only when it is needed, and only as a token.">
        <div className={styles.wide}>{text('claimant.claimantToken', 'Claimant token', { mono: true, optional: true })}</div>
      </Group>

      <Group title="Garage and vehicle" hint="Use tokens, never a registration number. Matching against earlier claims is exact and case-sensitive, so type the token exactly.">
        {text('garage.garageId', 'Garage id', { mono: true })}
        {text('vehicle.vehicleToken', 'Vehicle token', { mono: true })}
        {text('vehicle.make', 'Vehicle make', { optional: true })}
        {text('vehicle.model', 'Vehicle model', { optional: true })}
      </Group>

      <Group title="Incident" hint={`Times are in your browser's time zone (${zone}).`}>
        <TextField id="incident.incidentAt" name="incident.incidentAt" label="Incident date and time" type="datetime-local" value={values['incident.incidentAt']} onChange={set('incident.incidentAt')} error={errors['incident.incidentAt']} />
        <TextField id="incident.reportedAt" name="incident.reportedAt" label="Reported date and time" type="datetime-local" optional value={values['incident.reportedAt']} onChange={set('incident.reportedAt')} error={errors['incident.reportedAt']} />
        <SelectField id="incident.incidentType" name="incident.incidentType" label="Incident type" value={values['incident.incidentType']} onChange={set('incident.incidentType')} error={errors['incident.incidentType']}>
          <option value="">Choose a type</option>
          {INCIDENT_TYPES.map((type) => (
            <option key={type} value={type}>
              {incidentTypeLabels[type]}
            </option>
          ))}
        </SelectField>
        {text('incident.permittedLocationPrecision', 'Location precision', { optional: true, hint: 'For example CITY.' })}
      </Group>

      <Group title="Money" hint="Amounts are in major units, such as rupees, with at most two decimals. Nothing is rounded for you.">
        <TextField
          id="financial.currency"
          name="financial.currency"
          label="Currency"
          value={values['financial.currency']}
          onChange={(e) => setValues((v) => ({ ...v, 'financial.currency': e.target.value.toUpperCase() }))}
          error={errors['financial.currency']}
          maxLength={3}
          autoCapitalize="characters"
          autoComplete="off"
          mono
          hint="Three capital letters, such as INR."
        />
        <div />
        {(['financial.initialEstimateAmount', 'financial.approvedEstimateAmount', 'financial.deductible'] as const).map((path) => (
          <TextField
            key={path}
            id={path}
            name={path}
            label={FIELD_LABELS[path]}
            optional
            inputMode="decimal"
            autoComplete="off"
            mono
            value={values[path]}
            onChange={set(path)}
            error={errors[path]}
            hint="For example 45000.50"
          />
        ))}
      </Group>

      <div className={styles.actions}>
        <Button id="create-claim" type="submit" variant="primary" loading={create.isPending} loadingLabel="Creating the claim">
          Create claim
        </Button>
        <Link className={buttonClass({ variant: 'ghost' })} to="/app/claims">
          Cancel
        </Link>
        <p className={styles.fine}>A claim cannot be edited after it is created in this release, so check the details first.</p>
      </div>
    </form>
  )
}
