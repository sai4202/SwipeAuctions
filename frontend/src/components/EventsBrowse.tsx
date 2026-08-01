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
import ItemDetailStrip from './ItemDetailStrip'
import SortableTh from './SortableTh'
import { useSortableData } from '../useSort'

const STATUS: EventStatus[] = ['LIVE', 'UPCOMING', 'CLOSED']
const SLABEL: Record<EventStatus, string> = { LIVE: 'Live', UPCOMING: 'Upcoming', CLOSED: 'Closed' }
const ITEM_TABS = ['OPEN', 'SCHEDULED', 'CLOSED'] as const
const ITEM_TAB_LABEL: Record<(typeof ITEM_TABS)[number], string> = { OPEN: 'Live', SCHEDULED: 'Upcoming', CLOSED: 'Closed' }
const CLOSED_STATUSES = ['CLOSED', 'UNSOLD', 'CANCELLED']

function getEventSortValue(e: AuctionEvent, key: string) {
  switch (key) {
    case 'name': return e.name?.toLowerCase()
    case 'start': return e.startTime
    case 'end': return e.closingTime
    case 'location': return (e.location ?? '').toLowerCase()
    case 'count': return e.itemCount
    default: return null
  }
}

function getItemSortValue(a: Auction, key: string) {
  switch (key) {
    case 'title': return a.title?.toLowerCase()
    case 'startTime': return a.startTime
    case 'endTime': return a.currentEndTime
    case 'basePrice': return a.basePrice
    case 'currentBid': return a.currentHighestBid ?? -1
    case 'bids': return a.bidCount
    case 'bidsLeft': return a.bidsRemaining ?? -1
    default: return null
  }
}

