import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { placeBid, errorMessage, type Auction } from '../api'
import { useAuth } from '../auth'
import { formatCountdown, msUntil, money, cardImage, tierMeets, effectiveStatus } from '../util'
import TermsModal from './TermsModal'

const SLABEL: Record<string, string> = {
  OPEN: 'Live', SCHEDULED: 'Upcoming', CLOSED: 'Closed', UNSOLD: 'Unsold', CANCELLED: 'Cancelled',
}

/** Card image with a graceful gradient fallback if the photo fails to load. */
function CardThumb({ auction }: { auction: Auction }) {
  const [failed, setFailed] = useState(false)
  if (failed) {
    return (
      <div className="thumb thumb-fallback">
        <span>{auction.categoryName}</span>
      </div>
    )
  }
  return (
    <div className="thumb">
      <img src={cardImage(auction)} alt={auction.title} loading="lazy" onError={() => setFailed(true)} />
    </div>
  )
}

interface Props {
  auction: Auction
  inTray: boolean
  onToggleTray: (id: string) => void
}

type ModalPhase = 'terms' | 'low' | 'confirm' | 'success'

/**
 * The auction browse-card used on the browse page and inside an auction event's item list —
 * styled like a Flipkart/Amazon product card: image, clamped title, a single prominent price
 * (current bid, with the base price shown as a struck-through reference), and two separate
 * actions — "View Details" (the product page) and "Bid Now", which takes the amount already
 * dialed in on the card's own +/- stepper and goes straight to a confirm-or-reject popup right
 * here on the card — no navigation, and no re-entering the amount a second time.
 */
