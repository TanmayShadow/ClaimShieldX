type BrandMarkProps = { size?: number; className?: string }

/** The product mark: a case sheet with a magnifier. Decorative; the wordmark beside it carries the name. */
export function BrandMark({ size = 32, className }: BrandMarkProps) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 32 32" fill="none" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="4" fill="var(--ink)" />
      <path d="M9 7.5h11l4 4V24.5H9z" fill="var(--surface)" stroke="var(--surface)" strokeWidth="1.5" strokeLinejoin="round" />
      <circle cx="15.5" cy="16.5" r="4.2" stroke="var(--signal)" strokeWidth="2.2" />
      <path d="M18.6 19.6l3.4 3.4" stroke="var(--signal)" strokeWidth="2.2" strokeLinecap="round" />
    </svg>
  )
}