function fmt(dt: string): string {
  return new Date(dt).toLocaleString('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })
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

  const eventId = params.get('event') || ''
  const q = params.get('q') || ''
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
      if (needle) {
        const hay = [e.name, e.location ?? '', shortId(e.id), EVENT_CATEGORIES[e.categorySlug]?.label ?? '']
          .join(' ').toLowerCase()
        if (!hay.includes(needle)) return false
      }
      return true
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [events, categorySlug, estatus, vt, q, auctionsByEvent])

  const { sorted: sortedEvents, sortKey: eventSort, sortDir: eventSortDir, toggleSort: toggleEventSort } =
    useSortableData(filteredEvents, getEventSortValue)

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
  const { sorted: sortedItems, sortKey: itemSort, sortDir: itemSortDir, toggleSort: toggleItemSort } =
    useSortableData(filteredItems, getItemSortValue)

  if (error) return <div className="error">{error}</div>

  // ---- Item-level view (inside one event) ----
  if (eventId) {
    return (
      <div>
        <div className="section-head">
          <h1 className="page">{selectedEvent?.name ?? 'Auction Event'}</h1>
          <button className="btn ghost sm" onClick={() => setParam('event', '')}>← All events</button>
          {/* Live/Upcoming/Closed sits right in the banner, not buried below the stat card and
              filters, so it's visible without scrolling. */}
          <div className="tabs status-tabs" style={{ flexBasis: '100%', margin: '8px 0 0' }}>
            {ITEM_TABS.map((s) => (
              <button key={s} className={`tab ${itemTab === s ? 'active' : ''}`} onClick={() => setParam('itab', s)}>{ITEM_TAB_LABEL[s]}</button>
            ))}
          </div>
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

        {/* Filters — one line, not stacked rows, so the item table isn't pushed further down. */}
        <div className="card event-filter-bar">
          <div className="event-filter-row">
            <input className="search-glow" value={q} onChange={(e) => setParam('q', e.target.value)} placeholder="Search item…" style={{ flex: '1 1 220px' }} />
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
        </div>

        <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
          {!loading && filteredItems.length === 0 ? (
            <p className="muted" style={{ padding: 16 }}>No {ITEM_TAB_LABEL[itemTab].toLowerCase()} items match your filters.</p>
          ) : (
            <div style={{ overflowX: 'auto', padding: 16 }}>
              <table className="admin-table list-view-table">
                <colgroup>
                  <col style={{ width: 90 }} /><col />
                  <col style={{ width: 100 }} /><col style={{ width: 100 }} />
                  <col style={{ width: 100 }} /><col style={{ width: 100 }} />
                  <col style={{ width: 60 }} /><col style={{ width: 70 }} /><col style={{ width: 150 }} />
                </colgroup>
                <thead>
                  <tr>
                    <th>Image</th>
                    <SortableTh label="Details" sortKey="title" activeKey={itemSort} dir={itemSortDir} onSort={toggleItemSort} />
                    <SortableTh label="Start Time" sortKey="startTime" activeKey={itemSort} dir={itemSortDir} onSort={toggleItemSort} />
                    <SortableTh label="End Time" sortKey="endTime" activeKey={itemSort} dir={itemSortDir} onSort={toggleItemSort} />
                    <SortableTh label="Base Price" sortKey="basePrice" activeKey={itemSort} dir={itemSortDir} onSort={toggleItemSort} />
                    <SortableTh label="Current Bid" sortKey="currentBid" activeKey={itemSort} dir={itemSortDir} onSort={toggleItemSort} />
                    <SortableTh label="Bids" sortKey="bids" activeKey={itemSort} dir={itemSortDir} onSort={toggleItemSort} />
                    <SortableTh label="Bids Left" sortKey="bidsLeft" activeKey={itemSort} dir={itemSortDir} onSort={toggleItemSort} />
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {loading && <SkeletonTableRows rows={5} cols={9} />}
                  {sortedItems.map((a) => (
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
                        <ItemDetailStrip attributes={a.attributes} />
                      </td>
                      <td style={{ whiteSpace: 'nowrap' }}>{fmt(a.startTime)}</td>
                      <td style={{ whiteSpace: 'nowrap' }}>{fmt(a.currentEndTime)}</td>
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
                          {effectiveStatus(a) !== 'OPEN' ? (
                            <span className="btn sm" style={{ opacity: .55, pointerEvents: 'none' }}>
                              {effectiveStatus(a) === 'SCHEDULED' ? 'Not started' : 'Closed'}
                            </span>
                          ) : a.bidsRemaining === 0 ? (
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
  return (
    <div>
      {/* Main Live/Upcoming/Closed filter — sits where the page heading used to be, same gradient
          banner treatment as the flat browse page's status-tabs section. A plain text input (unlike
          Vehicle Type's dropdown) is safe here despite the banner's overflow:hidden, since it has no
          popover to clip. */}
      <div className="section-head">
        <div className="section-head-tabs">
          <div className="tabs status-tabs">
            {STATUS.map((s) => (
              <button key={s} className={`tab ${estatus === s ? 'active' : ''}`} onClick={() => setParam('estatus', s)}>{SLABEL[s]}</button>
            ))}
          </div>
        </div>
        <div className="events-search">
          <span className="mag">🔍</span>
          <input
            value={q} onChange={(e) => setParam('q', e.target.value)}
            placeholder="Search by name, location, ID…" aria-label="Search auction events"
          />
        </div>
      </div>

      {/* Category pill bar. */}
      <div className="tabs status-tabs" style={{ alignItems: 'center' }}>
        <FilterModal<string[]>
          label="Vehicle Type" count={vt.length}
          applied={vt} emptyValue={[]}
          onApply={(v) => setParam('vt', v.join(','))}
          renderBody={(staged, setStaged) => (
            <CheckboxListBody options={VEHICLE_TYPE_OPTIONS} selected={staged} onChange={setStaged} searchPlaceholder="Search Vehicle Types" />
          )}
        />
        <button className={`tab ${!categorySlug ? 'active' : ''}`} onClick={() => setPill(undefined)}>
          All Events ({pillCounts.events ?? 0})
        </button>
        {EVENT_CATEGORY_SLUGS.map((slug) => (
          <button key={slug} className={`tab ${categorySlug === slug ? 'active' : ''}`} onClick={() => setPill(slug)}>
            {EVENT_CATEGORIES[slug].pillLabel} ({pillCounts[slug] ?? 0})
          </button>
        ))}
        <span className="muted" style={{ marginLeft: 'auto', fontSize: 12.5 }}>{filteredEvents.length} found</span>
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
                  <SortableTh label="Event Name" sortKey="name" activeKey={eventSort} dir={eventSortDir} onSort={toggleEventSort} />
                  <SortableTh label="Start Date & Time" sortKey="start" activeKey={eventSort} dir={eventSortDir} onSort={toggleEventSort} />
                  <SortableTh label="End Date & Time" sortKey="end" activeKey={eventSort} dir={eventSortDir} onSort={toggleEventSort} />
                  <SortableTh label="Location" sortKey="location" activeKey={eventSort} dir={eventSortDir} onSort={toggleEventSort} />
                  <th>Type</th>
                  <SortableTh label="Auctions" sortKey="count" activeKey={eventSort} dir={eventSortDir} onSort={toggleEventSort} />
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
                    <td>
                      <button
                        className={eventStatus(e) === 'LIVE' ? 'btn sm' : 'btn ghost sm'}
                        onClick={() => {
                          const p = new URLSearchParams(params)
                          p.set('event', e.id)
                          p.set('itab', eventStatus(e) === 'UPCOMING' ? 'SCHEDULED' : eventStatus(e) === 'CLOSED' ? 'CLOSED' : 'OPEN')
                          setParams(p, { replace: true })
                        }}
                      >
                        {eventStatus(e) === 'LIVE' ? 'Bid Now' : 'View Event'}
                      </button>
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
