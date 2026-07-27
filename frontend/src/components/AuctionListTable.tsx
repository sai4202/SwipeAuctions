import { Link } from 'react-router-dom'
import { type Auction } from '../api'
import { money, cardImage, downloadValuation, effectiveStatus, formatDateTime } from '../util'

interface Props {
  auctions: Auction[]
  multiIds: string[]
  onToggleTray: (id: string) => void
}

/**
 * Flat "list view" rendering of an auction set — the table-style alternative to AuctionCard's
 * grid ("catalogue view"), toggled by the user on the browse/swipe-stock pages. The Action column
 * checks effectiveStatus (not just bidsRemaining) so closed/scheduled items never offer "Bid Now".
 */
export default function AuctionListTable({ auctions, multiIds, onToggleTray }: Props) {
  return (
    <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
      <div style={{ overflowX: 'auto', padding: 16 }}>
        <table className="admin-table">
          <thead>
            <tr>
              <th>Image</th><th>Details</th><th>Start Time</th><th>End Time</th>
              <th>Base Price</th><th>Current Bid</th><th>Bids</th><th>Bids Left</th><th>Action</th>
            </tr>
          </thead>
          <tbody>
            {auctions.map((a) => {
              const es = effectiveStatus(a)
              return (
                <tr key={a.id}>
                  <td>
                    <Link to={`/auctions/${a.id}`}>
                      <img src={cardImage(a)} alt={a.title} style={{ width: 70, height: 52, objectFit: 'cover', borderRadius: 8, display: 'block' }} />
                    </Link>
                    <button type="button" className="linkbtn" style={{ fontSize: 11 }} onClick={() => downloadValuation(a)}>⬇ Valuation</button>
                  </td>
                  <td>
                    <Link to={`/auctions/${a.id}`}><b>{a.title}</b></Link>
                    <div className="muted" style={{ fontSize: 12 }}>{[a.brand, a.condition.replace('_', ' ')].filter(Boolean).join(' · ')}</div>
                    <div className="muted" style={{ fontSize: 12 }}>◍ {[a.city, a.state].filter(Boolean).join(', ') || '—'}</div>
                  </td>
                  <td>{formatDateTime(a.startTime)}</td>
                  <td>{formatDateTime(a.currentEndTime)}</td>
                  <td>{money(a.basePrice)}</td>
                  <td>{a.currentHighestBid != null ? money(a.currentHighestBid) : '—'}</td>
                  <td>{a.bidCount}</td>
                  <td>
                    {a.bidsRemaining == null ? '—' : a.bidsRemaining === 0 ? (
                      <span style={{ color: 'var(--red-2)', fontWeight: 700 }}>None left</span>
                    ) : a.bidsRemaining}
                  </td>
                  <td>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 120 }}>
                      <Link to={`/auctions/${a.id}`} className="btn ghost sm">View Details</Link>
                      {es !== 'OPEN' ? (
                        <span className="btn sm" style={{ opacity: .55, pointerEvents: 'none' }}>
                          {es === 'SCHEDULED' ? 'Not started' : 'Closed'}
                        </span>
                      ) : a.bidsRemaining === 0 ? (
                        <span className="btn sm" style={{ opacity: .55, pointerEvents: 'none' }}>No bids left</span>
                      ) : (
                        <Link to={`/auctions/${a.id}?bid=1`} className="btn sm">Bid Now</Link>
                      )}
                      {multiIds.includes(a.id) ? (
                        <button type="button" className="btn light sm" onClick={() => onToggleTray(a.id)}>♥ In wishlist</button>
                      ) : (
                        <button type="button" className="btn ghost sm" onClick={() => onToggleTray(a.id)}>♡ Wishlist</button>
                      )}
                    </div>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
