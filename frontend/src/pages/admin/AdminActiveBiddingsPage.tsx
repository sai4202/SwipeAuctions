import { useEffect, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { getAdminAuctions, errorMessage, type AdminAuction } from '../../api'
import { money, openUserDetails, formatDateTimeShort } from '../../util'
import SortableTh from '../../components/SortableTh'
import { useSortableData } from '../../useSort'
import { getAuctionSortValue, Pager, AdminPageHeader } from './shared'
import type { AdminOutletContext } from '../../components/AdminLayout'

/** Every auction currently OPEN with at least one bid — reached from the global nav (admin-only),
 *  so "what's actually being bid on right now" is one click from anywhere, not just after drilling
 *  into the full Auctions table and sorting by bids. Filtered server-side (AuctionRepository
 *  #findByStatusAndHasBids), so pagination stays correct unlike a client-side bidCount>0 filter. */
export default function AdminActiveBiddingsPage() {
  const { refreshTick } = useOutletContext<AdminOutletContext>()
  const [auctions, setAuctions] = useState<AdminAuction[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [error, setError] = useState('')
  const { sorted: sortedAuctions, sortKey, sortDir, toggleSort } = useSortableData(auctions, getAuctionSortValue)

  const load = () => {
    getAdminAuctions('OPEN', page, 20, true)
      .then((res) => { setAuctions(res.content); setTotalPages(res.totalPages) })
      .catch((e) => setError(errorMessage(e)))
  }

  useEffect(load, [page, refreshTick])

  return (
    <div>
      <AdminPageHeader section="Active Biddings" title="Active Biddings" subtitle="Every open auction currently being bid on." />
      <div className="card">
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr>
              <SortableTh label="Title" sortKey="title" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Seller" sortKey="seller" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Base" sortKey="base" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Highest bid" sortKey="highest" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Bids" sortKey="bids" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <th>Ends</th>
              <th></th>
            </tr></thead>
            <tbody>
              {sortedAuctions.map((a) => (
                <tr key={a.id}>
                  <td>{a.title}</td>
                  <td>{a.sellerEmail}</td>
                  <td>{money(a.basePrice)}</td>
                  <td>
                    {a.currentWinnerId ? (
                      <button type="button" className="linkbtn" title={a.currentWinnerEmail ?? undefined}
                              onClick={() => openUserDetails(a.currentWinnerId!)}>
                        {money(a.currentHighestBid)}
                      </button>
                    ) : money(a.currentHighestBid)}
                  </td>
                  <td>{a.bidCount}</td>
                  <td>{formatDateTimeShort(a.currentEndTime)}</td>
                  <td>
                    <Link to={`/admin/auctions/${a.id}/bidders`} className="btn ghost sm">View Bidders</Link>
                  </td>
                </tr>
              ))}
              {auctions.length === 0 && <tr><td colSpan={7} className="muted">No active biddings right now.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </div>
  )
}
