import { useEffect, useState } from 'react'
import { getAdminDisputes, resolveDispute, errorMessage, type Dispute } from '../../api'
import SortableTh from '../../components/SortableTh'
import { useSortableData } from '../../useSort'
import { getDisputeSortValue, Pager, AdminPageHeader } from './shared'

export default function AdminDisputesPage() {
  const [disputes, setDisputes] = useState<Dispute[]>([])
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [selected, setSelected] = useState<Dispute | null>(null)
  const [notes, setNotes] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const { sorted: sortedDisputes, sortKey, sortDir, toggleSort } = useSortableData(disputes, getDisputeSortValue)

  const load = () => {
    getAdminDisputes(statusFilter || undefined, page).then((res) => { setDisputes(res.content); setTotalPages(res.totalPages) }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page])

  const open = (d: Dispute) => { setSelected(d); setNotes(d.adminNotes ?? ''); setError('') }

  const resolve = async (refundBuyer: boolean) => {
    if (!selected) return
    setBusy(true); setError('')
    try {
      const updated = await resolveDispute(selected.id, notes, refundBuyer)
      setDisputes((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
      setSelected(updated)
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <div>
      <AdminPageHeader section="Disputes" title="Disputes" subtitle="Buyer/seller disputes awaiting review or resolution." />
      <div className="card">
        <div className="admin-filters">
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            <option value="OPEN">Open</option>
            <option value="IN_REVIEW">In review</option>
            <option value="RESOLVED">Resolved</option>
          </select>
        </div>
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr>
              <SortableTh label="Auction" sortKey="auction" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Raised by" sortKey="raisedBy" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Reason" sortKey="reason" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Status" sortKey="status" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
            </tr></thead>
            <tbody>
              {sortedDisputes.map((d) => (
                <tr key={d.id} onClick={() => open(d)} style={{ cursor: 'pointer' }}>
                  <td>{d.auctionTitle}</td>
                  <td>{d.raisedByEmail}</td>
                  <td style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{d.reason}</td>
                  <td>{d.status}</td>
                </tr>
              ))}
              {disputes.length === 0 && <tr><td colSpan={4} className="muted">No disputes match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />

        {selected && (
          <div style={{ marginTop: 20, paddingTop: 18, borderTop: '1px dashed var(--border)' }}>
            <h2 style={{ fontSize: 15, margin: '0 0 6px' }}>{selected.auctionTitle} — {selected.status}</h2>
            <p className="muted">Raised by {selected.raisedByEmail}: {selected.reason}</p>
            <label>Admin notes</label>
            <textarea style={{ width: '100%', minHeight: 70, fontFamily: 'inherit' }} value={notes}
                      onChange={(e) => setNotes(e.target.value)} disabled={selected.status === 'RESOLVED'} />
            {selected.status !== 'RESOLVED' && (
              <div style={{ marginTop: 12, display: 'flex', gap: 10 }}>
                <button type="button" className="btn sm" disabled={busy} onClick={() => resolve(false)}>
                  {busy ? 'Saving…' : 'Resolve — release to seller'}
                </button>
                <button type="button" className="btn ghost sm" disabled={busy} onClick={() => resolve(true)}>
                  {busy ? 'Saving…' : 'Resolve — refund buyer'}
                </button>
              </div>
            )}
            <p className="muted" style={{ fontSize: 12.5, marginTop: 8 }}>
              Only affects money if the auction's sale proceeds are still escrowed (not yet auto-released or already withdrawn).
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
