import { createPortal } from 'react-dom'
import { Link } from 'react-router-dom'
import { type Auction } from '../api'
import { money } from '../util'
import TermsModal from './TermsModal'
import type { ModalPhase } from '../useAuctionBid'

interface Props {
  auction: Auction
  showBidModal: boolean
  modalPhase: ModalPhase
  pendingAmount: number
  bidError: string
  bidBusy: boolean
  onAcceptTerms: () => void
  onClose: () => void
  onConfirm: () => void
}

/**
 * The Terms & Conditions gate plus the confirm/success popup shared by every inline "type an
 * amount, hit Bid" surface (AuctionCard's grid card, AuctionListRow's list-view row) — same modal,
 * same copy, same portal-to-body positioning regardless of how narrow or wide the trigger itself is.
 */
export default function BidModals({
  auction, showBidModal, modalPhase, pendingAmount, bidError, bidBusy, onAcceptTerms, onClose, onConfirm,
}: Props) {
  if (!showBidModal) return null

  if (modalPhase === 'terms') {
    return <TermsModal title={auction.title} onAccept={onAcceptTerms} onCancel={onClose} />
  }

  return createPortal((
    // Portaled to <body> — inline here, a hover transform on the trigger's own container would
    // become this fixed-position backdrop's containing block, making it jump/resize as the mouse
    // moves in and out instead of covering the viewport.
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        {modalPhase === 'success' ? (
          <>
            <div className="modal-head">
              <h3>✓ Bid confirmed</h3>
              <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
            </div>
            <div className="modal-body">
              <div className="ok" style={{ fontSize: 15 }}>Bid placed! {money(pendingAmount)} on "{auction.title}".</div>
            </div>
            <div className="modal-foot" style={{ justifyContent: 'flex-end' }}>
              <button type="button" className="btn" onClick={onClose}>Done</button>
            </div>
          </>
        ) : (
          <>
            <div className="modal-head">
              <h3>Confirm your bid</h3>
              <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
            </div>
            <div className="modal-body">
              <p>Place a bid of <strong>{money(pendingAmount)}</strong> on "{auction.title}"?</p>
              {bidError && (
                <div className="error" style={{ marginTop: 10 }}>
                  {bidError}
                  {bidError.toLowerCase().includes('credit limit') && <> — <Link to="/wallet">Top up</Link></>}
                </div>
              )}
            </div>
            <div className="modal-foot">
              <button type="button" className="linkbtn" onClick={onClose}>Cancel</button>
              <button type="button" className="btn" disabled={bidBusy} onClick={onConfirm}>{bidBusy ? 'Placing…' : 'Confirm bid'}</button>
            </div>
          </>
        )}
      </div>
    </div>
  ), document.body)
}
