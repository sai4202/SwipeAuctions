import { useEffect, useState } from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { getAdminAnalytics, errorMessage, type AnalyticsPoint } from '../api'

/** Mirrors the reference dashboard's "User Registrations — Last 30 Days" widget, reusing the same
 *  analytics endpoint as the Trends charts (just the newUsers series, DAILY granularity). */
export default function RegistrationsChart() {
  const [data, setData] = useState<AnalyticsPoint[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getAdminAnalytics('DAILY').then((d) => setData(d.newUsers)).catch((e) => setError(errorMessage(e)))
  }, [])

  const total = data ? data.reduce((s, p) => s + p.value, 0) : 0

  return (
    <div className="card chart-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
        <h3 className="chart-card-title" style={{ margin: 0 }}>User Registrations — Last 30 Days</h3>
        <span className="count-chip">{total} total</span>
      </div>
      {error && <div className="error">{error}</div>}
      {!error && !data && <p className="muted">Loading…</p>}
      {data && (
        <ResponsiveContainer width="100%" height={200}>
          <BarChart data={data} margin={{ top: 6, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="label" stroke="var(--muted)" fontSize={10} tickLine={false} axisLine={{ stroke: 'var(--border)' }}
                   interval={Math.max(0, Math.floor(data.length / 8) - 1)} />
            <YAxis stroke="var(--muted)" fontSize={11} tickLine={false} axisLine={false} width={30} allowDecimals={false} />
            <Tooltip
              contentStyle={{ background: 'var(--panel)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12.5 }}
              labelStyle={{ color: 'var(--text)', fontWeight: 700 }}
              itemStyle={{ color: 'var(--text)' }}
              cursor={{ fill: 'var(--panel-2)' }}
            />
            <Bar dataKey="value" fill="var(--red)" radius={[3, 3, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
