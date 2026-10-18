import { render, screen } from '@testing-library/react'
import { StatusMark } from './StatusMark'
import type { MarkState } from './StatusMark'

const states: MarkState[] = ['pending', 'active', 'done', 'attention', 'blocked', 'failed', 'skipped', 'unknown', 'info']

describe('StatusMark', () => {
  it.each(states)('%s always shows a text label and a decorative shape', (state) => {
    const { container } = render(<StatusMark state={state}>Label for {state}</StatusMark>)
    expect(screen.getByText(`Label for ${state}`)).toBeVisible()
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('gives every state a different shape so colour is never the only signal', () => {
    const shapes = states.map((state) => {
      const { container, unmount } = render(<StatusMark state={state}>x</StatusMark>)
      const shape = container.querySelector('svg')!.getAttribute('data-icon')
      unmount()
      return shape
    })
    expect(new Set(shapes).size).toBe(states.length)
  })

  it('shows a timestamp only when it has one', () => {
    const { rerender } = render(<StatusMark state="done">Completed</StatusMark>)
    expect(document.querySelector('time')).toBeNull()
    rerender(
      <StatusMark state="done" at="2026-10-18T09:12:44Z" atLabel="09:12">
        Completed
      </StatusMark>,
    )
    expect(document.querySelector('time')).toHaveAttribute('datetime', '2026-10-18T09:12:44Z')
  })
})
