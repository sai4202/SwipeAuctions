import { useEffect, useState, type ReactNode } from 'react'

interface FilterModalProps<T> {
  title: string
  /** Currently-committed value; becomes the modal's initial staged value. */
  applied: T
  /** What "Reset" sets the staged value back to. */
  emptyValue: T
  onApply: (value: T) => void
  /** Backdrop click, X, and Escape all cancel — staged changes are discarded. */
  onClose: () => void
  renderBody: (staged: T, setStaged: (v: T) => void) => ReactNode
}

/**
 * Centered checkbox/range picker modal (Category/State/City/Bid Range). Only ever conditionally
 * mounted by the caller (never hidden via CSS), so staging + cancel work for free: useState(applied)
 * re-initializes fresh on every open, and unmounting on close discards any unapplied edits.
 */
export default function FilterModal<T>({ title, applied, emptyValue, onApply, onClose, renderBody }: FilterModalProps<T>) {
  const [staged, setStaged] = useState<T>(applied)

  useEffect(() => {
    function onKey(e: KeyboardEvent) { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">{renderBody(staged, setStaged)}</div>
        <div className="modal-foot">
          <button type="button" className="linkbtn" onClick={() => setStaged(emptyValue)}>Reset</button>
          <button type="button" className="btn" onClick={() => { onApply(staged); onClose() }}>Apply Now</button>
        </div>
      </div>
    </div>
  )
}

interface CheckboxListBodyProps {
  options: string[]
  selected: string[]
  onChange: (next: string[]) => void
  searchPlaceholder: string
}

/** Search box + two-column scrollable checkbox grid — the body used by Category/State/City modals. */
export function CheckboxListBody({ options, selected, onChange, searchPlaceholder }: CheckboxListBodyProps) {
  const [q, setQ] = useState('')
  const visible = options.filter((o) => o.toLowerCase().includes(q.toLowerCase()))
  const toggle = (opt: string) => onChange(selected.includes(opt) ? selected.filter((v) => v !== opt) : [...selected, opt])

  return (
    <>
      <div className="modal-search">
        <span className="mag">🔍</span>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={searchPlaceholder} />
      </div>
      <div className="modal-checklist">
        {visible.length === 0 ? (
          <p className="muted">No matches.</p>
        ) : (
          visible.map((opt) => (
            <label key={opt} className="modal-check-row">
              <input type="checkbox" checked={selected.includes(opt)} onChange={() => toggle(opt)} />
              {opt}
            </label>
          ))
        )}
      </div>
    </>
  )
}
