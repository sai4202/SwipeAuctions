import { useEffect, useState } from 'react'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { getCashFlow, errorMessage, type CashFlowPoint, type CashFlowRange } from '../api'
import { moneyCompact, money } from '../util'

const RANGES: { key: CashFlowRange; label: string }[] = [
  { key: 'TODAY', label: 'Today' },
  { key: 'YESTERDAY', label: 'Yesterday' },
  { key: 'LAST_7_DAYS', label: 'Last 7 Days' },
  { key: 'LAST_30_DAYS', label: 'Last 30 Days' },
]

/** Reference dashboard's "Platform Cash Flow" chart — Deposits vs Withdrawals only (no
 *  "Investments" series, since that product doesn't exist on SwipeAuctions), real wallet-ledger
 *  data bucketed by day. */
export default function CashFlowChart() {
  const [range, setRange] = useState<CashFlowRange>('LAST_7_DAYS')
  const [data, setData] = useState<CashFlowPoint[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getCashFlow(range).then(setData).catch((e) => setError(errorMessage(e)))
  }, [range])

  const hasData = data && data.some((p) => p.deposits > 0 || p.withdrawals > 0)

  return (
    <div className="card chart-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 10, marginBottom: 4 }}>
        <div>
          <h3 className="chart-card-title" style={{ margin: 0 }}>Platform Cash Flow</h3>
          <p className="muted" style={{ fontSize: 11.5, margin: '2px 0 0' }}>All users — {RANGES.find((r) => r.key === range)?.label.toLowerCase()}</p>
        </div>
        <select value={range} onChange={(e) => setRange(e.target.value as CashFlowRange)} style={{ width: 'auto', fontSize: 12.5 }}>
          {RANGES.map((r) => <option key={r.key} value={r.key}>{r.label}</option>)}
        </select>
      </div>
      {error && <div className="error">{error}</div>}
      {!error && !data && <p className="muted">Loading…</p>}
      {data && !hasData && <p className="muted" style={{ textAlign: 'center', padding: '40px 0' }}>No transaction data available</p>}
      {data && hasData && (
        <ResponsiveContainer width="100%" height={220}>
          <LineChart data={data} margin={{ top: 6, right: 10, left: 0, bottom: 0 }}>
            <CartesianGrid stroke="var(--border)" strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="label" stroke="var(--muted)" fontSize={11} tickLine={false} axisLine={{ stroke: 'var(--border)' }} />
            <YAxis stroke="var(--muted)" fontSize={11} tickLine={false} axisLine={false} width={44}
                   tickFormatter={(v: number) => moneyCompact(v).replace('₹', '')} />
            <Tooltip
              formatter={(v) => money(Number(v))}
              contentStyle={{ background: 'var(--panel)', border: '1px solid var(--border)', borderRadius: 8, fontSize: 12.5 }}
              labelStyle={{ color: 'var(--text)', fontWeight: 700 }}
              itemStyle={{ color: 'var(--text)' }}
              cursor={{ stroke: 'var(--border)' }}
            />
            <Legend wrapperStyle={{ fontSize: 12 }} />
            <Line type="monotone" dataKey="deposits" name="Deposits" stroke="var(--green)" strokeWidth={2} dot={false} />
            <Line type="monotone" dataKey="withdrawals" name="Withdrawals" stroke="var(--red-2)" strokeWidth={2} dot={false} />
          </LineChart>
        </ResponsiveContainer>
      )}
    </div>
  )
}
