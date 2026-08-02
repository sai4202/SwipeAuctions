import { useState } from 'react'
import { Link } from 'react-router-dom'
import { type Auction } from '../api'
import { moneyCompact, money, formatDateTimeShort, cardImage, openUserDetails } from '../util'
import { useAuctionBid } from '../useAuctionBid'
import BidModals from './BidModals'
import ShareMenu from './ShareMenu'

const SLABEL: Record<string, string> = {
  OPEN: 'Live', SCHEDULED: 'Upcoming', CLOSED: 'Closed', UNSOLD: 'Unsold', CANCELLED: 'Cancelled',
}

// A plain "🔒 GOLD"/"🔒 DIAMOND" text ribbon has no background of its own and disappears against a
// bright photo — a wishlist-heart-style solid icon badge reads at a glance regardless of what's
// behind it, the same way the heart itself always does.
const TIER_ICON: Record<string, string> = { SILVER: '🥈', GOLD: '🥇', DIAMOND: '💎' }

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

/**
 * The auction browse-card used on the browse page and inside an auction event's item list —
 * styled like a Flipkart/Amazon product card: image, clamped title, a single prominent price
 * (current bid, with the base price shown as a struck-through reference), and two separate
 * actions — "View Details" (the product page) and "Bid Now", which takes the amount already
 * dialed in on the card's own +/- stepper and goes straight to a confirm-or-reject popup right
 * here on the card — no navigation, and no re-entering the amount a second time.
 */
export default function AuctionCard({ auction: a, inTray, onToggleTray }: Props) {
  const {
    auction, amount, setAmount, minNext, es, isLive, bidStatusLabel,
    isAdmin, locked, showTierBadge,
    showBidModal, setShowBidModal, modalPhase, setModalPhase, pendingAmount, bidError, bidBusy,
    clickBidNow, confirmBid,
  } = useAuctionBid(a)

  return (
    <div className="card acard">
      <div className="thumb-wrap">
        <Link to={`/auctions/${auction.id}`} className="thumb-link">
          <CardThumb auction={auction} />
          <span className={`badge ${es} ribbon`}>{SLABEL[es] ?? es}</span>
          {showTierBadge && (
            <span className="tier-badge" style={{ top: 34 }} title={`${auction.requiredTier} tier required`}>
              {TIER_ICON[auction.requiredTier] ?? '🔒'}
            </span>
          )}
          {auction.yourBid != null && (
            <span
              className={`badge ${bidStatusLabel === 'Leading' || bidStatusLabel === 'Won' ? 'OPEN' : 'UNSOLD'} ribbon`}
              style={{ top: showTierBadge ? 66 : 34 }}
            >
              {bidStatusLabel}
            </span>
          )}
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
        <ShareMenu url={`/auctions/${auction.id}`} text={`Check out "${auction.title}" on SwipeAuctions:`} emailSubject={auction.title} />
      </div>
      <div className="row"><span className="cat">{auction.categoryName}</span><span className="cond">{auction.condition.replace('_', ' ')}</span></div>
      <Link to={`/auctions/${auction.id}`}><h3 className="acard-title">{auction.title}</h3></Link>
      <div className="loc">◍ {[auction.city, auction.state].filter(Boolean).join(', ') || '—'}{auction.brand ? ` · ${auction.brand}` : ''}</div>

      <div className="price-block">
        <div className="price-head">
          <div>
            <span className="price-label">Reserve Price</span>
            <div className="price-row">
              <span className="price-now">{moneyCompact(auction.basePrice)}</span>
            </div>
          </div>
          <div className="acard-times">
            <span>Starts {formatDateTimeShort(auction.startTime)}</span>
            <span>Ends {formatDateTimeShort(auction.currentEndTime)}</span>
          </div>
        </div>
        {isAdmin && (
          <div className="price-meta">
            <span className="muted" style={{ marginRight: 4 }}>Current max bid:</span>
            {auction.currentWinnerId ? (
              <button type="button" className="linkbtn" title={auction.currentWinnerEmail ?? undefined}
                      onClick={() => openUserDetails(auction.currentWinnerId!)}>
                {money(auction.currentHighestBid)}
              </button>
            ) : (
              <span>{money(auction.currentHighestBid)}</span>
            )}
          </div>
        )}
        {auction.bidsRemaining != null && (
          <div className="price-meta">
            <span style={auction.bidsRemaining === 0 ? { color: 'var(--red-2)', fontWeight: 700 } : undefined}>
              {auction.bidsRemaining === 0 ? 'No bids left' : `${auction.bidsRemaining} bid${auction.bidsRemaining === 1 ? '' : 's'} left`}
            </span>
          </div>
        )}
        {auction.yourBid != null && (
          <div className={bidStatusLabel === 'Leading' || bidStatusLabel === 'Won' ? 'you-leading' : 'you-outbid'} style={{ fontSize: 12, marginTop: 6 }}>
            Your bid {money(auction.yourBid)} · {bidStatusLabel}
          </div>
        )}
      </div>

      <div className={`card-cta ${!locked && !isAdmin && isLive ? 'card-cta-row' : ''}`}>
        {locked ? (
          <Link to="/subscription" className="btn sm">Upgrade your plan</Link>
        ) : isAdmin ? (
          <Link to={`/auctions/${auction.id}`} className="btn ghost sm">View Details</Link>
        ) : (
          <>
            <Link
              to={`/auctions/${auction.id}`}
              className={isLive ? 'btn ghost sm card-cta-icon' : 'btn ghost sm'}
              title="View Details"
              aria-label="View Details"
            >
              {isLive ? (
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7Z" /><circle cx="12" cy="12" r="3" />
                </svg>
              ) : 'View Details'}
            </Link>
            {isLive && auction.bidsRemaining === 0 ? (
              <span className="btn sm card-cta-disabled">No bids left</span>
            ) : isLive && (
              <>
                <input
                  type="number" className="card-cta-input bid-input-highlight" value={amount}
                  onChange={(e) => setAmount(e.target.value)} min={auction.yourBid != null ? minNext : 1}
                  placeholder="Enter amount"
                  aria-label={auction.yourBid != null ? `Your bid amount, minimum ${minNext}` : `Your bid amount, suggested ${minNext}`}
                />
                <button type="button" className="btn sm" onClick={clickBidNow}>Bid</button>
              </>
            )}
          </>
        )}
      </div>

      <BidModals
        auction={auction}
        showBidModal={showBidModal}
        modalPhase={modalPhase}
        pendingAmount={pendingAmount}
        bidError={bidError}
        bidBusy={bidBusy}
        onAcceptTerms={() => setModalPhase('confirm')}
        onClose={() => setShowBidModal(false)}
        onConfirm={confirmBid}
      />
    </div>
  )
}
