import { useEffect, useState } from 'react'
import { getAdminTransactions, errorMessage, type AdminTransaction } from '../../api'
import { money, formatDateTimeShort } from '../../util'
import { Pager, AdminPageHeader } from './shared'

const TYPES = ['TOPUP', 'HOLD', 'RELEASE', 'CAPTURE', 'DEBIT', 'PAYOUT', 'WITHDRAWAL', 'REFUND', 'SALE_PROCEEDS', 'REFERRAL_BONUS']

/** Reference site's "Transactions" section — the full wallet ledger, admin-wide instead of scoped
 *  to one user (the per-user version already exists and powers MyTransactionsPage). */
export default function AdminTransactionsPage() {
  const [rows, setRows] = useState<AdminTransaction[]>([])
  const [typeFilter, setTypeFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [error, setError] = useState('')

  const load = () => {
    getAdminTransactions(typeFilter || undefined, page).then((res) => {
      setRows(res.content); setTotalPages(res.totalPages); setTotalElements(res.totalElements)
    }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [typeFilter])
  useEffect(load, [typeFilter, page])

  return (
    <div>
      <AdminPageHeader section="Transactions" title="Wallet Transactions" subtitle={`${totalElements} ledger entries across every wallet.`} />

      <div className="card">
        <div className="admin-filters">
          <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
            <option value="">All types</option>
            {TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr><th>User</th><th>Type</th><th>Amount</th><th>Reference</th><th>Date &amp; Time</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.userEmail}</td>
                  <td>{r.type}</td>
                  <td>{money(r.amount)}</td>
                  <td className="muted" style={{ fontSize: 12 }}>{r.referenceType ? `${r.referenceType} · ${r.referenceId}` : '—'}</td>
                  <td>{formatDateTimeShort(r.createdAt)}</td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={5} className="muted">No transactions match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </div>
  )
}
