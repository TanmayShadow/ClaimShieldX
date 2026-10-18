import { render, screen } from '@testing-library/react'
import { SelectField, TextAreaField, TextField } from './Field'

describe('TextField', () => {
  it('associates the label with the input', () => {
    render(<TextField label="Claim reference" />)
    expect(screen.getByLabelText('Claim reference')).toBeInstanceOf(HTMLInputElement)
  })

  it('describes the input with its hint', () => {
    render(<TextField label="Garage" hint="Insurer's own id" />)
    expect(screen.getByLabelText('Garage')).toHaveAccessibleDescription("Insurer's own id")
  })

  it('marks an invalid input and describes it with the error, after the hint', () => {
    render(<TextField label="Garage" hint="Insurer's own id" error="Garage id is required" />)
    const input = screen.getByLabelText('Garage')
    expect(input).toBeInvalid()
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription("Insurer's own id Garage id is required")
  })

  it('does not mark a valid input invalid', () => {
    render(<TextField label="Garage" />)
    expect(screen.getByLabelText('Garage')).not.toHaveAttribute('aria-invalid')
  })

  it('passes required through and says when a field is optional', () => {
    render(
      <>
        <TextField label="Policy token" required />
        <TextField label="Claimant" optional />
      </>,
    )
    expect(screen.getByLabelText('Policy token')).toBeRequired()
    expect(screen.getByText('optional')).toBeInTheDocument()
  })

  it('gives every field its own id', () => {
    render(
      <>
        <TextField label="One" />
        <TextField label="Two" />
      </>,
    )
    expect(screen.getByLabelText('One').id).not.toBe(screen.getByLabelText('Two').id)
  })
})

describe('SelectField and TextAreaField', () => {
  it('label their controls', () => {
    render(
      <>
        <SelectField label="Stage">
          <option value="a">A</option>
        </SelectField>
        <TextAreaField label="Rationale" error="Too short" />
      </>,
    )
    expect(screen.getByLabelText('Stage')).toBeInstanceOf(HTMLSelectElement)
    expect(screen.getByLabelText('Rationale')).toHaveAccessibleDescription('Too short')
  })
})
