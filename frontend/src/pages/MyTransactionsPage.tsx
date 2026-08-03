import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth'
import { getMyTransactions, errorMessage, type WalletTransaction } from '../api'
import { money } from '../util'
import { useCachedFetch } from '../useCachedFetch'
import { SkeletonTableRows } from '../components/Skeleton'
import SortableTh from '../components/SortableTh'
import { useSortableData } from '../useSort'
import FilterPill from '../components/FilterPill'
import FilterModal, { CheckboxListBody } from '../components/FilterModal'

const TXN_TYPES = ['TOPUP', 'HOLD', 'RELEASE', 'CAPTURE', 'DEBIT', 'PAYOUT', 'WITHDRAWAL', 'REFUND', 'SALE_PROCEEDS', 'REFERRAL_BONUS']

function fmt(dt: string): string {
  return new Date(dt).toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function getSortValue(t: WalletTransaction, key: string) {
  switch (key) {
    case 'date': return t.createdAt
    case 'type': return t.type
    case 'amount': return t.amount
    case 'reference': return [t.referenceType, t.referenceId].filter(Boolean).join(' · ')
    default: return null
  }
}

export default function MyTransactionsPage() {
  const { isAuthenticated } = useAuth()
  const [error, setError] = useState('')
  const { data: txns = [], loading } = useCachedFetch<WalletTransaction[]>(
    isAuthenticated ? 'my-transactions' : null, getMyTransactions, { onError: (e) => setError(errorMessage(e)) },
  )

  const [refQ, setRefQ] = useState('')
  const [types, setTypes] = useState<string[]>([])
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [amtMin, setAmtMin] = useState('')
  const [amtMax, setAmtMax] = useState('')
  const hasFilters = Boolean(refQ || types.length || dateFrom || dateTo || amtMin || amtMax)
  const clearFilters = () => {
    setRefQ(''); setTypes([]); setDateFrom(''); setDateTo(''); setAmtMin(''); setAmtMax('')
  }

  const filtered = useMemo(() => txns.filter((t) => {
    if (refQ) {
      const hay = `${t.referenceType ?? ''} ${t.referenceId ?? ''}`.toLowerCase()
      if (!hay.includes(refQ.toLowerCase())) return false
    }
    if (types.length && !types.includes(t.type)) return false
    if (dateFrom && t.createdAt < dateFrom) return false
    if (dateTo && t.createdAt > `${dateTo}T23:59:59`) return false
    if (amtMin && t.amount < Number(amtMin)) return false
    if (amtMax && t.amount > Number(amtMax)) return false
    return true
  }), [txns, refQ, types, dateFrom, dateTo, amtMin, amtMax])

  const { sorted, sortKey, sortDir, toggleSort } = useSortableData(filtered, getSortValue)

  if (!isAuthenticated) {
    return <div className="container"><div className="card" style={{ maxWidth: 460 }}>Please <Link to="/login">sign in</Link> to view your transactions.</div></div>
  }

  return (
    <div className="container">
      <div className="section-head">
        <h1 className="page">My Transactions</h1>
      </div>

      <div className="filter-pill-row">
        <FilterPill label="Reference" active={Boolean(refQ)}>
          <div className="fgroup">
            <small>Reference</small>
            <input className="search-glow" value={refQ} onChange={(e) => setRefQ(e.target.value)} placeholder="Auction, order reference…" autoFocus />
          </div>
        </FilterPill>

        <FilterModal<string[]>
          label="Type" count={types.length}
          applied={types} emptyValue={[]}
          onApply={setTypes}
          renderBody={(staged, setStaged) => (
            <CheckboxListBody options={TXN_TYPES} selected={staged} onChange={setStaged} searchPlaceholder="Search types" />
          )}
        />

        <FilterModal<{ from: string; to: string }>
          label="Date Range" active={Boolean(dateFrom || dateTo)}
          applied={{ from: dateFrom, to: dateTo }} emptyValue={{ from: '', to: '' }}
          onApply={(v) => { setDateFrom(v.from); setDateTo(v.to) }}
          renderBody={(staged, setStaged) => (
            <div className="price-range">
              <input type="date" value={staged.from} onChange={(e) => setStaged({ ...staged, from: e.target.value })} />
              <span>–</span>
              <input type="date" value={staged.to} onChange={(e) => setStaged({ ...staged, to: e.target.value })} />
            </div>
          )}
        />

        <FilterModal<{ min: string; max: string }>
          label="Amount Range" active={Boolean(amtMin || amtMax)}
          applied={{ min: amtMin, max: amtMax }} emptyValue={{ min: '', max: '' }}
          onApply={(v) => { setAmtMin(v.min); setAmtMax(v.max) }}
          renderBody={(staged, setStaged) => (
            <div className="price-range">
              <input type="number" value={staged.min} onChange={(e) => setStaged({ ...staged, min: e.target.value })} placeholder="Min" />
              <span>–</span>
              <input type="number" value={staged.max} onChange={(e) => setStaged({ ...staged, max: e.target.value })} placeholder="Max" />
            </div>
          )}
        />

        {hasFilters && <button type="button" className="chip-reset-all" onClick={clearFilters}>Reset All</button>}
      </div>

      {error && <div className="error">{error}</div>}
      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        {!loading && filtered.length === 0 ? (
          <p className="muted" style={{ padding: 16 }}>{hasFilters ? 'No transactions match your filters.' : 'No wallet transactions yet.'}</p>
        ) : (
          <div style={{ overflowX: 'auto', padding: 16 }}>
            <table className="admin-table">
              <thead>
                <tr>
                  <SortableTh label="Date" sortKey="date" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
                  <SortableTh label="Type" sortKey="type" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
                  <SortableTh label="Amount" sortKey="amount" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
                  <SortableTh label="Reference" sortKey="reference" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
                </tr>
              </thead>
              <tbody>
                {loading && <SkeletonTableRows rows={5} cols={4} />}
                {sorted.map((t) => (
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
