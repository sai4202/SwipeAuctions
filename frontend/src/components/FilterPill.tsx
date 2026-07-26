import { useEffect, useRef, useState, type ReactNode } from 'react'

interface FilterPillProps {
  label: string
  /** Count shown in parens, e.g. "Category (1)". 0/undefined = no badge. */
  count?: number
  /** Tint the trigger without a numeric badge (for pills where a count doesn't read naturally). */
  active?: boolean
  panelClassName?: string
  children: ReactNode
}

/** Small inline popover pill — Keyword and More Filters use this; Category/State/City/Bid Range use the bigger FilterModal instead. */
export default function FilterPill({ label, count, active, panelClassName, children }: FilterPillProps) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  const isActive = Boolean(count) || active

  return (
    <div className="filter-pill-wrap" ref={ref}>
      <button type="button" className={`filter-pill ${isActive ? 'active' : ''}`} onClick={() => setOpen((o) => !o)} aria-expanded={open}>
        {label}{count ? ` (${count})` : ''} <span className="caret">▾</span>
      </button>
      {open && <div className={`filter-pill-panel ${panelClassName ?? ''}`}>{children}</div>}
    </div>
  )
}
