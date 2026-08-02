import { useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import {
  getAdminListings, updateListingRequiredTier, updateListingCategory, getAdminCategories, errorMessage,
  type AdminListing, type AdminCategory, type SubscriptionTier,
} from '../../api'
import { money } from '../../util'
import SortableTh from '../../components/SortableTh'
import { useSortableData } from '../../useSort'
import { getListingSortValue, Pager, AdminPageHeader } from './shared'
import type { AdminOutletContext } from '../../components/AdminLayout'

export default function AdminListingsPage() {
  const { refreshTick } = useOutletContext<AdminOutletContext>()
  const [listings, setListings] = useState<AdminListing[]>([])
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const { sorted: sortedListings, sortKey, sortDir, toggleSort } = useSortableData(listings, getListingSortValue)

  const load = () => {
    getAdminListings(statusFilter || undefined, page).then((res) => { setListings(res.content); setTotalPages(res.totalPages) }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => { getAdminCategories().then(setCategories).catch(() => {}) }, [])
  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page, refreshTick])

  const changeTier = async (l: AdminListing, requiredTier: SubscriptionTier) => {
    setBusyId(l.id); setError('')
    try {
      const updated = await updateListingRequiredTier(l.id, requiredTier)
      setListings((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
    } catch (e) { setError(errorMessage(e)) } finally { setBusyId(null) }
  }

  const changeCategory = async (l: AdminListing, categoryId: string) => {
    setBusyId(l.id); setError('')
    try {
      const updated = await updateListingCategory(l.id, categoryId)
      setListings((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
    } catch (e) { setError(errorMessage(e)) } finally { setBusyId(null) }
  }

  return (
    <div>
      <AdminPageHeader section="Listings" title="Listings" subtitle="Every catalogue item, its category, and its subscription gating." />
      <div className="card">
        <div className="admin-filters">
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            <option value="DRAFT">Draft</option>
            <option value="PUBLISHED">Published</option>
            <option value="WITHDRAWN">Withdrawn</option>
          </select>
        </div>
        {error && <div className="error">{error}</div>}
        <div style={{ overflowX: 'auto' }}>
          <table className="admin-table">
            <thead><tr>
              <SortableTh label="Title" sortKey="title" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Seller" sortKey="seller" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Category" sortKey="category" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Base Price" sortKey="price" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Status" sortKey="status" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Required Tier" sortKey="tier" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <th></th>
            </tr></thead>
            <tbody>
              {sortedListings.map((l) => (
                <tr key={l.id}>
                  <td><a href={`/auctions/${l.id}`} target="_blank" rel="noreferrer">{l.title}</a></td>
                  <td>{l.sellerEmail}</td>
                  <td>
                    <select value={l.categoryId} disabled={busyId === l.id} onChange={(e) => changeCategory(l, e.target.value)}>
                      {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                    </select>
                  </td>
                  <td>{money(l.reservePrice)}</td>
                  <td><span className={`badge ${l.status}`}>{l.status}</span></td>
                  <td>
                    <select value={l.requiredTier} disabled={busyId === l.id} onChange={(e) => changeTier(l, e.target.value as SubscriptionTier)}>
                      <option value="NONE">None</option>
                      <option value="SILVER">Silver</option>
                      <option value="GOLD">Gold</option>
                      <option value="DIAMOND">Diamond</option>
                    </select>
                  </td>
                  <td>{busyId === l.id ? '…' : ''}</td>
                </tr>
              ))}
              {listings.length === 0 && <tr><td colSpan={7} className="muted">No listings match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />
      </div>
    </div>
  )
}
