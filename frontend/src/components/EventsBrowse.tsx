import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { getAuctions, getEvents, getCategoryFilters, errorMessage, type Auction, type AuctionEvent } from '../api'
import { addMultiBid, removeMultiBid, getMultiBidIds, money, cardImage, downloadValuation, eventStatus, effectiveStatus, type EventStatus } from '../util'
import { EVENT_CATEGORIES, EVENT_CATEGORY_SLUGS } from '../eventCategories'
import { VEHICLE_TYPE_KEY, VEHICLE_TYPE_OPTIONS } from '../catalogFilters'
import { type BrowseFilter, filterParam, matchesFilter } from '../browseFilters'
import { useCachedFetch } from '../useCachedFetch'
import { SkeletonTableRows } from './Skeleton'
import FilterModal, { CheckboxListBody } from './FilterModal'

const STATUS: EventStatus[] = ['LIVE', 'UPCOMING', 'CLOSED']
const SLABEL: Record<EventStatus, string> = { LIVE: 'Live', UPCOMING: 'Upcoming', CLOSED: 'Closed' }
const ITEM_TABS = ['OPEN', 'SCHEDULED', 'CLOSED'] as const
const ITEM_TAB_LABEL: Record<(typeof ITEM_TABS)[number], string> = { OPEN: 'Live', SCHEDULED: 'Upcoming', CLOSED: 'Closed' }
const CLOSED_STATUSES = ['CLOSED', 'UNSOLD', 'CANCELLED']

type EventSortKey = 'name' | 'start' | 'end' | 'location' | 'count'
const EVENT_SORTERS: Record<EventSortKey, (e: AuctionEvent) => string | number> = {
  name: (e) => e.name.toLowerCase(),
  start: (e) => new Date(e.startTime).getTime(),
  end: (e) => new Date(e.closingTime).getTime(),
  location: (e) => (e.location ?? '').toLowerCase(),
  count: (e) => e.itemCount,
}

/** Clickable column header with an up/down sort indicator, used by the events table. */
function SortableTh({ label, sortKey, active, dir, onClick }: {
  label: string; sortKey: EventSortKey; active: EventSortKey | null; dir: 'asc' | 'desc'; onClick: (k: EventSortKey) => void
}) {
  const isActive = active === sortKey
  return (
    <th>
      <button
        type="button"
        onClick={() => onClick(sortKey)}
        style={{
          background: 'none', border: 'none', padding: 0, cursor: 'pointer', font: 'inherit',
          color: isActive ? 'var(--text)' : 'inherit', display: 'inline-flex', alignItems: 'center', gap: 4,
        }}
      >
        {label} <span style={{ opacity: isActive ? 1 : 0.4 }}>{isActive ? (dir === 'asc' ? '↑' : '↓') : '↕'}</span>
      </button>
    </th>
  )
}

function fmt(dt: string): string {
  return new Date(dt).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}
function shortId(id: string): string {
  return id.replace(/-/g, '').slice(0, 8).toUpperCase()
}

/**
 * CarTrade Exchange-style "Events" browsing UI: a top filter bar (Vehicle Type checkboxes,
 * location, status) + category pill row (All Events / Banks-OEM / Insurance / Premium / Auto),
 * a table of auction events, and — once one is entered — a table of that event's items. Mounted by
 * BrowsePage at /auctions?category=<event-category-slug> so it's the same page/URL as every other
 * category, just filtered to the selected one (?event=<id> drills into one event's items).
 */
