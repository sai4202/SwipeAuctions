import { useEffect, useRef, useState, type ReactNode } from 'react'

interface FilterModalProps<T> {
  /** Trigger button text, e.g. "City". */
  label: string
  /** Count shown in parens on the trigger, e.g. "City (2)". 0/undefined = no badge. */
  count?: number
  /** Tint the trigger without a numeric badge (for triggers where a count doesn't read naturally, e.g. Bid Range). */
  active?: boolean
  /** Anchor the dropdown's right edge to the trigger's right edge instead of its left — for a trigger
   *  that sits at the right end of its row, so the panel opens leftward and stays on screen. */
  align?: 'left' | 'right'
  /** Currently-committed value; becomes the panel's staged value each time it opens. */
  applied: T
  /** What "Reset" sets the staged value back to. */
  emptyValue: T
  onApply: (value: T) => void
  renderBody: (staged: T, setStaged: (v: T) => void) => ReactNode
}

/**
 * Self-contained filter trigger + anchored dropdown panel (Category/State/City/Bid Range/More
 * Filters/Vehicle Type). Opens right under its own trigger button — same anchoring as FilterPill —
 * instead of a full-page centered/backdrop modal, so it never blocks the rest of the page. Staging +
 * cancel work the same way as before: the panel body only mounts while open, so useState(applied)
 * re-initializes fresh on every open, and closing without "Apply Now" discards any edits.
 */
export default function FilterModal<T>({ label, count, active, align = 'left', applied, emptyValue, onApply, renderBody }: FilterModalProps<T>) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)
  const isActive = Boolean(count) || active

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  useEffect(() => {
    if (!open) return
    function onKey(e: KeyboardEvent) { if (e.key === 'Escape') setOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  return (
    <div className="filter-pill-wrap" ref={ref}>
      <button type="button" className={`filter-pill ${isActive ? 'active' : ''}`} onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        {label}{count ? ` (${count})` : ''} <span className="caret">▾</span>
      </button>
      {open && (
        <FilterDropdownPanel
          applied={applied} emptyValue={emptyValue} renderBody={renderBody} align={align}
          onApply={(v) => { onApply(v); setOpen(false) }}
        />
      )}
    </div>
  )
}

interface FilterDropdownPanelProps<T> {
  applied: T
  emptyValue: T
  align: 'left' | 'right'
  onApply: (value: T) => void
  renderBody: (staged: T, setStaged: (v: T) => void) => ReactNode
}

function FilterDropdownPanel<T>({ applied, emptyValue, align, onApply, renderBody }: FilterDropdownPanelProps<T>) {
  const [staged, setStaged] = useState<T>(applied)

  return (
    <div className={`filter-dropdown ${align === 'right' ? 'filter-dropdown-right' : ''}`}>
      {renderBody(staged, setStaged)}
      <div className="filter-dropdown-foot">
        <button type="button" className="linkbtn" onClick={() => setStaged(emptyValue)}>Reset</button>
        <button type="button" className="btn sm" onClick={() => onApply(staged)}>Apply Now</button>
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

/** Search box + compact ~5-row scrollable checkbox list — the body used by every checkbox filter. */
export function CheckboxListBody({ options, selected, onChange, searchPlaceholder }: CheckboxListBodyProps) {
  const [q, setQ] = useState('')
  const visible = options.filter((o) => o.toLowerCase().includes(q.toLowerCase()))
  const toggle = (opt: string) => onChange(selected.includes(opt) ? selected.filter((v) => v !== opt) : [...selected, opt])

  return (
    <>
      <div className="modal-search">
        <span className="mag">🔍</span>
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={searchPlaceholder} autoFocus />
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
