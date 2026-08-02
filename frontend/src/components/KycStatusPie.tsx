import { useEffect, useState } from 'react'
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { getAdminKycQueue, errorMessage } from '../api'

const SLICES: { key: 'APPROVED' | 'PENDING' | 'REJECTED'; label: string; color: string }[] = [
  { key: 'APPROVED', label: 'Verified', color: 'var(--green)' },
  { key: 'PENDING', label: 'Pending', color: 'var(--amber)' },
  { key: 'REJECTED', label: 'Rejected', color: 'var(--red-2)' },
]

/** KYC verification mix as a donut, mirroring the reference dashboard's "KYC Users Overview"
 *  panel — counts come from the same paginated KYC queue every other admin KYC view uses
 *  (just reading `totalElements` per status instead of the rows), so there's no new endpoint. */
export default function KycStatusPie() {
  const [counts, setCounts] = useState<Record<string, number> | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    Promise.all(SLICES.map((s) => getAdminKycQueue(s.key, 0, 1)))
      .then((results) => {
        const next: Record<string, number> = {}
        results.forEach((r, i) => { next[SLICES[i].key] = r.totalElements })
        setCounts(next)
      })
      .catch((e) => setError(errorMessage(e)))
  }, [])

  const total = counts ? Object.values(counts).reduce((a, b) => a + b, 0) : 0
  const data = counts ? SLICES.map((s) => ({ name: s.label, value: counts[s.key] ?? 0, color: s.color })) : []

  return (
    <div className="card chart-card">
      <h3 className="chart-card-title">KYC Users Overview</h3>
      <p className="muted" style={{ fontSize: 12, margin: '-6px 0 6px' }}>{total} total registered</p>
      {error && <div className="error">{error}</div>}
      {!error && !counts && <p className="muted">Loading…</p>}
      {counts && total === 0 && <p className="muted">No user data.</p>}
      {counts && total > 0 && (
        <ResponsiveContainer width="100%" height={200}>
          <PieChart>
            <Pie data={data} dataKey="value" nameKey="name" innerRadius={50} outerRadius={78} paddingAngle={2}>
              {data.map((d) => <Cell key={d.name} fill={d.color} />)}
            </Pie>
            <Tooltip
              contentStyle={{ background: 'var(--panel)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12.5 }}
              labelStyle={{ color: 'var(--text)', fontWeight: 700 }}
              itemStyle={{ color: 'var(--text)' }}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
          </PieChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
