import { useEffect, useState } from 'react'
import { getGeoBreakdown, errorMessage, type GeoBreakdown as GeoBreakdownData, type GeoRow } from '../api'

function BarList({ rows }: { rows: GeoRow[] }) {
  const max = Math.max(...rows.map((r) => r.count), 1)
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {rows.map((r, i) => (
        <div key={r.name}>
          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 13, marginBottom: 4 }}>
            <span>{i < 8 ? `${i + 1}. ` : ''}{r.name}</span>
            <b>{r.count}</b>
          </div>
          <div style={{ height: 6, borderRadius: 999, background: 'var(--panel-2)', overflow: 'hidden' }}>
            <div style={{ width: `${(r.count / max) * 100}%`, height: '100%', background: 'var(--red)', borderRadius: 999 }} />
          </div>
        </div>
      ))}
      {rows.length === 0 && <p className="muted" style={{ fontSize: 13 }}>No KYC location data yet.</p>}
    </div>
  )
}

/** Reference dashboard's "Users by State" / "Users by District" panels — from real KYC
 *  address data (state/city), not fabricated geography. */
export default function GeoBreakdown() {
  const [data, setData] = useState<GeoBreakdownData | null>(null)
  const [error, setError] = useState('')

  useEffect(() => { getGeoBreakdown().then(setData).catch((e) => setError(errorMessage(e))) }, [])

  return (
    <div className="chart-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <h3 style={{ margin: 0, fontSize: 14 }}>Users by State</h3>
          {data && <span className="count-chip">{data.byState.length} states</span>}
        </div>
        {error && <div className="error">{error}</div>}
        {!data && !error && <p className="muted">Loading…</p>}
        {data && <BarList rows={data.byState} />}
      </div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <h3 style={{ margin: 0, fontSize: 14 }}>Users by District</h3>
          {data && <span className="count-chip">Top {data.byDistrict.length}</span>}
        </div>
        {data && <BarList rows={data.byDistrict} />}
      </div>
    </div>
  )
}
