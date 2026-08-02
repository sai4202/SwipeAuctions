import { useEffect, useState } from 'react'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { getAdminAnalytics, errorMessage, type AdminAnalyticsData, type AnalyticsGranularity, type AnalyticsPoint } from '../api'
import { money, moneyCompact } from '../util'
import { ChartGridSkeleton } from './Skeleton'

const GRANULARITIES: { key: AnalyticsGranularity; label: string }[] = [
  { key: 'MONTHLY', label: 'Monthly' },
  { key: 'QUARTERLY', label: 'Quarterly' },
  { key: 'YEARLY', label: 'Yearly' },
]

/** One small-multiple line chart — single series, so no legend box (the title already names it),
 *  per the dataviz skill's rule that a lone series needs no legend. Thin 2px line, muted recessive
 *  grid/axes, a hover tooltip (every chart here is interactive by default). */
function MiniLineChart({ title, color, data, formatValue }: {
  title: string; color: string; data: AnalyticsPoint[]; formatValue: (v: number) => string
}) {
  return (
    <div className="card chart-card">
      <h3 className="chart-card-title">{title}</h3>
      <ResponsiveContainer width="100%" height={180}>
        <LineChart data={data} margin={{ top: 6, right: 10, left: 0, bottom: 0 }}>
          <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" stroke="var(--muted)" fontSize={11} tickLine={false} axisLine={{ stroke: 'var(--border)' }} />
          <YAxis stroke="var(--muted)" fontSize={11} tickLine={false} axisLine={false} width={44}
                 tickFormatter={(v: number) => moneyCompact(v).replace('₹', '')} />
          <Tooltip
            formatter={(v) => formatValue(Number(v))}
            contentStyle={{ background: 'var(--panel)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12.5 }}
            labelStyle={{ color: 'var(--text)', fontWeight: 700 }}
            itemStyle={{ color: 'var(--text)' }}
            cursor={{ stroke: 'var(--border)' }}
          />
          <Line type="monotone" dataKey="value" stroke={color} strokeWidth={2} dot={false}
                activeDot={{ r: 4, stroke: 'var(--panel)', strokeWidth: 2 }} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

export default function AdminAnalytics() {
  const [granularity, setGranularity] = useState<AnalyticsGranularity>('MONTHLY')
  const [data, setData] = useState<AdminAnalyticsData | null>(null)
  const [error, setError] = useState('')
  const [showTable, setShowTable] = useState(false)

  useEffect(() => {
    getAdminAnalytics(granularity).then(setData).catch((e) => setError(errorMessage(e)))
  }, [granularity])

  return (
    <div style={{ marginTop: 24 }}>
      <div className="section-head" style={{ marginBottom: 14 }}>
        <h2 style={{ fontSize: 16, margin: 0 }}>Trends</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          {GRANULARITIES.map((g) => (
            <button key={g.key} type="button" className={'btn sm' + (granularity === g.key ? '' : ' ghost')}
                    onClick={() => setGranularity(g.key)}>
              {g.label}
            </button>
          ))}
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      {!data ? <ChartGridSkeleton /> : (
        <>
          <div className="chart-grid">
            <MiniLineChart title="New Users" color="var(--green)" data={data.newUsers} formatValue={(v) => String(v)} />
            <MiniLineChart title="Stock Listed" color="var(--orange)" data={data.stockListed} formatValue={(v) => String(v)} />
            <MiniLineChart title="Stock Sold" color="var(--red-2)" data={data.stockSold} formatValue={(v) => String(v)} />
            <MiniLineChart title="GMV" color="var(--amber)" data={data.gmv} formatValue={(v) => money(v)} />
          </div>
          <button type="button" className="linkbtn" style={{ marginTop: 10 }} onClick={() => setShowTable((s) => !s)}>
            {showTable ? 'Hide data table' : 'Show data table'}
          </button>
          {showTable && (
            <div style={{ overflowX: 'auto', marginTop: 10 }}>
              <table className="admin-table">
                <thead><tr><th>Period</th><th>New Users</th><th>Stock Listed</th><th>Stock Sold</th><th>GMV</th></tr></thead>
                <tbody>
                  {data.newUsers.map((p, i) => (
                    <tr key={p.label}>
                      <td>{p.label}</td>
                      <td>{p.value}</td>
                      <td>{data.stockListed[i]?.value ?? 0}</td>
                      <td>{data.stockSold[i]?.value ?? 0}</td>
                      <td>{money(data.gmv[i]?.value ?? 0)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </div>
  )
}
