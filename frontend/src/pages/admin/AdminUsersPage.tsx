import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  getAdminUsers, getAdminUser, suspendUser, reactivateUser, getAdminUserHolds, releaseAdminHold, getAdminUserBids,
  getAdminKycQueue, errorMessage,
  type AdminUser, type AdminHold, type ReleaseHoldResult, type AdminUserBid,
} from '../../api'
import { money, moneyCompact, formatDateTimeShort } from '../../util'
import SortableTh from '../../components/SortableTh'
import { useSortableData } from '../../useSort'
import { getUserSortValue, bidRowStatus, AnimatedNumber, Pager, StatTile, AdminPageHeader } from './shared'

function UserStatTiles() {
  const [counts, setCounts] = useState<{ total: number; approved: number; pending: number; rejected: number } | null>(null)

  useEffect(() => {
    Promise.all([
      getAdminUsers({ page: 0, size: 1 }),
      getAdminKycQueue('APPROVED', 0, 1),
      getAdminKycQueue('PENDING', 0, 1),
      getAdminKycQueue('REJECTED', 0, 1),
    ]).then(([users, approved, pending, rejected]) => {
      setCounts({ total: users.totalElements, approved: approved.totalElements, pending: pending.totalElements, rejected: rejected.totalElements })
    }).catch(() => {})
  }, [])

  if (!counts) return null
  return (
    <div className="stat-tiles">
      <StatTile label="Total users" value={String(counts.total)} icon="👥" color="purple" />
      <StatTile label="KYC completed" value={String(counts.approved)} icon="✅" color="green" />
      <StatTile label="KYC pending" value={String(counts.pending)} icon="⏳" color="amber" />
      <StatTile label="KYC rejected" value={String(counts.rejected)} icon="✕" color="red" />
    </div>
  )
}

