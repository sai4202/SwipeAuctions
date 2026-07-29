import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useParams, useSearchParams, Link } from 'react-router-dom'
import {
  getAuction, placeBid, raiseDispute, completeAuctionPayment, getRegistrationFee, payRegistrationFee, errorMessage,
  type Auction,
} from '../api'
import { useAuth } from '../auth'
import { useWallet } from '../WalletContext'
import { useStomp } from '../StompContext'
import { formatCountdown, msUntil, money, cardImage, downloadValuation, isVideoUrl, tierMeets, effectiveStatus, resolveMediaUrl } from '../util'
import { useCachedFetch } from '../useCachedFetch'
import { AuctionDetailSkeleton } from '../components/Skeleton'
import TermsModal from '../components/TermsModal'
import DetailTabs from '../components/DetailTabs'

// Matches the backend's default auction.min-increment (BidService) — the smallest amount a bid must
// clear the current highest by, and the step size for the +/- bid stepper below.
const MIN_INCREMENT = 1

const STATUS_LABEL: Record<string, string> = {
  OPEN: 'Live', SCHEDULED: 'Upcoming', CLOSED: 'Closed', UNSOLD: 'Unsold', CANCELLED: 'Cancelled',
}

export default function AuctionDetailPage() {
  const { id } = useParams<{ id: string }>()
  const [searchParams] = useSearchParams()
  // Arriving via a catalog card's "Bid Now" (?bid=1) means the buyer already decided to bid — skip
  // straight to the bid/payment box instead of the full product page; "View Details" still shows
  // everything. A link below lets them reveal the gallery/specs without losing this focused view.
  const cameFromBidNow = searchParams.get('bid') === '1'
  const [showFullDetails, setShowFullDetails] = useState(!cameFromBidNow)
  const { isAuthenticated, kycCompleted, role, subscriptionTier, markRegistrationFeePaid } = useAuth()
  const isAdmin = role === 'ADMIN'
  const { wallet, refreshWallet } = useWallet()
  const { subscribe } = useStomp()
  const [error, setError] = useState('')
  // Cached per auction id — coming back to an item you already viewed (e.g. via browser back, or
  // re-opening from the wishlist) shows it instantly while a fresh fetch quietly runs behind it.
  const { data: auction, loading: auctionLoading, setData: setAuction, refresh } = useCachedFetch<Auction>(
    id ? `auction:${id}` : null,
    () => getAuction(id as string),
    { onError: (e) => setError(errorMessage(e)) },
  )
  const [amount, setAmount] = useState('')
  const [msg, setMsg] = useState('')
  const [busy, setBusy] = useState(false)
  const [, setTick] = useState(0)
  const [showDispute, setShowDispute] = useState(false)
  const [disputeReason, setDisputeReason] = useState('')
  const [disputeMsg, setDisputeMsg] = useState('')
  const [disputeBusy, setDisputeBusy] = useState(false)
  const [settleBusy, setSettleBusy] = useState(false)
  const [settleMsg, setSettleMsg] = useState('')
  const [confirmingBid, setConfirmingBid] = useState(false)
  const [showTerms, setShowTerms] = useState(false)
  const [activeImage, setActiveImage] = useState(0)
  // Registration-fee "pay to view" wall — shown in place of the whole page when getAuction() fails
  // with the fee-required error (see the `!auction` early return below).
  const [registrationFee, setRegistrationFee] = useState<number | null>(null)
  const [payingFee, setPayingFee] = useState(false)
  const [feeError, setFeeError] = useState('')
  // Insufficient-credit-limit is surfaced as a popup (not an inline banner/pre-emptive block) —
  // the bid form always shows once fee-paid/KYC/tier pass; only a failed bid submission triggers this.
  const [creditLimitError, setCreditLimitError] = useState('')
  const actionRef = useRef<HTMLDivElement>(null)
  const amountRef = useRef<HTMLInputElement>(null)
  const jumpedToBid = useRef(false)

  useEffect(() => { setActiveImage(0); jumpedToBid.current = false }, [id])
  useEffect(() => { const t = setInterval(() => setTick((x) => x + 1), 1000); return () => clearInterval(t) }, [])

  // "Bid Now" (vs. "View Details") on the card links here with ?bid=1[&amount=N] — jump straight to
  // the bidding action (carrying over whatever amount the buyer already dialed in on the card's own
  // stepper) instead of making them re-pick it here.
  useEffect(() => {
    if (jumpedToBid.current) return
    if (searchParams.get('bid') !== '1') return
    if (!auction) return
    jumpedToBid.current = true
    // The amount is a plain URL query param, editable by anyone — only accept it if it's actually a
    // positive number, so a hand-edited/malformed value (e.g. ?amount=abc) can't flow into the
    // confirm modal or placeBid() as NaN.
    const carriedAmount = searchParams.get('amount')
    const parsedAmount = carriedAmount ? Number(carriedAmount) : NaN
    if (Number.isFinite(parsedAmount) && parsedAmount > 0) setAmount(carriedAmount as string)
    actionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    amountRef.current?.focus()
  }, [auction, searchParams])

  // Live bid updates over STOMP — rides the one shared connection (StompContext) instead of
  // opening its own socket.
  useEffect(() => {
    if (!id) return
    return subscribe(`/topic/auctions/${id}`, (body) => {
      const b = JSON.parse(body) as { currentHighestBid: number; currentEndTime: string; bidCount: number }
      setAuction((prev) => (prev ? { ...prev, currentHighestBid: b.currentHighestBid, currentEndTime: b.currentEndTime, bidCount: b.bidCount } : prev))
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  useEffect(() => { getRegistrationFee().then(setRegistrationFee).catch(() => {}) }, [])

  const needsKyc = (msg: string) => msg.toLowerCase().includes('kyc')

  const doPayFee = async () => {
    setFeeError(''); setPayingFee(true)
    try {
      await payRegistrationFee()
      markRegistrationFeePaid()
      refresh()
    } catch (e) {
      setFeeError(errorMessage(e))
    } finally { setPayingFee(false) }
  }

  // First click (field empty) fills in the current minimum valid bid; every click after that just
  // steps by the minimum increment, floored so it can never drop below that minimum.
  const bumpAmount = (minNext: number, delta: number) => {
    setAmount((prev) => {
      const n = prev === '' ? NaN : Number(prev)
      if (Number.isNaN(n)) return String(minNext)
      return String(Math.max(minNext, n + delta))
    })
  }

  const doBid = (e: FormEvent) => {
    e.preventDefault()
    if (!id || !auction) return
    if (!Number.isFinite(Number(amount)) || Number(amount) <= 0) {
      setError('Enter a valid bid amount')
      return
    }
    // Terms & Conditions are gated on the bidder's first bid on THIS auction (auction.yourBid ==
    // null) — every increment bid after that goes straight to the normal confirm step.
    if (auction.yourBid == null) setShowTerms(true)
    else setConfirmingBid(true)
  }

  const confirmBid = async () => {
    if (!id) return
    setConfirmingBid(false)
    const amt = Number(amount)
    setError(''); setMsg(''); setBusy(true)
    try {
      await placeBid(id, amt)
      setMsg('Bid placed!')
      setAmount('')
      refresh()
      refreshWallet()
    } catch (e2) {
      const m = errorMessage(e2)
      if (m.toLowerCase().includes('credit limit')) setCreditLimitError(m)
      else setError(m)
    } finally { setBusy(false) }
  }

  const doCompletePayment = async () => {
    if (!id) return
    setSettleBusy(true); setSettleMsg('')
    try {
      const updated = await completeAuctionPayment(id)
      setAuction(updated)
      setSettleMsg('Payment complete — this auction is fully settled.')
      refreshWallet()
    } catch (e) { setSettleMsg(errorMessage(e)) } finally { setSettleBusy(false) }
  }

  const submitDispute = async (e: FormEvent) => {
    e.preventDefault()
    if (!id || !disputeReason.trim()) return
    setDisputeBusy(true); setDisputeMsg('')
    try {
      await raiseDispute(id, disputeReason)
      setDisputeMsg('Reported to the SwipeAuctions team — an admin will review it.')
      setDisputeReason('')
      setShowDispute(false)
    } catch (e2) { setDisputeMsg(errorMessage(e2)) } finally { setDisputeBusy(false) }
  }

  if (!auction) {
    const feeRequired = !auctionLoading && error.toLowerCase().includes('registration fee')
    return (
      <div className="container">
        <p><Link to="/auctions">← Back to auctions</Link></p>
        {auctionLoading ? (
          <AuctionDetailSkeleton />
        ) : feeRequired ? (
          <div className="card" style={{ maxWidth: 460, margin: '20px auto', textAlign: 'center' }}>
            <h1 className="page">Complete registration to view this item</h1>
            <p className="muted">
              {registrationFee
                ? `Pay a one-time registration fee of ${money(registrationFee)} to view auction details and start bidding.`
                : 'Pay the one-time registration fee to view auction details and start bidding.'}
            </p>
            {feeError && <div className="error">{feeError}</div>}
            <div style={{ marginTop: 16 }}>
              <button className="btn block" disabled={payingFee} onClick={doPayFee}>
                {payingFee ? 'Processing…' : `Pay ${registrationFee ? money(registrationFee) : ''} to continue`}
              </button>
            </div>
          </div>
        ) : error ? (
          <div className="error">{error}</div>
        ) : null}
      </div>
    )
  }

  const current = auction.currentHighestBid ?? auction.basePrice
  // What THIS bidder must clear next — their own previous bid on this item, not necessarily the
  // current leader's (see BidService.placeBid for why those are different questions now). A first
  // bid has no real floor server-side (any positive amount within credit limit is accepted) — the
  // base price here is only a suggested starting point for the stepper/placeholder, not enforced.
  const minNext = auction.yourBid != null ? auction.yourBid + MIN_INCREMENT : auction.basePrice
  const isFirstBid = auction.yourBid == null
  const gallery = auction.images.length > 0 ? auction.images.map(resolveMediaUrl) : [cardImage(auction)]
  const locked = auction.requiredTier !== 'NONE' && !tierMeets(subscriptionTier, auction.requiredTier)
  // Status only flips OPEN -> CLOSED/UNSOLD on a scheduler tick, so a raw `status` read can lag
  // behind the real end time — compute what it effectively is right now instead of trusting that.
  const es = effectiveStatus(auction)
  const isLive = es === 'OPEN'

  return (
    <div className="container">
      <p><Link to="/auctions">← Back to auctions</Link></p>

      <div className={`pdp-layout${showFullDetails ? '' : ' pdp-layout-focused'}`}>
        {/* ---- Gallery + specs ---- */}
        {showFullDetails && (
          <div className="card pdp-gallery">
            <div className="gallery-wrap">
              {gallery.length > 1 && (
                <div className="thumb-rail">
                  {gallery.map((src, i) => (
                    isVideoUrl(src) ? (
                      <video key={src + i} src={src} muted playsInline preload="metadata"
                             onClick={() => setActiveImage(i)} className={i === activeImage ? 'active' : ''} />
                    ) : (
                      <img key={src + i} src={src} alt="" onClick={() => setActiveImage(i)}
                           className={i === activeImage ? 'active' : ''} />
                    )
                  ))}
                </div>
              )}
              <div className="gallery-main">
                {isVideoUrl(gallery[activeImage] ?? gallery[0]) ? (
                  <video key={gallery[activeImage] ?? gallery[0]} src={gallery[activeImage] ?? gallery[0]} controls playsInline />
                ) : (
                  <img src={gallery[activeImage] ?? gallery[0]} alt={auction.title} />
                )}
              </div>
            </div>
            <button type="button" className="linkbtn" style={{ marginTop: 14 }} onClick={() => downloadValuation(auction)}>
              ⬇ Download valuation details
            </button>

            <DetailTabs attributes={auction.attributes} city={auction.city} state={auction.state} />
          </div>
        )}

        {/* ---- Buy box ---- */}
        <div className="card pdp-buybox">
          {!showFullDetails && (
            <button type="button" className="linkbtn" style={{ marginBottom: 10 }} onClick={() => setShowFullDetails(true)}>
              ← View photos &amp; full details
            </button>
          )}
          <div className="row">
            <span className={`badge ${es}`}>{STATUS_LABEL[es] ?? es}</span>
            {isLive && (
              <span className="countdown"><span className="live-dot" />{formatCountdown(msUntil(auction.currentEndTime))}</span>
            )}
          </div>
          <h1>{auction.title}</h1>
          <div className="row"><span className="cat">{auction.categoryName}</span><span className="cond">{auction.condition.replace('_', ' ')}</span></div>
          <div className="loc">◍ {[auction.city, auction.state].filter(Boolean).join(', ') || '—'}{auction.brand ? ` · ${auction.brand}` : ''}</div>

          <div className="price-block big">
            <div className="price-meta"><span>{auction.bidCount} bids</span></div>
            {auction.bidsRemaining != null && (
              <div className="price-meta"><span>{auction.bidsRemaining} bid{auction.bidsRemaining === 1 ? '' : 's'} remaining for you on this item</span></div>
            )}
          </div>

          <div ref={actionRef}>
            {isAdmin ? (
              <p className="muted">Admin accounts are for management only and can't place bids.</p>
            ) : !isLive ? (
              <p className="muted">Bidding is closed for this auction.</p>
            ) : !isAuthenticated ? (
              <p className="muted"><Link to="/login">Sign in</Link> to register and bid.</p>
            ) : locked ? (
              <div className="ok" style={{ background: 'var(--warn-bg, #fff7e6)' }}>
                This item requires a {auction.requiredTier} subscription.{' '}
                <Link to="/subscription">Upgrade your plan</Link> to view full access and bid.
              </div>
            ) : !kycCompleted ? (
              <div className="ok" style={{ background: 'var(--warn-bg, #fff7e6)' }}>
                Complete your KYC verification before bidding.{' '}
                <Link to="/kyc" state={{ from: `/auctions/${id}` }}>Complete KYC now</Link>
              </div>
            ) : auction.bidsRemaining === 0 ? (
              <p className="muted">No more bids available — you've used all 20 bids on this item.</p>
            ) : (
              <form onSubmit={doBid}>
                <label>{isFirstBid ? `Your bid (suggested ${money(minNext)})` : `Your bid (min ${money(minNext)})`}</label>
                <div className="bid-stepper">
                  <button type="button" className="step-btn" onClick={() => bumpAmount(minNext, -MIN_INCREMENT)} aria-label="Decrease bid">−</button>
                  <input ref={amountRef} type="number" value={amount} onChange={(e) => setAmount(e.target.value)}
                         min={isFirstBid ? MIN_INCREMENT : minNext} placeholder={`${minNext}`} />
                  <button type="button" className="step-btn" onClick={() => bumpAmount(minNext, MIN_INCREMENT)} aria-label="Increase bid">+</button>
                </div>
                <div style={{ marginTop: 12 }}><button className="btn block" disabled={busy}>{busy ? 'Placing…' : 'Place bid'}</button></div>
              </form>
            )}
          </div>

          {auction.isWinner && es === 'CLOSED' && !auction.settlementPaid && (
            <div className="error" style={{ marginTop: 12 }}>
              You won this auction — {money(current)} still owed to complete settlement.
              <div style={{ marginTop: 8 }}>
                <button type="button" className="btn sm" disabled={settleBusy} onClick={doCompletePayment}>
                  {settleBusy ? 'Processing…' : 'Complete payment'}
                </button>
              </div>
              {settleMsg && <div className={settleMsg.startsWith('Payment complete') ? 'ok' : 'error'} style={{ marginTop: 8 }}>{settleMsg}</div>}
            </div>
          )}
          {msg && <div className="ok">{msg}</div>}
          {error && (
            <div className="error">
              {error}
              {needsKyc(error) && <> — <Link to="/kyc" state={{ from: `/auctions/${id}` }}>Complete KYC now</Link></>}
            </div>
          )}
          {wallet && <p className="muted" style={{ marginTop: 16 }}>Wallet — available {money(wallet.availableBalance)}, held {money(wallet.heldBalance)}</p>}

          {isAuthenticated && (
            <div style={{ marginTop: 20, paddingTop: 16, borderTop: '1px dashed var(--border)' }}>
              {!showDispute ? (
                <button type="button" className="linkbtn" onClick={() => setShowDispute(true)}>Report an issue with this auction</button>
              ) : (
                <form onSubmit={submitDispute}>
                  <label>What's wrong?</label>
                  <textarea style={{ width: '100%', minHeight: 70, fontFamily: 'inherit' }} value={disputeReason}
                            onChange={(e) => setDisputeReason(e.target.value)} placeholder="Describe the issue…" />
                  <div style={{ marginTop: 10, display: 'flex', gap: 10 }}>
                    <button className="btn sm" disabled={disputeBusy || !disputeReason.trim()}>{disputeBusy ? 'Sending…' : 'Submit report'}</button>
                    <button type="button" className="btn ghost sm" onClick={() => setShowDispute(false)}>Cancel</button>
                  </div>
                </form>
              )}
              {disputeMsg && <div className={disputeMsg.startsWith('Reported') ? 'ok' : 'error'} style={{ marginTop: 10 }}>{disputeMsg}</div>}
            </div>
          )}
        </div>
      </div>

      {showTerms && (
        <TermsModal
          title={auction.title}
          onAccept={() => { setShowTerms(false); setConfirmingBid(true) }}
          onCancel={() => setShowTerms(false)}
        />
      )}

      {creditLimitError && (
        <div className="modal-backdrop" onClick={() => setCreditLimitError('')}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>Not enough bidding credit</h3>
              <button type="button" className="modal-close" onClick={() => setCreditLimitError('')} aria-label="Close">✕</button>
            </div>
            <div className="modal-body">
              <p>{creditLimitError}</p>
            </div>
            <div className="modal-foot">
              <button type="button" className="linkbtn" onClick={() => setCreditLimitError('')}>Cancel</button>
              <Link to="/wallet" className="btn">Top up</Link>
            </div>
          </div>
        </div>
      )}

      {confirmingBid && (
        <div className="modal-backdrop" onClick={() => setConfirmingBid(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-head">
              <h3>Confirm your bid</h3>
              <button type="button" className="modal-close" onClick={() => setConfirmingBid(false)} aria-label="Close">✕</button>
            </div>
            <div className="modal-body">
              <p>Place a bid of <strong>{money(Number(amount))}</strong> on "{auction.title}"?</p>
            </div>
            <div className="modal-foot">
              <button type="button" className="linkbtn" onClick={() => setConfirmingBid(false)}>Cancel</button>
              <button type="button" className="btn" disabled={busy} onClick={confirmBid}>{busy ? 'Placing…' : 'Confirm bid'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
