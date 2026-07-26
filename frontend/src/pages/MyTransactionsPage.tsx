import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import { getMyTransactions, errorMessage, type WalletTransaction } from '../api'
import { money } from '../util'
import { useCachedFetch } from '../useCachedFetch'
import { SkeletonTableRows } from '../components/Skeleton'

function fmt(dt: string): string {
  return new Date(dt).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export default function MyTransactionsPage() {
  const { isAuthenticated } = useAuth()
  const [error, setError] = useState('')
  const { data: txns = [], loading } = useCachedFetch<WalletTransaction[]>(
    isAuthenticated ? 'my-transactions' : null, getMyTransactions, { onError: (e) => setError(errorMessage(e)) },
  )

  if (!isAuthenticated) {
    return <div className="container"><div className="card" style={{ maxWidth: 460 }}>Please <Link to="/login">sign in</Link> to view your transactions.</div></div>
  }

  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">My Transactions</h1>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        {!loading && txns.length === 0 ? (
          <p className="muted" style={{ padding: 16 }}>No wallet transactions yet.</p>
        ) : (
          <div style={{ overflowX: 'auto', padding: 16 }}>
            <table className="admin-table">
              <thead>
                <tr><th>Date</th><th>Type</th><th>Amount</th><th>Reference</th></tr>
              </thead>
              <tbody>
                {loading && <SkeletonTableRows rows={5} cols={4} />}
                {txns.map((t) => (
                  <tr key={t.id}>
                    <td>{fmt(t.createdAt)}</td>
                    <td><span className="badge OPEN">{t.type}</span></td>
                    <td>{money(t.amount)}</td>
                    <td className="muted">{[t.referenceType, t.referenceId].filter(Boolean).join(' · ') || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
