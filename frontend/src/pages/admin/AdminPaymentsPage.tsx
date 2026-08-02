import { useEffect, useState } from 'react'
import { getAdminPayments, getSettlementStatus, errorMessage, type AdminPaymentOrder, type SettlementStatus } from '../../api'
import { money, formatDateTimeShort } from '../../util'
import { Pager, StatTile, AdminPageHeader } from './shared'

/** Reference site's "Payments" section — real Razorpay wallet top-up orders, admin-wide. */
export default function AdminPaymentsPage() {
  const [rows, setRows] = useState<AdminPaymentOrder[]>([])
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [settlement, setSettlement] = useState<SettlementStatus | null>(null)
  const [error, setError] = useState('')

  const load = () => {
    getAdminPayments(statusFilter || undefined, page).then((res) => {
      setRows(res.content); setTotalPages(res.totalPages); setTotalElements(res.totalElements)
    }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => { getSettlementStatus().then(setSettlement).catch(() => {}) }, [])
  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page])

  const succeededVolume = rows.filter((r) => r.status === 'SUCCEEDED').reduce((s, r) => s + r.amount, 0)

  return (
    <div>
      <AdminPageHeader section="Payments" title="Wallet Payments" subtitle="Razorpay top-up orders across every user's wallet." />

      <div className="stat-tiles">
        <StatTile label="Total orders" value={String(totalElements)} icon="🧾" color="blue" />
        <StatTile label="Settled (this page)" value={money(succeededVolume)} icon="💰" color="green" />
        <StatTile label="Payments gateway" value={settlement?.paymentsEnabled ? 'Configured' : 'Not configured'}
                  icon={settlement?.paymentsEnabled ? '✅' : '⚠️'} color={settlement?.paymentsEnabled ? 'green' : 'amber'} />
      </div>

      <div className="card">
        <div className="admin-filters">
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            <option value="PENDING">Pending</option>
            <option value="SUCCEEDED">Succeeded</option>
            <option value="FAILED">Failed</option>
          </select>
        </div>
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr><th>User</th><th>Amount</th><th>Purpose</th><th>Status</th><th>Created</th><th>Completed</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.userEmail}</td>
                  <td>{money(r.amount)}</td>
                  <td>{r.purpose.replace(/_/g, ' ')}</td>
                  <td><span className={`badge ${r.status}`}>{r.status}</span></td>
                  <td>{formatDateTimeShort(r.createdAt)}</td>
                  <td>{r.completedAt ? formatDateTimeShort(r.completedAt) : '—'}</td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={6} className="muted">No payment orders match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </div>
  )
}
