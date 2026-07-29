import { useMemo, useState } from 'react'
import { DETAIL_FIELDS, DETAIL_TABS, FALLBACK_ICON, FALLBACK_TAB, type DetailTab } from '../detailFields'

interface Props {
  /** Raw attributes map straight off the auction (both the structured fields from detailFields.ts
   *  and any older/freeform keys already on existing listings). */
  attributes: Record<string, string>
  /** City/State live on the listing itself, not the attribute bag — shown as the 2nd/3rd General
   *  Details cards (matching the reference layout) instead of being duplicated into attributes. */
  city?: string | null
  state?: string | null
}

interface Card { icon: string; label: string; value: string; wide: boolean }

/** Long values (a full Additional Remarks paragraph, or just an unusually long freeform value)
 *  need the full grid width — squeezed into one ~200px column they turn into a single-word-wide
 *  column of text many lines tall instead of reading like a normal paragraph. */
const WIDE_VALUE_THRESHOLD = 60

/**
 * Buckets `attributes` into the 5 fixed tabs (General Details / Registration / Insurance / Other
 * Details / Remarks) by known key, same grouping the admin "Add Stock" form uses to collect them
 * — see detailFields.ts. Cards are ordered to match detailFields.ts (not whatever order the
 * attributes map happens to arrive in) so the layout is stable and predictable. Any key not in
 * that list (e.g. an older item's freeform Fuel/KM driven/Year attributes) falls into General
 * Details instead of silently disappearing. Tabs with nothing to show are hidden rather than
 * rendered empty.
 */
export default function DetailTabs({ attributes, city, state }: Props) {
  const cardsByTab = useMemo(() => {
    const map = new Map<DetailTab, Card[]>()
    const used = new Set<string>()

    // Known fields first, in detailFields.ts's own order.
    for (const def of DETAIL_FIELDS) {
      const value = attributes[def.key]?.trim()
      used.add(def.key)
      if (!value) continue
      const wide = def.type === 'textarea' || value.length > WIDE_VALUE_THRESHOLD
      const list = map.get(def.tab) ?? []
      list.push({ icon: def.icon, label: def.label, value, wide })
      map.set(def.tab, list)
    }

    // City/State spliced into General Details right after Power Steering, matching the reference.
    const generalCards = map.get('General Details') ?? []
    const insertAt = Math.min(1, generalCards.length)
    const locationCards: Card[] = []
    if (state?.trim()) locationCards.push({ icon: '📍', label: 'State', value: state.trim(), wide: false })
    if (city?.trim()) locationCards.push({ icon: '📍', label: 'City', value: city.trim(), wide: false })
    if (locationCards.length > 0) {
      generalCards.splice(insertAt, 0, ...locationCards)
      map.set('General Details', generalCards)
    }

    // Anything left over (older/freeform attribute keys not in detailFields.ts) — keep visible
    // under General Details rather than dropping it.
    for (const [key, rawValue] of Object.entries(attributes)) {
      if (used.has(key)) continue
      const value = rawValue?.trim()
      if (!value) continue
      const list = map.get(FALLBACK_TAB) ?? []
      list.push({ icon: FALLBACK_ICON, label: key, value, wide: value.length > WIDE_VALUE_THRESHOLD })
      map.set(FALLBACK_TAB, list)
    }

    return map
  }, [attributes, city, state])

  const availableTabs = DETAIL_TABS.filter((t) => (cardsByTab.get(t)?.length ?? 0) > 0)
  const [active, setActive] = useState<DetailTab | null>(null)
  const activeTab = active && availableTabs.includes(active) ? active : availableTabs[0]

  if (availableTabs.length === 0) return null

  const cards = cardsByTab.get(activeTab) ?? []

  return (
    <div className="detail-tabs">
      <div className="detail-tabs-nav">
        {availableTabs.map((t) => (
          <button key={t} type="button" className={'detail-tab-btn' + (t === activeTab ? ' active' : '')} onClick={() => setActive(t)}>
            {t.toUpperCase()}
          </button>
        ))}
      </div>
      <div className="detail-tabs-grid">
        {cards.map((c, i) => (
          <div className={'detail-field' + (c.wide ? ' wide' : '')} key={c.label + i}>
            <span className="detail-field-icon">{c.icon}</span>
            <div>
              <div className="detail-field-label">{c.label}</div>
              <div className="detail-field-value">{c.value}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