export default function AdminUsersPage() {
  const [searchParams] = useSearchParams()
  const linkedUserId = searchParams.get('userId')
  const [users, setUsers] = useState<AdminUser[]>([])
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState('')
  const [activeFilter, setActiveFilter] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [walletUser, setWalletUser] = useState<AdminUser | null>(null)
  const { sorted: sortedUsers, sortKey, sortDir, toggleSort } = useSortableData(users, getUserSortValue)

  useEffect(() => {
    if (!linkedUserId) return
    getAdminUser(linkedUserId).then(setWalletUser).catch((e) => setError(errorMessage(e)))
  }, [linkedUserId])

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
    <div>
      <AdminPageHeader section="Users" title="Users Management" subtitle="Search, review KYC status, and manage account access." />
      <UserStatTiles />

      <div className="card">
        <div className="admin-filters">
          <input className="search-glow" placeholder="Search email or mobile…" value={search} onChange={(e) => setSearch(e.target.value)} />
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
            <thead><tr>
              <SortableTh label="Email" sortKey="email" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Mobile" sortKey="mobile" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Role" sortKey="role" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Tier" sortKey="tier" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="KYC" sortKey="kyc" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Deposit" sortKey="deposit" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Credit Limit" sortKey="credit" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Active Bids" sortKey="activeBids" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <SortableTh label="Status" sortKey="status" activeKey={sortKey} dir={sortDir} onSort={toggleSort} />
              <th></th>
            </tr></thead>
            <tbody>
              {sortedUsers.map((u) => (
                <tr key={u.id}>
                  {/* Opens the same User Details modal in place, not a new tab — unlike openUserDetails()
                      (used from the Auctions tab / catalogue cards), clicking a row already on this
                      Users page doesn't need to preserve a "page admin was on", since it's this one. */}
                  <td><button type="button" className="linkbtn" onClick={() => setWalletUser(u)}>{u.email}</button></td>
                  <td><button type="button" className="linkbtn" onClick={() => setWalletUser(u)}>{u.mobileNumber}</button></td>
                  <td>{u.role}</td>
                  <td>{u.subscriptionTier}</td>
                  <td>{u.kycStatus}</td>
                  <td>{money(u.walletAvailableBalance)}</td>
                  <td>{moneyCompact(u.walletCreditLimit)}</td>
                  <td>{u.activeBidCount}</td>
                  <td>{u.active ? <span className="ok" style={{ margin: 0, display: 'inline-block' }}>Active</span>
                                : <span className="error" style={{ margin: 0, display: 'inline-block' }}>Suspended</span>}</td>
                  <td style={{ display: 'flex', gap: 6 }}>
                    <button type="button" className="btn ghost sm" disabled={busyId === u.id} onClick={() => toggle(u)}>
                      {busyId === u.id ? '…' : u.active ? 'Suspend' : 'Reactivate'}
                    </button>
                  </td>
                </tr>
              ))}
              {users.length === 0 && <tr><td colSpan={10} className="muted">No users match.</td></tr>}
            </tbody>
          </table>
        </div>
        <Pager page={page} totalPages={totalPages} onChange={setPage} />
      </div>

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

/** Everything about one user an admin might need in one place: profile, wallet + locked EMD
 *  holds (each individually releasable), and every auction they've ever bid on — so "how many
 *  items is this person bidding on right now" is a glance, not a hunt across several screens. */
function WalletModal({ user, onClose, onReleased }: {
  user: AdminUser
  onClose: () => void
  onReleased: (res: ReleaseHoldResult) => void
}) {
  const [holds, setHolds] = useState<AdminHold[]>([])
  const [bids, setBids] = useState<AdminUserBid[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [confirmingId, setConfirmingId] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([getAdminUserHolds(user.id), getAdminUserBids(user.id)])
      .then(([h, b]) => { setHolds(h); setBids(b) })
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setLoading(false))
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

  const openBidCount = bids.filter((b) => b.auctionStatus === 'OPEN').length

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-card" style={{ maxWidth: 720 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>User Details — {user.email}</h3>
          <button type="button" className="modal-close" onClick={onClose} aria-label="Close">✕</button>
        </div>
        <div className="modal-body">
          {error && <div className="error">{error}</div>}

          <div className="udetail-section" style={{ animationDelay: '40ms' }}>
            <h4 style={{ margin: '0 0 8px' }}>Profile</h4>
            <div className="stat" style={{ marginBottom: 18 }}>
              <div><div className="k">Mobile</div><div className="v" style={{ fontSize: 15 }}>{user.mobileNumber}</div></div>
              <div><div className="k">Role</div><div className="v" style={{ fontSize: 15 }}>{user.role}</div></div>
              <div><div className="k">Subscription</div><div className="v" style={{ fontSize: 15 }}>
                {user.subscriptionTier}{user.subscriptionExpiresAt ? ` · until ${formatDateTimeShort(user.subscriptionExpiresAt)}` : ''}
              </div></div>
              <div><div className="k">KYC</div><div className="v" style={{ fontSize: 15 }}>{user.kycStatus}</div></div>
              <div><div className="k">Verified</div><div className="v" style={{ fontSize: 15 }}>
                {[user.emailVerified && 'Email', user.mobileVerified && 'Mobile'].filter(Boolean).join(', ') || 'None'}
              </div></div>
              <div><div className="k">Joined</div><div className="v" style={{ fontSize: 15 }}>{formatDateTimeShort(user.createdAt)}</div></div>
            </div>
          </div>

          <div className="udetail-section" style={{ animationDelay: '130ms' }}>
            <h4 style={{ margin: '0 0 8px' }}>Wallet</h4>
            <div className="stat" style={{ marginBottom: 16 }}>
              <div><div className="k">Deposit (Available)</div><div className="v"><AnimatedNumber value={user.walletAvailableBalance} format={money} /></div></div>
              <div><div className="k">Held (Locked)</div><div className="v"><AnimatedNumber value={user.walletHeldBalance} format={money} /></div></div>
              <div><div className="k">Credit Limit</div><div className="v"><AnimatedNumber value={user.walletCreditLimit} format={moneyCompact} /></div></div>
            </div>
            {loading ? (
              <p className="muted">Loading…</p>
            ) : holds.length === 0 ? (
              <p className="muted">No locked wallet holds for this user.</p>
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

          <div className="udetail-section" style={{ animationDelay: '220ms' }}>
            <h4 style={{ margin: '20px 0 8px' }}>
              Bidding Activity {!loading && <span className="muted" style={{ fontWeight: 400, fontSize: 13 }}>— {openBidCount} item{openBidCount === 1 ? '' : 's'} currently bidding on, {bids.length} total</span>}
            </h4>
            {loading ? (
              <p className="muted">Loading…</p>
            ) : bids.length === 0 ? (
              <p className="muted">This user hasn't bid on anything yet.</p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table className="admin-table">
                  <thead><tr><th>Item</th><th>Category</th><th>Your Bid</th><th>Current Highest</th><th>Status</th><th>Auction</th></tr></thead>
                  <tbody>
                    {bids.map((b) => {
                      const st = bidRowStatus(b)
                      return (
                        <tr key={b.auctionId}>
                          <td>{b.listingTitle}</td>
                          <td>{b.categoryName}</td>
                          <td>{money(b.yourBid)}</td>
                          <td>{money(b.currentHighestBid)}</td>
                          <td><span className={st.cls} style={{ margin: 0, display: 'inline-block' }}>{st.label}</span></td>
                          <td>{b.auctionStatus}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
