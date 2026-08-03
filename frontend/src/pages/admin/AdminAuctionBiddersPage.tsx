import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  getAuction, getAdminAuctionBidders, adminEditBid, adminDisqualifyBidder, adminReinstateBidder, errorMessage,
  type Auction, type AuctionBidder,
} from '../../api'
import { money, formatDateTimeShort, openUserDetails } from '../../util'
import { AdminPageHeader } from './shared'

/** Every bidder currently on an auction, one row per bidder (their own best bid), ranked
 *  highest-first, with admin correction tools per row: edit the bid amount directly (a mistaken or
 *  disputed bid), or disqualify/reinstate the bidder from just this one auction. Its own full page
 *  (not a modal) — the row of inline forms needs real width, and a modal capped it. Reached from
 *  the admin Auctions table's "See Bidders" and the admin-only "View Bidders" button on the public
 *  Browse/Swipe Stock cards, both of which only show it once an item actually has bids. */
export default function AdminAuctionBiddersPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [auction, setAuction] = useState<Auction | null>(null)
  const [bidders, setBidders] = useState<AuctionBidder[] | null>(null)
  const [error, setError] = useState('')
  const [editingBidId, setEditingBidId] = useState<string | null>(null)
  const [editAmount, setEditAmount] = useState('')
  const [editReason, setEditReason] = useState('')
  const [dqBidderId, setDqBidderId] = useState<string | null>(null)
  const [dqReason, setDqReason] = useState('')
  const [busyBidderId, setBusyBidderId] = useState<string | null>(null)
  const [actionError, setActionError] = useState('')

  const load = () => {
    if (!id) return
    getAdminAuctionBidders(id).then(setBidders).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => {
    if (!id) return
    getAuction(id).then(setAuction).catch((e) => setError(errorMessage(e)))
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  if (!id) return null

  const startEdit = (b: AuctionBidder) => {
    setEditingBidId(b.bidId); setEditAmount(String(b.amount)); setEditReason(''); setActionError('')
    setDqBidderId(null)
  }

  const submitEdit = async (e: FormEvent, b: AuctionBidder) => {
    e.preventDefault()
    setBusyBidderId(b.bidderId); setActionError('')
    try {
      await adminEditBid(b.bidId, Number(editAmount), editReason)
      setEditingBidId(null)
      load()
    } catch (err) { setActionError(errorMessage(err)) } finally { setBusyBidderId(null) }
  }

  const startDisqualify = (b: AuctionBidder) => {
    setDqBidderId(b.bidderId); setDqReason(''); setActionError('')
    setEditingBidId(null)
  }

  const confirmDisqualify = async (e: FormEvent, b: AuctionBidder) => {
    e.preventDefault()
    setBusyBidderId(b.bidderId); setActionError('')
    try {
      await adminDisqualifyBidder(id, b.bidderId, dqReason)
      setDqBidderId(null)
      load()
    } catch (err) { setActionError(errorMessage(err)) } finally { setBusyBidderId(null) }
  }

  const reinstate = async (b: AuctionBidder) => {
    if (!confirm(`Reinstate ${b.email} on this auction?`)) return
    setBusyBidderId(b.bidderId); setActionError('')
    try {
      await adminReinstateBidder(id, b.bidderId)
      load()
    } catch (err) { setActionError(errorMessage(err)) } finally { setBusyBidderId(null) }
  }

  return (
    <div>
      <AdminPageHeader
        section="Auctions"
        title={auction ? `Bidders — ${auction.title}` : 'Bidders'}
        subtitle={auction ? `Base price ${money(auction.basePrice)} · ${auction.status}` : undefined}
        actions={<button type="button" className="btn ghost sm" onClick={() => navigate(-1)}>← Back</button>}
      />
      <div className="card">
        {error && <div className="error">{error}</div>}
        {actionError && <div className="error">{actionError}</div>}
        {!error && !bidders && <p className="muted">Loading…</p>}
        {bidders && bidders.length === 0 && <p className="muted">No bidders on this auction yet.</p>}
        {bidders && bidders.length > 0 && (
          <div style={{ overflowX: 'auto' }}>
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Bidder</th>
                  <th>Best bid</th>
                  <th>Bids placed</th>
                  <th>Last bid</th>
                  <th>Status</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {bidders.map((b) => (
                  <tr key={b.bidderId} style={b.disqualified ? { opacity: 0.55 } : undefined}>
                    <td>
                      <button type="button" className="linkbtn" onClick={() => openUserDetails(b.bidderId)}>{b.email}</button>
                    </td>
                    <td>{money(b.amount)}</td>
                    <td>{b.bidCount}</td>
                    <td>{formatDateTimeShort(b.lastBidAt)}</td>
                    <td>
                      {b.disqualified ? <span className="badge CANCELLED">Disqualified</span>
                        : b.leading ? <span className="badge OPEN">Leading</span>
                        : <span className="badge UNSOLD">Outbid</span>}
                    </td>
                    <td>
                      {editingBidId === b.bidId ? (
                        <form onSubmit={(e) => submitEdit(e, b)} style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                          <input type="number" min={0.01} step="0.01" value={editAmount}
                                 onChange={(e) => setEditAmount(e.target.value)} required style={{ width: 120 }} />
                          <input placeholder="Reason (required)" value={editReason}
                                 onChange={(e) => setEditReason(e.target.value)} required style={{ width: 220 }} />
                          <button type="submit" className="btn sm" disabled={busyBidderId === b.bidderId}>
                            {busyBidderId === b.bidderId ? '…' : 'Save'}
                          </button>
                          <button type="button" className="btn ghost sm" onClick={() => setEditingBidId(null)}>Cancel</button>
                        </form>
                      ) : dqBidderId === b.bidderId ? (
                        <form onSubmit={(e) => confirmDisqualify(e, b)} style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                          <input placeholder="Reason (required)" value={dqReason}
                                 onChange={(e) => setDqReason(e.target.value)} required style={{ width: 220 }} />
                          <button type="submit" className="btn sm" disabled={busyBidderId === b.bidderId}>
                            {busyBidderId === b.bidderId ? '…' : 'Confirm'}
                          </button>
                          <button type="button" className="btn ghost sm" onClick={() => setDqBidderId(null)}>Cancel</button>
                        </form>
                      ) : (
                        <div style={{ display: 'flex', gap: 8 }}>
                          {!b.disqualified && (
                            <>
                              <button type="button" className="btn ghost sm" onClick={() => startEdit(b)}>Edit bid</button>
                              <button type="button" className="btn ghost sm" onClick={() => startDisqualify(b)}>Disqualify</button>
                            </>
                          )}
                          {b.disqualified && (
                            <button type="button" className="btn ghost sm" disabled={busyBidderId === b.bidderId} onClick={() => reinstate(b)}>
                              {busyBidderId === b.bidderId ? '…' : 'Reinstate'}
                            </button>
                          )}
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
