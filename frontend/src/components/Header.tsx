import { Link, NavLink, useNavigate, useLocation } from 'react-router-dom'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useAuth } from '../auth'
import { useWallet } from '../WalletContext'
import { useFloatingTabs } from '../FloatingTabsContext'
import { useTheme } from '../ThemeContext'
import { logout, adminLogout } from '../api'
import { moneyCompact } from '../util'

export default function Header() {
  const { isAuthenticated, role, lastRole, kycCompleted, signOut } = useAuth()
  const { wallet } = useWallet()
  const { theme, toggleTheme } = useTheme()
  const credit = wallet?.availableCreditLimit ?? null
  const { tabCount, maxTabs, addTab } = useFloatingTabs()
  const navigate = useNavigate()
  const location = useLocation()
  const isAdmin = role === 'ADMIN'
  // The auctions/swipe-stock grid reads its query from the URL's `q` param — search from anywhere
  // in the header by writing that same param, preserving whatever other filters are already applied
  // if we're already on one of those pages.
  const isBrowseRoute = location.pathname === '/auctions' || location.pathname === '/swipe-stock'
  const [q, setQ] = useState('')
  useEffect(() => {
    setQ(isBrowseRoute ? new URLSearchParams(location.search).get('q') || '' : '')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname, location.search])
  const submitSearch = (e: FormEvent) => {
    e.preventDefault()
    const target = location.pathname === '/swipe-stock' ? '/swipe-stock' : '/auctions'
    const params = new URLSearchParams(isBrowseRoute ? location.search : '')
    if (q) params.set('q', q); else params.delete('q')
    navigate(`${target}${params.toString() ? `?${params.toString()}` : ''}`)
  }
  // Whoever most recently signed out (manual logout or session expiry) should land back on their own
  // login page, not always the customer one.
  const loginPath = lastRole === 'ADMIN' ? '/admin-login' : '/login'
  const [profileOpen, setProfileOpen] = useState(false)
  const profileRef = useRef<HTMLDivElement>(null)
  const [mobileOpen, setMobileOpen] = useState(false)
  const closeMobile = () => setMobileOpen(false)
  const tabsFull = tabCount >= maxTabs
  // Each mini tab is a same-origin iframe replica of the whole site — hide "Add Tab" inside those so
  // it's only ever offered from the real top-level page, not recursively from within a mini tab.
  const isTopLevel = typeof window !== 'undefined' && window.self === window.top

  // Invalidate the session server-side first (best-effort — an already-expired token shouldn't block
  // clearing local state) so the admin single-session cap doesn't lock the next login out.
  const doLogout = async () => {
    try { await (isAdmin ? adminLogout() : logout()) } catch { /* token may already be invalid */ }
    signOut()
    navigate('/')
  }

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (profileRef.current && !profileRef.current.contains(e.target as Node)) setProfileOpen(false)
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  useEffect(() => {
    function onResize() { if (window.innerWidth > 900) setMobileOpen(false) }
    function onKey(e: KeyboardEvent) { if (e.key === 'Escape') setMobileOpen(false) }
    window.addEventListener('resize', onResize)
    window.addEventListener('keydown', onKey)
    return () => { window.removeEventListener('resize', onResize); window.removeEventListener('keydown', onKey) }
  }, [])

  return (
    <>
      <header className="header">
        <Link to="/" className="brand">
          <span className="mark">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M14 3l7 7" /><path d="M18 7l-9 9" /><path d="M3 21l5-5" /><path d="M8.5 11.5l4 4" />
            </svg>
          </span>
          <span className="name">SWIPE <span>AUCTIONS</span></span>
        </Link>

        <nav className="mainnav">
          <NavLink to="/" end>Home</NavLink>
          <NavLink to="/auctions">Auctions</NavLink>
          <NavLink to="/swipe-stock">Swipe Stock</NavLink>
          {isAuthenticated && isTopLevel && (
            <button
              type="button"
              className="add-tab-btn"
              onClick={() => addTab('/auctions')}
              disabled={tabsFull}
              title={tabsFull ? `Maximum ${maxTabs} mini tabs open` : 'Open a movable mini tab'}
            >
              ＋ Add Tab
            </button>
          )}
          {isAdmin && <NavLink to="/admin">Admin</NavLink>}
        </nav>

        <div className="header-right">
          <form className="header-search" onSubmit={submitSearch}>
            <span className="header-search-icon">🔍</span>
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder="Search auctions…"
              aria-label="Search auctions"
            />
          </form>
          <button
            type="button"
            className="icon-btn theme-toggle"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
            title={theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
          >
            {theme === 'dark' ? (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <circle cx="12" cy="12" r="4" />
                <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
              </svg>
            ) : (
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" />
              </svg>
            )}
          </button>
          {isAuthenticated && !isAdmin && !kycCompleted && (
            <Link to="/kyc" className="link-login" style={{ color: 'var(--orange, #d97706)', whiteSpace: 'nowrap' }}>Complete KYC</Link>
          )}
          {isAuthenticated && credit != null && (
            <Link to="/wallet" className="credit"><span className="gdot" />Available Credit <b>{moneyCompact(credit)}</b></Link>
          )}
          {isAuthenticated && !isAdmin && (
            <div className="account-menu" ref={profileRef}>
              <button type="button" className="profile-trigger" onClick={() => setProfileOpen((o) => !o)} aria-label="Profile menu">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="12" cy="8" r="4" /><path d="M4 20c0-4.4 3.6-7 8-7s8 2.6 8 7" />
                </svg>
                <span className="caret">▾</span>
              </button>
              {profileOpen && (
                <div className="account-dropdown">
                  <NavLink to="/profile" className="account-dropdown-item" onClick={() => setProfileOpen(false)}>Profile</NavLink>
                  <NavLink to="/my-wins" className="account-dropdown-item" onClick={() => setProfileOpen(false)}>My Wins</NavLink>
                  <NavLink to="/my-transactions" className="account-dropdown-item" onClick={() => setProfileOpen(false)}>My Transactions</NavLink>
                  <NavLink to="/subscription" className="account-dropdown-item" onClick={() => setProfileOpen(false)}>Subscription</NavLink>
                  <NavLink to="/wishlist" className="account-dropdown-item" onClick={() => setProfileOpen(false)}>Wishlist</NavLink>
                  <NavLink to="/wallet" className="account-dropdown-item" onClick={() => setProfileOpen(false)}>Wallet</NavLink>
                  <NavLink to="/about" className="account-dropdown-item" onClick={() => setProfileOpen(false)}>About Us</NavLink>
                  <NavLink to="/contact" className="account-dropdown-item" onClick={() => setProfileOpen(false)}>Contact Us</NavLink>
                  <button type="button" className="account-dropdown-item" onClick={() => { setProfileOpen(false); doLogout() }}>Logout</button>
                </div>
              )}
            </div>
          )}
          {isAdmin && (
            <button className="btn ghost sm" onClick={doLogout}>Logout</button>
          )}
          {!isAuthenticated && (
            <Link to={loginPath} className="link-login">Login</Link>
          )}
        </div>

        <button
          type="button"
          className="hamburger-btn"
          aria-label="Menu"
          aria-expanded={mobileOpen}
          onClick={() => setMobileOpen((o) => !o)}
        >
          <span /><span /><span />
        </button>
      </header>

      <nav className={`mobile-menu ${mobileOpen ? 'open' : ''}`}>
        <form className="mobile-search" onSubmit={(e) => { submitSearch(e); closeMobile() }}>
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search auctions…"
            aria-label="Search auctions"
          />
        </form>
        <NavLink to="/" end onClick={closeMobile}>Home</NavLink>
        <NavLink to="/auctions" onClick={closeMobile}>Auctions</NavLink>
        <NavLink to="/swipe-stock" onClick={closeMobile}>Swipe Stock</NavLink>
        {isAuthenticated && isTopLevel && (
          <button type="button" disabled={tabsFull} onClick={() => { addTab('/auctions'); closeMobile() }}>
            ＋ Add Tab{tabsFull ? ` (max ${maxTabs})` : ''}
          </button>
        )}
        {isAdmin && <NavLink to="/admin" onClick={closeMobile}>Admin</NavLink>}
        {isAuthenticated && !isAdmin && !kycCompleted && (
          <NavLink to="/kyc" onClick={closeMobile} style={{ color: 'var(--orange, #d97706)' }}>Complete KYC</NavLink>
        )}
        {isAuthenticated && !isAdmin && (
          <>
            <div className="mobile-menu-divider" />
            <NavLink to="/profile" onClick={closeMobile}>Profile</NavLink>
            <NavLink to="/my-wins" onClick={closeMobile}>My Wins</NavLink>
            <NavLink to="/my-transactions" onClick={closeMobile}>My Transactions</NavLink>
            <NavLink to="/subscription" onClick={closeMobile}>Subscription</NavLink>
            <NavLink to="/wishlist" onClick={closeMobile}>Wishlist</NavLink>
            {credit != null && <NavLink to="/wallet" onClick={closeMobile}>Wallet — <b>{moneyCompact(credit)}</b></NavLink>}
            <NavLink to="/about" onClick={closeMobile}>About Us</NavLink>
            <NavLink to="/contact" onClick={closeMobile}>Contact Us</NavLink>
          </>
        )}
        <div className="mobile-menu-divider" />
        <button type="button" onClick={toggleTheme}>{theme === 'dark' ? '☀ Light mode' : '☾ Dark mode'}</button>
        {isAuthenticated ? (
          <button type="button" onClick={() => { doLogout(); closeMobile() }}>Logout</button>
        ) : (
          <NavLink to={loginPath} onClick={closeMobile}>Login</NavLink>
        )}
      </nav>

      <div className="side-tabs">
        <Link to="/swipe-stock">Swipe Stock</Link>
      </div>
    </>
  )
}
