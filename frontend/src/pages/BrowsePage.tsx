import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAuctions, errorMessage, type Auction } from '../api'
import { formatCountdown, msUntil, money } from '../util'

const STATUSES = ['', 'OPEN', 'SCHEDULED', 'CLOSED']
const LABEL: Record<string, string> = { '': 'All', OPEN: 'Live', SCHEDULED: 'Upcoming', CLOSED: 'Closed' }

export default function BrowsePage() {
  const [auctions, setAuctions] = useState<Auction[]>([])
  const [status, setStatus] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [, setTick] = useState(0)

  useEffect(() => {
    setLoading(true)
    getAuctions(status || undefined)
      .then(setAuctions)
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
  }, [status])

  // Tick every second so countdowns update.
  useEffect(() => {
    const t = setInterval(() => setTick((x) => x + 1), 1000)
    return () => clearInterval(t)
  }, [])

  return (
    <div>
      <h1>Browse auctions</h1>
      <div className="tabs">
        {STATUSES.map((s) => (
          <button key={s} className={`tab ${status === s ? 'active' : ''}`} onClick={() => setStatus(s)}>
            {LABEL[s]}
          </button>
        ))}
      </div>
      {error && <div className="error">{error}</div>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : auctions.length === 0 ? (
        <p className="muted">No auctions here yet.</p>
      ) : (
        <div className="grid">
          {auctions.map((a) => (
            <Link key={a.id} to={`/auctions/${a.id}`} className="card auction-card">
              <div className="row">
                <span className={`badge ${a.status}`}>{a.status}</span>
                {a.status === 'OPEN' && <span className="countdown">{formatCountdown(msUntil(a.currentEndTime))}</span>}
              </div>
              <h3>{a.title}</h3>
              <div className="row">
                <span className="muted">{a.currentHighestBid != null ? 'Current bid' : 'Starting'}</span>
                <span className="price">{money(a.currentHighestBid ?? a.basePrice)}</span>
              </div>
              <div className="muted">{a.bidCount} bid{a.bidCount === 1 ? '' : 's'}</div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
