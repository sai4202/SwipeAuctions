import { useEffect, useState } from 'react'
import { getAdminReferrals, errorMessage, type AdminReferralDashboard } from '../../api'
import { StatTile, AdminPageHeader } from './shared'

/** Reference site's "Referral Dashboard" — real, non-monetary invite tracking (see
 *  ReferralController): a referral code is just the referrer's own user id, captured client-side
 *  right after a new account first signs in. No commission/payout tree, unlike the reference. */
export default function AdminReferralsPage() {
  const [data, setData] = useState<AdminReferralDashboard | null>(null)
  const [search, setSearch] = useState('')
  const [error, setError] = useState('')

  useEffect(() => { getAdminReferrals().then(setData).catch((e) => setError(errorMessage(e))) }, [])

  const filtered = data?.referrers.filter((r) => r.email.toLowerCase().includes(search.toLowerCase())) ?? []

  return (
    <div>
      <AdminPageHeader section="Referrals" title="Referral Dashboard" subtitle="Who's inviting new users, and how many have joined." />

      <div className="stat-tiles">
        <StatTile label="Total users" value={String(data?.totalUsers ?? '—')} icon="👥" color="purple" />
        <StatTile label="Total referrals made" value={String(data?.totalReferralsMade ?? '—')} icon="🔗" color="blue" />
        <StatTile label="Top referrer" value={data?.topReferrerEmail ?? '—'} icon="🏆" color="amber" />
      </div>

      <div className="card">
        {error && <div className="error">{error}</div>}
        <div className="admin-filters">
          <input className="search-glow" placeholder="Search by email…" value={search} onChange={(e) => setSearch(e.target.value)} />
        </div>
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr><th>#</th><th>Email</th><th>Referral code</th><th>Total referrals</th></tr></thead>
            <tbody>
              {filtered.map((r, i) => (
                <tr key={r.userId}>
                  <td>{i + 1}</td>
                  <td>{r.email}</td>
                  <td className="muted" style={{ fontSize: 12 }}>{r.userId}</td>
                  <td>{r.totalReferrals}</td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={4} className="muted">No referrals yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
