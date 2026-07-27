import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { getAuction, registerToBid, placeBid, errorMessage, API_BASE, type Auction } from '../api'
import { useAuth } from '../auth'
import { useWallet } from '../WalletContext'
import { getMultiBidIds, removeMultiBid, formatCountdown, msUntil, money, cardImage, effectiveStatus, MULTI_KEY } from '../util'
import TermsModal from '../components/TermsModal'

interface Tile { auction: Auction; registered: boolean; amount: string; msg: string; err: string; flash: boolean }

export default function MultiBiddingPage() {
  const { isAuthenticated } = useAuth()
  const { refreshWallet } = useWallet()
  const [ids, setIds] = useState<string[]>(getMultiBidIds())
  const [tiles, setTiles] = useState<Record<string, Tile>>({})
  const [, setTick] = useState(0)
  const [termsForId, setTermsForId] = useState<string | null>(null)

  // Load / prune auctions whenever the id list changes.
  useEffect(() => {
    ids.forEach((id) => {
      getAuction(id)
        .then((a) => setTiles((t) => (t[id]
          ? { ...t, [id]: { ...t[id], auction: a, registered: t[id].registered || a.registered } }
          : { ...t, [id]: { auction: a, registered: a.registered, amount: '', msg: '', err: '', flash: false } })))
        .catch(() => {})
    })
    setTiles((t) => {
      const copy = { ...t }
      Object.keys(copy).forEach((k) => { if (!ids.includes(k)) delete copy[k] })
      return copy
    })
  }, [ids])

  // One STOMP connection; subscribe to every tracked auction.
  useEffect(() => {
    if (ids.length === 0) return
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_BASE}/ws`),
      reconnectDelay: 4000,
      onConnect: () => {
        ids.forEach((id) =>
          client.subscribe(`/topic/auctions/${id}`, (frame) => {
            const b = JSON.parse(frame.body) as { currentHighestBid: number; currentEndTime: string; bidCount: number }
            setTiles((t) => (t[id] ? { ...t, [id]: { ...t[id], auction: { ...t[id].auction, currentHighestBid: b.currentHighestBid, currentEndTime: b.currentEndTime, bidCount: b.bidCount }, flash: true } } : t))
            setTimeout(() => setTiles((t) => (t[id] ? { ...t, [id]: { ...t[id], flash: false } } : t)), 800)
          }),
        )
      },
    })
    client.activate()
    return () => { void client.deactivate() }
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
  const upd = (id: string, patch: Partial<Tile>) => setTiles((t) => (t[id] ? { ...t, [id]: { ...t[id], ...patch } } : t))

  const doRegister = async (id: string) => {
    upd(id, { err: '', msg: '' })
    try { const r = await registerToBid(id); upd(id, { registered: true, msg: r.message }); refreshWallet() }
    catch (e) {
      const m = errorMessage(e)
      if (m.toLowerCase().includes('already registered')) upd(id, { registered: true, msg: 'Registered' })
      else upd(id, { err: m })
    }
  }
  const proceedBid = async (id: string) => {
    const tile = tiles[id]; if (!tile) return
    const amt = Number(tile.amount)
    if (!window.confirm(`Place a bid of ${money(amt)} on "${tile.auction.title}"?`)) return
    upd(id, { err: '', msg: '' })
    try {
      await placeBid(id, amt); upd(id, { amount: '', msg: 'Bid placed' })
      const a = await getAuction(id); upd(id, { auction: a })
      refreshWallet()
    }
    catch (e) { upd(id, { err: errorMessage(e) }) }
  }
  // Terms & Conditions are gated on the bidder's first bid on THIS auction (auction.yourBid ==
  // null) — every increment bid after that goes straight to the normal confirm dialog.
  const doBid = (id: string) => {
    const tile = tiles[id]; if (!tile) return
    if (tile.auction.yourBid == null) { setTermsForId(id); return }
    void proceedBid(id)
  }

  const list = ids.map((id) => tiles[id]).filter(Boolean) as Tile[]

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
          {list.map((tile) => {
            const a = tile.auction
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
                ) : !tile.registered ? (
                  <button className="btn sm" onClick={() => doRegister(a.id)}>Register — hold {money(a.basePrice)}</button>
                ) : a.bidsRemaining === 0 ? (
                  <span className="muted">No bids left</span>
                ) : (
                  <div className="bidrow">
                    <input type="number" placeholder={`min ${minNext}`} value={tile.amount} onChange={(e) => upd(a.id, { amount: e.target.value })} />
                    <button className="btn sm" onClick={() => doBid(a.id)}>Bid</button>
                  </div>
                )}
                {tile.msg && <div className="ok" style={{ marginTop: 0, padding: '6px 10px' }}>{tile.msg}</div>}
                {tile.err && <div className="error" style={{ marginTop: 0, padding: '6px 10px' }}>{tile.err}</div>}
              </div>
            )
          })}
        </div>
      )}

      {termsForId && tiles[termsForId] && (
        <TermsModal
          title={tiles[termsForId].auction.title}
          onAccept={() => { const id = termsForId; setTermsForId(null); void proceedBid(id) }}
          onCancel={() => setTermsForId(null)}
        />
      )}
    </div>
  )
}
