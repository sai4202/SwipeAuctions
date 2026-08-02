import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAdminPayments, errorMessage, type AdminPaymentOrder } from '../api'
import { money } from '../util'

/** Reference dashboard's "Pending Payments" preview table — top 4, with a link to the full
 *  Payments queue. Reference labels these "Manual Review"; SwipeAuctions' equivalent is simply a
 *  PENDING Razorpay order (Razorpay hasn't confirmed yet), so that's the status shown. */
export default function PendingPaymentsPreview() {
  const [rows, setRows] = useState<AdminPaymentOrder[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getAdminPayments('PENDING', 0, 4).then((res) => setRows(res.content)).catch((e) => setError(errorMessage(e)))
  }, [])

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <h3 style={{ margin: 0, fontSize: 14 }}>Pending Payments</h3>
        <Link to="/admin/payments" className="linkbtn" style={{ fontSize: 12.5 }}>View all</Link>
      </div>
      {error && <div className="error">{error}</div>}
      <div style={{ overflowX: 'auto' }}>
        <table className="admin-table">
          <thead><tr><th>User</th><th>Amount</th><th>Status</th></tr></thead>
          <tbody>
            {rows?.map((p) => (
              <tr key={p.id}>
                <td>{p.userEmail}</td>
                <td>{money(p.amount)}</td>
                <td><span className="badge PENDING">Pending</span></td>
              </tr>
            ))}
            {rows && rows.length === 0 && <tr><td colSpan={3} className="muted">Nothing pending.</td></tr>}
            {!rows && !error && <tr><td colSpan={3} className="muted">Loading…</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}