export default function EventsBrowse({ categorySlug }: { categorySlug?: string }) {
  const [params, setParams] = useSearchParams()
  const [error, setError] = useState('')
  // 'auctions' is the exact same cache key BrowsePage uses for getAuctions() — arriving here from
  // (or leaving to) the flat browse grid reuses the same cached fetch instead of paying for it twice.
  const { data: auctions = [], loading: auctionsLoading } = useCachedFetch<Auction[]>(
    'auctions', getAuctions, { onError: (e) => setError(errorMessage(e)) },
  )
  const { data: events = [], loading: eventsLoading } = useCachedFetch<AuctionEvent[]>('events', getEvents, {
    onError: (e) => setError(errorMessage(e)),
  })
  const loading = auctionsLoading || eventsLoading
  const [multiIds, setMultiIds] = useState<string[]>(getMultiBidIds())
  const [vtModalOpen, setVtModalOpen] = useState(false)
  const [eventSort, setEventSort] = useState<EventSortKey | null>(null)
  const [eventSortDir, setEventSortDir] = useState<'asc' | 'desc'>('asc')
  const toggleEventSort = (k: EventSortKey) => {
    if (eventSort === k) setEventSortDir((d) => (d === 'asc' ? 'desc' : 'asc'))
    else { setEventSort(k); setEventSortDir('asc') }
  }

  const eventId = params.get('event') || ''
  const q = params.get('q') || ''
  const loc = params.get('loc') || ''
  const vt = useMemo(() => (params.get('vt') || '').split(',').filter(Boolean), [params])
  const estatus = (params.get('estatus') as EventStatus) || 'LIVE'
  const itemTab = (params.get('itab') as (typeof ITEM_TABS)[number]) || 'OPEN'

  const toggleMulti = (id: string) => {
    setMultiIds(multiIds.includes(id) ? removeMultiBid(id) : addMultiBid(id))
  }
  const setParam = (k: string, v: string) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v); else p.delete(k)
    setParams(p, { replace: true })
  }
  const setPill = (slug?: string) => {
    const p = new URLSearchParams(params)
    if (slug) p.set('category', slug); else p.set('category', 'events')
    p.delete('event')
    setParams(p, { replace: true })
  }

  const auctionsByEvent = useMemo(() => {
    const m = new Map<string, Auction[]>()
    auctions.forEach((a) => {
      if (!a.eventId) return
      if (!m.has(a.eventId)) m.set(a.eventId, [])
      m.get(a.eventId)!.push(a)
    })
    return m
  }, [auctions])

  const vehicleTypesOf = (evId: string): string[] => {
    const items = auctionsByEvent.get(evId) ?? []
    return [...new Set(items.map((a) => a.attributes?.[VEHICLE_TYPE_KEY]).filter(Boolean) as string[])]
  }

  const pillCounts = useMemo(() => {
    const counts: Record<string, number> = { events: events.length }
    EVENT_CATEGORY_SLUGS.forEach((slug) => { counts[slug] = events.filter((e) => e.categorySlug === slug).length })
    return counts
  }, [events])

  const selectedEvent = eventId ? events.find((e) => e.id === eventId) ?? null : null

  const filteredEvents = useMemo(() => {
    const needle = q.trim().toLowerCase()
    return events.filter((e) => {
      if (categorySlug && e.categorySlug !== categorySlug) return false
      if (eventStatus(e) !== estatus) return false
      if (vt.length > 0) {
        const types = vehicleTypesOf(e.id)
        if (!vt.some((v) => types.includes(v))) return false
      }
      if (loc && !(e.location ?? '').toLowerCase().includes(loc.toLowerCase())) return false
      if (needle && !(e.name.toLowerCase().includes(needle) || (e.location ?? '').toLowerCase().includes(needle))) return false
      return true
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [events, categorySlug, estatus, vt, loc, q, auctionsByEvent])

  const sortedEvents = useMemo(() => {
    if (!eventSort) return filteredEvents
    const getVal = EVENT_SORTERS[eventSort]
    const sign = eventSortDir === 'asc' ? 1 : -1
    return [...filteredEvents].sort((a, b) => {
      const av = getVal(a), bv = getVal(b)
      return av < bv ? -sign : av > bv ? sign : 0
    })
  }, [filteredEvents, eventSort, eventSortDir])

  // ---- Item-level (inside one event) filters — live-fetched from the event's category, cached under
  // the same key format BrowsePage uses so the two share a cache entry for the same category. ----
  const items = auctionsByEvent.get(eventId) ?? []
  const itemBrandOptions = useMemo(() => [...new Set(items.map((a) => a.brand).filter(Boolean) as string[])].sort(), [items])
  const { data: liveItemFilters = [] } = useCachedFetch<Awaited<ReturnType<typeof getCategoryFilters>>>(
    selectedEvent ? `category-filters:${selectedEvent.categoryId}` : null,
    () => getCategoryFilters((selectedEvent as AuctionEvent).categoryId),
    { maxAgeMs: 5 * 60_000 },
  )
  const itemCatFilters: BrowseFilter[] = useMemo(() => {
    const list: BrowseFilter[] = []
    if (itemBrandOptions.length > 0) list.push({ key: 'brand', label: 'Make', type: 'brand', options: itemBrandOptions })
    liveItemFilters.forEach((f) => list.push({ key: f.key, label: f.label, type: f.valueType === 'NUMBER' ? 'NUMBER' : 'ENUM', options: f.options }))
    return list
  }, [itemBrandOptions, liveItemFilters])
  const filteredItems = useMemo(() => {
    const needle = q.trim().toLowerCase()
    return items.filter((a) => {
      const es = effectiveStatus(a)
      const matchesTab = itemTab === 'CLOSED' ? CLOSED_STATUSES.includes(es) : es === itemTab
      if (!matchesTab) return false
      if (vt.length > 0 && !vt.includes(a.attributes?.[VEHICLE_TYPE_KEY] ?? '')) return false
      for (const f of itemCatFilters) {
        const v = params.get(filterParam(f.key))
        if (v && !matchesFilter(a, f, v)) return false
      }
      if (needle && !`${a.title} ${a.brand ?? ''}`.toLowerCase().includes(needle)) return false
      return true
    })
  }, [items, itemTab, vt, itemCatFilters, params, q])

  if (error) return <div className="error">{error}</div>

  // ---- Item-level view (inside one event) ----
  if (eventId) {
    return (
      <div>
        <div className="section-head">
          <h1 className="page">{selectedEvent?.name ?? 'Auction Event'}</h1>
          <button className="btn ghost sm" onClick={() => setParam('event', '')}>← All events</button>
        </div>

        {selectedEvent && (
          <div className="card" style={{ marginBottom: 18 }}>
            <div className="stat">
              <div><span className="k">Location</span><div className="v" style={{ fontSize: 15 }}>{selectedEvent.location || '—'}</div></div>
              <div><span className="k">Start Time</span><div className="v" style={{ fontSize: 15 }}>{fmt(selectedEvent.startTime)}</div></div>
              <div><span className="k">Closing Time</span><div className="v" style={{ fontSize: 15 }}>{fmt(selectedEvent.closingTime)}</div></div>
              <div><span className="k">No. of Items</span><div className="v" style={{ fontSize: 15 }}>{selectedEvent.itemCount}</div></div>
            </div>
          </div>
        )}

        {/* Filters live at the top of the page, not a sidebar. */}
        <div className="card event-filter-bar">
          <div className="event-filter-row">
            <input value={q} onChange={(e) => setParam('q', e.target.value)} placeholder="Search item…" style={{ flex: '1 1 220px' }} />
            {itemCatFilters.map((f) => {
              const pkey = filterParam(f.key)
              const val = params.get(pkey) || ''
              return f.type === 'NUMBER' ? (
                <input key={pkey} type="number" value={val} onChange={(e) => setParam(pkey, e.target.value)}
                  placeholder={f.label} style={{ flex: '1 1 140px' }} />
              ) : (
                <select key={pkey} value={val} onChange={(e) => setParam(pkey, e.target.value)} style={{ flex: '1 1 140px' }}>
                  <option value="">{f.label}: Any</option>
                  {f.options.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
              )
            })}
          </div>
          <div className="event-filter-row event-filter-row-bottom">
            <button type="button" className={`filter-pill ${vt.length ? 'active' : ''}`} onClick={() => setVtModalOpen(true)}>
              Vehicle Type{vt.length ? ` (${vt.length})` : ''} <span className="caret">▾</span>
            </button>
          </div>
        </div>

        {vtModalOpen && (
          <FilterModal<string[]>
            title="Vehicle Type" applied={vt} emptyValue={[]}
            onApply={(v) => setParam('vt', v.join(','))} onClose={() => setVtModalOpen(false)}
            renderBody={(staged, setStaged) => (
              <CheckboxListBody options={VEHICLE_TYPE_OPTIONS} selected={staged} onChange={setStaged} searchPlaceholder="Search Vehicle Types" />
            )}
          />
        )}

        <div className="tabs status-tabs">
          {ITEM_TABS.map((s) => (
            <button key={s} className={`tab ${itemTab === s ? 'active' : ''}`} onClick={() => setParam('itab', s)}>{ITEM_TAB_LABEL[s]}</button>
          ))}
        </div>

        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          {!loading && filteredItems.length === 0 ? (
            <p className="muted" style={{ padding: 16 }}>No {ITEM_TAB_LABEL[itemTab].toLowerCase()} items match your filters.</p>
          ) : (
            <div style={{ overflowX: 'auto', padding: 16 }}>
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Image</th><th>Details</th><th>Start Time</th><th>End Time</th>
                    <th>Base Price</th><th>Current Bid</th><th>Bids</th><th>Bids Left</th><th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {loading && <SkeletonTableRows rows={5} cols={9} />}
                  {filteredItems.map((a) => (
                    <tr key={a.id}>
                      <td>
                        <Link to={`/auctions/${a.id}`}>
                          <img src={cardImage(a)} alt={a.title} style={{ width: 70, height: 52, objectFit: 'cover', borderRadius: 8, display: 'block' }} />
                        </Link>
                        <button type="button" className="linkbtn" style={{ fontSize: 11 }} onClick={() => downloadValuation(a)}>⬇ Valuation</button>
                      </td>
                      <td>
                        <Link to={`/auctions/${a.id}`}><b>{a.title}</b></Link>
                        <div className="muted" style={{ fontSize: 12 }}>{[a.brand, a.condition.replace('_', ' ')].filter(Boolean).join(' · ')}</div>
                        <div className="muted" style={{ fontSize: 12 }}>◍ {[a.city, a.state].filter(Boolean).join(', ') || '—'}</div>
                        {Object.entries(a.attributes).length > 0 && (
                          <div className="muted" style={{ fontSize: 11, marginTop: 2 }}>
                            {Object.entries(a.attributes).map(([k, v]) => `${k}: ${v}`).join(' · ')}
                          </div>
                        )}
                      </td>
                      <td>{fmt(a.startTime)}</td>
                      <td>{fmt(a.currentEndTime)}</td>
                      <td>{money(a.basePrice)}</td>
                      <td>{a.currentHighestBid != null ? money(a.currentHighestBid) : '—'}</td>
                      <td>{a.bidCount}</td>
                      <td>
                        {a.bidsRemaining == null ? '—' : a.bidsRemaining === 0 ? (
                          <span style={{ color: 'var(--red-2)', fontWeight: 700 }}>None left</span>
                        ) : a.bidsRemaining}
                      </td>
                      <td>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 120 }}>
                          <Link to={`/auctions/${a.id}`} className="btn ghost sm">View Details</Link>
                          {a.bidsRemaining === 0 ? (
                            <span className="btn sm" style={{ opacity: .55, pointerEvents: 'none' }}>No bids left</span>
                          ) : (
                            <Link to={`/auctions/${a.id}?bid=1`} className="btn sm">Bid Now</Link>
                          )}
                          {multiIds.includes(a.id) ? (
                            <button className="btn light sm" onClick={() => toggleMulti(a.id)}>♥ In wishlist</button>
                          ) : (
                            <button className="btn ghost sm" onClick={() => toggleMulti(a.id)}>♡ Wishlist</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    )
  }

  // ---- Events-list view ----
  const categoryLabel = categorySlug ? EVENT_CATEGORIES[categorySlug]?.label ?? 'Auction' : 'Auction'
  return (
    <div>
      <div className="section-head">
        <h1 className="page">{SLABEL[estatus]} {categoryLabel} Events</h1>
      </div>

      <div className="global-search">
        <input value={q} onChange={(e) => setParam('q', e.target.value)} placeholder="Search auction events by name, location…" aria-label="Search auction events" />
      </div>

      {/* Top filter bar. */}
      <div className="filter-pill-row">
        <input value={loc} onChange={(e) => setParam('loc', e.target.value)} placeholder="Location (city, state)"
          style={{ width: 200, padding: '9px 14px', borderRadius: 'var(--radius-full)' }} />
        <button type="button" className={`filter-pill ${vt.length ? 'active' : ''}`} onClick={() => setVtModalOpen(true)}>
          Vehicle Type{vt.length ? ` (${vt.length})` : ''} <span className="caret">▾</span>
        </button>
        <span className="muted" style={{ marginLeft: 'auto', fontSize: 12.5 }}>{filteredEvents.length} found</span>
      </div>

      {vtModalOpen && (
        <FilterModal<string[]>
          title="Vehicle Type" applied={vt} emptyValue={[]}
          onApply={(v) => setParam('vt', v.join(','))} onClose={() => setVtModalOpen(false)}
          renderBody={(staged, setStaged) => (
            <CheckboxListBody options={VEHICLE_TYPE_OPTIONS} selected={staged} onChange={setStaged} searchPlaceholder="Search Vehicle Types" />
          )}
        />
      )}

      {/* Category pill bar. */}
      <div className="tabs status-tabs">
        <button className={`tab ${!categorySlug ? 'active' : ''}`} onClick={() => setPill(undefined)}>
          All Events ({pillCounts.events ?? 0})
        </button>
        {EVENT_CATEGORY_SLUGS.map((slug) => (
          <button key={slug} className={`tab ${categorySlug === slug ? 'active' : ''}`} onClick={() => setPill(slug)}>
            {EVENT_CATEGORIES[slug].pillLabel} ({pillCounts[slug] ?? 0})
          </button>
        ))}
      </div>

      <div className="tabs status-tabs">
        {STATUS.map((s) => (
          <button key={s} className={`tab ${estatus === s ? 'active' : ''}`} onClick={() => setParam('estatus', s)}>{SLABEL[s]}</button>
        ))}
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        {!loading && filteredEvents.length === 0 ? (
          <p className="muted" style={{ padding: 16 }}>No {SLABEL[estatus].toLowerCase()} auction events match your filters.</p>
        ) : (
          <div style={{ overflowX: 'auto', padding: 16 }}>
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Event ID</th>
                  <SortableTh label="Event Name" sortKey="name" active={eventSort} dir={eventSortDir} onClick={toggleEventSort} />
                  <SortableTh label="Start Date & Time" sortKey="start" active={eventSort} dir={eventSortDir} onClick={toggleEventSort} />
                  <SortableTh label="End Date & Time" sortKey="end" active={eventSort} dir={eventSortDir} onClick={toggleEventSort} />
                  <SortableTh label="Location" sortKey="location" active={eventSort} dir={eventSortDir} onClick={toggleEventSort} />
                  <th>Type</th>
                  <SortableTh label="Auctions" sortKey="count" active={eventSort} dir={eventSortDir} onClick={toggleEventSort} />
                  <th>Vehicle Type</th><th>Action</th>
                </tr>
              </thead>
              <tbody>
                {loading && <SkeletonTableRows rows={6} cols={9} />}
                {sortedEvents.map((e) => (
                  <tr key={e.id}>
                    <td className="muted">{shortId(e.id)}</td>
                    <td>{e.name}</td>
                    <td>{fmt(e.startTime)}</td>
                    <td>{fmt(e.closingTime)}</td>
                    <td>{e.location || '—'}</td>
                    <td><span className={`badge ${eventStatus(e) === 'CLOSED' ? 'CLOSED' : eventStatus(e) === 'UPCOMING' ? 'SCHEDULED' : 'OPEN'}`}>{SLABEL[eventStatus(e)]}</span></td>
                    <td>{e.itemCount}</td>
                    <td className="muted">{vehicleTypesOf(e.id).join(', ') || '—'}</td>
                    <td><button className="btn sm" onClick={() => setParam('event', e.id)}>Bid Now</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
