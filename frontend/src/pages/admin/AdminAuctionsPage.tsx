import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import {
  getAdminAuctions, forceCloseAuction, updateAuction, errorMessage,
  type AdminAuction,
} from '../../api'
import { money, openUserDetails } from '../../util'
import SortableTh from '../../components/SortableTh'
import { useSortableData } from '../../useSort'
import { getAuctionSortValue, toLocalInput, Pager, AdminPageHeader } from './shared'
import type { AdminOutletContext } from '../../components/AdminLayout'

export default function AdminAuctionsPage() {
  const { refreshTick } = useOutletContext<AdminOutletContext>()
  const navigate = useNavigate()
  const [auctions, setAuctions] = useState<AdminAuction[]>([])
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [editing, setEditing] = useState<AdminAuction | null>(null)
  const { sorted: sortedAuctions, sortKey, sortDir, toggleSort } = useSortableData(auctions, getAuctionSortValue)

  const load = () => {
    getAdminAuctions(statusFilter || undefined, page).then((res) => { setAuctions(res.content); setTotalPages(res.totalPages) }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page, refreshTick])

  const doForceClose = async (a: AdminAuction) => {
    if (!confirm(`Force-close "${a.title}" now? This settles it immediately at the current highest bid.`)) return
    setBusyId(a.id); setError('')
    try {
      const updated = await forceCloseAuction(a.id)
      setAuctions((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
    } catch (e) { setError(errorMessage(e)) } finally { setBusyId(null) }
  }

  const canModify = (a: AdminAuction) => (a.status === 'SCHEDULED' || a.status === 'OPEN') && a.bidCount === 0

  return (
    <div>
      <AdminPageHeader section="Auctions" title="Auctions" subtitle="Live and scheduled auctions across the platform." />
      <div className="card">
        <div className="admin-filters">
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="OPEN">Open</option>
            <option value="CLOSED">Closed</option>
            <option value="UNSOLD">Unsold</option>
            <option value="CANCELLED">Cancelled</option>
          </select>
        </div>
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr>
              <SortableTh label="Title" sortKey="title" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Seller" sortKey="seller" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Base" sortKey="base" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Highest bid" sortKey="highest" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Bids" sortKey="bids" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Status" sortKey="status" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
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
                  <td><span className={`badge ${a.status}`}>{a.status}</span></td>
                  <td style={{ display: 'flex', gap: 8 }}>
                    {canModify(a) && (
                      <button type="button" className="btn ghost sm" onClick={() => setEditing(a)}>Modify</button>
                    )}
                    {a.status === 'OPEN' && (
                      <button type="button" className="btn ghost sm" disabled={busyId === a.id} onClick={() => doForceClose(a)}>
                        {busyId === a.id ? '…' : 'Force close'}
                      </button>
                    )}
                    {a.bidCount > 0 && (
                      <button type="button" className="btn ghost sm" onClick={() => navigate(`/admin/auctions/${a.id}/bidders`)}>See Bidders</button>
                    )}
                  </td>
                </tr>
              ))}
              {auctions.length === 0 && <tr><td colSpan={7} className="muted">No auctions match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />

        {editing && (
          <ModifyAuctionModal
            auction={editing}
            onClose={() => setEditing(null)}
            onSaved={(updated) => {
              setAuctions((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
              setEditing(null)
            }}
          />
        )}
      </div>
    </div>
  )
}

function ModifyAuctionModal({ auction, onClose, onSaved }: {
  auction: AdminAuction
  onClose: () => void
  onSaved: (updated: AdminAuction) => void
}) {
  const [title, setTitle] = useState(auction.title)
  const [basePrice, setBasePrice] = useState(String(auction.basePrice))
  const [startTime, setStartTime] = useState(toLocalInput(auction.startTime))
  const [endTime, setEndTime] = useState(toLocalInput(auction.currentEndTime))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true); setError('')
    try {
      const updated = await updateAuction(auction.id, {
        title, basePrice: Number(basePrice), startTime, endTime,
      })
      onSaved(updated)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>Modify auction</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {error && <div className="error">{error}</div>}
            <div className="fgroup">
              <small>Title</small>
              <input value={title} onChange={(e) => setTitle(e.target.value)} required />
            </div>
            <div className="fgroup">
              <small>Base price (₹)</small>
              <input type="number" value={basePrice} onChange={(e) => setBasePrice(e.target.value)} required min={0} />
            </div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <div className="fgroup" style={{ flex: 1, minWidth: 200 }}>
                <small>Start time</small>
                <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
              </div>
              <div className="fgroup" style={{ flex: 1, minWidth: 200 }}>
                <small>End time</small>
                <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} required />
              </div>
            </div>
            <p className="muted" style={{ fontSize: 12.5 }}>Only available before any bid has been placed.</p>
            <button type="submit" className="btn" disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button>
          </form>
        </div>
      </div>
    </div>
  )
}
