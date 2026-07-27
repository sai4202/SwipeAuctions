import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import {
  getAdminStats, getAdminUsers, suspendUser, reactivateUser, getAdminUserHolds, releaseAdminHold,
  getAdminAuctions, forceCloseAuction, updateAuction, getAdminDisputes, resolveDispute, errorMessage,
  getAdminCategories, createAdminCategory, getAdminCategoryAttributes, createAdminCategoryAttribute,
  createStockListing, uploadStockImage, createStockAuction, bulkImportStock, downloadStockTemplate,
  getAdminKycQueue, approveKyc, rejectKyc,
  getRegistrationFee, updateRegistrationFee, getSubscriptionPrices, updateSubscriptionPrices,
  type AdminStats, type AdminUser, type AdminAuction, type Dispute, type AdminHold, type ReleaseHoldResult,
  type AdminCategory, type AdminCategoryAttribute, type BulkImportResult, type AdminKyc,
  type SubscriptionPrice, type SubscriptionTier, type BillingCycle,
} from '../api'
import { money, moneyCompact } from '../util'
import { StatTilesSkeleton } from '../components/Skeleton'

type Tab = 'overview' | 'users' | 'auctions' | 'disputes' | 'categories' | 'kyc' | 'settings'

/** Shared Prev/Next pager for the paginated admin dashboard tabs. 0-indexed `page`, 1-indexed display. */
function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 14 }}>
      <button type="button" className="btn ghost sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>Prev</button>
      <span className="muted" style={{ fontSize: 13 }}>Page {page + 1} of {totalPages}</span>
      <button type="button" className="btn ghost sm" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </div>
  )
}

