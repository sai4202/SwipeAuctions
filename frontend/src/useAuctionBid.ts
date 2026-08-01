import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { placeBid, errorMessage, type Auction } from './api'
import { useAuth } from './auth'
import { tierMeets, effectiveStatus } from './util'

export type ModalPhase = 'terms' | 'confirm' | 'success'

/**
 * The card's own inline "type an amount, hit Bid, confirm" flow — extracted so the list-view table
 * (AuctionListRow) can offer the exact same behavior per row instead of just linking off to the
 * detail page. One instance per auction card/row; each holds its own optimistic override, so a bid
 * placed on one row doesn't touch the state of any other.
 */
export function useAuctionBid(a: Auction) {
  const { isAuthenticated, subscriptionTier, role } = useAuth()
  const navigate = useNavigate()
  const [amount, setAmount] = useState('')

  // Optimistic patch after a successful bid placed right here — the parent's auction list won't
  // reflect it until its own next fetch/websocket tick, so this keeps this card/row itself correct
  // until then. Cleared whenever the parent hands us a fresh `auction` object.
  const [override, setOverride] = useState<Partial<Auction> | null>(null)
  useEffect(() => setOverride(null), [a])
  const auction = override ? { ...a, ...override } : a

  const [showBidModal, setShowBidModal] = useState(false)
  const [modalPhase, setModalPhase] = useState<ModalPhase>('confirm')
  const [pendingAmount, setPendingAmount] = useState(0)
  const [bidError, setBidError] = useState('')
  const [bidBusy, setBidBusy] = useState(false)

  // What THIS bidder must clear on their next bid — their own previous bid on this item, not
  // necessarily the current leader's amount. A bidder can keep raising their own bid without
  // needing to beat whoever else is ahead; whether that actually takes the lead is a separate
  // question (see `leading` below), decided server-side the same way (BidService.placeBid).
  const minNext = auction.yourBid != null ? auction.yourBid + 1 : auction.basePrice

  // Status only flips OPEN -> CLOSED/UNSOLD on a scheduler tick, so a raw `status` read can lag
  // behind the real end time — compute what it effectively is right now instead of trusting that.
  const es = effectiveStatus(auction)
  const isLive = es === 'OPEN'
  // Must be `isWinner` (server truth: am I literally auction.currentWinner), NOT an amount
  // comparison like `yourBid >= currentHighestBid`. Since bids only need to beat the bidder's OWN
  // previous bid now, two different bidders can legitimately tie on amount — whoever got there
  // first keeps the lead (BidService only promotes on a *strictly* higher bid), but a `>=` amount
  // check would incorrectly show BOTH tied bidders as "Leading". isWinner has no such ambiguity.
  const leading = auction.isWinner
  // Once the auction is closed, "leading/outbid" becomes "won/lost" — same underlying flag.
  const closed = es === 'CLOSED' || es === 'UNSOLD'
  const bidStatusLabel = closed ? (leading ? 'Won' : 'Lost') : (leading ? 'Leading' : 'Outbid')
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
    // Take exactly what the bidder typed — no client-side comparison against the minimum increment
    // or credit limit. The backend is the single source of truth for whether an amount is valid
    // (BidService checks both) and reports back through the normal confirm-modal error path if not;
    // we only fall back to the suggested minimum when the field was left blank, never to override
    // an amount the bidder actually entered.
    const amt = amount ? Number(amount) : minNext
    setBidError('')
    setPendingAmount(amt)
    // Terms & Conditions are gated on the bidder's first bid on THIS auction (auction.yourBid ==
    // null) — every increment bid after that goes straight to the normal confirm step.
    setModalPhase(auction.yourBid == null ? 'terms' : 'confirm')
    setShowBidModal(true)
  }

  const confirmBid = async () => {
    setBidError(''); setBidBusy(true)
    try {
      const res = await placeBid(auction.id, pendingAmount)
      // currentHighestBid and isWinner come from the server's real post-bid state, not inferred
      // from the amount just sent — an accepted bid doesn't necessarily become the new leader (see
      // BidService.placeBid), so assuming pendingAmount is now the highest (or that placing a bid
      // makes you the winner) would wrongly show "Leading" on a bid that's actually still trailing.
      setOverride({ currentHighestBid: res.currentHighestBid, bidCount: auction.bidCount + 1, yourBid: pendingAmount, currentEndTime: res.currentEndTime, isWinner: res.leading })
      setAmount('')
      setModalPhase('success')
      // This confirmation is the one guaranteed thing the bidder sees — the live "Bid placed" toast
      // (NotificationContext) is pushed over a websocket that may still be connecting on a fresh page
      // load and can lose that race, so this popup itself has to stay up long enough to be trusted,
      // not vanish the moment the request resolves.
      setTimeout(() => setShowBidModal(false), 3000)
    } catch (e) {
      const m = errorMessage(e)
      if (m.toLowerCase().includes('registration fee')) {
        // Fee unpaid — the full auction page shows the "pay to view/bid" wall; this quick-bid
        // popup has no room to collect payment itself.
        setShowBidModal(false)
        navigate(`/auctions/${auction.id}`)
        return
      }
      setBidError(m)
    } finally {
      setBidBusy(false)
    }
  }

  return {
    auction, amount, setAmount, minNext, es, isLive, leading, closed, bidStatusLabel,
    isAdmin, locked, showTierBadge,
    showBidModal, setShowBidModal, modalPhase, setModalPhase, pendingAmount, bidError, bidBusy,
    clickBidNow, confirmBid,
  }
}
