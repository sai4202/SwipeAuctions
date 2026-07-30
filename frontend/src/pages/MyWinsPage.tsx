import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import { getMyWins, errorMessage, type Auction } from '../api'
import { money, cardImage, downloadValuation } from '../util'
import { useCachedFetch } from '../useCachedFetch'
import { SkeletonTableRows } from '../components/Skeleton'

function fmt(dt: string): string {
  return new Date(dt).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export default function MyWinsPage() {
  const { isAuthenticated } = useAuth()
  const [error, setError] = useState('')
  const [q, setQ] = useState('')
  const { data: wins = [], loading } = useCachedFetch<Auction[]>(
    isAuthenticated ? 'my-wins' : null, getMyWins, { onError: (e) => setError(errorMessage(e)) },
  )

  if (!isAuthenticated) {
    return <div className="container"><div className="card" style={{ maxWidth: 460 }}>Please <Link to="/login">sign in</Link> to view your wins.</div></div>
  }

  const needle = q.trim().toLowerCase()
  const filtered = wins.filter((a) => !needle || `${a.title} ${a.brand ?? ''}`.toLowerCase().includes(needle))

  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">My Wins</h1>
      </div>
      <div className="admin-filters" style={{ marginBottom: 10 }}>
        <input className="search-glow" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Search won items…" style={{ width: 220 }} />
      </div>
      {error && <div className="error">{error}</div>}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        {!loading && filtered.length === 0 ? (
          <p className="muted" style={{ padding: 16 }}>You haven't won any auctions yet.</p>
        ) : (
          <div style={{ overflowX: 'auto', padding: 16 }}>
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Image</th><th>Details</th><th>Start Time</th><th>End Time</th>
                  <th>Base Price</th><th>Current Bid</th><th>Bids</th><th>Action</th>
                </tr>
              </thead>
              <tbody>
                {loading && <SkeletonTableRows rows={4} cols={8} />}
                {filtered.map((a) => (
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
                    <td>{fmt(a.startTime)}</td>
                    <td>{fmt(a.currentEndTime)}</td>
                    <td>{money(a.basePrice)}</td>
                    <td>{a.currentHighestBid != null ? money(a.currentHighestBid) : '—'}</td>
                    <td>{a.bidCount}</td>
                    <td>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 120 }}>
                        <Link to={`/auctions/${a.id}`} className="btn ghost sm">View Details</Link>
                        <span className={`badge ${a.settlementPaid ? 'OPEN' : 'UNSOLD'}`}>
                          {a.settlementPaid ? 'Paid' : 'Balance Due'}
                        </span>
                      </div>
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
