import { useEffect, useState } from 'react'
import { getAdminKycQueue, approveKyc, rejectKyc, errorMessage, type AdminKyc } from '../../api'
import SortableTh from '../../components/SortableTh'
import { useSortableData } from '../../useSort'
import { getKycSortValue, Pager, AdminPageHeader } from './shared'

export default function AdminKycPage() {
  const [rows, setRows] = useState<AdminKyc[]>([])
  const [statusFilter, setStatusFilter] = useState('PENDING')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [selected, setSelected] = useState<AdminKyc | null>(null)
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const { sorted: sortedRows, sortKey, sortDir, toggleSort } = useSortableData(rows, getKycSortValue)

  const load = () => {
    getAdminKycQueue(statusFilter || undefined, page).then((res) => { setRows(res.content); setTotalPages(res.totalPages) }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page])

  const open = (k: AdminKyc) => { setSelected(k); setRemarks(k.remarks ?? ''); setError('') }

  const decide = async (approve: boolean) => {
    if (!selected) return
    if (!approve && !remarks.trim()) { setError('Remarks are required to reject.'); return }
    setBusy(true); setError('')
    try {
      const updated = approve ? await approveKyc(selected.userId, remarks || undefined) : await rejectKyc(selected.userId, remarks)
      setRows((prev) => prev.map((x) => (x.userId === updated.userId ? updated : x)))
      setSelected(updated)
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <div>
      <AdminPageHeader section="KYC" title="KYC Review" subtitle="Identity verification queue — approve or reject with remarks." />
      <div className="card">
        <div className="admin-filters">
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            <option value="PENDING">Pending</option>
            <option value="APPROVED">Approved</option>
            <option value="REJECTED">Rejected</option>
          </select>
        </div>
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr>
              <SortableTh label="Email" sortKey="email" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Full name" sortKey="fullName" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Aadhaar" sortKey="aadhaar" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="PAN" sortKey="pan" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Provider" sortKey="provider" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Status" sortKey="status" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Submitted" sortKey="submitted" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
            </tr></thead>
            <tbody>
              {sortedRows.map((k) => (
                <tr key={k.userId} onClick={() => open(k)} style={{ cursor: 'pointer' }}>
                  <td>{k.email}</td>
                  <td>{k.fullName ?? '—'}</td>
                  <td>{k.aadhaarMasked ?? '—'}</td>
                  <td>{k.panNumberMasked ?? '—'}</td>
                  <td>{k.provider ?? '—'}</td>
                  <td>{k.status}</td>
                  <td>{k.submittedAt ? new Date(k.submittedAt).toLocaleString() : '—'}</td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={7} className="muted">No KYC submissions match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />

        {selected && (
          <div style={{ marginTop: 20, paddingTop: 18, borderTop: '1px dashed var(--border)' }}>
            <h2 style={{ fontSize: 15, margin: '0 0 6px' }}>{selected.email} — {selected.status}</h2>
            <p className="muted" style={{ fontSize: 13 }}>
              {selected.fullName ?? '—'}, {selected.dateOfBirth ?? '—'} · {selected.address ?? '—'}, {selected.city ?? '—'},{' '}
              {selected.state ?? '—'} {selected.pincode ?? ''}
            </p>
            <label>Remarks {selected.status === 'PENDING' && '(required to reject)'}</label>
            <textarea style={{ width: '100%', minHeight: 70, fontFamily: 'inherit' }} value={remarks}
                      onChange={(e) => setRemarks(e.target.value)} disabled={selected.status !== 'PENDING'} />
            {selected.status === 'PENDING' && (
              <div style={{ marginTop: 12, display: 'flex', gap: 10 }}>
                <button type="button" className="btn sm" disabled={busy} onClick={() => decide(true)}>
                  {busy ? 'Saving…' : 'Approve'}
                </button>
                <button type="button" className="btn ghost sm" disabled={busy} onClick={() => decide(false)}>
                  {busy ? 'Saving…' : 'Reject'}
                </button>
              </div>
            )}
            {selected.reviewedBy && (
              <p className="muted" style={{ fontSize: 12.5, marginTop: 8 }}>Reviewed by {selected.reviewedBy}.</p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
