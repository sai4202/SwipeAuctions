import { Routes, Route, Link, useNavigate } from 'react-router-dom'
import { useAuth } from './auth'
import BrowsePage from './pages/BrowsePage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import AuctionDetailPage from './pages/AuctionDetailPage'
import WalletPage from './pages/WalletPage'

export default function App() {
  const { isAuthenticated, email, signOut } = useAuth()
  const navigate = useNavigate()

  return (
    <div>
      <header className="nav">
        <Link to="/" className="brand"><span className="logo">S</span> SwipeAuctions</Link>
        <nav className="nav-links">
          <Link to="/">Browse</Link>
          {isAuthenticated && <Link to="/wallet">Wallet</Link>}
          {isAuthenticated ? (
            <>
              <span className="who">{email}</span>
              <button className="btn ghost" onClick={() => { signOut(); navigate('/') }}>Sign out</button>
            </>
          ) : (
            <>
              <Link to="/login">Login</Link>
              <Link to="/register" className="btn">Register</Link>
            </>
          )}
        </nav>
      </header>
      <main className="container">
        <Routes>
          <Route path="/" element={<BrowsePage />} />
          <Route path="/auctions/:id" element={<AuctionDetailPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/wallet" element={<WalletPage />} />
        </Routes>
      </main>
    </div>
  )
}
