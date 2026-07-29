import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAuctions, placeBid, errorMessage, type Auction } from '../api'
import { useAuth } from '../auth'
import { useWallet } from '../WalletContext'
import { useStomp } from '../StompContext'
import { useCachedFetch, updateCache } from '../useCachedFetch'
import { getMultiBidIds, removeMultiBid, formatCountdown, msUntil, money, cardImage, effectiveStatus, MULTI_KEY } from '../util'
import TermsModal from '../components/TermsModal'

/** Per-tile UI-only state (the bid form + its own result) — kept separate from auction data, which
 *  now lives in the shared 'auctions' cache instead of a per-tile fetch (see below). */
interface TileUi { amount: string; msg: string; err: string; flash: boolean }
const EMPTY_UI: TileUi = { amount: '', msg: '', err: '', flash: false }

export default function MultiBiddingPage() {
  const { isAuthenticated } = useAuth()
  const { refreshWallet } = useWallet()
  const { subscribe } = useStomp()
  const [ids, setIds] = useState<string[]>(getMultiBidIds())
  // Same 'auctions' cache key BrowsePage/EventsBrowse use — one shared fetch instead of one
  // GET /api/auctions/{id} per wishlist tile (each of those paid ~7 queries server-side; a dozen
  // wishlisted items used to mean a dozen full round trips just to render this page). Landing here
  // right after Browse is now instant since the list is already cached.
  const { data: allAuctions = [] } = useCachedFetch<Auction[]>('auctions', getAuctions, {})
  const auctionsById = useMemo(() => {
    const m: Record<string, Auction> = {}
    allAuctions.forEach((a) => { m[a.id] = a })
    return m
  }, [allAuctions])
  const [ui, setUi] = useState<Record<string, TileUi>>({})
  const [, setTick] = useState(0)
  const [termsForId, setTermsForId] = useState<string | null>(null)

  // Drop UI state for anything removed from the wishlist.
  useEffect(() => {
    setUi((u) => {
      const copy = { ...u }
      Object.keys(copy).forEach((k) => { if (!ids.includes(k)) delete copy[k] })
      return copy
    })
  }, [ids])

  // Subscribe to every tracked auction on the one shared connection (StompContext) and patch the
  // shared 'auctions' cache directly (updateCache, see useCachedFetch.ts) — any other mounted page
  // reading that same cache (Browse, Events) picks the live update up too, instead of this page
  // keeping its own private copy and its own socket.
  useEffect(() => {
    const unsubscribes = ids.map((id) =>
      subscribe(`/topic/auctions/${id}`, (body) => {
        const b = JSON.parse(body) as { currentHighestBid: number; currentEndTime: string; bidCount: number }
        updateCache<Auction[]>('auctions', (list) =>
          list.map((a) => (a.id === id ? { ...a, currentHighestBid: b.currentHighestBid, currentEndTime: b.currentEndTime, bidCount: b.bidCount } : a)))
        setUi((u) => ({ ...u, [id]: { ...(u[id] ?? EMPTY_UI), flash: true } }))
        setTimeout(() => setUi((u) => (u[id] ? { ...u, [id]: { ...u[id], flash: false } } : u)), 800)
      }),
    )
    return () => unsubscribes.forEach((fn) => fn())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ids])

  useEffect(() => { const t = setInterval(() => setTick((x) => x + 1), 1000); return () => clearInterval(t) }, [])

  // Cross-tab sync — see the identical comment in BrowsePage.tsx.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== null && e.key !== MULTI_KEY) return
      setIds(getMultiBidIds())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const remove = (id: string) => setIds(removeMultiBid(id))
  const updUi = (id: string, patch: Partial<TileUi>) => setUi((u) => ({ ...u, [id]: { ...(u[id] ?? EMPTY_UI), ...patch } }))

  const proceedBid = async (id: string) => {
    const auction = auctionsById[id]; if (!auction) return
    const tileUi = ui[id] ?? EMPTY_UI
    const amt = Number(tileUi.amount)
    if (!window.confirm(`Place a bid of ${money(amt)} on "${auction.title}"?`)) return
    updUi(id, { err: '', msg: '' })
    try {
      // currentHighestBid/isWinner come from the server's real post-bid state, not inferred from
      // the amount just sent — same reasoning as AuctionCard.tsx's optimistic patch. bidCount is
      // deliberately left untouched here (unlike AuctionCard's own override): this page's own STOMP
      // subscription below (subscribe(`/topic/auctions/${id}`, ...)) patches the same 'auctions'
      // cache entry with the server's authoritative bidCount for every bid on a tracked auction,
      // including this one — usually only moments after this response lands. Incrementing it here
      // too would double-count whenever that broadcast wins the race and arrives first (it patches
      // bidCount to the true post-bid value, then this handler would add another +1 on top of it).
      const res = await placeBid(id, amt)
      updUi(id, { amount: '', msg: 'Bid placed' })
      updateCache<Auction[]>('auctions', (list) =>
        list.map((a) => (a.id === id
          ? { ...a, currentHighestBid: res.currentHighestBid, yourBid: amt, currentEndTime: res.currentEndTime, isWinner: res.leading }
          : a)))
      refreshWallet()
    }
    catch (e) { updUi(id, { err: errorMessage(e) }) }
  }
  // Terms & Conditions are gated on the bidder's first bid on THIS auction (auction.yourBid ==
  // null) — every increment bid after that goes straight to the normal confirm dialog.
  const doBid = (id: string) => {
    const auction = auctionsById[id]; if (!auction) return
    if (auction.yourBid == null) { setTermsForId(id); return }
    void proceedBid(id)
  }

  const list = ids
    .map((id) => (auctionsById[id] ? { auction: auctionsById[id], ui: ui[id] ?? EMPTY_UI } : null))
    .filter((x): x is { auction: Auction; ui: TileUi } => x != null)

  if (!isAuthenticated) {
    return <div className="container"><div className="card" style={{ maxWidth: 460 }}>Please <Link to="/login">sign in</Link> to view your wishlist.</div></div>
  }

  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">Wishlist</h1>
        <Link to="/auctions" className="btn ghost sm">＋ Add auctions</Link>
      </div>
      <p className="muted" style={{ marginTop: 0 }}>
        Track and bid on several auctions at once — each tile updates live. Add auctions from the{' '}
        <Link to="/auctions">Auctions</Link> page (♡ Wishlist).
      </p>

      {list.length === 0 ? (
        <p className="muted">Your wishlist is empty.</p>
      ) : (
        <div className="multi-grid">
          {list.map(({ auction: a, ui: tile }) => {
            const current = a.currentHighestBid ?? a.basePrice
            // What THIS bidder must clear next — their own previous bid on this item, not
            // necessarily the current leader's (see BidService.placeBid for why those differ).
            const minNext = a.yourBid != null ? a.yourBid + 1 : a.basePrice
            // Status only flips OPEN -> CLOSED/UNSOLD on a scheduler tick, so a raw `status` read can
            // lag behind the real end time — compute what it effectively is right now instead.
            const es = effectiveStatus(a)
            return (
              <div key={a.id} className={`mtile ${tile.flash ? 'flash' : ''}`}>
                <Link to={`/auctions/${a.id}`} className="mthumb">
                  <img src={cardImage(a)} alt={a.title} loading="lazy"
                    onError={(e) => { (e.currentTarget as HTMLImageElement).style.visibility = 'hidden' }} />
                </Link>
                <div className="row"><span className="cat">{a.categoryName}</span><span className="x" onClick={() => remove(a.id)}>×</span></div>
                <Link to={`/auctions/${a.id}`}><h3 style={{ margin: 0 }}>{a.title}</h3></Link>
                <div className="row">
                  <span className={`badge ${es}`}>{es}</span>
                  {es === 'OPEN' && <span className="countdown">{formatCountdown(msUntil(a.currentEndTime))}</span>}
                </div>
                <div className="row">
                  <span className="muted">{a.currentHighestBid != null ? 'Current' : 'Starting'} · {a.bidCount} bids</span>
                  <span className="price">{money(current)}</span>
                </div>
                {a.bidsRemaining != null && (
                  <div className="muted" style={{ fontSize: 12, ...(a.bidsRemaining === 0 ? { color: 'var(--red-2)', fontWeight: 700 } : {}) }}>
                    {a.bidsRemaining === 0 ? 'No bids left for you on this item' : `${a.bidsRemaining} bid${a.bidsRemaining === 1 ? '' : 's'} left for you`}
                  </div>
                )}
                {es !== 'OPEN' ? (
                  <span className="muted">Bidding closed</span>
                ) : a.bidsRemaining === 0 ? (
                  <span className="muted">No bids left</span>
                ) : (
                  <div className="bidrow">
                    <input type="number" placeholder={a.yourBid != null ? `min ${minNext}` : `suggested ${minNext}`} value={tile.amount} onChange={(e) => updUi(a.id, { amount: e.target.value })} />
                    <button className="btn sm" onClick={() => doBid(a.id)}>Bid</button>
                  </div>
                )}
                {tile.msg && <div className="ok" style={{ marginTop: 0, padding: '6px 10px' }}>{tile.msg}</div>}
                {tile.err && (
                  <div className="error" style={{ marginTop: 0, padding: '6px 10px' }}>
                    {tile.err}
                    {tile.err.toLowerCase().includes('credit limit') && <> — <Link to="/wallet">Top up</Link></>}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}

      {termsForId && auctionsById[termsForId] && (
        <TermsModal
          title={auctionsById[termsForId].title}
          onAccept={() => { const id = termsForId; setTermsForId(null); void proceedBid(id) }}
          onCancel={() => setTermsForId(null)}
        />
      )}
    </div>
  )
}
