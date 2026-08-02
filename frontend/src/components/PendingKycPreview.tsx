import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getAdminKycQueue, errorMessage, type AdminKyc } from '../api'

/** Reference dashboard's "Pending KYC Submissions" preview table — top 5, with a link to the full
 *  KYC queue for the rest. */
export default function PendingKycPreview() {
  const [rows, setRows] = useState<AdminKyc[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getAdminKycQueue('PENDING', 0, 5).then((res) => setRows(res.content)).catch((e) => setError(errorMessage(e)))
  }, [])

  return (
    <div className="card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
        <h3 style={{ margin: 0, fontSize: 14 }}>Pending KYC Submissions</h3>
        <Link to="/admin/kyc" className="linkbtn" style={{ fontSize: 12.5 }}>View all</Link>
      </div>
      {error && <div className="error">{error}</div>}
      <div style={{ overflowX: 'auto' }}>
        <table className="admin-table">
          <thead><tr><th>User</th><th>Email</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {rows?.map((k) => (
              <tr key={k.userId}>
                <td>{k.fullName ?? '—'}</td>
                <td>{k.email}</td>
                <td><span className="badge PENDING">pending</span></td>
                <td><Link to="/admin/kyc" className="btn ghost sm">Review</Link></td>
              </tr>
            ))}
            {rows && rows.length === 0 && <tr><td colSpan={4} className="muted">Nothing pending.</td></tr>}
            {!rows && !error && <tr><td colSpan={4} className="muted">Loading…</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}
