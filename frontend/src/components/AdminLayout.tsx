import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth'
import AddStockModal from './AddStockModal'

type NavItem = { to: string; label: string; icon: string; end?: boolean }
type NavSection = { label: string; items: NavItem[] }

const NAV: NavSection[] = [
  {
    label: 'Overview',
    items: [
      { to: '/admin', label: 'Dashboard', icon: '\u{1F3E0}', end: true },
      { to: '/admin/users', label: 'Users', icon: '\u{1F465}' },
      { to: '/admin/kyc', label: 'KYC', icon: '\u{1FAAA}' },
    ],
  },
  {
    label: 'Catalog',
    items: [
      { to: '/admin/listings', label: 'Listings', icon: '\u{1F4E6}' },
      { to: '/admin/categories', label: 'Categories', icon: '\u{1F5C2}️' },
      { to: '/admin/auctions', label: 'Auctions', icon: '\u{1F528}' },
    ],
  },
  {
    label: 'Finance',
    items: [
      { to: '/admin/plans', label: 'Plans', icon: '\u{1F4CB}' },
      { to: '/admin/holds', label: 'Credit & Holds', icon: '\u{1F512}' },
      { to: '/admin/payments', label: 'Payments', icon: '\u{1F4B3}' },
      { to: '/admin/withdrawals', label: 'Withdrawals', icon: '\u{1F3E6}' },
      { to: '/admin/transactions', label: 'Transactions', icon: '\u{1F4C8}' },
      { to: '/admin/referrals', label: 'Referrals', icon: '\u{1F517}' },
    ],
  },
  {
    label: 'Operations',
    items: [
      { to: '/admin/disputes', label: 'Disputes', icon: '⚖️' },
      { to: '/admin/audit-log', label: 'Audit Log', icon: '\u{1F4DC}' },
    ],
  },
  {
    label: 'Support',
    items: [
      { to: '/admin/chat', label: 'Chat', icon: '\u{1F4AC}' },
    ],
  },
  {
    label: 'Updates',
    items: [
      { to: '/admin/banners', label: 'Banners', icon: '\u{1F5BC}️' },
    ],
  },
  {
    label: 'Config',
    items: [
      { to: '/admin/settings', label: 'Settings', icon: '⚙️' },
    ],
  },
]

/** Sidebar shell wrapping every /admin/* route — sectioned nav (Overview / Catalog / Operations /
 *  Config), matching the reference admin panel's persistent-sidebar layout pattern, while keeping
 *  SwipeAuctions' own palette/typography and its own site-wide Header above it. Owns the route
 *  guard (redirect to /admin-login if not signed in as ADMIN) so every nested route is protected,
 *  not just the index page — the old single-page AdminDashboardPage only guarded itself. */
export default function AdminLayout() {
  const { isAuthenticated, role, email } = useAuth()
  const navigate = useNavigate()
  const [addStockOpen, setAddStockOpen] = useState(false)
  const [refreshTick, setRefreshTick] = useState(0)

  useEffect(() => {
    if (!isAuthenticated || role !== 'ADMIN') navigate('/admin-login')
  }, [isAuthenticated, role, navigate])

  if (role !== 'ADMIN') return null

  return (
    <div className="admin-shell">
      <aside className="admin-sidebar">
        {NAV.map((section) => (
          <div key={section.label} className="admin-sidebar-section">
            <div className="admin-sidebar-label">{section.label}</div>
            {section.items.map((item) => (
              <NavLink key={item.to} to={item.to} end={item.end}
                       className={({ isActive }) => 'admin-nav-link' + (isActive ? ' active' : '')}>
                <span className="icon">{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
          </div>
        ))}
        <div className="admin-sidebar-section">
          <div className="admin-sidebar-label">Signed in</div>
          <p className="muted" style={{ fontSize: 12, padding: '0 10px', wordBreak: 'break-all' }}>{email}</p>
          <button type="button" className="btn ghost sm" style={{ margin: '4px 10px' }} onClick={() => setAddStockOpen(true)}>
            + Add Stock
          </button>
        </div>
      </aside>
      <div className="admin-main">
        <Outlet context={{ refreshTick } satisfies AdminOutletContext} />
      </div>
      {addStockOpen && (
        <AddStockModal onClose={() => setAddStockOpen(false)} onCreated={() => setRefreshTick((t) => t + 1)} />
      )}
    </div>
  )
}

export type AdminOutletContext = { refreshTick: number }
