import { useEffect, useState } from 'react'
import { getAuditLog, errorMessage, type AuditLogEntry } from '../../api'
import { formatDateTimeShort } from '../../util'
import SortableTh from '../../components/SortableTh'
import { useSortableData } from '../../useSort'
import { AUDIT_ACTIONS, auditActionLabel, getAuditLogSortValue, Pager, AdminPageHeader } from './shared'

export default function AdminAuditLogPage() {
  const [entries, setEntries] = useState<AuditLogEntry[]>([])
  const [actionFilter, setActionFilter] = useState('')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [error, setError] = useState('')
  const { sorted: sortedEntries, sortKey, sortDir, toggleSort } = useSortableData(entries, getAuditLogSortValue)

  const load = () => {
    getAuditLog(actionFilter || undefined, fromDate || undefined, toDate || undefined, page)
      .then((res) => { setEntries(res.content); setTotalPages(res.totalPages) })
      .catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [actionFilter, fromDate, toDate])
  useEffect(load, [actionFilter, fromDate, toDate, page])

  return (
    <div>
      <AdminPageHeader section="Audit Log" title="Audit Log" subtitle="Every admin action, who did it, and when." />
      <div className="card">
        <div className="admin-filters" style={{ flexWrap: 'wrap', gap: 10 }}>
          <select value={actionFilter} onChange={(e) => setActionFilter(e.target.value)}>
            <option value="">All actions</option>
            {AUDIT_ACTIONS.map((a) => <option key={a} value={a}>{auditActionLabel(a)}</option>)}
          </select>
          <div className="fgroup" style={{ maxWidth: 160 }}>
            <small>From</small>
            <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
          </div>
          <div className="fgroup" style={{ maxWidth: 160 }}>
            <small>To</small>
            <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
          </div>
        </div>
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr>
              <SortableTh label="Time" sortKey="time" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Admin" sortKey="admin" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Action" sortKey="action" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Target" sortKey="target" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <th>Summary</th>
            </tr></thead>
            <tbody>
              {sortedEntries.map((e) => (
                <tr key={e.id}>
                  <td style={{ whiteSpace: 'nowrap' }}>{formatDateTimeShort(e.createdAt)}</td>
                  <td>{e.adminEmail}</td>
                  <td>{auditActionLabel(e.action)}</td>
                  <td>{e.targetType}</td>
                  <td>{e.summary}</td>
                </tr>
              ))}
              {entries.length === 0 && <tr><td colSpan={5} className="muted">No audit log entries match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </div>
  )
}