export default function AuctionCard({ auction: a, inTray, onToggleTray }: Props) {
  const { isAuthenticated, subscriptionTier, role } = useAuth()
  const navigate = useNavigate()
  const [amount, setAmount] = useState('')
  // Optimistic patch after a successful bid placed right here on the card — the parent's auction
  // list won't reflect it until its own next fetch/websocket tick, so this keeps the card itself
  // correct until then. Cleared whenever the parent hands us a fresh `auction` object.
  const [override, setOverride] = useState<Partial<Auction> | null>(null)
  useEffect(() => setOverride(null), [a])
  const auction = override ? { ...a, ...override } : a

  const [showBidModal, setShowBidModal] = useState(false)
  const [modalPhase, setModalPhase] = useState<ModalPhase>('confirm')
  const [pendingAmount, setPendingAmount] = useState(0)
  const [bidError, setBidError] = useState('')
  const [bidBusy, setBidBusy] = useState(false)

  const hasBid = auction.currentHighestBid != null
  const minNext = hasBid ? (auction.currentHighestBid as number) + 1 : auction.basePrice

  // First click (field empty) fills in the current minimum valid bid; every click after that just
  // steps by the increment, floored so it can never drop below that minimum.
  const bumpAmount = (delta: number) => {
    setAmount((prev) => {
      const n = prev === '' ? NaN : Number(prev)
      if (Number.isNaN(n)) return String(minNext)
      return String(Math.max(minNext, n + delta))
    })
  }

  // Status only flips OPEN -> CLOSED/UNSOLD on a scheduler tick, so a raw `status` read can lag
  // behind the real end time — compute what it effectively is right now instead of trusting that.
  const es = effectiveStatus(auction)
  const isLive = es === 'OPEN'
  const leading = auction.yourBid != null && hasBid && auction.yourBid >= (auction.currentHighestBid as number)
  // Once the auction is closed, "leading/outbid" no longer means anything — show the final Won/Lost
  // outcome instead so the card always reflects where the bidder actually stands.
  const closed = es === 'CLOSED' || es === 'UNSOLD'
  const won = closed && auction.isWinner
  const bidStatusLabel = closed ? (won ? 'Won' : 'Lost') : (leading ? 'Leading' : 'Outbid')
  const isAdmin = role === 'ADMIN'
  // Admins can't place bids at all (enforced server-side) but should still be able to browse and
  // open every listing for management/support purposes — the subscription paywall is for bidders.
  const locked = !isAdmin && auction.requiredTier !== 'NONE' && !tierMeets(subscriptionTier, auction.requiredTier)
  // Admins see the tier a listing requires regardless of lock state, purely as information.
  const showTierBadge = auction.requiredTier !== 'NONE' && (locked || isAdmin)

  const clickBidNow = () => {
    // Catalog browsing is public, but bidding isn't — send an anonymous visitor to sign in first
    // instead of letting them reach a confirm popup that only then fails with "Unauthorized".
    if (!isAuthenticated) {
      navigate('/login', { state: { from: `/auctions/${auction.id}` } })
      return
    }
    const amt = Number(amount || minNext)
    setBidError('')
    if (!Number.isFinite(amt) || amt <= 0 || amt < minNext) {
      setModalPhase('low')
    } else {
      setPendingAmount(amt)
      // Terms & Conditions are gated on the bidder's first bid on THIS auction (auction.yourBid ==
      // null) — every increment bid after that goes straight to the normal confirm step.
      setModalPhase(auction.yourBid == null ? 'terms' : 'confirm')
    }
    setShowBidModal(true)
  }

  const confirmBid = async () => {
    setBidError(''); setBidBusy(true)
    try {
      const res = await placeBid(auction.id, pendingAmount)
      setOverride({ currentHighestBid: pendingAmount, bidCount: auction.bidCount + 1, yourBid: pendingAmount, currentEndTime: res.currentEndTime })
      setAmount('')
      setModalPhase('success')
      // This confirmation is the one guaranteed thing the bidder sees — the live "Bid placed" toast
      // (NotificationContext) is pushed over a websocket that may still be connecting on a fresh page
      // load and can lose that race, so this popup itself has to stay up long enough to be trusted,
      // not vanish the moment the request resolves.
      setTimeout(() => setShowBidModal(false), 3000)
    } catch (e) {
      const m = errorMessage(e)
      if (m.toLowerCase().includes('register to bid first')) {
        // Not registered (no EMD hold) yet — that flow still needs the full auction page.
        setShowBidModal(false)
        navigate(`/auctions/${auction.id}`)
        return
      }
      setBidError(m)
    } finally {
      setBidBusy(false)
    }
  }

  return (
    <div className="card acard">
      <div className="thumb-wrap">
        <Link to={`/auctions/${auction.id}`} className="thumb-link">
          <CardThumb auction={auction} />
          <span className={`badge ${es} ribbon`}>{SLABEL[es] ?? es}</span>
          {isLive && <span className="countdown ribbon-time">{formatCountdown(msUntil(auction.currentEndTime))}</span>}
          {showTierBadge && <span className="badge ribbon" style={{ top: 34 }}>🔒 {auction.requiredTier}</span>}
        </Link>
        <button
          type="button"
          className={`wishlist-btn ${inTray ? 'active' : ''}`}
          onClick={() => onToggleTray(auction.id)}
          title={inTray ? 'Remove from wishlist' : 'Add to wishlist'}
          aria-label={inTray ? 'Remove from wishlist' : 'Add to wishlist'}
        >
          {inTray ? '♥' : '♡'}
        </button>
      </div>
      <div className="row"><span className="cat">{auction.categoryName}</span><span className="cond">{auction.condition.replace('_', ' ')}</span></div>
      <Link to={`/auctions/${auction.id}`}><h3 className="acard-title">{auction.title}</h3></Link>
      <div className="loc">◍ {[auction.city, auction.state].filter(Boolean).join(', ') || '—'}{auction.brand ? ` · ${auction.brand}` : ''}</div>

      <div className="price-block">
        <span className="price-label">{hasBid ? 'Current bid' : 'Starting price'}</span>
        <div className="price-row">
          <span className="price-now">{money(hasBid ? auction.currentHighestBid : auction.basePrice)}</span>
          {hasBid && <span className="price-was">Base {money(auction.basePrice)}</span>}
        </div>
        <div className="price-meta">
          <span>{auction.bidCount} bid{auction.bidCount === 1 ? '' : 's'}</span>
          <span>Min next {money(minNext)}</span>
        </div>
        {auction.yourBid != null && (
          <div className={bidStatusLabel === 'Leading' || bidStatusLabel === 'Won' ? 'you-leading' : 'you-outbid'} style={{ fontSize: 12, marginTop: 6 }}>
            Your bid {money(auction.yourBid)} · {bidStatusLabel}
          </div>
        )}
      </div>

      <div className={`card-cta ${!locked && !isAdmin && isLive ? 'card-cta-stack' : ''}`}>
        {locked ? (
          <Link to="/subscription" className="btn sm">Upgrade your plan</Link>
        ) : isAdmin ? (
          <Link to={`/auctions/${auction.id}`} className="btn ghost sm">View Details</Link>
        ) : (
          <>
            <Link to={`/auctions/${auction.id}`} className="btn ghost sm">View Details</Link>
            {isLive && (
              <div className="bid-stepper">
                <button type="button" className="step-btn" onClick={() => bumpAmount(-1)} aria-label="Decrease bid">−</button>
                <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} min={minNext} placeholder={`${minNext}`} />
                <button type="button" className="step-btn" onClick={() => bumpAmount(1)} aria-label="Increase bid">+</button>
              </div>
            )}
            {isLive && (
              <button type="button" className="btn sm" onClick={clickBidNow}>Bid Now</button>
            )}
          </>
        )}
      </div>

      {showBidModal && modalPhase === 'terms' && (
        <TermsModal
          title={auction.title}
          onAccept={() => setModalPhase('confirm')}
          onCancel={() => setShowBidModal(false)}
        />
      )}

      {showBidModal && modalPhase !== 'terms' && (
        <div className="modal-backdrop" onClick={() => setShowBidModal(false)}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            {modalPhase === 'low' ? (
              <>
                <div className="modal-head">
                  <h3>Bid too low</h3>
                  <button type="button" className="modal-close" onClick={() => setShowBidModal(false)} aria-label="Close">✕</button>
                </div>
                <div className="modal-body">
                  <div className="error">Minimum bid is {money(minNext)}.</div>
                </div>
                <div className="modal-foot" style={{ justifyContent: 'flex-end' }}>
                  <button type="button" className="btn" onClick={() => { setAmount(String(minNext)); setShowBidModal(false) }}>OK</button>
                </div>
              </>
            ) : modalPhase === 'success' ? (
              <>
                <div className="modal-head">
                  <h3>✓ Bid confirmed</h3>
                  <button type="button" className="modal-close" onClick={() => setShowBidModal(false)} aria-label="Close">✕</button>
                </div>
                <div className="modal-body">
                  <div className="ok" style={{ fontSize: 15 }}>Bid placed! {money(pendingAmount)} on "{auction.title}".</div>
                </div>
                <div className="modal-foot" style={{ justifyContent: 'flex-end' }}>
                  <button type="button" className="btn" onClick={() => setShowBidModal(false)}>Done</button>
                </div>
              </>
            ) : (
              <>
                <div className="modal-head">
                  <h3>Confirm your bid</h3>
                  <button type="button" className="modal-close" onClick={() => setShowBidModal(false)} aria-label="Close">✕</button>
                </div>
                <div className="modal-body">
                  <p>Place a bid of <strong>{money(pendingAmount)}</strong> on "{auction.title}"?</p>
                  {bidError && <div className="error" style={{ marginTop: 10 }}>{bidError}</div>}
                </div>
                <div className="modal-foot">
                  <button type="button" className="linkbtn" onClick={() => setShowBidModal(false)}>Cancel</button>
                  <button type="button" className="btn" disabled={bidBusy} onClick={confirmBid}>{bidBusy ? 'Placing…' : 'Confirm bid'}</button>
                </div>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
