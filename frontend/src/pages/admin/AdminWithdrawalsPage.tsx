import { useEffect, useState } from 'react'
import { getAdminWithdrawals, getSettlementStatus, errorMessage, type AdminWithdrawal, type SettlementStatus } from '../../api'
import { money, formatDateTimeShort } from '../../util'
import { Pager, StatTile, AdminPageHeader } from './shared'

const TABS = [
  { key: '', label: 'All' },
  { key: 'PENDING', label: 'Pending' },
  { key: 'SUCCEEDED', label: 'Succeeded' },
  { key: 'FAILED', label: 'Failed' },
]

/** Reference site's "Withdrawals" (and "Pre-Maturity Exit", which turned out to alias the same
 *  page) section — seller Razorpay Payouts, admin-wide. Payouts submit to Razorpay synchronously
 *  the moment a seller requests one (WalletController#withdraw / RazorpayPaymentService#withdraw)
 *  — there's no pending-admin-approval step in the real money flow, so unlike the reference this is
 *  a monitoring log, not an approve/reject queue (a fake approve button here would do nothing). */
export default function AdminWithdrawalsPage() {
  const [rows, setRows] = useState<AdminWithdrawal[]>([])
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [settlement, setSettlement] = useState<SettlementStatus | null>(null)
  const [error, setError] = useState('')

  const load = () => {
    getAdminWithdrawals(statusFilter || undefined, page).then((res) => {
      setRows(res.content); setTotalPages(res.totalPages); setTotalElements(res.totalElements)
    }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => { getSettlementStatus().then(setSettlement).catch(() => {}) }, [])
  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page])

  return (
    <div>
      <AdminPageHeader section="Withdrawals" title="Withdrawal Requests"
                        subtitle="Seller payouts, submitted to Razorpay automatically — this is a monitoring log, not an approval queue." />

      <div className="stat-tiles">
        <StatTile label="Total requests" value={String(totalElements)} icon="🏦" color="purple" />
        <StatTile label="Payouts gateway" value={settlement?.payoutsEnabled ? 'Configured' : 'Not configured'}
                  icon={settlement?.payoutsEnabled ? '✅' : '⚠️'} color={settlement?.payoutsEnabled ? 'green' : 'amber'} />
      </div>

      <div className="card">
        <div className="tabs" style={{ marginBottom: 14 }}>
          {TABS.map((t) => (
            <button key={t.key} type="button" className={'tab' + (statusFilter === t.key ? ' active' : '')}
                    onClick={() => setStatusFilter(t.key)}>
              {t.label}
            </button>
          ))}
        </div>
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr><th>User</th><th>Amount</th><th>Status</th><th>Payout ref</th><th>Requested</th><th>Completed</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.userEmail}</td>
                  <td>{money(r.amount)}</td>
                  <td><span className={`badge ${r.status}`}>{r.status}</span></td>
                  <td className="muted" style={{ fontSize: 12 }}>{r.razorpayPayoutId ?? '—'}</td>
                  <td>{formatDateTimeShort(r.createdAt)}</td>
                  <td>{r.completedAt ? formatDateTimeShort(r.completedAt) : '—'}</td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={6} className="muted">No withdrawal requests match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </div>
  )
}
