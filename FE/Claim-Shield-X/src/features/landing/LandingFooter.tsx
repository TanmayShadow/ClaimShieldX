import styles from './Sections.module.css'

export function LandingFooter({ note = 'ClaimShield X is a prototype. Every example on this page is SYNTHETIC DEMO DATA.' }: { note?: string }) {
  return (
    <footer className={styles.footer}>
      <div className={`container ${styles.footerInner}`}>
        <p>{note}</p>
        <a href="#main">Back to the top</a>
      </div>
    </footer>
  )
}
