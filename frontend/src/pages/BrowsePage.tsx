import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { getAuctions, getCategories, getCategoryFilters, errorMessage, type Auction, type Category, type CategoryFilter } from '../api'
import { addMultiBid, removeMultiBid, getMultiBidIds, money, tierMeets, effectiveStatus, MULTI_KEY } from '../util'
import { type BrowseFilter, filterParam, matchesFilter } from '../browseFilters'
import { EVENT_CATEGORY_SLUGS } from '../eventCategories'
import { useAuth } from '../auth'
import { useCachedFetch } from '../useCachedFetch'
import AuctionCard from '../components/AuctionCard'
import AuctionListTable from '../components/AuctionListTable'
import EventsBrowse from '../components/EventsBrowse'
import FilterPill from '../components/FilterPill'
import FilterModal, { CheckboxListBody } from '../components/FilterModal'
import { AuctionGridSkeleton, SkeletonTableRows } from '../components/Skeleton'

const STATUS = ['OPEN', 'SCHEDULED', 'CLOSED']
// Auctions that ended without a sale (reserve not met) or were pulled by an admin are still "over" —
// group them under the Closed tab so nothing silently disappears now that there's no All tab to catch them.
const CLOSED_LIKE = ['CLOSED', 'UNSOLD', 'CANCELLED']
const SLABEL: Record<string, string> = { OPEN: 'Live', SCHEDULED: 'Upcoming', CLOSED: 'Closed' }
const CONDITIONS = ['', 'NEW', 'USED', 'REFURBISHED', 'FOR_PARTS']
const TIERS = ['', 'SILVER', 'GOLD', 'DIAMOND']
const TIER_LABEL: Record<string, string> = { SILVER: 'Silver & below', GOLD: 'Gold & below', DIAMOND: 'Diamond & below' }

interface MoreFiltersValue { condition: string; attrs: Record<string, string> }

interface Chip {
  key: string
  label: string
  value: string
  onRemove: () => void
}

