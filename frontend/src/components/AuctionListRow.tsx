import { Link } from 'react-router-dom'
import { type Auction } from '../api'
import { money, cardImage, downloadValuation, formatDateTimeShort, closingSoon } from '../util'
import { useAuctionBid } from '../useAuctionBid'
import BidModals from './BidModals'
import ItemDetailStrip from './ItemDetailStrip'
import ShareMenu from './ShareMenu'

interface Props {
  auction: Auction
  inTray: boolean
  onToggleTray: (id: string) => void
}

/**
 * One row of the list-view table — the row-shaped sibling of AuctionCard's grid tile, sharing the
 * exact same inline "type an amount, hit Bid" flow (useAuctionBid + BidModals) instead of just
 * linking off to the detail page, so bidding works identically regardless of which view is active.
 */
export default function AuctionListRow({ auction: a, inTray, onToggleTray }: Props) {
  const {
    auction, amount, setAmount, minNext, es, isAdmin, locked,
    showBidModal, setShowBidModal, modalPhase, setModalPhase, pendingAmount, bidError, bidBusy,
    clickBidNow, confirmBid,
  } = useAuctionBid(a)

  const soon = es === 'OPEN' && closingSoon(auction)
  return (
    <tr className={es === 'OPEN' ? (soon ? 'closing-soon-row' : 'live-pulse-row') : ''}>
      <td>
        <Link to={`/auctions/${auction.id}`}>
          <img src={cardImage(auction)} alt={auction.title} style={{ width: 70, height: 52, objectFit: 'cover', borderRadius: 8, display: 'block' }} />
        </Link>
        <button type="button" className="linkbtn" style={{ fontSize: 11 }} onClick={() => downloadValuation(auction)}>⬇ Valuation</button>
      </td>
      <td>
        <Link to={`/auctions/${auction.id}`}><b>{auction.title}</b></Link>
        {es === 'OPEN' && (
          <span className={`badge OPEN ${soon ? 'closing-soon' : 'live-pulse'}`} style={{ marginLeft: 8 }}>
            {soon ? 'Closing Soon' : 'Live'}
          </span>
        )}
        <div className="muted" style={{ fontSize: 12 }}>{[auction.brand, auction.condition.replace('_', ' ')].filter(Boolean).join(' · ')}</div>
        <div className="muted" style={{ fontSize: 12 }}>◍ {[auction.city, auction.state].filter(Boolean).join(', ') || '—'}</div>
        <ItemDetailStrip attributes={auction.attributes} />
      </td>
      <td style={{ whiteSpace: 'nowrap' }}>{formatDateTimeShort(auction.startTime)}</td>
      <td style={{ whiteSpace: 'nowrap' }}>{formatDateTimeShort(auction.currentEndTime)}</td>
      <td>{money(auction.basePrice)}</td>
      <td>{auction.currentHighestBid != null ? money(auction.currentHighestBid) : '—'}</td>
      <td>{auction.bidCount}</td>
      <td>
        {auction.bidsRemaining == null ? '—' : auction.bidsRemaining === 0 ? (
          <span style={{ color: 'var(--red-2)', fontWeight: 700 }}>None left</span>
        ) : auction.bidsRemaining}
      </td>
      <td>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 150 }}>
          <Link to={`/auctions/${auction.id}`} className="btn ghost sm">View Details</Link>
          {locked ? (
            <Link to="/subscription" className="btn sm">Upgrade your plan</Link>
          ) : isAdmin ? (
            auction.bidCount > 0 && (
              <Link to={`/admin/auctions/${auction.id}/bidders`} className="btn ghost sm">View Bidders</Link>
            )
          ) : es !== 'OPEN' ? (
            <span className="btn sm" style={{ opacity: .55, pointerEvents: 'none' }}>
              {es === 'SCHEDULED' ? 'Not started' : 'Closed'}
            </span>
          ) : auction.bidsRemaining === 0 ? (
            <span className="btn sm" style={{ opacity: .55, pointerEvents: 'none' }}>No bids left</span>
          ) : (
            <div style={{ display: 'flex', gap: 6 }}>
              <input
                type="number" className="card-cta-input bid-input-highlight" value={amount}
                onChange={(e) => setAmount(e.target.value)} min={auction.yourBid != null ? minNext : 1}
                placeholder="Enter amount"
                title={`Suggested amount: ${minNext}`}
                aria-label={auction.yourBid != null ? `Your bid amount, minimum ${minNext}` : `Your bid amount, suggested ${minNext}`}
                style={{ width: 100 }}
              />
              <button type="button" className="btn sm" onClick={clickBidNow}>Bid</button>
            </div>
          )}
          <div style={{ display: 'flex', gap: 6 }}>
            {inTray ? (
              <button type="button" className="btn light sm" onClick={() => onToggleTray(auction.id)}>♥ In wishlist</button>
            ) : (
              <button type="button" className="btn ghost sm" onClick={() => onToggleTray(auction.id)}>♡ Wishlist</button>
            )}
            <ShareMenu url={`/auctions/${auction.id}`} text={`Check out "${auction.title}" on SwipeAuctions:`}
                       emailSubject={auction.title} triggerClassName="btn ghost sm" />
          </div>
        </div>
      </td>

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
    </tr>
  )
}
