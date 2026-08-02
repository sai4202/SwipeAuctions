import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useSearchParams } from 'react-router-dom'
import { useAuth } from '../auth'
import { getAuctions, errorMessage, type Auction } from '../api'
import { addMultiBid, removeMultiBid, getMultiBidIds, MULTI_KEY } from '../util'
import { useCachedFetch } from '../useCachedFetch'
import AuctionCard from '../components/AuctionCard'
import AuctionListTable from '../components/AuctionListTable'
import { AuctionGridSkeleton, SkeletonTableRows } from '../components/Skeleton'

export default function MyBidsPage() {
  const { isAuthenticated } = useAuth()
  const [error, setError] = useState('')
  const [params, setParams] = useSearchParams()
  const view = params.get('view') === 'list' ? 'list' : 'catalogue'
  const setView = (v: string) => {
    const p = new URLSearchParams(params)
    if (v) p.set('view', v); else p.delete('view')
    setParams(p, { replace: true })
  }

  // Shares the 'auctions' cache key with BrowsePage/AuctionDetailPage — no extra fetch, and every
  // auction already carries the viewer's own yourBid, so no dedicated backend endpoint is needed.
  const { data: all = [], loading } = useCachedFetch<Auction[]>(
    isAuthenticated ? 'auctions' : null, getAuctions, { onError: (e) => setError(errorMessage(e)) },
  )
  const myBids = all.filter((a) => a.yourBid != null)

  const [multiIds, setMultiIds] = useState<string[]>(getMultiBidIds())
  const toggleMulti = (id: string) => {
    setMultiIds(multiIds.includes(id) ? removeMultiBid(id) : addMultiBid(id))
  }
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== null && e.key !== MULTI_KEY) return
      setMultiIds(getMultiBidIds())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  if (!isAuthenticated) {
    return <div className="container"><div className="card" style={{ maxWidth: 460 }}>Please <Link to="/login">sign in</Link> to view your bids.</div></div>
  }

  return (
    <div className={`container${view === 'list' ? ' container-wide' : ''}`}>
      <div className="section-head">
        <h1 className="page">Your Bids</h1>
        {!loading && !error && (
          <div className="view-toggle">
            <button
              type="button" className={view === 'catalogue' ? 'active' : ''}
              onClick={() => setView('')} title="Catalogue view" aria-label="Catalogue view"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="3" width="8" height="8" rx="1.5" /><rect x="13" y="3" width="8" height="8" rx="1.5" />
                <rect x="3" y="13" width="8" height="8" rx="1.5" /><rect x="13" y="13" width="8" height="8" rx="1.5" />
              </svg>
            </button>
            <button
              type="button" className={view === 'list' ? 'active' : ''}
              onClick={() => setView('list')} title="List view" aria-label="List view"
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <line x1="4" y1="6" x2="20" y2="6" /><line x1="4" y1="12" x2="20" y2="12" /><line x1="4" y1="18" x2="20" y2="18" />
              </svg>
            </button>
          </div>
        )}
      </div>

      <div className="results">
        {error && <div className="error">{error}</div>}
        {loading ? (
          view === 'list' ? (
            <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
              <div style={{ overflowX: 'auto', padding: 16 }}>
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>Image</th><th>Details</th><th>Start Time</th><th>End Time</th>
                      <th>Base Price</th><th>Current Bid</th><th>Bids</th><th>Bids Left</th><th>Action</th>
                    </tr>
                  </thead>
                  <tbody><SkeletonTableRows rows={5} cols={9} /></tbody>
                </table>
              </div>
            </div>
          ) : (
            <AuctionGridSkeleton />
          )
        ) : myBids.length === 0 ? (
          <p className="muted">You haven't placed any bids yet.</p>
        ) : view === 'list' ? (
          <AuctionListTable auctions={myBids} multiIds={multiIds} onToggleTray={toggleMulti} />
        ) : (
          <div className="grid">
            {myBids.map((a) => (
              <AuctionCard key={a.id} auction={a} inTray={multiIds.includes(a.id)} onToggleTray={toggleMulti} />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