export default function BrowsePage() {
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  // /swipe-stock renders this same page, locked down to the Swipe Stock platform seller's own
  // listings — everything else (filters, layout, grid) is identical to the normal auctions page.
  const isSwipeStockView = location.pathname === '/swipe-stock'
  const [params, setParams] = useSearchParams()
  const [error, setError] = useState('')
  const [, setTick] = useState(0)
  // Cached: the same 'auctions' key is shared with /swipe-stock (identical fetch, filtered
  // client-side either way) and with AuctionDetailPage, so switching between them, or coming back
  // to browse after viewing one item, is instant instead of re-paying the full fetch every time.
  const { data: all = [], loading } = useCachedFetch<Auction[]>('auctions', getAuctions, {
    onError: (e) => setError(errorMessage(e)),
  })
  const { data: cats = [] } = useCachedFetch<Category[]>('categories', getCategories, { maxAgeMs: 5 * 60_000 })
  const [multiIds, setMultiIds] = useState<string[]>(getMultiBidIds())

  const toggleMulti = (id: string) => {
    setMultiIds(multiIds.includes(id) ? removeMultiBid(id) : addMultiBid(id))
  }

  // Cross-tab sync: the wishlist is plain localStorage with no listener anywhere, so a change made in
  // another tab/iframe (see FloatingTabsContext) never reached an already-open page.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== null && e.key !== MULTI_KEY) return
      setMultiIds(getMultiBidIds())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const status = params.get('status') || 'OPEN'
  const q = params.get('q') || ''
  const categoryParam = params.get('category') || ''
  const categorySlugs = useMemo(() => categoryParam.split(',').filter(Boolean), [categoryParam])
  // Bank Vehicles / Insurance / Premium / Auto (+ the "events" pseudo-slug for "All Events") use the
  // CarTrade-style Events browsing UI instead of the flat auction grid below. Reached only via direct
  // links (e.g. HomePage chips) that set a single event-category slug — the new multi-select Category
  // modal below excludes these slugs entirely, so this check never fires from that modal.
  const isEventsView = categorySlugs.length === 1 && (categorySlugs[0] === 'events' || EVENT_CATEGORY_SLUGS.includes(categorySlugs[0]))
  const condition = params.get('condition') || ''
  const priceMin = params.get('min') || ''
  const priceMax = params.get('max') || ''
  const stateParam = params.get('state') || ''
  const stateSel = useMemo(() => stateParam.split(',').filter(Boolean), [stateParam])
  const cityParam = params.get('city') || ''
  const citySel = useMemo(() => cityParam.split(',').filter(Boolean), [cityParam])
  const tier = params.get('tier') || ''
  const view = params.get('view') === 'list' ? 'list' : 'catalogue'

  const setParam = (k: string, v: string) => {
    const p = new URLSearchParams(params)
    if (v) p.set(k, v); else p.delete(k)
    setParams(p, { replace: true })
  }
  const setMultiParam = (k: string, values: string[]) => setParam(k, values.join(','))

  const hasCatParams = [...params.keys()].some((k) => k.startsWith('a_'))
  const hasFilters = Boolean(
    q || categorySlugs.length || condition || priceMin || priceMax || stateSel.length || citySel.length || tier || hasCatParams,
  )
  const clearFilters = () => {
    const p = new URLSearchParams(params)
    ;['q', 'category', 'condition', 'min', 'max', 'state', 'city', 'tier'].forEach((k) => p.delete(k))
    ;[...p.keys()].filter((k) => k.startsWith('a_')).forEach((k) => p.delete(k))
    setParams(p, { replace: true })
  }

  useEffect(() => { const t = setInterval(() => setTick((x) => x + 1), 1000); return () => clearInterval(t) }, [])

  const catNameBySlug = useMemo(() => {
    const m: Record<string, string> = {}
    cats.forEach((c) => { m[c.slug] = c.name })
    return m
  }, [cats])
  const nameToSlug = useMemo(() => {
    const m: Record<string, string> = {}
    cats.forEach((c) => { m[c.name] = c.slug })
    return m
  }, [cats])
  // Event-pseudo-categories (Bank Vehicles/Insurance/Premium/Auto) stay reachable only via their
  // existing entry points, not this flat multi-select — mixing them with flat categories doesn't map
  // onto a single results grid.
  const categoryOptions = useMemo(
    () => cats.filter((c) => !EVENT_CATEGORY_SLUGS.includes(c.slug)).map((c) => c.name),
    [cats],
  )
  const selectedCatNames = useMemo(
    () => categorySlugs.map((s) => catNameBySlug[s]).filter((n): n is string => Boolean(n)),
    [categorySlugs, catNameBySlug],
  )
  // Dynamic per-category attribute filters only make sense when exactly one category is selected.
  const selectedCatName = categorySlugs.length === 1 ? catNameBySlug[categorySlugs[0]] : ''
  const selectedCategoryId = categorySlugs.length === 1 ? cats.find((c) => c.slug === categorySlugs[0])?.id : undefined
  const brandOptions = useMemo(() => {
    if (!selectedCatName) return []
    const set = new Set<string>()
    all.forEach((a) => { if (a.categoryName === selectedCatName && a.brand) set.add(a.brand) })
    return [...set].sort()
  }, [all, selectedCatName])

  // Live per-category filters, fetched from the backend (real distinct attribute values, not a hardcoded
  // map) and cached per category — reopening the same category's filter panel is instant thereafter.
  const { data: liveFilters = [] } = useCachedFetch<CategoryFilter[]>(
    selectedCategoryId ? `category-filters:${selectedCategoryId}` : null,
    () => getCategoryFilters(selectedCategoryId as string),
    { maxAgeMs: 5 * 60_000 },
  )
  const catFilters: BrowseFilter[] = useMemo(() => {
    const list: BrowseFilter[] = []
    if (brandOptions.length > 0) list.push({ key: 'brand', label: 'Brand', type: 'brand', options: brandOptions })
    liveFilters.forEach((f) => list.push({ key: f.key, label: f.label, type: f.valueType === 'NUMBER' ? 'NUMBER' : 'ENUM', options: f.options }))
    return list
  }, [brandOptions, liveFilters])

  const stateOptions = useMemo(
    () => [...new Set(all.map((a) => a.state).filter((s): s is string => Boolean(s)))].sort(),
    [all],
  )
  const cityOptions = useMemo(() => {
    const pool = stateSel.length ? all.filter((a) => a.state && stateSel.includes(a.state)) : all
    return [...new Set(pool.map((a) => a.city).filter((c): c is string => Boolean(c)))].sort()
  }, [all, stateSel])

  const filtered = all.filter((a) => {
    if (isSwipeStockView && !a.swipeStock) return false
    const es = effectiveStatus(a)
    if (status === 'CLOSED' ? !CLOSED_LIKE.includes(es) : es !== status) return false
    if (selectedCatNames.length > 0 && !selectedCatNames.includes(a.categoryName)) return false
    if (condition && a.condition !== condition) return false
    const price = a.currentHighestBid ?? a.basePrice
    if (priceMin && price < Number(priceMin)) return false
    if (priceMax && price > Number(priceMax)) return false
    if (stateSel.length > 0 && !(a.state && stateSel.includes(a.state))) return false
    if (citySel.length > 0 && !(a.city && citySel.includes(a.city))) return false
    // Cumulative like real subscriber access: picking "Gold" shows Silver + Gold items (everything a
    // Gold subscriber could actually see), not just items requiring exactly Gold.
    if (tier && !tierMeets(tier, a.requiredTier)) return false
    if (q) {
      const hay = `${a.title} ${a.categoryName} ${a.brand ?? ''} ${a.city ?? ''}`.toLowerCase()
      if (!hay.includes(q.toLowerCase())) return false
    }
    // Category-specific attribute filters.
    for (const f of catFilters) {
      const v = params.get(filterParam(f.key))
      if (v && !matchesFilter(a, f, v)) return false
    }
    return true
  })

  const appliedMoreFilters: MoreFiltersValue = useMemo(() => {
    const attrs: Record<string, string> = {}
    catFilters.forEach((f) => { attrs[f.key] = params.get(filterParam(f.key)) || '' })
    return { condition, attrs }
  }, [condition, catFilters, params])

  const applyMoreFilters = (v: MoreFiltersValue) => {
    const p = new URLSearchParams(params)
    if (v.condition) p.set('condition', v.condition); else p.delete('condition')
    catFilters.forEach((f) => {
      const pkey = filterParam(f.key)
      const val = v.attrs[f.key]
      if (val) p.set(pkey, val); else p.delete(pkey)
    })
    setParams(p, { replace: true })
  }

  const bidRangeChipValue = () => {
    if (priceMin && priceMax) return `${money(Number(priceMin))} – ${money(Number(priceMax))}`
    if (priceMin) return `≥ ${money(Number(priceMin))}`
    return `≤ ${money(Number(priceMax))}`
  }

  const chips: Chip[] = useMemo(() => {
    const list: Chip[] = []
    if (q) list.push({ key: 'q', label: 'Keyword', value: q, onRemove: () => setParam('q', '') })
    categorySlugs.forEach((slug) => list.push({
      key: `category-${slug}`, label: 'Category', value: catNameBySlug[slug] ?? slug,
      onRemove: () => setMultiParam('category', categorySlugs.filter((s) => s !== slug)),
    }))
    stateSel.forEach((s) => list.push({
      key: `state-${s}`, label: 'State', value: s,
      onRemove: () => setMultiParam('state', stateSel.filter((x) => x !== s)),
    }))
    citySel.forEach((c) => list.push({
      key: `city-${c}`, label: 'City', value: c,
      onRemove: () => setMultiParam('city', citySel.filter((x) => x !== c)),
    }))
    if (priceMin || priceMax) list.push({
      key: 'bid-range', label: 'Bid Range', value: bidRangeChipValue(),
      onRemove: () => { setParam('min', ''); setParam('max', '') },
    })
    if (condition) list.push({
      key: 'condition', label: 'Condition', value: condition.replace('_', ' '),
      onRemove: () => setParam('condition', ''),
    })
    if (tier) list.push({
      key: 'tier', label: 'Tier', value: TIER_LABEL[tier] ?? tier,
      onRemove: () => setParam('tier', ''),
    })
    catFilters.forEach((f) => {
      const pkey = filterParam(f.key)
      const v = params.get(pkey)
      if (v) list.push({ key: pkey, label: f.label, value: v, onRemove: () => setParam(pkey, '') })
    })
    return list
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, categorySlugs, stateSel, citySel, priceMin, priceMax, condition, tier, catFilters, params, catNameBySlug])

  if (isEventsView) {
    // Always table-based (event list, then an event's item list) — same "no left-right scrolling"
    // treatment as the flat browse's own list view below.
    return (
      <div className="container container-wide">
        <EventsBrowse categorySlug={categorySlugs[0] === 'events' ? undefined : categorySlugs[0]} />
      </div>
    )
  }

  return (
    <div className={`container${view === 'list' ? ' container-wide' : ''}`}>
      <div className="section-head">
        <div className="section-head-tabs">
          {isSwipeStockView && <span className="eyebrow">Swipe Stock</span>}
          <div className="tabs status-tabs">
            {STATUS.map((s) => (
              <button key={s} className={`tab ${status === s ? 'active' : ''}`} onClick={() => setParam('status', s)}>{SLABEL[s]}</button>
            ))}
          </div>
        </div>
        {isAuthenticated && (
          <Link to="/wishlist" className="btn ghost sm">♡ Wishlist{multiIds.length > 0 ? ` (${multiIds.length})` : ''}</Link>
        )}
      </div>

      <div className="filter-pill-row">
        <FilterPill label="Keyword" active={Boolean(q)}>
          <div className="fgroup">
            <small>Keyword</small>
            <input className="search-glow" value={q} onChange={(e) => setParam('q', e.target.value)} placeholder="Title, brand, city…" autoFocus />
          </div>
        </FilterPill>

        <FilterModal<string[]>
          label="Category" count={categorySlugs.length}
          applied={categorySlugs} emptyValue={[]}
          onApply={(v) => setMultiParam('category', v)}
          renderBody={(staged, setStaged) => (
            <CheckboxListBody
              options={categoryOptions}
              selected={staged.map((slug) => catNameBySlug[slug] ?? slug)}
              onChange={(names) => setStaged(names.map((n) => nameToSlug[n]).filter(Boolean))}
              searchPlaceholder="Search Categories"
            />
          )}
        />
        <FilterModal<string[]>
          label="State" count={stateSel.length}
          applied={stateSel} emptyValue={[]}
          onApply={(v) => setMultiParam('state', v)}
          renderBody={(staged, setStaged) => (
            <CheckboxListBody options={stateOptions} selected={staged} onChange={setStaged} searchPlaceholder="Search States" />
          )}
        />
        <FilterModal<string[]>
          label="City" count={citySel.length}
          applied={citySel} emptyValue={[]}
          onApply={(v) => setMultiParam('city', v)}
          renderBody={(staged, setStaged) => (
            <CheckboxListBody options={cityOptions} selected={staged} onChange={setStaged} searchPlaceholder="Search Cities" />
          )}
        />
        <FilterModal<{ min: string; max: string }>
          label="Bid Range" active={Boolean(priceMin || priceMax)}
          applied={{ min: priceMin, max: priceMax }} emptyValue={{ min: '', max: '' }}
          onApply={(v) => { setParam('min', v.min); setParam('max', v.max) }}
          renderBody={(staged, setStaged) => (
            <div className="price-range">
              <input type="number" value={staged.min} onChange={(e) => setStaged({ ...staged, min: e.target.value })} placeholder="Min" />
              <span>–</span>
              <input type="number" value={staged.max} onChange={(e) => setStaged({ ...staged, max: e.target.value })} placeholder="Max" />
            </div>
          )}
        />

        <FilterPill label="Tier" active={Boolean(tier)}>
          <div className="fgroup">
            <small>Subscription tier</small>
            <select value={tier} onChange={(e) => setParam('tier', e.target.value)}>
              {TIERS.map((t) => <option key={t} value={t}>{t ? TIER_LABEL[t] : 'Any'}</option>)}
            </select>
          </div>
        </FilterPill>

        <FilterModal<MoreFiltersValue>
          label="More Filters" active={Boolean(condition) || hasCatParams}
          applied={appliedMoreFilters} emptyValue={{ condition: '', attrs: {} }}
          onApply={applyMoreFilters}
          renderBody={(staged, setStaged) => (
            <>
              <div className="fgroup">
                <small>Condition</small>
                <select value={staged.condition} onChange={(e) => setStaged({ ...staged, condition: e.target.value })}>
                  {CONDITIONS.map((c) => <option key={c} value={c}>{c ? c.replace('_', ' ') : 'Any'}</option>)}
                </select>
              </div>
              {catFilters.length > 0 && (
                <div className="cat-filters">
                  <div className="cat-filters-head">{selectedCatName} filters</div>
                  {catFilters.map((f) => {
                    const val = staged.attrs[f.key] || ''
                    return (
                      <div className="fgroup" key={f.key}>
                        <small>{f.label}</small>
                        {f.type === 'NUMBER' ? (
                          <input
                            type="number" value={val} placeholder="Any"
                            onChange={(e) => setStaged({ ...staged, attrs: { ...staged.attrs, [f.key]: e.target.value } })}
                          />
                        ) : (
                          <select
                            value={val}
                            onChange={(e) => setStaged({ ...staged, attrs: { ...staged.attrs, [f.key]: e.target.value } })}
                          >
                            <option value="">Any</option>
                            {f.options.map((o) => <option key={o} value={o}>{o}</option>)}
                          </select>
                        )}
                      </div>
                    )
                  })}
                </div>
              )}
              {categorySlugs.length !== 1 && (
                <p className="muted" style={{ fontSize: 12 }}>Select exactly one category to see its specific filters.</p>
              )}
            </>
          )}
        />
      </div>

      {hasFilters && (
        <div className="chip-row">
          <button type="button" className="chip-reset-all" onClick={clearFilters}>Reset All</button>
          {chips.map((c) => (
            <div className="chip" key={c.key}>
              <div className="chip-top">
                <span className="chip-value">{c.value}</span>
                <button type="button" className="chip-remove" onClick={c.onRemove} aria-label={`Remove ${c.label} filter`}>✕</button>
              </div>
              <span className="chip-label">{c.label}</span>
            </div>
          ))}
        </div>
      )}

      <div className="results">
        {error && <div className="error">{error}</div>}
        {!loading && !error && (
          <div className="results-bar">
            <span className="muted">{filtered.length} {filtered.length === 1 ? 'auction' : 'auctions'}</span>
            <div className="view-toggle">
              <button
                type="button" className={view === 'catalogue' ? 'active' : ''}
                onClick={() => setParam('view', '')} title="Catalogue view" aria-label="Catalogue view"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <rect x="3" y="3" width="8" height="8" rx="1.5" /><rect x="13" y="3" width="8" height="8" rx="1.5" />
                  <rect x="3" y="13" width="8" height="8" rx="1.5" /><rect x="13" y="13" width="8" height="8" rx="1.5" />
                </svg>
              </button>
              <button
                type="button" className={view === 'list' ? 'active' : ''}
                onClick={() => setParam('view', 'list')} title="List view" aria-label="List view"
              >
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="4" y1="6" x2="20" y2="6" /><line x1="4" y1="12" x2="20" y2="12" /><line x1="4" y1="18" x2="20" y2="18" />
                </svg>
              </button>
            </div>
          </div>
        )}
        {loading ? (
          view === 'list' ? (
            <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
              <div style={{ overflowX: 'auto', padding: 16 }}>
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Image</th><th>Details</th><th>Start Time</th><th>End Time</th>
                      <th>Base Price</th><th>Current Bid</th><th>Bids</th><th>Bids Left</th><th>Action</th>
                    </tr>
                  </thead>
                  <tbody><SkeletonTableRows rows={5} cols={9} /></tbody>
                </table>
              </div>
            </div>
          ) : (
            <AuctionGridSkeleton />
          )
        ) : filtered.length === 0 ? (
          <p className="muted">No auctions match your filters.</p>
        ) : view === 'list' ? (
          <AuctionListTable auctions={filtered} multiIds={multiIds} onToggleTray={toggleMulti} />
        ) : (
          <div className="grid">
            {filtered.map((a) => (
              <AuctionCard key={a.id} auction={a} inTray={multiIds.includes(a.id)} onToggleTray={toggleMulti} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