export default function AdminDashboardPage() {
  const { isAuthenticated, role, email } = useAuth()
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('overview')
  const [addStockOpen, setAddStockOpen] = useState(false)
  const [refreshTick, setRefreshTick] = useState(0)

  useEffect(() => {
    if (!isAuthenticated || role !== 'ADMIN') navigate('/admin-login')
  }, [isAuthenticated, role, navigate])

  if (role !== 'ADMIN') return null

  return (
    <div className="container">
      <div className="section-head">
        <div>
          <span className="eyebrow">Admin</span>
          <h1 className="page" style={{ marginTop: 12 }}>Dashboard</h1>
          <p className="muted">Signed in as {email}.</p>
        </div>
        <button type="button" className="btn sm" onClick={() => setAddStockOpen(true)}>+ Add Stock</button>
      </div>

      <div className="tabs" style={{ margin: '18px 0' }}>
        {(['overview', 'users', 'auctions', 'disputes', 'categories', 'kyc', 'settings'] as Tab[]).map((t) => (
          <button key={t} type="button" className={'tab' + (tab === t ? ' active' : '')} onClick={() => setTab(t)}>
            {t === 'kyc' ? 'KYC' : t[0].toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>

      {tab === 'overview' && <Overview />}
      {tab === 'users' && <Users />}
      {tab === 'auctions' && <Auctions key={refreshTick} />}
      {tab === 'disputes' && <Disputes />}
      {tab === 'categories' && <Categories key={refreshTick} />}
      {tab === 'kyc' && <Kyc />}
      {tab === 'settings' && <Settings />}

      {addStockOpen && (
        <AddStockModal onClose={() => setAddStockOpen(false)} onCreated={() => setRefreshTick((t) => t + 1)} />
      )}
    </div>
  )
}

function Overview() {
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [error, setError] = useState('')

  useEffect(() => { getAdminStats().then(setStats).catch((e) => setError(errorMessage(e))) }, [])

  if (error) return <div className="error">{error}</div>
  if (!stats) return <StatTilesSkeleton />

  return (
    <div className="stat-tiles">
      <div className="stat-tile"><div className="k">Total users</div><div className="v">{stats.totalUsers}</div></div>
      <div className="stat-tile"><div className="k">Open auctions</div><div className="v">{stats.openAuctions}</div></div>
      <div className="stat-tile"><div className="k">GMV (captured)</div><div className="v">{money(stats.gmv)}</div></div>
      <div className="stat-tile"><div className="k">Open disputes</div><div className="v">{stats.openDisputes}</div></div>
    </div>
  )
}

function Users() {
  const [users, setUsers] = useState<AdminUser[]>([])
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [walletUser, setWalletUser] = useState<AdminUser | null>(null)

  const load = () => {
    getAdminUsers({
      search: search || undefined,
      role: roleFilter || undefined,
      active: activeFilter ? activeFilter === 'true' : undefined,
      page,
    }).then((res) => { setUsers(res.content); setTotalPages(res.totalPages) }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [search, roleFilter, activeFilter])
  useEffect(load, [search, roleFilter, activeFilter, page])

  const toggle = async (u: AdminUser) => {
    setBusyId(u.id); setError('')
    try {
      const updated = u.active ? await suspendUser(u.id) : await reactivateUser(u.id)
      setUsers((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
    } catch (e) { setError(errorMessage(e)) } finally { setBusyId(null) }
  }

  const applyWalletUpdate = (userId: string, res: ReleaseHoldResult) => {
    const patch = { walletAvailableBalance: res.availableBalance, walletHeldBalance: res.heldBalance, walletCreditLimit: res.creditLimit }
    setUsers((prev) => prev.map((x) => (x.id === userId ? { ...x, ...patch } : x)))
    setWalletUser((prev) => (prev && prev.id === userId ? { ...prev, ...patch } : prev))
  }

  return (
    <div className="card">
      <div className="admin-filters">
        <input placeholder="Search email or mobile…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select value={roleFilter} onChange={(e) => setRoleFilter(e.target.value)}>
          <option value="">All roles</option>
          <option value="USER">Customer</option>
          <option value="DEALER">Dealer</option>
        </select>
        <select value={activeFilter} onChange={(e) => setActiveFilter(e.target.value)}>
          <option value="">Any status</option>
          <option value="true">Active</option>
          <option value="false">Suspended</option>
        </select>
      </div>
      {error && <div className="error">{error}</div>}
      <div style={{ overflowX: 'auto' }}>
        <table className="admin-table">
          <thead><tr><th>Email</th><th>Mobile</th><th>Role</th><th>KYC</th><th>Deposit</th><th>Credit Limit</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {users.map((u) => (
              <tr key={u.id}>
                <td>{u.email}</td>
                <td>{u.mobileNumber}</td>
                <td>{u.role}</td>
                <td>{u.kycStatus}</td>
                <td>{money(u.walletAvailableBalance)}</td>
                <td>{moneyCompact(u.walletCreditLimit)}</td>
                <td>{u.active ? <span className="ok" style={{ margin: 0, display: 'inline-block' }}>Active</span>
                              : <span className="error" style={{ margin: 0, display: 'inline-block' }}>Suspended</span>}</td>
                <td style={{ display: 'flex', gap: 6 }}>
                  <button type="button" className="btn ghost sm" disabled={busyId === u.id} onClick={() => toggle(u)}>
                    {busyId === u.id ? '…' : u.active ? 'Suspend' : 'Reactivate'}
                  </button>
                  <button type="button" className="btn ghost sm" onClick={() => setWalletUser(u)}>Wallet</button>
                </td>
              </tr>
            ))}
            {users.length === 0 && <tr><td colSpan={8} className="muted">No users match.</td></tr>}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={totalPages} onChange={setPage} />

      {walletUser && (
        <WalletModal
          user={walletUser}
          onClose={() => setWalletUser(null)}
          onReleased={(res) => applyWalletUpdate(walletUser.id, res)}
        />
      )}
    </div>
  )
}

/** Admin view of one user's wallet: deposit/credit summary plus their active (locked) EMD holds,
 *  each individually releasable back to the user's available balance. */
function WalletModal({ user, onClose, onReleased }: {
  user: AdminUser
  onClose: () => void
  onReleased: (res: ReleaseHoldResult) => void
}) {
  const [holds, setHolds] = useState<AdminHold[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [confirmingId, setConfirmingId] = useState<string | null>(null)

  useEffect(() => {
    getAdminUserHolds(user.id).then(setHolds).catch((e) => setError(errorMessage(e))).finally(() => setLoading(false))
  }, [user.id])

  const release = async (hold: AdminHold) => {
    setConfirmingId(null)
    setBusyId(hold.id); setError('')
    try {
      const res = await releaseAdminHold(hold.id)
      setHolds((prev) => prev.filter((h) => h.id !== hold.id))
      onReleased(res)
    } catch (e) { setError(errorMessage(e)) } finally { setBusyId(null) }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 560 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>Wallet — {user.email}</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          <div className="stat" style={{ marginBottom: 16 }}>
            <div><div className="k">Deposit (Available)</div><div className="v">{money(user.walletAvailableBalance)}</div></div>
            <div><div className="k">Held (Locked)</div><div className="v">{money(user.walletHeldBalance)}</div></div>
            <div><div className="k">Credit Limit</div><div className="v">{moneyCompact(user.walletCreditLimit)}</div></div>
          </div>
          {error && <div className="error">{error}</div>}
          {loading ? (
            <p className="muted">Loading locked amounts…</p>
          ) : holds.length === 0 ? (
            <p className="muted">No locked (active EMD) amounts for this user.</p>
          ) : (
            <table className="admin-table">
              <thead><tr><th>Auction</th><th>Locked amount</th><th></th></tr></thead>
              <tbody>
                {holds.map((h) => (
                  <tr key={h.id}>
                    <td>{h.listingTitle}</td>
                    <td>{money(h.amount)}</td>
                    <td>
                      {confirmingId === h.id ? (
                        <span style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                          <span className="muted" style={{ fontSize: 12.5 }}>Release {money(h.amount)}?</span>
                          <button type="button" className="btn sm" disabled={busyId === h.id} onClick={() => release(h)}>
                            {busyId === h.id ? '…' : 'Confirm'}
                          </button>
                          <button type="button" className="btn ghost sm" disabled={busyId === h.id} onClick={() => setConfirmingId(null)}>
                            Cancel
                          </button>
                        </span>
                      ) : (
                        <button type="button" className="btn ghost sm" onClick={() => setConfirmingId(h.id)}>
                          Refund / Release
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  )
}

function Auctions() {
  const [auctions, setAuctions] = useState<AdminAuction[]>([])
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [editing, setEditing] = useState<AdminAuction | null>(null)

  const load = () => {
    getAdminAuctions(statusFilter || undefined, page).then((res) => { setAuctions(res.content); setTotalPages(res.totalPages) }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page])

  const doForceClose = async (a: AdminAuction) => {
    if (!confirm(`Force-close "${a.title}" now? This settles it immediately at the current highest bid.`)) return
    setBusyId(a.id); setError('')
    try {
      const updated = await forceCloseAuction(a.id)
      setAuctions((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
    } catch (e) { setError(errorMessage(e)) } finally { setBusyId(null) }
  }

  const canModify = (a: AdminAuction) => (a.status === 'SCHEDULED' || a.status === 'OPEN') && a.bidCount === 0

  return (
    <div className="card">
      <div className="admin-filters">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All statuses</option>
          <option value="SCHEDULED">Scheduled</option>
          <option value="OPEN">Open</option>
          <option value="CLOSED">Closed</option>
          <option value="UNSOLD">Unsold</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>
      {error && <div className="error">{error}</div>}
      <div style={{ overflowX: 'auto' }}>
        <table className="admin-table">
          <thead><tr><th>Title</th><th>Seller</th><th>Base</th><th>Highest bid</th><th>Bids</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {auctions.map((a) => (
              <tr key={a.id}>
                <td>{a.title}</td>
                <td>{a.sellerEmail}</td>
                <td>{money(a.basePrice)}</td>
                <td>{money(a.currentHighestBid)}</td>
                <td>{a.bidCount}</td>
                <td><span className={`badge ${a.status}`}>{a.status}</span></td>
                <td style={{ display: 'flex', gap: 8 }}>
                  {canModify(a) && (
                    <button type="button" className="btn ghost sm" onClick={() => setEditing(a)}>Modify</button>
                  )}
                  {a.status === 'OPEN' && (
                    <button type="button" className="btn ghost sm" disabled={busyId === a.id} onClick={() => doForceClose(a)}>
                      {busyId === a.id ? '…' : 'Force close'}
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {auctions.length === 0 && <tr><td colSpan={7} className="muted">No auctions match.</td></tr>}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={totalPages} onChange={setPage} />

      {editing && (
        <ModifyAuctionModal
          auction={editing}
          onClose={() => setEditing(null)}
          onSaved={(updated) => {
            setAuctions((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
            setEditing(null)
          }}
        />
      )}
    </div>
  )
}

/** yyyy-MM-ddTHH:mm for a <input type="datetime-local">, from an ISO LocalDateTime string. */
function toLocalInput(iso: string) {
  return iso.slice(0, 16)
}

function ModifyAuctionModal({ auction, onClose, onSaved }: {
  auction: AdminAuction
  onClose: () => void
  onSaved: (updated: AdminAuction) => void
}) {
  const [title, setTitle] = useState(auction.title)
  const [basePrice, setBasePrice] = useState(String(auction.basePrice))
  const [startTime, setStartTime] = useState(toLocalInput(auction.startTime))
  const [endTime, setEndTime] = useState(toLocalInput(auction.currentEndTime))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true); setError('')
    try {
      const updated = await updateAuction(auction.id, {
        title, basePrice: Number(basePrice), startTime, endTime,
      })
      onSaved(updated)
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 480 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>Modify auction</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {error && <div className="error">{error}</div>}
            <div className="fgroup">
              <small>Title</small>
              <input value={title} onChange={(e) => setTitle(e.target.value)} required />
            </div>
            <div className="fgroup">
              <small>Base price (₹)</small>
              <input type="number" value={basePrice} onChange={(e) => setBasePrice(e.target.value)} required min={0} />
            </div>
            <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
              <div className="fgroup" style={{ flex: 1, minWidth: 200 }}>
                <small>Start time</small>
                <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} required />
              </div>
              <div className="fgroup" style={{ flex: 1, minWidth: 200 }}>
                <small>End time</small>
                <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} required />
              </div>
            </div>
            <p className="muted" style={{ fontSize: 12.5 }}>Only available before any bid has been placed.</p>
            <button type="submit" className="btn" disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button>
          </form>
        </div>
      </div>
    </div>
  )
}

function Disputes() {
  const [disputes, setDisputes] = useState<Dispute[]>([])
  const [statusFilter, setStatusFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [selected, setSelected] = useState<Dispute | null>(null)
  const [notes, setNotes] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = () => {
    getAdminDisputes(statusFilter || undefined, page).then((res) => { setDisputes(res.content); setTotalPages(res.totalPages) }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page])

  const open = (d: Dispute) => { setSelected(d); setNotes(d.adminNotes ?? ''); setError('') }

  const resolve = async (refundBuyer: boolean) => {
    if (!selected) return
    setBusy(true); setError('')
    try {
      const updated = await resolveDispute(selected.id, notes, refundBuyer)
      setDisputes((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))
      setSelected(updated)
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <div className="card">
      <div className="admin-filters">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="IN_REVIEW">In review</option>
          <option value="RESOLVED">Resolved</option>
        </select>
      </div>
      {error && <div className="error">{error}</div>}
      <div style={{ overflowX: 'auto' }}>
        <table className="admin-table">
          <thead><tr><th>Auction</th><th>Raised by</th><th>Reason</th><th>Status</th></tr></thead>
          <tbody>
            {disputes.map((d) => (
              <tr key={d.id} onClick={() => open(d)} style={{ cursor: 'pointer' }}>
                <td>{d.auctionTitle}</td>
                <td>{d.raisedByEmail}</td>
                <td style={{ maxWidth: 320, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{d.reason}</td>
                <td>{d.status}</td>
              </tr>
            ))}
            {disputes.length === 0 && <tr><td colSpan={4} className="muted">No disputes match.</td></tr>}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={totalPages} onChange={setPage} />

      {selected && (
        <div style={{ marginTop: 20, paddingTop: 18, borderTop: '1px dashed var(--border)' }}>
          <h2 style={{ fontSize: 15, margin: '0 0 6px' }}>{selected.auctionTitle} — {selected.status}</h2>
          <p className="muted">Raised by {selected.raisedByEmail}: {selected.reason}</p>
          <label>Admin notes</label>
          <textarea style={{ width: '100%', minHeight: 70, fontFamily: 'inherit' }} value={notes}
                    onChange={(e) => setNotes(e.target.value)} disabled={selected.status === 'RESOLVED'} />
          {selected.status !== 'RESOLVED' && (
            <div style={{ marginTop: 12, display: 'flex', gap: 10 }}>
              <button type="button" className="btn sm" disabled={busy} onClick={() => resolve(false)}>
                {busy ? 'Saving…' : 'Resolve — release to seller'}
              </button>
              <button type="button" className="btn ghost sm" disabled={busy} onClick={() => resolve(true)}>
                {busy ? 'Saving…' : 'Resolve — refund buyer'}
              </button>
            </div>
          )}
          <p className="muted" style={{ fontSize: 12.5, marginTop: 8 }}>
            Only affects money if the auction's sale proceeds are still escrowed (not yet auto-released or already withdrawn).
          </p>
        </div>
      )}
    </div>
  )
}

function Kyc() {
  const [rows, setRows] = useState<AdminKyc[]>([])
  const [statusFilter, setStatusFilter] = useState('PENDING')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [selected, setSelected] = useState<AdminKyc | null>(null)
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = () => {
    getAdminKycQueue(statusFilter || undefined, page).then((res) => { setRows(res.content); setTotalPages(res.totalPages) }).catch((e) => setError(errorMessage(e)))
  }

  useEffect(() => setPage(0), [statusFilter])
  useEffect(load, [statusFilter, page])

  const open = (k: AdminKyc) => { setSelected(k); setRemarks(k.remarks ?? ''); setError('') }

  const decide = async (approve: boolean) => {
    if (!selected) return
    if (!approve && !remarks.trim()) { setError('Remarks are required to reject.'); return }
    setBusy(true); setError('')
    try {
      const updated = approve ? await approveKyc(selected.userId, remarks || undefined) : await rejectKyc(selected.userId, remarks)
      setRows((prev) => prev.map((x) => (x.userId === updated.userId ? updated : x)))
      setSelected(updated)
    } catch (e) { setError(errorMessage(e)) } finally { setBusy(false) }
  }

  return (
    <div className="card">
      <div className="admin-filters">
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </div>
      {error && <div className="error">{error}</div>}
      <div style={{ overflowX: 'auto' }}>
        <table className="admin-table">
          <thead><tr><th>Email</th><th>Full name</th><th>Aadhaar</th><th>PAN</th><th>Provider</th><th>Status</th><th>Submitted</th></tr></thead>
          <tbody>
            {rows.map((k) => (
              <tr key={k.userId} onClick={() => open(k)} style={{ cursor: 'pointer' }}>
                <td>{k.email}</td>
                <td>{k.fullName ?? '—'}</td>
                <td>{k.aadhaarMasked ?? '—'}</td>
                <td>{k.panNumberMasked ?? '—'}</td>
                <td>{k.provider ?? '—'}</td>
                <td>{k.status}</td>
                <td>{k.submittedAt ? new Date(k.submittedAt).toLocaleString() : '—'}</td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={7} className="muted">No KYC submissions match.</td></tr>}
          </tbody>
        </table>
      </div>
      <Pager page={page} totalPages={totalPages} onChange={setPage} />

      {selected && (
        <div style={{ marginTop: 20, paddingTop: 18, borderTop: '1px dashed var(--border)' }}>
          <h2 style={{ fontSize: 15, margin: '0 0 6px' }}>{selected.email} — {selected.status}</h2>
          <p className="muted" style={{ fontSize: 13 }}>
            {selected.fullName ?? '—'}, {selected.dateOfBirth ?? '—'} · {selected.address ?? '—'}, {selected.city ?? '—'},{' '}
            {selected.state ?? '—'} {selected.pincode ?? ''}
          </p>
          <label>Remarks {selected.status === 'PENDING' && '(required to reject)'}</label>
          <textarea style={{ width: '100%', minHeight: 70, fontFamily: 'inherit' }} value={remarks}
                    onChange={(e) => setRemarks(e.target.value)} disabled={selected.status !== 'PENDING'} />
          {selected.status === 'PENDING' && (
            <div style={{ marginTop: 12, display: 'flex', gap: 10 }}>
              <button type="button" className="btn sm" disabled={busy} onClick={() => decide(true)}>
                {busy ? 'Saving…' : 'Approve'}
              </button>
              <button type="button" className="btn ghost sm" disabled={busy} onClick={() => decide(false)}>
                {busy ? 'Saving…' : 'Reject'}
              </button>
            </div>
          )}
          {selected.reviewedBy && (
            <p className="muted" style={{ fontSize: 12.5, marginTop: 8 }}>Reviewed by {selected.reviewedBy}.</p>
          )}
        </div>
      )}
    </div>
  )
}

const VALUE_TYPES: AdminCategoryAttribute['valueType'][] = ['TEXT', 'NUMBER', 'BOOLEAN', 'ENUM']

function Categories() {
  const [categories, setCategories] = useState<AdminCategory[]>([])
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [parentId, setParentId] = useState('')
  const [selected, setSelected] = useState<AdminCategory | null>(null)
  const [attributes, setAttributes] = useState<AdminCategoryAttribute[]>([])
  const [attrError, setAttrError] = useState('')
  const [attrSaving, setAttrSaving] = useState(false)
  const [attrKey, setAttrKey] = useState('')
  const [attrLabel, setAttrLabel] = useState('')
  const [attrType, setAttrType] = useState<AdminCategoryAttribute['valueType']>('TEXT')
  const [attrFilterable, setAttrFilterable] = useState(true)

  const load = () => { getAdminCategories().then(setCategories).catch((e) => setError(errorMessage(e))) }
  useEffect(load, [])

  const openCategory = (c: AdminCategory) => {
    setSelected(c); setAttrError(''); setAttributes([])
    getAdminCategoryAttributes(c.id).then(setAttributes).catch((e) => setAttrError(errorMessage(e)))
  }

  const submitCategory = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true); setError('')
    try {
      const created = await createAdminCategory({ name, slug, parentId: parentId || null })
      setCategories((prev) => [...prev, created])
      setName(''); setSlug(''); setParentId('')
    } catch (e) { setError(errorMessage(e)) } finally { setSaving(false) }
  }

  const submitAttribute = async (e: FormEvent) => {
    e.preventDefault()
    if (!selected) return
    setAttrSaving(true); setAttrError('')
    try {
      const created = await createAdminCategoryAttribute(selected.id, {
        key: attrKey, label: attrLabel, valueType: attrType, filterable: attrFilterable,
      })
      setAttributes((prev) => [...prev, created])
      setAttrKey(''); setAttrLabel(''); setAttrType('TEXT'); setAttrFilterable(true)
    } catch (e) { setAttrError(errorMessage(e)) } finally { setAttrSaving(false) }
  }

  return (
    <div className="card">
      <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Add a category</h2>
      <form onSubmit={submitCategory} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div className="fgroup">
          <small>Name</small>
          <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Furniture" required />
        </div>
        <div className="fgroup">
          <small>Slug</small>
          <input value={slug} onChange={(e) => setSlug(e.target.value)} placeholder="e.g. furniture" required />
        </div>
        <div className="fgroup">
          <small>Parent (optional)</small>
          <select value={parentId} onChange={(e) => setParentId(e.target.value)}>
            <option value="">None</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <button type="submit" className="btn sm" disabled={saving}>{saving ? 'Adding…' : 'Add category'}</button>
      </form>
      {error && <div className="error">{error}</div>}

      <div style={{ overflowX: 'auto', marginTop: 18 }}>
        <table className="admin-table">
          <thead><tr><th>Name</th><th>Slug</th><th>Parent</th><th></th></tr></thead>
          <tbody>
            {categories.map((c) => (
              <tr key={c.id} onClick={() => openCategory(c)} style={{ cursor: 'pointer' }}>
                <td>{c.name}</td>
                <td>{c.slug}</td>
                <td>{categories.find((p) => p.id === c.parentId)?.name ?? '—'}</td>
                <td className="muted">Manage attributes →</td>
              </tr>
            ))}
            {categories.length === 0 && <tr><td colSpan={4} className="muted">No categories yet.</td></tr>}
          </tbody>
        </table>
      </div>

      {selected && (
        <div style={{ marginTop: 20, paddingTop: 18, borderTop: '1px dashed var(--border)' }}>
          <h2 style={{ fontSize: 15, margin: '0 0 6px' }}>{selected.name} — attributes</h2>
          <p className="muted" style={{ fontSize: 12.5 }}>
            Attributes become spec fields for listings in this category, and filterable ones show up as browse filters.
          </p>
          {attrError && <div className="error">{attrError}</div>}
          <div style={{ overflowX: 'auto' }}>
            <table className="admin-table">
              <thead><tr><th>Key</th><th>Label</th><th>Type</th><th>Filterable</th></tr></thead>
              <tbody>
                {attributes.map((a) => (
                  <tr key={a.id}>
                    <td>{a.key}</td><td>{a.label}</td><td>{a.valueType}</td><td>{a.filterable ? 'Yes' : 'No'}</td>
                  </tr>
                ))}
                {attributes.length === 0 && <tr><td colSpan={4} className="muted">No attributes yet.</td></tr>}
              </tbody>
            </table>
          </div>
          <form onSubmit={submitAttribute} style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'flex-end', marginTop: 12 }}>
            <div className="fgroup">
              <small>Key</small>
              <input value={attrKey} onChange={(e) => setAttrKey(e.target.value)} placeholder="e.g. Material" required />
            </div>
            <div className="fgroup">
              <small>Label</small>
              <input value={attrLabel} onChange={(e) => setAttrLabel(e.target.value)} placeholder="e.g. Material" required />
            </div>
            <div className="fgroup">
              <small>Type</small>
              <select value={attrType} onChange={(e) => setAttrType(e.target.value as AdminCategoryAttribute['valueType'])}>
                {VALUE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </div>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
              <input type="checkbox" checked={attrFilterable} onChange={(e) => setAttrFilterable(e.target.checked)} />
              Filterable
            </label>
            <button type="submit" className="btn ghost sm" disabled={attrSaving}>{attrSaving ? 'Adding…' : 'Add attribute'}</button>
          </form>
        </div>
      )}
    </div>
  )
}

const TIERS: SubscriptionTier[] = ['SILVER', 'GOLD', 'DIAMOND']
const CYCLES: { key: BillingCycle; label: string }[] = [
  { key: 'MONTHLY', label: 'Monthly' },
  { key: 'QUARTERLY', label: 'Quarterly' },
  { key: 'HALF_YEARLY', label: 'Half-yearly' },
  { key: 'YEARLY', label: 'Yearly' },
]

/** Registration fee + the 12 (tier x cycle) subscription prices. Both are informational/config
 *  only for now — no payment is actually collected against either (see SubscriptionService /
 *  PlatformSettingsService javadoc). */
function Settings() {
  const [fee, setFee] = useState('')
  const [feeSaving, setFeeSaving] = useState(false)
  const [feeMsg, setFeeMsg] = useState('')
  const [feeError, setFeeError] = useState('')

  const [prices, setPrices] = useState<SubscriptionPrice[]>([])
  const [priceSaving, setPriceSaving] = useState(false)
  const [priceMsg, setPriceMsg] = useState('')
  const [priceError, setPriceError] = useState('')

  useEffect(() => {
    getRegistrationFee().then((f) => setFee(String(f))).catch((e) => setFeeError(errorMessage(e)))
    getSubscriptionPrices().then(setPrices).catch((e) => setPriceError(errorMessage(e)))
  }, [])

  const priceFor = (tier: SubscriptionTier, cycle: BillingCycle) =>
    prices.find((p) => p.tier === tier && p.billingCycle === cycle)?.price ?? 0

  const setPriceFor = (tier: SubscriptionTier, cycle: BillingCycle, value: string) => {
    const n = Number(value)
    setPrices((prev) => {
      const exists = prev.some((p) => p.tier === tier && p.billingCycle === cycle)
      if (!exists) return [...prev, { tier, billingCycle: cycle, price: n }]
      return prev.map((p) => (p.tier === tier && p.billingCycle === cycle ? { ...p, price: n } : p))
    })
  }

  const submitFee = async (e: FormEvent) => {
    e.preventDefault()
    setFeeSaving(true); setFeeError(''); setFeeMsg('')
    try {
      const saved = await updateRegistrationFee(Number(fee))
      setFee(String(saved))
      setFeeMsg('Registration fee saved.')
    } catch (e2) { setFeeError(errorMessage(e2)) } finally { setFeeSaving(false) }
  }

  const submitPrices = async (e: FormEvent) => {
    e.preventDefault()
    setPriceSaving(true); setPriceError(''); setPriceMsg('')
    try {
      const saved = await updateSubscriptionPrices(prices)
      setPrices(saved)
      setPriceMsg('Subscription prices saved.')
    } catch (e2) { setPriceError(errorMessage(e2)) } finally { setPriceSaving(false) }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      <div className="card">
        <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Registration fee</h2>
        <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
          One-time fee shown to new signups. Informational only for now — nothing is charged yet.
        </p>
        <form onSubmit={submitFee} style={{ display: 'flex', gap: 10, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div className="fgroup" style={{ maxWidth: 200 }}>
            <small>Fee (₹)</small>
            <input type="number" min={0} value={fee} onChange={(e) => setFee(e.target.value)} />
          </div>
          <button type="submit" className="btn sm" disabled={feeSaving}>{feeSaving ? 'Saving…' : 'Save'}</button>
        </form>
        {feeError && <div className="error">{feeError}</div>}
        {feeMsg && <div className="ok">{feeMsg}</div>}
      </div>

      <div className="card">
        <h2 style={{ fontSize: 15, margin: '0 0 12px' }}>Subscription prices</h2>
        <p className="muted" style={{ fontSize: 12.5, marginTop: 0 }}>
          Prices shown on the buyer subscription page. Informational only for now — subscribing doesn't
          charge anything yet.
        </p>
        <form onSubmit={submitPrices}>
          <div style={{ overflowX: 'auto' }}>
            <table className="admin-table">
              <thead>
                <tr>
                  <th>Tier</th>
                  {CYCLES.map((c) => <th key={c.key}>{c.label}</th>)}
                </tr>
              </thead>
              <tbody>
                {TIERS.map((tier) => (
                  <tr key={tier}>
                    <td>{tier}</td>
                    {CYCLES.map((c) => (
                      <td key={c.key}>
                        <input
                          type="number"
                          min={0}
                          style={{ width: 100 }}
                          value={priceFor(tier, c.key)}
                          onChange={(e) => setPriceFor(tier, c.key, e.target.value)}
                        />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button type="submit" className="btn sm" style={{ marginTop: 12 }} disabled={priceSaving}>
            {priceSaving ? 'Saving…' : 'Save prices'}
          </button>
        </form>
        {priceError && <div className="error">{priceError}</div>}
        {priceMsg && <div className="ok">{priceMsg}</div>}
      </div>
    </div>
  )
}

const CONDITIONS = ['NEW', 'USED', 'REFURBISHED', 'FOR_PARTS']
const NEW_CATEGORY_VALUE = '__new__'

/**
 * "+ Add Stock": admin creates inventory attributed to the Swipe Stock platform seller account —
 * one item at a time, or many at once via an Excel file. Every item shows up on the normal
 * Auctions browse regardless; the "List on Swipe Stock page" checkbox (or the sheet's "Swipe Stock"
 * column) is what additionally surfaces it on /swipe-stock.
 */
function AddStockModal({ onClose, onCreated }: { onClose: () => void; onCreated: () => void }) {
  const [mode, setMode] = useState<'single' | 'bulk'>('single')
  const [categories, setCategories] = useState<AdminCategory[]>([])

  useEffect(() => { getAdminCategories().then(setCategories).catch(() => {}) }, [])

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 640 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>+ Add Stock</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          <div className="tabs">
            <button type="button" className={'tab' + (mode === 'single' ? ' active' : '')} onClick={() => setMode('single')}>Single Item</button>
            <button type="button" className={'tab' + (mode === 'bulk' ? ' active' : '')} onClick={() => setMode('bulk')}>Bulk Upload (Excel)</button>
          </div>
          {mode === 'single'
            ? <AddStockSingleForm categories={categories} onCategoriesChanged={setCategories} onCreated={onCreated} />
            : <AddStockBulkForm onCreated={onCreated} />}
        </div>
      </div>
    </div>
  )
}

function AddStockSingleForm({ categories, onCategoriesChanged, onCreated }: {
  categories: AdminCategory[]
  onCategoriesChanged: (cats: AdminCategory[]) => void
  onCreated: () => void
}) {
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [newCategoryName, setNewCategoryName] = useState('')
  const [brand, setBrand] = useState('')
  const [condition, setCondition] = useState('USED')
  const [city, setCity] = useState('')
  const [state, setState] = useState('')
  const [zip, setZip] = useState('')
  const [reservePrice, setReservePrice] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [swipeStock, setSwipeStock] = useState(false)
  const [requiredTier, setRequiredTier] = useState<'NONE' | 'SILVER' | 'GOLD' | 'DIAMOND'>('NONE')
  const [files, setFiles] = useState<File[]>([])
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')

  const reset = () => {
    setTitle(''); setDescription(''); setBrand(''); setCity(''); setState(''); setZip('')
    setReservePrice(''); setStartTime(''); setEndTime(''); setSwipeStock(false); setRequiredTier('NONE'); setFiles([])
    setCategoryId(''); setNewCategoryName('')
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setSaving(true); setError(''); setSuccess('')
    try {
      const usingNewCategory = categoryId === NEW_CATEGORY_VALUE
      if (usingNewCategory && !newCategoryName.trim()) throw new Error('Enter a name for the new category')

      const listing = await createStockListing({
        title,
        description: description || undefined,
        categoryId: usingNewCategory ? undefined : categoryId || undefined,
        categoryName: usingNewCategory ? newCategoryName.trim() : undefined,
        brand: brand || undefined,
        condition,
        city: city || undefined,
        state: state || undefined,
        zip: zip || undefined,
        reservePrice: Number(reservePrice),
        swipeStock,
        requiredTier,
      })

      for (let i = 0; i < files.length; i++) {
        await uploadStockImage(listing.id, files[i], i === 0)
      }

      await createStockAuction(listing.id, {
        basePrice: Number(reservePrice),
        // <input type="datetime-local"> already yields "YYYY-MM-DDTHH:mm" local wall-clock time,
        // which is exactly what the backend's LocalDateTime expects — no Date/timezone conversion.
        startTime: startTime || null,
        endTime: endTime || null,
      })

      if (usingNewCategory) {
        getAdminCategories().then(onCategoriesChanged).catch(() => {})
      }
      setSuccess(`"${title}" created${swipeStock ? ' — listed on Swipe Stock' : ''}.`)
      reset()
      onCreated()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {error && <div className="error">{error}</div>}
      {success && <div className="ok">{success}</div>}

      <div className="fgroup">
        <small>Title</small>
        <input value={title} onChange={(e) => setTitle(e.target.value)} required />
      </div>
      <div className="fgroup">
        <small>Description</small>
        <input value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <div className="fgroup" style={{ flex: 1, minWidth: 180 }}>
          <small>Category</small>
          <select value={categoryId} onChange={(e) => setCategoryId(e.target.value)} required>
            <option value="">Select…</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            <option value={NEW_CATEGORY_VALUE}>＋ New category…</option>
          </select>
        </div>
        {categoryId === NEW_CATEGORY_VALUE && (
          <div className="fgroup" style={{ flex: 1, minWidth: 180 }}>
            <small>New category name</small>
            <input value={newCategoryName} onChange={(e) => setNewCategoryName(e.target.value)} placeholder="e.g. Furniture" required />
          </div>
        )}
        <div className="fgroup" style={{ flex: 1, minWidth: 140 }}>
          <small>Brand</small>
          <input value={brand} onChange={(e) => setBrand(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 140 }}>
          <small>Condition</small>
          <select value={condition} onChange={(e) => setCondition(e.target.value)}>
            {CONDITIONS.map((c) => <option key={c} value={c}>{c.replace('_', ' ')}</option>)}
          </select>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <div className="fgroup" style={{ flex: 1, minWidth: 120 }}>
          <small>City</small>
          <input value={city} onChange={(e) => setCity(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 100 }}>
          <small>State</small>
          <input value={state} onChange={(e) => setState(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 100 }}>
          <small>Zip</small>
          <input value={zip} onChange={(e) => setZip(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 140 }}>
          <small>Base price (₹)</small>
          <input type="number" value={reservePrice} onChange={(e) => setReservePrice(e.target.value)} required min={0} />
        </div>
      </div>

      <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
        <div className="fgroup" style={{ flex: 1, minWidth: 200 }}>
          <small>Start time (optional, default now)</small>
          <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
        </div>
        <div className="fgroup" style={{ flex: 1, minWidth: 200 }}>
          <small>End time (optional, default +3 days)</small>
          <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
        </div>
      </div>

      <div className="fgroup">
        <small>Images (first one becomes the cover)</small>
        <input type="file" accept="image/*" multiple onChange={(e) => setFiles(Array.from(e.target.files ?? []))} />
      </div>

      <div className="fgroup" style={{ maxWidth: 220 }}>
        <small>Required subscription tier</small>
        <select value={requiredTier} onChange={(e) => setRequiredTier(e.target.value as typeof requiredTier)}>
          <option value="NONE">None — visible to everyone</option>
          <option value="SILVER">Silver</option>
          <option value="GOLD">Gold</option>
          <option value="DIAMOND">Diamond</option>
        </select>
      </div>

      <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13.5 }}>
        <input type="checkbox" checked={swipeStock} onChange={(e) => setSwipeStock(e.target.checked)} />
        List on the Swipe Stock page
      </label>

      <button type="submit" className="btn" disabled={saving}>{saving ? 'Creating…' : 'Create item'}</button>
    </form>
  )
}

function AddStockBulkForm({ onCreated }: { onCreated: () => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [swipeStock, setSwipeStock] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<BulkImportResult | null>(null)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (!file) return
    setSaving(true); setError(''); setResult(null)
    try {
      const res = await bulkImportStock(file, swipeStock)
      setResult(res)
      if (res.created > 0) onCreated()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      <p className="muted" style={{ fontSize: 13 }}>
        Upload an .xlsx/.xls file with columns: <b>Title</b>, <b>Category</b>, Brand, Condition, City, State,
        Zip, <b>Base Price</b>, Start Time, End Time, Swipe Stock. Only Title, Category and Base Price are
        required — a category that doesn't exist yet is created automatically. A bad row is skipped, not the
        whole file.
      </p>
      <button type="button" className="linkbtn" onClick={() => downloadStockTemplate()}>⬇ Download template</button>

      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {error && <div className="error">{error}</div>}
        <div className="fgroup">
          <small>Excel file</small>
          <input type="file" accept=".xlsx,.xls" onChange={(e) => setFile(e.target.files?.[0] ?? null)} required />
        </div>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13.5 }}>
          <input type="checkbox" checked={swipeStock} onChange={(e) => setSwipeStock(e.target.checked)} />
          List all rows on the Swipe Stock page (unless a row's own "Swipe Stock" column says otherwise)
        </label>
        <button type="submit" className="btn" disabled={saving || !file}>{saving ? 'Importing…' : 'Import'}</button>
      </form>

      {result && (
        <div>
          <p className="ok">Imported {result.created} of {result.totalRows} row(s).</p>
          {result.errors.length > 0 && (
            <div style={{ overflowX: 'auto' }}>
              <table className="admin-table">
                <thead><tr><th>Row</th><th>Error</th></tr></thead>
                <tbody>
                  {result.errors.map((e) => <tr key={e.row}><td>{e.row}</td><td>{e.message}</td></tr>)}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
