import { useEffect, useState } from 'react'
import { getAdminHolds, releaseAdminHold, errorMessage, type AdminHoldFull } from '../../api'
import { money, formatDateTimeShort, openUserDetails } from '../../util'
import { Pager, StatTile, AdminPageHeader } from './shared'

/** Reference site calls this "Investments" (fixed-return deposit positions) — SwipeAuctions has no
 *  such product, so this is reframed onto the real equivalent: every EMD hold currently locking a
 *  bidder's wallet balance against a live auction, admin-wide instead of per-user. */
export default function AdminHoldsPage() {
  const [holds, setHolds] = useState<AdminHoldFull[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)

  const load = () => {
    getAdminHolds(page).then((res) => {
      setHolds(res.content); setTotalPages(res.totalPages); setTotalElements(res.totalElements)
    }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(load, [page])

  const release = async (h: AdminHoldFull) => {
    if (!confirm(`Release ${money(h.amount)} held for ${h.bidderEmail} on "${h.listingTitle}"?`)) return
    setBusyId(h.id); setError('')
    try {
      await releaseAdminHold(h.id)
      setHolds((prev) => prev.filter((x) => x.id !== h.id))
      setTotalElements((n) => n - 1)
    } catch (e) { setError(errorMessage(e)) } finally { setBusyId(null) }
  }

  const totalLocked = holds.reduce((sum, h) => sum + h.amount, 0)

  return (
    <div>
      <AdminPageHeader section="Credit &amp; Holds" title="Credit &amp; Holds Overview"
                        subtitle="Every EMD hold currently locking a bidder's wallet balance against a live auction." />

      <div className="stat-tiles">
        <StatTile label="Active holds" value={String(totalElements)} icon="🔒" color="purple" />
        <StatTile label="Locked (this page)" value={money(totalLocked)} icon="💰" color="amber" />
      </div>

      <div className="card">
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr><th>Bidder</th><th>Auction</th><th>Amount</th><th>Locked since</th><th></th></tr></thead>
            <tbody>
              {holds.map((h) => (
                <tr key={h.id}>
                  <td><button type="button" className="linkbtn" onClick={() => openUserDetails(h.bidderId)}>{h.bidderEmail}</button></td>
                  <td>{h.listingTitle}</td>
                  <td>{money(h.amount)}</td>
                  <td>{formatDateTimeShort(h.createdAt)}</td>
                  <td>
                    <button type="button" className="btn ghost sm" disabled={busyId === h.id} onClick={() => release(h)}>
                      {busyId === h.id ? '…' : 'Release'}
                    </button>
                  </td>
                </tr>
              ))}
              {holds.length === 0 && <tr><td colSpan={5} className="muted">No active holds.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </div>
  )
}
