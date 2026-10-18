import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { contrastRatio } from '@/utils/contrast'

const css = readFileSync(resolve(process.cwd(), 'src/styles/tokens.css'), 'utf8')

function token(name: string): string {
  const match = new RegExp(`--${name}:\\s*(#[0-9a-fA-F]{6})`).exec(css)
  if (!match) throw new Error(`token --${name} is not a #rrggbb colour in tokens.css`)
  return match[1]!
}

const TEXT = 4.5
const UI = 3

describe('design tokens', () => {
  it('defines the documented palette exactly', () => {
    expect(token('paper')).toBe('#f4f0e7')
    expect(token('paper-deep')).toBe('#e9e2d4')
    expect(token('surface')).toBe('#fffefa')
    expect(token('ink')).toBe('#182321')
    expect(token('ink-soft')).toBe('#52605b')
    expect(token('line')).toBe('#cec8ba')
    expect(token('signal')).toBe('#f05a3c')
    expect(token('signal-deep')).toBe('#c83f29')
    expect(token('caution')).toBe('#e7bd43')
    expect(token('evidence')).toBe('#4e8178')
    expect(token('blueprint')).toBe('#3e627a')
    expect(token('danger')).toBe('#b73535')
  })

  describe.each([['paper'], ['paper-deep'], ['surface']])('text on %s', (background) => {
    it.each([['ink'], ['ink-soft'], ['blueprint'], ['danger'], ['signal-ink'], ['evidence-ink'], ['caution-ink']])(
      '%s reaches 4.5:1',
      (foreground) => {
        expect(contrastRatio(token(foreground), token(background))).toBeGreaterThanOrEqual(TEXT)
      },
    )
  })

  it('keeps control borders and focus rings at 3:1 on the surfaces they sit on', () => {
    for (const background of ['paper', 'surface']) {
      expect(contrastRatio(token('line-strong'), token(background))).toBeGreaterThanOrEqual(UI)
      expect(contrastRatio(token('blueprint'), token(background))).toBeGreaterThanOrEqual(UI)
    }
  })

  it('keeps button labels readable', () => {
    expect(contrastRatio(token('ink'), token('signal'))).toBeGreaterThanOrEqual(TEXT)
    expect(contrastRatio(token('surface'), token('signal-deep'))).toBeGreaterThanOrEqual(TEXT)
    expect(contrastRatio(token('surface'), token('danger'))).toBeGreaterThanOrEqual(TEXT)
    expect(contrastRatio(token('ink'), token('caution'))).toBeGreaterThanOrEqual(TEXT)
  })

  it('keeps text on ink panels readable', () => {
    for (const foreground of ['paper', 'surface', 'signal', 'evidence-light', 'blueprint-light']) {
      expect(contrastRatio(token(foreground), token('ink'))).toBeGreaterThanOrEqual(TEXT)
    }
  })

  it('does not use the decorative line colour for anything that must be seen', () => {
    // --line is a divider, below 3:1 by design; controls must use --line-strong.
    expect(contrastRatio(token('line'), token('surface'))).toBeLessThan(UI)
  })
})
