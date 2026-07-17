import { useEffect, useState, type FormEvent } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import {
  getAuction, getWallet, registerToBid, placeBid, errorMessage,
  type Auction, type WalletBalance,
} from '../api'
import { useAuth } from '../auth'
import { formatCountdown, msUntil, money } from '../util'

export default function AuctionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const { isAuthenticated } = useAuth()
  const [auction, setAuction] = useState<Auction | null>(null)
  const [wallet, setWallet] = useState<WalletBalance | null>(null)
  const [registered, setRegistered] = useState(false)
  const [amount, setAmount] = useState('')
  const [error, setError] = useState('')
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)
  const [, setTick] = useState(0)

  const refresh = () => { if (id) getAuction(id).then(setAuction).catch((e) => setError(errorMessage(e))) }

  useEffect(() => { refresh() }, [id])
  useEffect(() => { if (isAuthenticated) getWallet().then(setWallet).catch(() => {}) }, [isAuthenticated])
  useEffect(() => { const t = setInterval(() => setTick((x) => x + 1), 1000); return () => clearInterval(t) }, [])

  // Live bid updates over STOMP.
  useEffect(() => {
    if (!id) return
    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 4000,
      onConnect: () => {
        client.subscribe(`/topic/auctions/${id}`, (frame) => {
          const b = JSON.parse(frame.body) as { currentHighestBid: number; currentEndTime: string; bidCount: number }
          setAuction((prev) => (prev ? { ...prev, currentHighestBid: b.currentHighestBid, currentEndTime: b.currentEndTime, bidCount: b.bidCount } : prev))
        })
      },
    })
    client.activate()
    return () => { void client.deactivate() }
  }, [id])

  const doRegister = async () => {
    if (!id) return
    setError(''); setMsg(''); setBusy(true)
    try {
      const r = await registerToBid(id)
      setRegistered(true)
      setWallet({ availableBalance: r.availableBalance, heldBalance: r.heldBalance })
      setMsg(r.message)
    } catch (e) {
      const m = errorMessage(e)
      if (m.toLowerCase().includes('already registered')) { setRegistered(true); setMsg('Already registered — place your bid.') }
      else setError(m)
    } finally { setBusy(false) }
  }

  const doBid = async (e: FormEvent) => {
    e.preventDefault()
    if (!id) return
    setError(''); setMsg(''); setBusy(true)
    try {
      await placeBid(id, Number(amount))
      setMsg('Bid placed!')
      setAmount('')
      refresh()
      getWallet().then(setWallet).catch(() => {})
    } catch (e2) {
      setError(errorMessage(e2))
    } finally { setBusy(false) }
  }

  if (!auction) return <p className="muted">Loading…</p>

  const current = auction.currentHighestBid ?? auction.basePrice
  const minNext = auction.currentHighestBid != null ? auction.currentHighestBid + 1 : auction.basePrice

  return (
    <div style={{ maxWidth: 640 }}>
      <p><Link to="/">← Back to browse</Link></p>
      <div className="card">
        <div className="row">
          <span className={`badge ${auction.status}`}>{auction.status}</span>
          {auction.status === 'OPEN' && (
            <span className="countdown"><span className="live-dot" />{formatCountdown(msUntil(auction.currentEndTime))}</span>
          )}
        </div>
        <h1>{auction.title}</h1>
        <div className="stat">
          <div><div className="k">{auction.currentHighestBid != null ? 'Current bid' : 'Starting price'}</div><div className="v">{money(current)}</div></div>
          <div><div className="k">Bids</div><div className="v">{auction.bidCount}</div></div>
        </div>

        {auction.status !== 'OPEN' ? (
          <p className="muted">Bidding is closed for this auction.</p>
        ) : !isAuthenticated ? (
          <p className="muted"><Link to="/login">Sign in</Link> to register and bid.</p>
        ) : !registered ? (
          <>
            <p className="muted">Registering places a refundable deposit hold of {money(auction.basePrice)}.</p>
            <button className="btn" onClick={doRegister} disabled={busy}>{busy ? '…' : 'Register to bid'}</button>
          </>
        ) : (
          <form onSubmit={doBid}>
            <label>Your bid (min {money(minNext)})</label>
            <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} min={minNext} />
            <div style={{ marginTop: 12 }}><button className="btn" disabled={busy}>{busy ? 'Placing…' : 'Place bid'}</button></div>
          </form>
        )}
        {msg && <div className="ok">{msg}</div>}
        {error && <div className="error">{error}</div>}
        {wallet && <p className="muted" style={{ marginTop: 16 }}>Wallet — available {money(wallet.availableBalance)}, held {money(wallet.heldBalance)}</p>}
      </div>
    </div>
  )
}
